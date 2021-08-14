package org.saturn;
import org.saturn.app.facade.BotFacade;
import org.saturn.app.service.DataBaseConnection;
import org.saturn.app.service.LogService;
import org.saturn.app.service.impl.DataBaseConnectionImpl;
import org.saturn.app.service.impl.LogServiceImpl;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public class ApplicationRunner {
    private final DataBaseConnection dbConnection;
    private final LogService internalService;

    public ApplicationRunner(){
        this.dbConnection = new DataBaseConnectionImpl();
        this.internalService = new LogServiceImpl(this.dbConnection.getConnection());
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
        BotFacade saturn = new BotFacade(dbConnection.getConnection());
        saturn.setChannel("programming");
        saturn.setNick("JavaBot#256c392");
        saturn.isMainThread = true;
        saturn.joinDelay = 1000;
        
        saturn.start();
    }
}
