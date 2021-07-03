package org.saturn.app.service.impl;

import org.saturn.app.service.NoteService;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NoteServiceImpl implements NoteService {
    public NoteServiceImpl() {
    }

    @Override
    public void save(String trip, String note) {
        try {
            Connection connect = Connect.connect();
            PreparedStatement insertNote = connect.prepareStatement("INSERT INTO notes ('id', 'name', 'note') VALUES (null, ?, ?);");
            insertNote.setString(1, trip);
            insertNote.setString(2, note);
            insertNote.executeUpdate();

            insertNote.close();
            connect.close();
        } catch (URISyntaxException | SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getAllNotes() {
        return null;
    }

    @Override
    public List<String> getNotesByTrip(String trip) {
        List<String> notes = new ArrayList<>();
        try {
            Connection connection = Connect.connect();

            PreparedStatement notesByTrip = connection.prepareStatement("SELECT * FROM notes WHERE name = ?");
            notesByTrip.setString(1, trip);
            notesByTrip.execute();

            ResultSet resultSet = notesByTrip.getResultSet();
            while (resultSet.next()) {
                notes.add(resultSet.getString(3));
            }
            resultSet.close();
            connection.close();
        } catch (URISyntaxException | SQLException e) {
            e.printStackTrace();
        }
        return notes;
    }

    @Override
    public void clearNotesByTrip(String trip){
        try {
            Connection connection = Connect.connect();

            PreparedStatement notesByTrip = connection.prepareStatement("DELETE FROM notes WHERE name = ?");
            notesByTrip.setString(1, trip);
            notesByTrip.execute();

            connection.close();
        } catch (URISyntaxException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static class Connect {
        public static Connection connect() throws URISyntaxException {
            URL resource = Connect.class.getResource("/hackchat.db");
            String dbPath = Paths.get(resource.toURI()).toFile().getAbsolutePath();
            String url = "jdbc:sqlite:" + dbPath;
            try {
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
            return null;
        }
    }
}
