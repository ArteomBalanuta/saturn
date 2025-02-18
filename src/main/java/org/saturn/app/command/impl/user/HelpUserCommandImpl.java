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
    String payload = Util.alignWithWhiteSpace(helpPayload, "-", "\u2009", false);
    String examples = String.format(helpExamples, prefix, prefix, prefix, prefix, prefix, prefix);

    super.engine.outService.enqueueMessageForSending(
        author, header + payload + examples, isWhisper());

    log.info("Executed [help] command by user: {}", author);

    return Optional.of(Status.SUCCESSFUL);
  }

  // .ddg   ​
  //
  // ​
  public static final String helpHeader =
      "All commands can be used through '/whisper'\\n" + "Prefix: %s \\n" + "Commands:\\n";

  public static String helpPayload =
      "\u2009help,h\u2009- prints this output \\n"
          + "\u2009say,echo <text>\u2009\u2009\u2009- echoes the input \\n"
          + "\u2009time,t <city|country>\u2009\u2009\u2009- time output \\n"
          + "\u2009note <text>\u2009\u2009\u2009- saves a note \\n"
          + "\u2009notes\u2009\u2009\u2009\u2009\u2009- lists your saved notes \\n"
          + "\u2009notes purge\u2009\u2009\u2009\u2009- removes all notes \\n"
          + "\u2009msg <nick> <text>\u2009\u2009- sends a message to trips registered by <nick>. \\n"
          + "\u2009register,reg <nick> <trip\u2009\u2009- registers the user. \\n"
          + "\u2009info,i <nick>\u2009u2009\u2009\u2009- whispers back user's nicks, hashes\\n"
          + "\u2009list <channel_name>\u2009\u2009- prints hash,trip,nicks of users in the channel\\n"
          + "\u2009weather <city>\u2009\u2009\u2009- weather data (Many thanks to API.OPEN-METEO.COM)\\n"
          + "\u2009ping\u2009\u2009\u2009\u2009\u2009- prints the latency between bot and hc\\n"
          + "\u2009afk [reason]\u2009\u2009\u2009\u2009\u2009\u2009- marks the user as afk\\n"
          + "\u2009lastseen <name>\u2009\u2009- prints useful info about users activity\\n"
          + "\u2009lastmessages <trip> <count>\u2009\u2009- prints users last messages\\n"
          + "\u2009sub\u2009\u2009- you receive nick,hashes for joining users\\n"
          + "\u2009unsub\u2009\u2009\u2009- cancels the subscription\\n"
          + "Moderator commands:\\n"
          + "\u2009shadowban <nick|trip|hash>\u2009- bans the user by either nick,trip or hash\\n"
          + "\u2009msgroom <room> <text>\u2009\u2009- sends the mail to specified room\\n"
          + "\u2009captcha <on|off>\u2009\u2009- enables/disables captcha\\n"
          + "\u2009automove <on|off>\u2009\u2009- enables/disables auto move to ?lounge room from ?purgatory\\n"
          + "\u2009lock <on|off>\u2009\u2009\u2009- enables/disables the lock on the current room\\n"
          + "\u2009move <name> <from> <to>\u2009\u2009\u2009- moves the user to specified room\\n"
          + "\u2009replica <channel>\u2009\u2009\u2009- runs an instance in specified room\\n"
          + "\u2009replicaoff <channel>\u2009\u2009\u2009- shut downs the replica in channel\\n"
          + "\u2009replicastatus\u2009\u2009\u2009- prints basic info about running replicas\\n"
          + "Admin commands:\\n"
          + "\u2009sql <SQL>\u2009\u2009- executes the sql against bot's database\\n";

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

  public static final String SOLID =
      "```Text \\n"
          + "S - single responsibility principle \\n"
          + "O - open-close principle \\n"
          + "L - liskov substitution principle \\n"
          + "I - interface segregation principle \\n"
          + "D - dependency inversion principle \\n"
          + "``` \\n";
}
