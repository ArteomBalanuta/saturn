package org.saturn.app.command.impl.moderator;

import static org.saturn.app.util.Util.getAdminTrips;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.dto.payload.ChatMessage;

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
    Optional<String> argument = requiredArgument(0, "active 8Wotmg");
    if (argument.isEmpty()) {
      log.info("Executed [active] command by user: {}, no target set", author());
      replyToAuthor("Example: %sactive 8Wotmg".formatted(engine.prefix));
      return Optional.of(Status.FAILED);
    }

    String target = argument.get();
    String result = engine.sqlService.executeActivityStats(target);
    replyToAuthor("Stats: \\n%s".formatted(result));
    log.info(
        "Executed [active] command by user: {}, trip: {}, target: {}",
        author(),
        chatMessage.getTrip(),
        target);
    return successful();
  }

  public static final String SQL_STATS_PER_HOUR_OF_WEEK =
      """
                -- Count messages for each trip, grouped by day of the week and hour
                WITH MessagesPerTrip AS (
                    SELECT
                        trip,
                        EXTRACT(DAY_OF_WEEK FROM DATEADD(MILLISECOND, created_on, TIMESTAMP '1970-01-01 00:00:00')) AS day_number,
                        EXTRACT(HOUR FROM DATEADD(MILLISECOND, created_on, TIMESTAMP '1970-01-01 00:00:00')) AS hour,
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
                            WHEN 1 THEN 'Sunday'
                            WHEN 2 THEN 'Monday'
                            WHEN 3 THEN 'Tuesday'
                            WHEN 4 THEN 'Wednesday'
                            WHEN 5 THEN 'Thursday'
                            WHEN 6 THEN 'Friday'
                            WHEN 7 THEN 'Saturday'
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
                FROM Probability where LOWER(trip) = LOWER(?) ORDER BY trip, day_number, hour;""";
}
