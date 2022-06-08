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
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class SQLServiceImpl extends OutService implements SQLService {

    private Connection connection;

    public SQLServiceImpl(Connection connection, BlockingQueue<String> queue) {
        super(queue);
        this.connection = connection;
    }
    
    public void executeSQLCmd(String cmd) {
        String[] cmdtext = cmd.split("sql ");
        String result = this.execute(cmdtext[1]);
        enqueueMessageForSending(result);
    }
    
    @Override
    public String execute(String sql) {
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
            
            while(result.next()) {
                List<String> row = new ArrayList<>();
                for(int i = 1; i <= numberOfColumns; i++) {
                    String cell =  result.getString(i) == null ? "null" : result.getString(i);
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

    private String generateTable(List<String> columnNames, List<List<String>> listOfRows) {
        TableGenerator tableGenerator = new TableGenerator();    

        return tableGenerator.generateTable(columnNames, listOfRows);
    }
}
