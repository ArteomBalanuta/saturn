package org.saturn.app.service.impl;

import org.saturn.app.service.DataBaseConnectionService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DataBaseConnectionServiceImpl implements DataBaseConnectionService {
    private String databasePath;
    private Connection connection;

    public DataBaseConnectionServiceImpl() {
        try {
//            this.databasePath = new File(
//                    ApplicationRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile()
//                            .toPath().toString().concat("/hackchat.db");
             this.databasePath = "/home/ab/workspace/projects/saturn/hackchat.db";
        } catch (Exception e) {
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
