package org.saturn.app.agent.persistence;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.sql.AgentSqlErrorCode;
import org.saturn.app.agent.sql.ValidatedAgentSql;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteLimits;

@Slf4j
public final class SqliteAgentSqlRepository implements AgentSqlRepository {
  private static final int PROGRESS_HANDLER_INSTRUCTIONS = 100;
  private static final int MAX_EXPRESSION_DEPTH = 100;
  private static final int MAX_COMPOUND_SELECTS = 20;

  private final SqliteReadOnlyConnectionFactory connectionFactory;
  private final Gson gson = new Gson();

  public SqliteAgentSqlRepository(SqliteReadOnlyConnectionFactory connectionFactory) {
    this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
  }

  @Override
  public AgentSqlResult execute(ValidatedAgentSql sql, AgentSqlConfig config) {
    Objects.requireNonNull(sql, "sql");
    Objects.requireNonNull(config, "config");
    long startedAt = System.nanoTime();
    try (Connection connection = connectionFactory.open()) {
      applyLimits(connection, config);
      long timeoutNanos = timeoutNanos(config.timeout());
      ProgressHandler.setHandler(
          connection,
          PROGRESS_HANDLER_INSTRUCTIONS,
          new ProgressHandler() {
            @Override
            protected int progress() {
              return System.nanoTime() - startedAt >= timeoutNanos ? 1 : 0;
            }
          });
      AgentSqlResult result;
      try {
        result = executeQuery(connection, sql.sql(), config, startedAt);
      } finally {
        ProgressHandler.clearHandler(connection);
      }
      log.info(
          "Agent SQL completed fingerprint={} elapsedMs={} rows={} outcome=success",
          safeFingerprint(sql.fingerprint()),
          result.elapsedMillis(),
          result.rows().size());
      return result;
    } catch (SQLException exception) {
      AgentSqlErrorCode code = classify(exception);
      log.warn(
          "Agent SQL failed fingerprint={} outcome={}", safeFingerprint(sql.fingerprint()), code);
      throw new AgentPersistenceException(code, safeMessage(code), exception);
    }
  }

  private AgentSqlResult executeQuery(
      Connection connection, String sql, AgentSqlConfig config, long startedAt)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.setMaxRows(maxRowsWithSentinel(config.maxRows()));
      if (!statement.execute(sql)) {
        throw new SQLException("Statement did not produce a result set");
      }
      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        if (columnCount > config.maxColumns()) {
          throw new AgentPersistenceException(
              AgentSqlErrorCode.RESULT_TOO_LARGE, "Agent SQL returned too many columns", null);
        }

