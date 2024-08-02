package org.saturn.app.service.impl;

import org.saturn.app.service.NoteService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;

public class NoteServiceImpl extends OutService implements NoteService {
    private final Connection connection;

    public NoteServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }
    
    public void executeNotesPurge(String author, String trip) {
        this.clearNotesByTrip(trip);
        enqueueMessageForSending("@" + author + "'s notes are gone");
    }

    public void executeListNotes(String author, String trip) {
        List<String> notes = this.getNotesByTrip(trip);
        enqueueMessageForSending("@" + author + "'s notes: \\n ```Text \\n" + notes.toString() + "\\n```");
    }

    @Override
    public void save(String trip, String note) {
        try {
            PreparedStatement insertNote = connection
                    .prepareStatement("INSERT INTO notes ('id', 'name', 'note') VALUES (null, ?, ?);");
            insertNote.setString(1, trip);
            insertNote.setString(2, note);
            insertNote.executeUpdate();

            insertNote.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getNotesByTrip(String trip) {
        List<String> notes = new ArrayList<>();
        try {
            PreparedStatement notesByTrip = connection.prepareStatement("SELECT * FROM notes WHERE name = ?");
            notesByTrip.setString(1, trip);
            notesByTrip.execute();

            ResultSet resultSet = notesByTrip.getResultSet();
            while (resultSet.next()) {
                notes.add(escapeJson(resultSet.getString(3)));
            }
            notesByTrip.close();
            resultSet.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notes;
    }

    @Override
    public void clearNotesByTrip(String trip) {
        try {
            PreparedStatement notesByTrip = connection.prepareStatement("DELETE FROM notes WHERE name = ?");
            notesByTrip.setString(1, trip);
            notesByTrip.execute();
            
            notesByTrip.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
