package org.saturn.app.command.impl.user;

import static org.saturn.app.model.dto.Weather.getTime;
import static org.saturn.app.util.DateUtil.formatRfc1123;
import static org.saturn.app.util.DateUtil.tsToSec8601;
import static org.saturn.app.util.Util.extractCoordinates;
import static org.saturn.app.util.Util.extractCountryName;
import static org.saturn.app.util.Util.getResponseByURL;
import static org.saturn.app.util.Util.gson;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.command.UserCommandBaseImpl;
import org.saturn.app.command.annotation.CommandAliases;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.model.Role;
import org.saturn.app.model.Status;
import org.saturn.app.model.TimeResponse;
import org.saturn.app.model.dto.WeatherTime;
import org.saturn.app.model.dto.payload.ChatMessage;
import org.saturn.app.util.Util;

@Slf4j
@CommandAliases(aliases = {"time", "t"})
public class TimeUserCommandImpl extends UserCommandBaseImpl {
  private final List<String> aliases = new ArrayList<>();

  public TimeUserCommandImpl(EngineImpl engine, ChatMessage message, List<String> aliases) {
    super(message, engine, List.of("x"));
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
    return Role.REGULAR;
  }

  private static final String apiGeoNames =
      "http://api.geonames.org/search?q=%s&maxRows=1&username=dev1";
  private static final String apiTime = "https://api.sunrisesunset.io/json?lat=%s&lng=%s";
  private static final String apiTimeIo =
      "https://timeapi.io/api/Time/current/coordinate?latitude=%s&longitude=%s";

  @Override
  public Optional<Status> execute() {
    List<String> arguments = getArguments();

    String author = chatMessage.getNick();
    String trip = chatMessage.getTrip();
    if (arguments.isEmpty()) {
      log.info("Executed [time] command by user: {}, trip: {}", author, trip);
      engine.outService.enqueueMessageForSending(
          author, "Example: " + engine.getPrefix() + "time Tokyo", isWhisper());
      return Optional.of(Status.FAILED);
    }

    StringBuilder zoneB = new StringBuilder();
    arguments.forEach(a -> zoneB.append(" ").append(a));

    String zone = zoneB.toString().trim();

    /* get coordinates */
    String uri = String.format(apiGeoNames, zone.trim().replace(" ", "%20"));
    String body = getResponseByURL(uri);
    String coordinates = extractCoordinates(body);
    String country = extractCountryName(body);
    String lat = coordinates.split(",")[0];
    String lng = coordinates.split(",")[1];

    String timeUri = String.format(apiTime, lat, lng);
    String responseByURL = getResponseByURL(timeUri);

    TimeResponse timeResponse = gson.fromJson(responseByURL, TimeResponse.class);

    String header = "\\n Time: **" + zone + ", " + country + "** \\n ";
    TimeResponse.StringDto time = timeResponse.getResults();

    String timeZoneUri = String.format(apiTimeIo, lat, lng);
    WeatherTime weatherTime = getTime(Util.getResponseByURL(timeZoneUri));
    String currentTime = getCurrentTimeAt(weatherTime);

    String offsetH;
    int offsetHours = Integer.parseInt(time.getUtc_offset()) / 60;
    if (offsetHours == 0) {
      offsetH = "0";
    } else if (offsetHours > 0) {
      offsetH = "+" + offsetHours;
    } else {
      offsetH = "-" + offsetHours;
    }

    String payload =
        "today: %s \\n"
            + "time: %s \\n"
            + "zone: %s \\n"
            + "UTC offset: %s \\n"
            + "sun rise: %s \\n"
            + "sun set: %s \\n"
            + "first light: %s \\n"
            + "last light: %s \\n"
            + "dawn: %s \\n"
            + "dusk: %s \\n"
            + "solar noon: %s \\n"
            + "golden hour: %s \\n"
            + "day length: %s";

    String formattedPayload =
        String.format(
            Util.alignWithWhiteSpace(payload, ":", "\u2009", false),
            time.getDate(),
            currentTime,
            time.getTimezone(),
            offsetH,
            time.getSunrise(),
            time.getSunset(),
            time.getFirst_light(),
            time.getLast_light(),
            time.getDawn(),
            time.getDusk(),
            time.getSolar_noon(),
            time.getGolden_hour(),
            time.getDay_length());

    engine.outService.enqueueMessageForSending(author, header + formattedPayload, isWhisper());

    log.info("User: {} executed [time] command using trip: {}", author, trip);
    return Optional.of(Status.SUCCESSFUL);
  }

  public String getCurrentTimeAt(WeatherTime time) {
    ZonedDateTime zonedDateTime =
        Instant.ofEpochSecond(tsToSec8601(time.dateTime, time.timeZone))
            .atZone(ZoneId.of(time.timeZone));
    return formatRfc1123(
        zonedDateTime.toEpochSecond(), TimeUnit.SECONDS, zonedDateTime.getZone().toString());
  }
}
