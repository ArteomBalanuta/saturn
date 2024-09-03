package org.saturn.app.service;

import java.sql.Connection;
import java.util.List;

public interface SQLService {
    
    String executeSql(String cmd, boolean withOutput);
    String executeFormatted(String sql);
    
    String getBasicUserData(String hash, String trip);
}
