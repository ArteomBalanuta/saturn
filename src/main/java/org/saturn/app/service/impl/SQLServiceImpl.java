package org.saturn.app.service.impl;

import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.service.SQLService;
import org.saturn.app.service.impl.util.TableGenerator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;

public class SQLServiceImpl extends OutService implements SQLService {
    
    private Connection connection;
    
    public SQLServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }
    
    @Override
    public String executeSQLCmd(String cmd) {
        String[] cmdParts = cmd.split("sql ");
        return this.executeFormatted(cmdParts[1]);
    }
    
    @Override
    public String executeFormatted(String sql) {
        StringBuilder string = new StringBuilder();
        string.append("```Text \\n");
        List<String> columnNames = new ArrayList<>();
        List<List<String>> listOfRows = new ArrayList<>();
        try {
            Statement cmd = connection.createStatement();
            ResultSet result = cmd.executeQuery(sql);
            
            ResultSetMetaData metaData = result.getMetaData();
            
            int numberOfColumns = metaData.getColumnCount();
            for (int i = 1; i <= numberOfColumns; i++) {
                columnNames.add(metaData.getColumnName(i));
            }
            
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= numberOfColumns; i++) {
                    String cell = result.getString(i) == null ? "null" : result.getString(i);
                    row.add(cell);
                }
                
                listOfRows.add(row);
            }
            
            cmd.close();
        } catch (SQLException e) {
            e.printStackTrace();
            
            listOfRows.clear();
            
        }
        
        String table = StringEscapeUtils.escapeJson(generateTable(columnNames, listOfRows));
        string.append(table);
        string.append("\\n ```");
        
        return string.toString();
    }
    
    @Override
    public List<String> getBannedIds() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT id from banned;");
            
            List<String> result = new ArrayList<>();
            ResultSet resultSet = statement.getResultSet();
            while (resultSet.next()) {
                result.add(escapeJson(resultSet.getString(1)));
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public String getBasicUserData(String hash, String trip) {
        Set<String> hashes = new HashSet<>();
        Set<String> nicks = new HashSet<>();
        try {
            Statement statement = connection.createStatement();
            
            String sql = "select distinct hash,nick from messages where trip = '" + trip + "' limit 15;";
            if (trip == null || "".equals(trip.trim())) {
                sql = "select distinct hash, nick from messages where hash = '" + hash + "' limit 15;";
            }
            statement.execute(sql);
            ResultSet resultSet = statement.getResultSet();
            
            StringBuilder result = new StringBuilder();
            while (resultSet.next()) {
                hashes.add(escapeJson(resultSet.getString(1)).trim());
                nicks.add(escapeJson(resultSet.getString(2)).trim());
            }
            
            result.append("Hashes: \\n");
            hashes.forEach(h -> result.append(h).append(", "));
            result.append(" \\n");
            
            result.append("Nicks: \\n");
            nicks.forEach(n -> result.append(n).append(", "));
            result.append(" \\n");
            
            
            return result.toString();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        
        return null;
    }
    
    //    @Override
    //    public String getBasicUserData(String hash, String trip) {
    //        try {
    //            Statement statement = connection.createStatement();
    //
    //            String tripCondition = "";
    //            if (trip != null && !trip.trim().equals("")) {
    //                tripCondition = "OR trip = '" + trip + "'";
    //            }
    //            statement.execute("SELECT distinct hash, trip, nick from messages where hash='" + hash + "'" +
    //            tripCondition + "  limit 20;");
    //            ResultSet resultSet = statement.getResultSet();
    //
    //            StringBuilder result = new StringBuilder();
    //            while (resultSet.next()) {
    //                result.append(escapeJson(resultSet.getString(1)));
    //                result.append("  ");
    //                result.append(escapeJson(resultSet.getString(2)));
    //                result.append("  ");
    //                result.append(escapeJson(resultSet.getString(3)));
    //                result.append("\\n");
    //            }
    //
    //            return result.toString();
    //        } catch (SQLException throwables) {
    //            throwables.printStackTrace();
    //        }
    //
    //        return null;
    //    }
    //
    //
    private String generateTable(List<String> columnNames, List<List<String>> listOfRows) {
        TableGenerator tableGenerator = new TableGenerator();
        
        return tableGenerator.generateTable(columnNames, listOfRows);
    }
}
