package org.saturn.app.util;

public final class SqlUtil {
  public static final String INSERT_INTO_TRIPS_TYPE_TRIP_CREATED_ON_VALUES =
      "INSERT INTO trips('type', 'trip', 'created_on') VALUES (?, ?, ?);";
  public static final String UPDATE_TRIPS_SET_TYPE_WHERE_TRIP =
      "UPDATE trips SET type=? WHERE trip=?;";

  public static final String DELETE_TRIP_NAMES =
      """
    DELETE FROM trip_names WHERE trip_id IN (
            SELECT id FROM trips WHERE trip = ?
    ) OR name_id IN (
    SELECT id FROM names WHERE name = ?
    );
    """;

  public static final String DELETE_TRIP =
      """
    DELETE FROM trips WHERE trip = ?;
    """;

  public static final String DELETE_NAME =
      """
    DELETE FROM names WHERE name = ?;
    """;

  public static final String INSERT_NAMES =
      "INSERT INTO names (name, created_on) VALUES (?, strftime('%s', 'now'))";
  public static final String INSERT_TRIPS =
      "INSERT INTO trips (type, trip, created_on) VALUES (?, ?, strftime('%s', 'now'))";
  public static final String INSERT_TRIP_NAME =
      "INSERT INTO trip_names (trip_id, name_id) VALUES (?, ?)";
  public static final String
      INSERT_INTO_EXECUTED_COMMANDS_TRIP_COMMAND_NAME_ARGUMENTS_STATUS_CREATED_ON_VALUES =
          "INSERT INTO executed_commands ('trip','command_name','arguments','status','created_on','channel') VALUES (?, ?, ?, ?, ?, ?);";
  public static final String INSERT_INTO_MESSAGES_TRIP_NAME_HASH_MESSAGE_CREATED_ON_VALUES =
      "INSERT INTO messages ('trip', 'name', 'hash', 'message', 'created_on', 'channel') VALUES (?, ?, ?, ?, ?, ?);";
  public static final String
      INSERT_INTO_MAIL_OWNER_RECEIVER_MESSAGE_STATUS_IS_WHISPER_CREATED_ON_VALUES =
          "INSERT INTO mail ('owner','receiver','message','status','is_whisper', 'created_on') VALUES (?, ?, ?, ?, ?, ?);";

  public static final String GET_TRIP_BY_NICK_REGISTERED_OR_TRIP =
      """
            SELECT t.trip\s
            FROM trip_names tn\s
            INNER JOIN names n on tn.name_id  = n.id\s
            INNER JOIN trips t on tn.trip_id = t.id\s
            WHERE LOWER(name) = ? OR LOWER(t.trip) = ?;""";

  public static final String SELECT_NAME_TRIP_REGISTERED =
      """
SELECT DISTINCT n.name,t.trip\s
FROM trip_names tn\s
INNER JOIN trips t on tn.trip_id = t.id\s
INNER JOIN names n on tn.name_id = n.id ORDER BY t.trip DESC;
""";
  public static final String SELECT_MAIL_BY_NICK_OR_TRIP =
      "SELECT id, owner, receiver, message, status, is_whisper, created_on FROM mail WHERE receiver LIKE ? AND status = 'PENDING';";
  public static final String UPDATE_MAIL_SET_STATUS_DELIVERED_WHERE_RECEIVER =
      "UPDATE mail SET status='DELIVERED' WHERE id = ?";
  public static final String INSERT_INTO_BANNED_USERS_TRIP_NAME_HASH_REASON_CREATED_ON_VALUES =
      "INSERT INTO banned_users(trip,name,hash,reason,created_on) VALUES (?,?,?,?,?);";
  public static final String DELETE_FROM_BANNED_USERS_WHERE_NAME_OR_TRIP_OR_HASH =
      "DELETE FROM banned_users WHERE name = ? OR trip = ? OR hash = ?;";
  public static final String SELECT_BANNED_USERS =
      "SELECT trip,name,hash,reason FROM banned_users;";
  public static final String SELECT_ROLE_BY_TRIP = "SELECT type FROM trips WHERE trip == ?;";

  /* For now using USER role per every whitelisted ?lounge user */
  public static final String SELECT_LOUNGE_TRIPS = "SELECT trip FROM trips WHERE type = 'USER';";
  public static final String DELETE_FROM_BANNED_USERS = "DELETE FROM banned_users;";
  public static final String INSERT_INTO_NOTES_TRIP_NOTE_CREATED_ON_VALUES =
      "INSERT INTO notes ('trip', 'note','created_on') VALUES (?, ?, ?);";
  public static final String SELECT_NOTES_BY_TRIP = "SELECT * FROM notes WHERE trip = ?";
  public static final String DELETE_FROM_NOTES_WHERE_TRIP = "DELETE FROM notes WHERE trip = ?";
  public static final String SELECT_DISTINCT_HASH_NAME_FROM_MESSAGES_WHERE_TRIP =
      "select distinct hash,name from messages where trip = '";
  public static final String SELECT_DISTINCT_HASH_NAME_FROM_MESSAGES_WHERE_HASH =
      "select distinct hash,name from messages where hash = '";
  public static final String SELECT_LAST_SEEN =
      "SELECT message,created_on FROM messages WHERE (name = ? or trip = ?) and (message not in ('LEFT','JOINED')) order by created_on desc limit 1;";

  /*
    strftime('%s', 'now') gets the current time in seconds.
    900 is 15 * 60 seconds (15 minutes).
    We multiply by 1000 because created_on is in milliseconds.
    This query will return all rows where the created_on timestamp is within the last 15 minutes.
  */
  public static final String SELECT_SEEN_RECENTLY_AS =
      "SELECT distinct name FROM messages WHERE (hash = ? or (trip = ? and (trip IS NOT NULL and trip != '' and trip != 'null'))) and (message in ('LEFT','JOINED')) and created_on >= (strftime('%s', 'now') - 900) * 1000 limit 5";

  public static final String SELECT_LAST_N_MESSAGES =
      "SELECT name,message,created_on FROM messages WHERE (name = ? or trip = ?) and (message not in ('LEFT','JOINED')) order by created_on desc limit ?;";
  public static final String SELECT_SESSION_JOINED =
      "SELECT created_on FROM messages WHERE (name = ? or trip = ?) and message = 'JOINED' order by created_on desc limit 1;";
}
