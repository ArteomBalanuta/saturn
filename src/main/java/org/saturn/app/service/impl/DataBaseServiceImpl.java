package org.saturn.app.service.impl;

import org.saturn.ApplicationRunner;
import org.saturn.app.service.DataBaseService;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DataBaseServiceImpl implements DataBaseService {
    private String databasePath;
    private Connection connection;

    public DataBaseServiceImpl(String path) {
        try {
            validateDbPath(path);
            this.databasePath = path;
            this.connection = setUpConnection();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected void validateDbPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            throw new RuntimeException("Can't find database file: " + path);
        }
    }

    private Connection setUpConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    @Override
    public Connection getConnection() {
        return this.connection;
    }

}
