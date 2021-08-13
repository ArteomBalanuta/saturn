package org.saturn.app.service.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.saturn.app.service.NoteService;

public class NoteServiceImpl implements NoteService {
    private Connection connection;

    public NoteServiceImpl(Connection connection) {
        this.connection = connection;
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
                notes.add(resultSet.getString(3));
            }

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

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
