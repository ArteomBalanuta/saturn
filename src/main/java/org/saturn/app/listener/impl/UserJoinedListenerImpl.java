package org.saturn.app.listener.impl;

import static org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl.SOURCE_CHANNELS;
import static org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl.getDestinationChannel;
import static org.saturn.app.util.SqlUtil.SELECT_LOUNGE_TRIPS;
import static org.saturn.app.util.Util.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.impl.moderator.AutoMoveUserCommandImpl;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;
import org.saturn.app.model.dto.User;

@Slf4j
public class UserJoinedListenerImpl implements Listener {
  @Override
  public String getListenerName() {
    return "joinListener";
  }

  private final EngineImpl engine;

  public UserJoinedListenerImpl(EngineImpl engine) {
    this.engine = engine;
  }

  @Override
  public void notify(String jsonText) {
    JsonElement element = JsonParser.parseString(jsonText);
    JsonObject object = element.getAsJsonObject();
    User user = gson.fromJson(object, User.class);
    log.info(
        "User joined - nick: {}, trip: {}, hash: {}, channel: {}",
        user.getNick(),
        user.getTrip(),
        user.getHash(),
        user.getChannel());

    engine.addActiveUser(user);
    engine.shareUserInfo(user);
    engine.kickIfShadowBanned(user);
    /* AutoMoveCommand has been triggered */
    if (AutoMoveUserCommandImpl.isAutoMoveEnabled()
        && engine.engineType.equals(EngineType.REPLICA)
        && SOURCE_CHANNELS.contains(engine.channel)) {
      log.warn("AutoMoveCommand feature flag is true");
      if (getWhitelistedTrips().contains(user.getTrip())) {
        engine.outService.enqueueMessageForSending(
            user.getNick(),
            "your trip is authorized to join ?lounge, you will be moved to ?lounge",
            false);
        engine.modService.kickTo(user.getNick(), getDestinationChannel());
        log.info("User: {}, has been moved to: {}", user.getNick(), getDestinationChannel());
      }
    }
  }

  private List<String> getWhitelistedTrips() {
    List<String> trips = new ArrayList<>();
    log.debug("Querying the trips table for USER trips");
    try {
      PreparedStatement statement = engine.getDbConnection().prepareStatement(SELECT_LOUNGE_TRIPS);
      statement.execute();

      ResultSet resultSet = statement.getResultSet();
      while (resultSet.next()) {
        trips.add(resultSet.getString("trip"));
      }
      statement.close();
      resultSet.close();
    } catch (SQLException e) {
      log.info("Error: {}", e.getMessage());
      log.error("Stack trace", e);
    }

    log.info("Retrieved: {}, trips", trips.size());
    return trips;
  }
}

/*
*grant AAfFKK USER
*grant VEbeHK USER
*grant NO/4w4 USER
*grant Myh1TA USER
*grant 2ZQ3+0 USER
*grant coBad2 USER
*grant 9kQGU6 USER
*grant +BBiCm USER
*grant cmdTV+ USER
*grant aoXWSB USER
*grant for9zT USER
*grant zV2BBB USER
*grant FaAfFY USER
*grant sDMF6Q USER
*grant jYTF8t USER
*grant F7IuX2 USER
*grant gOtnKd USER
*grant 0UTOss USER
*grant 3/e804 USER
*grant A8FTOC USER
*grant /JoyWo USER
*grant 48wNI7 USER
*grant k5uRbC USER
*grant Jpr4bJ USER
*grant IC3bl5 USER
*grant /StGZu USER
*grant MQkpTM USER
*grant jeyM4l USER
*grant FQ2U+8 USER
*grant +fs0AT USER
*grant Tar/// USER
*grant IiPtqX USER
*grant 6M6hbr USER
*grant LY105Q USER
*grant R5n5dC USER
*grant fzWxIe USER
*grant eyJdud USER
*grant 1G6EnU USER
*grant vtFJUL USER
*grant vuPizP USER
*grant ZIYSBT USER
*grant 9A2yhx USER
*grant 0/JM7u USER
*grant ToP++E USER
*grant u3rwOv USER
*grant OSArw7 USER
*grant CrvQXO USER
*grant 6Xgj9g USER
*grant foMeFv USER
*grant XDL9Nb USER
*grant mgcSSR USER
*grant godDDS USER
*grant /DeDr/ USER
*grant bPi8nj USER
*grant LrziAI USER
*grant V2V5f7 USER
*grant ko60fH USER
*grant eHsdHe USER
*grant 9pP6M5 USER
*grant k7jgLY USER
*grant txMoon USER
*grant ezp/5u USER
*grant j156Wo USER
*grant dnS+hr USER
*grant Zvoxsl USER
*grant RFa+gs USER
*grant mBN1Ek USER
*grant utcAWA USER
*grant NJRDQJ USER
*grant wwDQww USER
*grant DQJCph USER
*grant HCBt3b USER
*grant WEBPut USER
*grant 0AKKA0 USER
*grant USAriP USER
*grant aiwLKl USER
*grant Hi/UVU USER
*grant /9Br2y USER
*grant ViMXVG USER
*grant MRx/sb USER
*grant IRC/Gz USER
*grant C5Puj6 USER
*grant CJ0kky USER
*grant aEWCdx USER
*grant BpC+MG USER
*grant xI/cmd USER

*grant jYTF8t,F7IuX2,gOtnKd,0UTOss,3/e804,A8FTOC,/JoyWo,48wNI7,k5uRbC,Jpr4bJ,IC3bl5,/StGZu,MQkpTM,jeyM4l,FQ2U+8,+fs0AT,Tar///,IiPtqX,6M6hbr,LY105Q,R5n5dC,fzWxIe,eyJdud,1G6EnU,vtFJUL,vuPizP,ZIYSBT,9A2yhx,0/JM7u,ToP++E,u3rwOv,OSArw7,CrvQXO,6Xgj9g,foMeFv,XDL9Nb,mgcSSR,godDDS,/DeDr/,bPi8nj,LrziAI,V2V5f7,ko60fH,eHsdHe,9pP6M5,k7jgLY,txMoon,ezp/5u,j156Wo,dnS+hr,Zvoxsl,RFa+gs,mBN1Ek,utcAWA,NJRDQJ,wwDQww,DQJCph,HCBt3b,WEBPut,0AKKA0,USAriP,aiwLKl,Hi/UVU,/9Br2y,ViMXVG,MRx/sb,IRC/Gz,C5Puj6,CJ0kky,aEWCdx,BpC+MG,xI/cmd USER

*/
