import org.saturn.Saturn;
import org.saturn.app.service.InternalService;
import org.saturn.app.service.impl.InternalServiceImpl;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public class ApplicationRunner {
    final InternalService internalService = new InternalServiceImpl();

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
        Saturn saturn = new Saturn();
        saturn.setChannel("programming");
        saturn.setNick("JavaBot#256c392");
        saturn.isMainThread = true;
        saturn.joinDelay = 1000;
        
        saturn.start();
    }
}
