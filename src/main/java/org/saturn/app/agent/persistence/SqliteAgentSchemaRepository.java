package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Column;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.ForeignKey;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Index;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Table;

public final class SqliteAgentSchemaRepository implements AgentSchemaRepository {
  private final SqliteReadOnlyConnectionFactory connectionFactory;

  public SqliteAgentSchemaRepository(SqliteReadOnlyConnectionFactory connectionFactory) {
    this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
  }

  @Override
  public AgentDatabaseSchema describe() {
    try (Connection connection = connectionFactory.open()) {
      List<Table> tables = new ArrayList<>();
      for (String tableName : readTableNames(connection)) {
        tables.add(
            new Table(
                tableName,
                readColumns(connection, tableName),
                readIndexes(connection, tableName),
                readForeignKeys(connection, tableName)));
      }
      return new AgentDatabaseSchema(tables);
    } catch (SQLException exception) {
      throw new AgentPersistenceException("Unable to inspect agent database schema", exception);
    }
  }

  private List<String> readTableNames(Connection connection) throws SQLException {
    List<String> names = new ArrayList<>();
    try (ResultSet resultSet =
        connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
      while (resultSet.next()) {
        String schema = resultSet.getString("TABLE_SCHEM");
        if ("public".equalsIgnoreCase(schema)) {
          names.add(resultSet.getString("TABLE_NAME"));
        }
      }
    }
    return names;
  }

  private List<Column> readColumns(Connection connection, String tableName) throws SQLException {
    List<Column> columns = new ArrayList<>();
    try (ResultSet resultSet =
        connection.getMetaData().getColumns(null, "public", tableName, "%")) {
      while (resultSet.next()) {
        boolean primaryKey =
            isPrimaryKey(connection, tableName, resultSet.getString("COLUMN_NAME"));
        boolean nullable =
            resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls && !primaryKey;
        columns.add(
            new Column(
                resultSet.getInt("ORDINAL_POSITION") - 1,
                resultSet.getString("COLUMN_NAME"),
                resultSet.getString("TYPE_NAME"),
                nullable,
                primaryKey));
      }
    }
    return columns;
  }

  private List<Index> readIndexes(Connection connection, String tableName) throws SQLException {
    List<IndexHeader> headers = new ArrayList<>();
    try (ResultSet resultSet =
        connection.getMetaData().getIndexInfo(null, "public", tableName, false, false)) {
      while (resultSet.next()) {
        String indexName = resultSet.getString("INDEX_NAME");
        if (indexName != null
            && headers.stream().noneMatch(header -> header.name().equals(indexName))) {
          headers.add(new IndexHeader(indexName, !resultSet.getBoolean("NON_UNIQUE")));
        }
      }
    }

    List<Index> indexes = new ArrayList<>();
    for (IndexHeader header : headers) {
      List<String> columns = new ArrayList<>();
      try (ResultSet resultSet =
          connection.getMetaData().getIndexInfo(null, "public", tableName, false, false)) {
        while (resultSet.next()) {
          String columnName = resultSet.getString("COLUMN_NAME");
          if (header.name().equals(resultSet.getString("INDEX_NAME")) && columnName != null) {
            columns.add(columnName);
          }
        }
      }
      indexes.add(new Index(header.name(), header.unique(), columns));
    }
    return indexes;
  }

  private List<ForeignKey> readForeignKeys(Connection connection, String tableName)
      throws SQLException {
    List<ForeignKey> foreignKeys = new ArrayList<>();
    try (ResultSet resultSet =
        connection.getMetaData().getImportedKeys(null, "public", tableName)) {
      while (resultSet.next()) {
        foreignKeys.add(
            new ForeignKey(
                resultSet.getInt("KEY_SEQ"),
                resultSet.getInt("KEY_SEQ"),
                resultSet.getString("PKTABLE_NAME"),
                resultSet.getString("FKCOLUMN_NAME"),
                resultSet.getString("PKCOLUMN_NAME"),
                String.valueOf(resultSet.getShort("UPDATE_RULE")),
                String.valueOf(resultSet.getShort("DELETE_RULE")),
                ""));
      }
    }
    return foreignKeys;
  }

  private boolean isPrimaryKey(Connection connection, String tableName, String columnName)
      throws SQLException {
    try (ResultSet resultSet = connection.getMetaData().getPrimaryKeys(null, "public", tableName)) {
      while (resultSet.next()) {
        if (columnName.equalsIgnoreCase(resultSet.getString("COLUMN_NAME"))) {
          return true;
        }
      }
      return false;
    }
  }

  private record IndexHeader(String name, boolean unique) {}
}
