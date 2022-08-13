package org.saturn.app.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface SQLService {
    
    String executeSql(String cmd, boolean withOutput);
    String executeFormatted(String sql);
    List<String> getBannedIds();
    
    String getBasicUserData(String hash, String trip);
    
    Connection getConnection();
}
