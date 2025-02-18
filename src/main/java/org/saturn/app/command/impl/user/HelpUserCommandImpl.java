package org.saturn.app.command.impl.user;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

@Slf4j
@CommandAliases(aliases = {"help", "h"})
public class HelpUserCommandImpl extends UserCommandBaseImpl {
  private String prefix;

  public HelpUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);

    if (super.engine.config != null) {
      prefix = super.engine.config.getString("cmdPrefix");
    }
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.REGULAR;
  }

  @Override
  public Optional<Status> execute() {
    String author = chatMessage.getNick();

    String header = String.format(helpHeader, prefix);
    String adminPayload = Util.alignWithWhiteSpace(adminCommands, "-", "\u2009", false);
    String moderatorPayload = Util.alignWithWhiteSpace(moderatorCommands, "-", "\u2009", false);
    String userPayload = Util.alignWithWhiteSpace(userCommands, "-", "\u2009", false);
    String examples = String.format(helpExamples, prefix, prefix, prefix, prefix, prefix, prefix);

    StringBuilder helpPayload = new StringBuilder();
    helpPayload
        .append(header)
        .append("\u2009\u2009\u2009\u2009\u2009\u2009\u2009\u2009 \\n Admin commands:\\n")
        .append(adminPayload)
        .append("\u2009\u2009\u2009\u2009\u2009\u2009\u2009\u2009 \\n Moderator commands:\\n")
        .append(moderatorPayload)
        .append("\u2009\u2009\u2009\u2009\u2009\u2009\u2009\u2009 \\n User commands:\\n")
        .append(userPayload)
        .append(examples);

    super.engine.outService.enqueueMessageForSending(author, helpPayload.toString(), isWhisper());

    log.info("Executed [help] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }

  // .ddg   ​
  //
  // ​
  public static final String helpHeader =
      "All commands can be used through '/whisper'\\n" + "Prefix: %s \\n" + "Commands:\\n";
  public static String adminCommands =
      "\u2009grant <trip> <role>\u2009\u2009- grants the trip a role, ex: ADMIN,MODERATOR,USER\\n"
          + "\u2009sql <SQL>\u2009\u2009- executes the sql against bot's database\\n"
          + "\u2009mine <room> <start|stop>\u2009\u2009- starts a trip miner in the destination room\\n"
          + "\u2009mem\u2009\u2009- prints JVM memory usage\\n"
          + "\u2009msgroom <room> <text>\u2009\u2009- sends the mail to specified room\\n"
          + "\u2009replica <channel>\u2009\u2009\u2009- runs an instance in specified room\\n"
          + "\u2009replicaoff <channel>\u2009\u2009\u2009- shut downs the replica in channel\\n"
          + "\u2009replicastatus\u2009\u2009\u2009- prints basic info about running replicas\\n";

  public static String moderatorCommands =
      "\u2009activity <trip>\u2009\u2009- prints some funny data on users recent activity\\n"
          + "\u2009automove <on|off>\u2009\u2009- enables/disables auto move to ?lounge room from ?purgatory\\n"
          + "\u2009captcha <on|off>\u2009\u2009- enables/disables captcha\\n"
          + "\u2009auth <trip>\u2009\u2009- authorizes the list. \\n"
          + "\u2009deauth <trip>\u2009\u2009- removes authorized trip. \\n"
          + "\u2009kick,out <nick>\u2009\u2009- self explanatory. \\n"
          + "\u2009lastmessages <trip> <count>\u2009\u2009- prints users last messages\\n"
          + "\u2009lock <on|off>\u2009\u2009\u2009- enables/disables the lock on the current room\\n"
          + "\u2009overflow,shoot <nick>\u2009\u2009- self explanatory. \\n"
          + "\u2009register,reg <nick> <trip>\u2009\u2009- registers the user into bots catalog. \\n"
          + "\u2009move <name> <from> <to>\u2009\u2009\u2009- moves the user to specified room\\n"
          + "\u2009resurrect\u2009- moves last kicked user back.\\n"
          + "\u2009shadowban <nick|trip|hash>\u2009- bans the user by either nick,trip or hash\\n"
          + "\u2009unshadowban <nick|trip|hash>\u2009- unbans the user by either nick,trip or hash\\n"
          + "\u2009ban <nick>\u2009- bans the user\\n"
          + "\u2009unban <hash>\u2009- unbans the user by hash\\n"
          + "\u2009shadowbanlist\u2009- prints banned users\\n";

  public static String userCommands =
      "\u2009help,h\u2009- prints this output \\n"
          + "\u2009afk [reason]\u2009\u2009\u2009\u2009\u2009\u2009- marks the user as afk\\n"
          + "\u2009ape\u2009\u2009\u2009\u2009\u2009\u2009- prints an ape\\n"
          + "\u2009howto\u2009\u2009\u2009\u2009\u2009\u2009- beginners hack chat moderation guide\\n"
          + "\u2009info,i <nick>\u2009u2009\u2009\u2009- whispers back user's nicks, hashes\\n"
          + "\u2009lastseen <name>\u2009\u2009- prints useful info about users activity\\n"
          + "\u2009list <channel_name>\u2009\u2009- prints hash,trip,nicks of users in the channel\\n"
          + "\u2009msg <nick> <text>\u2009\u2009- sends a message to trips registered by <nick>. \\n"
          + "\u2009notes\u2009\u2009\u2009\u2009\u2009- lists your saved notes \\n"
          + "\u2009note <text>\u2009\u2009\u2009- saves a note \\n"
          + "\u2009notes purge\u2009\u2009\u2009\u2009- removes all notes \\n"
          + "\u2009ping\u2009\u2009\u2009\u2009\u2009- prints the latency between bot and hc\\n"
          + "\u2009users\u2009\u2009\u2009\u2009\u2009- prints a list of regular users\\n"
          + "\u2009say,echo <text>\u2009\u2009\u2009- echoes the input \\n"
          + "\u2009sub\u2009\u2009- you receive nick,hashes for joining users\\n"
          + "\u2009time,t <city|country>\u2009\u2009\u2009- time output \\n"
          + "\u2009unsub\u2009\u2009\u2009- cancels the subscription\\n"
          + "\u2009weather <city>\u2009\u2009\u2009- weather data (Many thanks to API.OPEN-METEO.COM)\\n"
          + "\u2009version,v\u2009\u2009\u2009- prints the running version\\n";

  public static String helpExamples =
      "Examples:\\n"
          + "\u2009 %scaptcha on \\n"
          + "\u2009 %safk domestic business \\n"
          + "\u2009 %slist programming \\n"
          + "\u2009 %sweather nc, charlotte \\n"
          + "\u2009 %smail santa Get me a native java compiler \\n"
          + "\u2009 %smsg wwandrew you, tonight \\n"
          + "\u2009\u2009\u2009\u2009\u2009\u2009\u2009\u2009 \\n"
          + "\u2009 Developed by mercury, _https://github.com/ArteomBalanuta/saturn_\\n";
}
