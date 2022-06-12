package org.saturn.app.service;

import java.util.List;

public interface SQLService {
    
    String executeSQLCmd(String cmd);
    String executeFormatted(String sql);
    List<String> getBannedIds();
    
    String getBasicUserData(String hash, String trip);
    
}
