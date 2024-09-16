package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"active"})
public class ActivityCommandImpl extends UserCommandBaseImpl {
    public static final String SQL_STATS_PER_HOUR_OF_WEEK = "\n" +
            "\n" +
            "-- Count messages for each trip, grouped by day of the week and hour\n" +
            "WITH MessagesPerTrip AS (\n" +
            "    SELECT\n" +
            "        trip,\n" +
            "        strftime('%w', created_on / 1000, 'unixepoch') AS day_number, -- Day of the week (0 = Sunday, 6 = Saturday)\n" +
            "        strftime('%H', created_on / 1000, 'unixepoch') AS hour, -- Hour of the day\n" +
            "        COUNT(*) AS message_count\n" +
            "    FROM messages\n" +
            "    GROUP BY trip, day_number, hour\n" +
            "),\n" +
            "\n" +
            "-- Count total messages across all trips\n" +
            "TotalMessages AS (\n" +
            "    SELECT\n" +
            "        trip,\n" +
            "        COUNT(*) AS total_message_count\n" +
            "    FROM messages\n" +
            "    GROUP BY trip\n" +
            "),\n" +
            "\n" +
            "-- Calculate the probability of each trip being active on each day and hour\n" +
            "Probability AS (\n" +
            "    SELECT\n" +
            "        m.trip,\n" +
            "        m.day_number,\n" +
            "        m.hour,\n" +
            "        (m.message_count * 1.0 / t.total_message_count) * 100 AS probability_percentage,\n" +
            "        CASE m.day_number\n" +
            "            WHEN '0' THEN 'Sunday'\n" +
            "            WHEN '1' THEN 'Monday'\n" +
            "            WHEN '2' THEN 'Tuesday'\n" +
            "            WHEN '3' THEN 'Wednesday'\n" +
            "            WHEN '4' THEN 'Thursday'\n" +
            "            WHEN '5' THEN 'Friday'\n" +
            "            WHEN '6' THEN 'Saturday'\n" +
            "        END AS day_full\n" +
            "    FROM MessagesPerTrip m\n" +
            "    JOIN TotalMessages t ON m.trip = t.trip\n" +
            ")\n" +
            "\n" +
            "-- Final result, with normalized percentages\n" +
            "SELECT\n" +
            "    trip,\n" +
            "    day_full AS day_of_week,\n" +
            "    hour,\n" +
            "    probability_percentage\n" +
            "FROM Probability where LOWER(trip) == LOWER('?') \n" +
            "ORDER BY trip, day_number, hour;";
    private final List<String> aliases = new ArrayList<>();

    public ActivityCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
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
        if (arguments.isEmpty()) {
            log.info("Executed [active] command by user: {}, no target set", author);
            engine.outService.enqueueMessageForSending(author,"Example: " + engine.prefix + "online 8Wotmg", isWhisper());
            return Optional.of(Status.FAILED);
        }

        String target = arguments.get(0);
        String s = engine.sqlService.executeFormatted(SQL_STATS_PER_HOUR_OF_WEEK.replace("?", target.trim().replace("'","").replace("\"","")));
        engine.outService.enqueueMessageForSending(author,"Stats: \\n" + s, isWhisper());
        log.info("Executed [active] command by user: {}, trip: {}, target: {}", author, chatMessage.getTrip(), target);
        return Optional.of(Status.SUCCESSFUL);
    }
}
