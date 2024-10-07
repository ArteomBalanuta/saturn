package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.RandomStringUtils;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.JoinChannelListener;
import org.saturn.app.listener.impl.KickCommandListenerImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.JoinChannelListenerDto;
import org.saturn.app.model.dto.User;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"automove"})
public class AutoMoveUserCommandImpl extends UserCommandBaseImpl {
    public static String SOURCE_CHANNEL = "purgatory";
    private static boolean AUTO_MOVE_STATUS = false;

    public static boolean isAutoMoveStatus() {
        return AUTO_MOVE_STATUS;
    }

    public final static String CHANNEL = "lounge";
    private final static String TRIPS = "AAfFKK,VEbeHK,NO/4w4,Myh1TA,2ZQ3+0,coBad2,9kQGU6,+BBiCm,cmdTV+," +
            "aoXWSB,for9zT,zV2BBB,FaAfFY,sDMF6Q,jYTF8t,F7IuX2,gOtnKd,0UTOss,3/e804,A8FTOC," +
            "/JoyWo,48wNI7,k5uRbC,Jpr4bJ,IC3bl5,/StGZu,MQkpTM,jeyM4l,FQ2U+8,+fs0AT,Tar///," +
            "IiPtqX,6M6hbr,LY105Q,R5n5dC,fzWxIe,eyJdud,1G6EnU,vtFJUL,vuPizP,ZIYSBT,9A2yhx," +
            "0/JM7u,ToP++E,u3rwOv,OSArw7,CrvQXO,6Xgj9g,foMeFv,XDL9Nb,mgcSSR,godDDS,/DeDr/," +
            "bPi8nj,LrziAI,V2V5f7,ko60fH,eHsdHe,9pP6M5,k7jgLY,txMoon,ezp/5u,j156Wo,dnS+hr," +
            "Zvoxsl,RFa+gs,mBN1Ek,utcAWA,NJRDQJ,wwDQww,DQJCph,HCBt3b,WEBPut,0AKKA0,USAriP," +
            "aiwLKl,Hi/UVU,/9Br2y,ViMXVG,MRx/sb,IRC/Gz,C5Puj6,CJ0kky,aEWCdx,BpC+MG,xI/cmd";
    public final static List<String> AUTHORIZED_LOUNGE_TRIPS = List.of(TRIPS.split(","));
    private final List<String> aliases = new ArrayList<>();

    public AutoMoveUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
        super(message, engine, getAdminTrips(engine));
        super.setAliases(this.getAliases());
        this.aliases.addAll(aliases);
    }

    @Override
    public List<String> getAliases() {
        return this.aliases;
    }

    @Override
    public List<String> getArguments() {
        return super.getArguments();
    }

    @Override
    public Role getAuthorizedRole() {
        return Role.MODERATOR;
    }

    @Override
    public Optional<Status> execute() {
        List<String> arguments = getArguments();
        String author = chatMessage.getNick();

        if (arguments.size() != 1) {
            engine.outService.enqueueMessageForSending(author, engine.prefix + "automove [on|off]", isWhisper());
            engine.outService.enqueueMessageForSending(author, "Current status: " + AUTO_MOVE_STATUS, isWhisper());
            log.info("Executed [automove] command by user: {} - missing required parameters", author);
            return Optional.of(Status.FAILED);
        }

        if (arguments.get(0).trim().equalsIgnoreCase("on")) {
            AUTO_MOVE_STATUS = true;
            engine.outService.enqueueMessageForSending(author, " " + engine.prefix + "automove is enabled", isWhisper());
        } else  if (arguments.get(0).trim().equalsIgnoreCase("off")) {
            AUTO_MOVE_STATUS = false;
            engine.outService.enqueueMessageForSending(author, " " + engine.prefix + "automove is disabled", isWhisper());
        }

        log.info("Executed [automove] command by user: {}, arguments: {}", author, arguments.get(0).trim());
        return Optional.of(Status.SUCCESSFUL);
    }
}
