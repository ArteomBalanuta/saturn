package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.service.NoteService;
import org.saturn.app.util.DateUtil;
import org.saturn.app.util.SqlUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;

@Slf4j
public class NoteServiceImpl extends OutService implements NoteService {
  private final Connection connection;

  public NoteServiceImpl(Connection connection, BlockingQueue<String> queue) {
    super(queue);
    this.connection = connection;
  }

  public void executeNotesPurge(String author, String trip) {
    this.clearNotesByTrip(trip);
    enqueueMessageForSending(author, "'s notes has been deleted", false);
  }

  public void executeListNotes(String author, String trip) {
    List<String> notes = this.getNotesByTrip(trip);
    enqueueMessageForSending(
        author, "'s notes: \\n ```Text \\n" + notes.toString() + "\\n```", false);
  }

  @Override
  public void save(String trip, String note) {
    try {
      PreparedStatement insertNote =
          connection.prepareStatement(SqlUtil.INSERT_INTO_NOTES_TRIP_NOTE_CREATED_ON_VALUES);
      insertNote.setString(1, trip);
      insertNote.setString(2, note);
      insertNote.setLong(3, DateUtil.getTimestampNow());
      insertNote.executeUpdate();

      insertNote.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }

  @Override
  public List<String> getNotesByTrip(String trip) {
    List<String> notes = new ArrayList<>();
    try {
      PreparedStatement notesByTrip = connection.prepareStatement(SqlUtil.SELECT_NOTES_BY_TRIP);
      notesByTrip.setString(1, trip);
      notesByTrip.execute();

      ResultSet resultSet = notesByTrip.getResultSet();
      while (resultSet.next()) {
        notes.add(escapeJson(resultSet.getString("note")));
      }
      notesByTrip.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
    return notes;
  }

  @Override
  public void clearNotesByTrip(String trip) {
    try {
      PreparedStatement notesByTrip =
          connection.prepareStatement(SqlUtil.DELETE_FROM_NOTES_WHERE_TRIP);
      notesByTrip.setString(1, trip);
      notesByTrip.execute();

      notesByTrip.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }
  }
}
