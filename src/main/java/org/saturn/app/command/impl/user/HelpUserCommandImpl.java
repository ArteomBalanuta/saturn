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
  private final String prefix;

  public HelpUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
    super.setAliases(aliases);
    this.prefix = super.engine.getPrefix();
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
      String.join(
              "\\n",
              "\u2009grant,access <trip> <role>\u2009- grants a role to a trip",
              "\u2009sql <SQL>\u2009\u2009\u2009\u2009- runs SQL against the bot database",
              "\u2009mine <room> <start|stop>\u2009- controls the trip miner in a room",
              "\u2009mem,memory\u2009\u2009\u2009- shows JVM memory usage",
              "\u2009msgroom,msgchannel <room> <text>\u2009- sends a message to another room",
              "\u2009replica,bot <channel>\u2009- starts a replica in a room",
              "\u2009replicaoff <channel>\u2009- stops a running replica",
              "\u2009replicastatus,status\u2009- shows host and replica status",
              "\u2009whiskey <channel> <name>\u2009- starts an agent replica with a custom nick",
              "\u2009restart,reload\u2009\u2009- restarts the host and its replicas",
              "\u2009shutdown,exit\u2009\u2009- stops the application")
          + "\\n";

  public static String moderatorCommands =
      String.join(
              "\\n",
              "\u2009activity <trip>\u2009\u2009\u2009- shows recent activity patterns for a trip",
              "\u2009automove <on|off>\u2009\u2009- toggles auto-move between configured rooms",
              "\u2009captcha <on|off>\u2009\u2009- enables or disables captcha",
              "\u2009auth,authorize <trip>\u2009- authorizes a trip on the room",
              "\u2009deauth <trip>\u2009\u2009\u2009- removes trip authorization",
              "\u2009kick,k,out <nick>\u2009\u2009- kicks a user from the room",
              "\u2009nuke <room>\u2009\u2009\u2009- locks a room and clears users from it",
              "\u2009messages,lastmessages <trip> <count>\u2009- shows recent messages for a trip",
              "\u2009lock,lockroom <on|off>\u2009- locks or unlocks the current room",
              "\u2009overflow,shoot <nick>\u2009- sends the selected overflow action",
              "\u2009register,reg <nick> <trip>\u2009- registers or updates a nick/trip pair",
              "\u2009remove <name|trip>\u2009\u2009- removes a registered user",
              "\u2009move <name> <from> <to>\u2009- moves a user between rooms",
              "\u2009resurrect\u2009\u2009\u2009\u2009- moves the last kicked user back",
              "\u2009shadowban,sban <target>\u2009- shadow-bans by nick, trip, or hash",
              "\u2009shadowbanlist,banlist\u2009- lists shadow-banned users",
              "\u2009unshadowban <target>\u2009- removes a shadow ban",
              "\u2009ban <nick>\u2009\u2009\u2009\u2009- bans a user",
              "\u2009unban <hash>\u2009\u2009\u2009- unbans by hash",
              "\u2009unbanall\u2009\u2009\u2009\u2009- clears all room bans",
              "\u2009mute,dumb <nick>\u2009\u2009- mutes a user",
              "\u2009unmute <hash>\u2009\u2009- unmutes by hash",
              "\u2009color <name> <color>\u2009- applies a color to an online user",
              "\u2009flair <name> <flair>\u2009- applies a flair to an online user")
          + "\\n";

  public static String userCommands =
      String.join(
              "\\n",
              "\u2009help,h\u2009\u2009\u2009\u2009\u2009- shows this help output",
              "\u2009afk [reason]\u2009\u2009\u2009- marks you as AFK",
              "\u2009ape,harambe\u2009\u2009\u2009- prints an ape",
              "\u2009howto,hcguide\u2009\u2009- shows the moderation crash course",
              "\u2009info,whois <nick>\u2009\u2009- shows a user's trip and hash",
              "\u2009lastseen <name>\u2009\u2009- shows when a user was last active",
              "\u2009list <channel>\u2009\u2009\u2009- lists users in a room",
              "\u2009msg,mail <nick> <text>\u2009- sends mail to a registered user",
              "\u2009msgroom <room> <text>\u2009- sends a message to another room",
              "\u2009nicks,t2n <trip>\u2009\u2009- lists known nicks for a trip",
              "\u2009notes\u2009\u2009\u2009\u2009\u2009- lists your saved notes",
              "\u2009note,save <text>\u2009\u2009- saves a note",
              "\u2009notes purge\u2009\u2009\u2009- removes all saved notes",
              "\u2009ping,p\u2009\u2009\u2009\u2009\u2009- shows bot latency",
              "\u2009users\u2009\u2009\u2009\u2009\u2009- lists registered users",
              "\u2009say,echo <text>\u2009\u2009- echoes text back",
              "\u2009sub,subscribe\u2009\u2009\u2009- subscribes to join notifications",
              "\u2009time,t <city|country>\u2009- shows local time",
              "\u2009unsub,unsubscribe\u2009- cancels join notifications",
              "\u2009weather,w <city>\u2009\u2009- shows weather data",
              "\u2009version,v\u2009\u2009\u2009- shows the running version",
              "\u2009ws,wsay <text>\u2009\u2009- forwards text to the support relay",
              "\u2009wsa <text>\u2009\u2009\u2009\u2009- sends anonymous support relay text",
              "\u2009dbzhelp,dbz\u2009\u2009\u2009- shows DBZ game commands")
          + "\\n";

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
