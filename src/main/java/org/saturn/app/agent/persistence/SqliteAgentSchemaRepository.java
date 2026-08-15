package org.saturn.app.agent.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Column;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.ForeignKey;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Index;
import org.saturn.app.agent.persistence.AgentDatabaseSchema.Table;

public final class SqliteAgentSchemaRepository implements AgentSchemaRepository {
  private static final String USER_TABLES_SQL =
      """
      SELECT name
      FROM sqlite_master
      WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
      ORDER BY name
      """;

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
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(USER_TABLES_SQL)) {
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }
    }
    return names;
  }

  private List<Column> readColumns(Connection connection, String tableName) throws SQLException {
    List<Column> columns = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("PRAGMA table_info(" + quoteIdentifier(tableName) + ")")) {
      while (resultSet.next()) {
        boolean primaryKey = resultSet.getInt("pk") > 0;
        boolean nullable = resultSet.getInt("notnull") == 0 && !primaryKey;
        columns.add(
            new Column(
                resultSet.getInt("cid"),
                resultSet.getString("name"),
                resultSet.getString("type"),
                nullable,
                primaryKey));
      }
    }
    return columns;
  }

  private List<Index> readIndexes(Connection connection, String tableName) throws SQLException {
    List<IndexHeader> headers = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("PRAGMA index_list(" + quoteIdentifier(tableName) + ")")) {
      while (resultSet.next()) {
        headers.add(new IndexHeader(resultSet.getString("name"), resultSet.getInt("unique") == 1));
      }
    }

    List<Index> indexes = new ArrayList<>();
    for (IndexHeader header : headers) {
      List<String> columns = new ArrayList<>();
      try (Statement statement = connection.createStatement();
          ResultSet resultSet =
              statement.executeQuery("PRAGMA index_info(" + quoteIdentifier(header.name()) + ")")) {
        while (resultSet.next()) {
          String columnName = resultSet.getString("name");
          if (columnName != null) {
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
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("PRAGMA foreign_key_list(" + quoteIdentifier(tableName) + ")")) {
      while (resultSet.next()) {
        foreignKeys.add(
            new ForeignKey(
                resultSet.getInt("id"),
                resultSet.getInt("seq"),
                resultSet.getString("table"),
                resultSet.getString("from"),
                resultSet.getString("to"),
                resultSet.getString("on_update"),
                resultSet.getString("on_delete"),
                resultSet.getString("match")));
      }
    }
    return foreignKeys;
  }

  private String quoteIdentifier(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  private record IndexHeader(String name, boolean unique) {}
}
