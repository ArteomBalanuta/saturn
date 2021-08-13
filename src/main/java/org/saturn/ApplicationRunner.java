package org.saturn;
import org.saturn.app.service.DataBaseConnection;
import org.saturn.app.service.InternalService;
import org.saturn.app.service.impl.DataBaseConnectionImpl;
import org.saturn.app.service.impl.InternalServiceImpl;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public class ApplicationRunner {
    private final DataBaseConnection dbConnection;
    private final InternalService internalService;

    public ApplicationRunner(){
        this.dbConnection = new DataBaseConnectionImpl();
        this.internalService = new InternalServiceImpl(this.dbConnection.getConnection());
    }

    public static void main(String[] args) {
        ApplicationRunner applicationRunner = new ApplicationRunner();
        applicationRunner.start();
    }
    
    void start(){
        logStartBotEvent();
        runSaturnBot();
    }
    
    private void logStartBotEvent(){
        internalService.log("appStart", "started", Timestamp.valueOf(LocalDateTime.now()).getTime());
    }
    
    private void runSaturnBot(){
        Saturn saturn = new Saturn(dbConnection.getConnection());
        saturn.setChannel("programming");
        saturn.setNick("JavaBot#256c392");
        saturn.isMainThread = true;
        saturn.joinDelay = 1000;
        
        saturn.start();
    }
}
