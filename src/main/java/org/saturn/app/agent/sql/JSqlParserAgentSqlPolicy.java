package org.saturn.app.agent.sql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.TableStatement;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.saturn.app.agent.AgentSqlConfig;
import org.saturn.app.agent.persistence.AgentDatabaseSchema;

public final class JSqlParserAgentSqlPolicy implements AgentSqlPolicy {
  private static final Set<String> PARSER_UNSUPPORTED_STATEMENTS =
      Set.of("attach", "detach", "pragma", "vacuum");
  private static final Set<String> FORBIDDEN_FUNCTIONS =
      Set.of("load_extension", "readfile", "writefile");

  private final AgentSqlConfig config;

  public JSqlParserAgentSqlPolicy(AgentSqlConfig config) {
    this.config = Objects.requireNonNull(config, "config");
  }

  @Override
  public ValidatedAgentSql validate(String sql, AgentDatabaseSchema schema) {
    Objects.requireNonNull(schema, "schema");
    if (sql == null || sql.isBlank()) {
      throw rejection(AgentSqlErrorCode.EMPTY_SQL, "SQL must not be blank");
    }
    if (sql.codePointCount(0, sql.length()) > config.maxSqlChars()) {
      throw rejection(AgentSqlErrorCode.SQL_TOO_LONG, "SQL exceeds the configured limit");
    }

    Statement statement = parseSingleStatement(sql);
    if (!(statement instanceof Select select)
        || select instanceof Values
        || select instanceof TableStatement) {
      throw rejection(AgentSqlErrorCode.FORBIDDEN_STATEMENT, "Only SELECT statements are allowed");
    }
    requireReadOnlyWithItems(select);

    PolicyTablesNamesFinder finder = new PolicyTablesNamesFinder();
    Set<String> referencedTables = finder.getTables(statement);
    Set<String> allowedTables =
        schema.tableNames().stream()
            .map(JSqlParserAgentSqlPolicy::normalizeIdentifier)
            .collect(Collectors.toUnmodifiableSet());
    for (String table : referencedTables) {
      String normalized = normalizeIdentifier(table);
      if (normalized.startsWith("sqlite_") || !allowedTables.contains(normalized)) {
        throw rejection(AgentSqlErrorCode.FORBIDDEN_TABLE, "SQL references a forbidden table");
      }
    }

    return new ValidatedAgentSql(sql, fingerprint(sql));
  }

  private Statement parseSingleStatement(String sql) {
    try {
      Statements statements = CCJSqlParserUtil.parseStatements(sql);
      List<Statement> parsed = statements.getStatements();
      if (parsed.size() != 1) {
        throw rejection(
            AgentSqlErrorCode.FORBIDDEN_STATEMENT, "Exactly one SQL statement is allowed");
      }
      return parsed.getFirst();
    } catch (JSQLParserException exception) {
      AgentSqlErrorCode code =
          PARSER_UNSUPPORTED_STATEMENTS.contains(leadingKeyword(sql))
              ? AgentSqlErrorCode.FORBIDDEN_STATEMENT
              : AgentSqlErrorCode.MALFORMED_SQL;
      throw new AgentSqlPolicyException(code, "SQL could not be parsed", exception);
    }
  }

  private void requireReadOnlyWithItems(Select select) {
    List<WithItem<?>> withItems = select.getWithItemsList();
    if (withItems == null) {
      return;
    }
    for (WithItem<?> withItem : withItems) {
      Select withSelect = withItem.getSelect();
      if (withSelect == null) {
        throw rejection(
            AgentSqlErrorCode.FORBIDDEN_STATEMENT, "Data-changing CTEs are not allowed");
      }
      requireReadOnlyWithItems(withSelect);
    }
  }

  private static String fingerprint(String sql) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(sql.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String leadingKeyword(String sql) {
    String stripped = sql.stripLeading();
    int end = 0;
    while (end < stripped.length() && Character.isLetter(stripped.charAt(end))) {
      end++;
    }
    return stripped.substring(0, end).toLowerCase(Locale.ROOT);
  }

  private static String normalizeIdentifier(String identifier) {
    String normalized = identifier.strip();
    if (normalized.length() >= 2) {
      char first = normalized.charAt(0);
      char last = normalized.charAt(normalized.length() - 1);
      if ((first == '"' && last == '"')
          || (first == '`' && last == '`')
          || (first == '[' && last == ']')) {
        normalized = normalized.substring(1, normalized.length() - 1);
      }
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  private static AgentSqlPolicyException rejection(AgentSqlErrorCode code, String message) {
    return new AgentSqlPolicyException(code, message);
  }

  private static final class PolicyTablesNamesFinder extends TablesNamesFinder<Void> {
    @Override
    public <S> Void visit(Function function, S context) {
      requireSafeFunction(function.getName());
      return super.visit(function, context);
    }

    @Override
    public <S> Void visit(TableFunction tableFunction, S context) {
      requireSafeFunction(tableFunction.getFunction().getName());
      return super.visit(tableFunction, context);
    }

    private void requireSafeFunction(String name) {
      if (name == null) {
        return;
      }
      String normalized = normalizeIdentifier(name);
      if (FORBIDDEN_FUNCTIONS.contains(normalized) || normalized.startsWith("pragma_")) {
        throw rejection(
            AgentSqlErrorCode.FORBIDDEN_FUNCTION, "SQL references a forbidden function");
      }
    }
  }
}
