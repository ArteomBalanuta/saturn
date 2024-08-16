package org.saturn.app.command.impl.moderator;

import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

import java.util.List;
import java.util.Optional;

import static org.saturn.app.util.Util.getAdminTrips;

@Slf4j
@CommandAliases(aliases = {"active", "activity"})
public class ActivityCommandImpl extends UserCommandBaseImpl {
  public ActivityCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, getAdminTrips(engine));
    super.setAliases(aliases);
  }

  @Override
  public Role getAuthorizedRole() {
    return Role.MODERATOR;
  }

  @Override
  public Optional<Status> execute() {
    final List<String> arguments = getArguments();
    final String author = chatMessage.getNick();
    if (arguments.isEmpty()) {
      log.info("Executed [active] command by user: {}, no target set", author);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.prefix + "active 8Wotmg", isWhisper());
      return Optional.of(Status.FAILED);
    }

    final String target = arguments.getFirst();
    final String result =
        engine.sqlService.executeFormatted(
            SQL_STATS_PER_HOUR_OF_WEEK.replace(
                "?", target.trim().replace("'", "").replace("\"", "")));
    engine.outService.enqueueMessageForSending(author, "Stats: \\n" + result, isWhisper());
    log.info(
        "Executed [active] command by user: {}, trip: {}, target: {}",
        author,
        chatMessage.getTrip(),
        target);
    return Optional.of(Status.SUCCESSFUL);
  }

  public static final String SQL_STATS_PER_HOUR_OF_WEEK =
      """
                -- Count messages for each trip, grouped by day of the week and hour
                WITH MessagesPerTrip AS (
                    SELECT
                        trip,
                        strftime('%w', created_on / 1000, 'unixepoch') AS day_number, -- Day of the week (0 = Sunday, 6 = Saturday)
                        strftime('%H', created_on / 1000, 'unixepoch') AS hour, -- Hour of the day
                        COUNT(*) AS message_count
                    FROM messages
                    GROUP BY trip, day_number, hour
                ),

                -- Count total messages across all trips
                TotalMessages AS (
                    SELECT
                        trip,
                        COUNT(*) AS total_message_count
                    FROM messages
                    GROUP BY trip
                ),

                -- Calculate the probability of each trip being active on each day and hour
                Probability AS (
                    SELECT
                        m.trip,
                        m.day_number,
                        m.hour,
                        (m.message_count * 1.0 / t.total_message_count) * 100 AS probability_percentage,
                        CASE m.day_number
                            WHEN '0' THEN 'Sunday'
                            WHEN '1' THEN 'Monday'
                            WHEN '2' THEN 'Tuesday'
                            WHEN '3' THEN 'Wednesday'
                            WHEN '4' THEN 'Thursday'
                            WHEN '5' THEN 'Friday'
                            WHEN '6' THEN 'Saturday'
                        END AS day_full
                    FROM MessagesPerTrip m
                    JOIN TotalMessages t ON m.trip = t.trip
                )

                -- Final result, with normalized percentages
                SELECT
                    trip,
                    day_full AS day_of_week,
                    hour,
                    probability_percentage
                FROM Probability where LOWER(trip) == LOWER('?') ORDER BY trip, day_number, hour;""";
}
