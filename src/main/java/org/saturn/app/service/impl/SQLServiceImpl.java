package org.saturn.app.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.saturn.app.service.SQLService;
import org.saturn.app.service.impl.util.TableGenerator;

import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.saturn.app.util.SeparatorFormatter.addSeparator;
import static org.saturn.app.util.Util.setToList;

@Slf4j
public class SQLServiceImpl extends OutService implements SQLService {
    
    private Connection connection;
    
    public SQLServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }
    
    @Override
    public String executeSql(String cmd, boolean withOutput) {
        String[] cmdParts = cmd.split("sql ");
        if (withOutput) {
            log.info("Executing SQL query, expecting output");
            return this.executeFormatted(cmdParts[1]);
        }
        try {
            Statement statement = connection.createStatement();
            int updatedRows  = statement.executeUpdate(cmdParts[1]);
            log.info("Executed SQL query: {} rows updated", updatedRows);
            return String.valueOf(updatedRows);
        } catch (SQLException e) {
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace", e);

            return e.getMessage();
        }
    }
    
    @Override
    public String executeFormatted(String sql) {
        StringBuilder string = new StringBuilder();
        string.append("\\n```Text\\n");
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
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace", e);

            listOfRows.clear();
            return e.getMessage();
        }
        
        String table = StringEscapeUtils.escapeXml11(StringEscapeUtils.escapeJson(generateTable(columnNames, listOfRows))).replace("`","");
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
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace", e);
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
            if (trip == null || trip.trim().isEmpty()) {
                sql = "select distinct hash,nick from messages where hash = '" + hash + "' limit 15;";
            }
            statement.execute(sql);
            ResultSet resultSet = statement.getResultSet();
            
            StringBuilder result = new StringBuilder();
            while (resultSet.next()) {
                Optional.ofNullable(resultSet.getString(1)).ifPresent(s_hash -> hashes.add(escapeJson(s_hash).trim()));
                Optional.ofNullable(resultSet.getString(2)).ifPresent(s_nick -> nicks.add(escapeJson(s_nick)));
            }
            
            List formattedHashes = addSeparator(setToList(hashes), ',');
            List formattedNicks = addSeparator(setToList(nicks), ',');
            
            result.append("Hashes: \\n");
            formattedHashes.forEach(result::append);
            result.append(" \\n");
            
            result.append("Nicks: \\n");
            formattedNicks.forEach(result::append);
            result.append(" \\n");
            
            return result.toString();
        } catch (SQLException e) {
            log.info("Error: {}", e.getMessage());
            log.error("Stack trace", e);
        }

        return null;
    }
    
    @Override
    public Connection getConnection() {
        return this.connection;
    }

    private String generateTable(List<String> columnNames, List<List<String>> listOfRows) {
        TableGenerator tableGenerator = new TableGenerator();
        
        return tableGenerator.generateTable(columnNames, listOfRows);
    }
}
