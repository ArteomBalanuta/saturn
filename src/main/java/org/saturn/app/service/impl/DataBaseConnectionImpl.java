package org.saturn.app.service.impl;

import java.io.File;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.saturn.ApplicationRunner;
import org.saturn.app.service.DataBaseConnection;

public class DataBaseConnectionImpl implements DataBaseConnection {
    private String databasePath;
    private Connection connection;

    public DataBaseConnectionImpl() {
        try {
            this.databasePath = new File(
                    ApplicationRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile()
                            .toPath().toString().concat("/hackchat.db");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        this.connection = setUpConnection();
    }

    @Override
    public Connection getConnection() {
        return this.connection;
    }

    private Connection setUpConnection() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return connection;
    }

}
