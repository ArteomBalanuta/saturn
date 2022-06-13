package org.saturn.app.service;

import java.sql.Connection;
import java.util.List;

public interface SQLService {
    
    String executeSQLCmd(String cmd);
    String executeFormatted(String sql);
    List<String> getBannedIds();
    
    String getBasicUserData(String hash, String trip);
    
    Connection getConnection();
}
