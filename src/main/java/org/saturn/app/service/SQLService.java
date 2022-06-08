package org.saturn.app.service;

public interface SQLService {
    
    void executeSQLCmd(String cmd);
    String execute(String sql);
    
}