        List<String> columns = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
          columns.add(metadata.getColumnLabel(column));
        }

        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (resultSet.next()) {
          if (rows.size() >= config.maxRows()) {
            truncated = true;
            break;
          }
          List<Object> row = new ArrayList<>(columnCount);
          for (int column = 1; column <= columnCount; column++) {
            BoundedValue value = boundedValue(resultSet.getObject(column), config.maxCellChars());
            row.add(value.value());
            truncated |= value.truncated();
          }
          rows.add(row);
        }

        long elapsedMillis = elapsedMillis(startedAt);
        return boundResultSize(columns, rows, truncated, elapsedMillis, config.maxResultChars());
      }
    }
  }

  private void applyLimits(Connection connection, AgentSqlConfig config) throws SQLException {
    if (!(connection instanceof SQLiteConnection sqlite)) {
      throw new SQLException("Expected an SQLite connection");
    }
    sqlite.setBusyTimeout(busyTimeoutMillis(config.timeout()));
    sqlite.setLimit(
        SQLiteLimits.SQLITE_LIMIT_LENGTH,
        utf8ByteLimit(Math.max(config.maxCellChars(), config.maxResultChars())));
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_SQL_LENGTH, utf8ByteLimit(config.maxSqlChars()));
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_COLUMN, config.maxColumns());
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_EXPR_DEPTH, MAX_EXPRESSION_DEPTH);
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_COMPOUND_SELECT, MAX_COMPOUND_SELECTS);
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_ATTACHED, 0);
    sqlite.setLimit(SQLiteLimits.SQLITE_LIMIT_WORKER_THREADS, 0);
  }

  private AgentSqlResult boundResultSize(
      List<String> columns,
      List<List<Object>> rows,
      boolean truncated,
      long elapsedMillis,
      int maxResultChars) {
    while (true) {
      AgentSqlResult result = new AgentSqlResult(columns, rows, truncated, elapsedMillis);
      String json = gson.toJson(result);
      if (json.codePointCount(0, json.length()) <= maxResultChars) {
        return result;
      }
      if (rows.isEmpty()) {
        throw new AgentPersistenceException(
            AgentSqlErrorCode.RESULT_TOO_LARGE,
            "Agent SQL metadata exceeds the result limit",
            null);
      }
      rows.removeLast();
      truncated = true;
    }
  }

  private BoundedValue boundedValue(Object value, int maxChars) {
    if (value == null) {
      return new BoundedValue(null, false);
    }
    if (value instanceof byte[] bytes) {
      return boundedBlob(bytes, maxChars);
    }
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return new BoundedValue(((Number) value).longValue(), false);
    }
    if (value instanceof Number number) {
      return new BoundedValue(number.doubleValue(), false);
    }
    return boundedText(value.toString(), maxChars);
  }

  private BoundedValue boundedBlob(byte[] bytes, int maxChars) {
    String encoded = Base64.getEncoder().encodeToString(bytes);
    if (encoded.length() <= maxChars) {
      return new BoundedValue(encoded, false);
    }
    int byteLimit = Math.min(bytes.length, Math.max(0, maxChars / 4 * 3));
    String bounded = Base64.getEncoder().encodeToString(Arrays.copyOf(bytes, byteLimit));
    while (bounded.length() > maxChars && byteLimit > 0) {
      bounded = Base64.getEncoder().encodeToString(Arrays.copyOf(bytes, --byteLimit));
    }
    return new BoundedValue(bounded, true);
  }

  private BoundedValue boundedText(String value, int maxChars) {
    int length = value.codePointCount(0, value.length());
    if (length <= maxChars) {
      return new BoundedValue(value, false);
    }
    return new BoundedValue(value.substring(0, value.offsetByCodePoints(0, maxChars)), true);
  }

  private AgentSqlErrorCode classify(SQLException exception) {
    if (exception instanceof SQLiteException sqliteException) {
      SQLiteErrorCode resultCode = sqliteException.getResultCode();
      if (resultCode == SQLiteErrorCode.SQLITE_INTERRUPT
          || resultCode == SQLiteErrorCode.SQLITE_BUSY
          || resultCode == SQLiteErrorCode.SQLITE_BUSY_TIMEOUT
          || resultCode == SQLiteErrorCode.SQLITE_BUSY_RECOVERY
          || resultCode == SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT
          || resultCode == SQLiteErrorCode.SQLITE_LOCKED
          || resultCode == SQLiteErrorCode.SQLITE_LOCKED_SHAREDCACHE) {
        return AgentSqlErrorCode.TIMEOUT;
      }
      if (resultCode == SQLiteErrorCode.SQLITE_TOOBIG) {
        return AgentSqlErrorCode.RESULT_TOO_LARGE;
      }
    }
    String message = exception.getMessage();
    if (message != null && message.toLowerCase(Locale.ROOT).contains("too many columns")) {
      return AgentSqlErrorCode.RESULT_TOO_LARGE;
    }
    return AgentSqlErrorCode.EXECUTION_FAILED;
  }

  private String safeMessage(AgentSqlErrorCode code) {
    return switch (code) {
      case TIMEOUT -> "Agent SQL query timed out";
      case RESULT_TOO_LARGE -> "Agent SQL result exceeded a configured limit";
      default -> "Agent SQL execution failed";
    };
  }

  private long timeoutNanos(Duration timeout) {
    try {
      return timeout.toNanos();
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private int busyTimeoutMillis(Duration timeout) {
    long millis;
    try {
      millis = timeout.toMillis();
    } catch (ArithmeticException exception) {
      millis = Integer.MAX_VALUE;
    }
    return (int) Math.clamp(millis, 1, Integer.MAX_VALUE);
  }

  private long elapsedMillis(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
  }

  private int maxRowsWithSentinel(int maxRows) {
    return maxRows == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxRows + 1;
  }

  private int utf8ByteLimit(int maxChars) {
    return maxChars > Integer.MAX_VALUE / 4 ? Integer.MAX_VALUE : maxChars * 4;
  }

  private String safeFingerprint(String fingerprint) {
    return fingerprint != null && fingerprint.matches("[0-9a-f]{64}") ? fingerprint : "invalid";
  }

  private record BoundedValue(Object value, boolean truncated) {}
}
