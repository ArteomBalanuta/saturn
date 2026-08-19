package org.saturn.app.agent.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents the tabular result of an agent SQL operation. */
public record AgentSqlResult(
    List<String> columns, List<List<Object>> rows, boolean truncated, long elapsedMillis) {
  public AgentSqlResult {
    Objects.requireNonNull(columns, "columns");
    Objects.requireNonNull(rows, "rows");
    columns = List.copyOf(columns);
    rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
  }
}
