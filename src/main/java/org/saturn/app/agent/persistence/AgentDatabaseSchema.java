package org.saturn.app.agent.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Represents the discovered database schema used by agent database tools. */
public record AgentDatabaseSchema(List<Table> tables) {
  public AgentDatabaseSchema {
    Objects.requireNonNull(tables, "tables");
    tables = List.copyOf(tables);
  }

  /**
   * Implements the {@code tableNames} operation for this agent component.
   *
   * @return the operation result
   */
  public Set<String> tableNames() {
    return tables.stream().map(Table::name).collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Implements the {@code findTable} operation for this agent component.
   *
   * @param name input argument used by this operation
   * @return the operation result
   */
  public Optional<Table> findTable(String name) {
    if (name == null) {
      return Optional.empty();
    }
    String normalized = name.toLowerCase(Locale.ROOT);
    return tables.stream()
        .filter(table -> table.name().toLowerCase(Locale.ROOT).equals(normalized))
        .findFirst();
  }

  /** Carries the table value used by the enclosing agent component. */
  /** Carries the table value used by the enclosing agent component. */
  public record Table(
      String name, List<Column> columns, List<Index> indexes, List<ForeignKey> foreignKeys) {
    public Table {
      Objects.requireNonNull(name, "name");
      columns = List.copyOf(columns);
      indexes = List.copyOf(indexes);
      foreignKeys = List.copyOf(foreignKeys);
    }
  }

  /** Carries the column value used by the enclosing agent component. */
  /** Carries the column value used by the enclosing agent component. */
  public record Column(
      int ordinal, String name, String declaredType, boolean nullable, boolean primaryKey) {
    public Column {
      Objects.requireNonNull(name, "name");
      declaredType = declaredType == null ? "" : declaredType;
    }
  }

  /** Carries the index value used by the enclosing agent component. */
  /** Carries the index value used by the enclosing agent component. */
  public record Index(String name, boolean unique, List<String> columns) {
    public Index {
      Objects.requireNonNull(name, "name");
      columns = List.copyOf(columns);
    }
  }

  /** Carries the foreign key value used by the enclosing agent component. */
  /** Carries the foreign key value used by the enclosing agent component. */
  public record ForeignKey(
      int id,
      int sequence,
      String referencedTable,
      String fromColumn,
      String toColumn,
      String onUpdate,
      String onDelete,
      String match) {
    /**
     * Constructs this value after validating and defensively retaining its supplied inputs.
     *
     * @param id the id input; null handling follows the validation performed by this declaration
     * @param sequence the sequence input; null handling follows the validation performed by this
     *     declaration
     * @param referencedTable the referencedTable input; null handling follows the validation
     *     performed by this declaration
     * @param fromColumn the fromColumn input; null handling follows the validation performed by
     *     this declaration
     * @param toColumn the toColumn input; null handling follows the validation performed by this
     *     declaration
     * @param onUpdate the onUpdate input; null handling follows the validation performed by this
     *     declaration
     * @param onDelete the onDelete input; null handling follows the validation performed by this
     *     declaration
     * @param match the match input; null handling follows the validation performed by this
     *     declaration
     */
    public ForeignKey {
      Objects.requireNonNull(referencedTable, "referencedTable");
      Objects.requireNonNull(fromColumn, "fromColumn");
      toColumn = toColumn == null ? "" : toColumn;
      onUpdate = onUpdate == null ? "" : onUpdate;
      onDelete = onDelete == null ? "" : onDelete;
      match = match == null ? "" : match;
    }
  }
}
