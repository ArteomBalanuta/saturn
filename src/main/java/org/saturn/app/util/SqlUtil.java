package org.saturn.app.util;

public final class SqlUtil {
    public static final String INSERT_INTO_TRIPS_TYPE_TRIP_CREATED_ON_VALUES = "INSERT INTO trips('type', 'trip', 'created_on') VALUES (?, ?, ?);";
    public static final String UPDATE_TRIPS_SET_TYPE_WHERE_TRIP = "UPDATE trips SET type=? WHERE trip=?;";
    public static final String INSERT_INTO_EXECUTED_COMMANDS_TRIP_COMMAND_NAME_ARGUMENTS_STATUS_CREATED_ON_VALUES = "INSERT INTO executed_commands ('trip','command_name','arguments','status','created_on') VALUES (?, ?, ?, ?, ?);";
    public static final String INSERT_INTO_MESSAGES_TRIP_NAME_HASH_MESSAGE_CREATED_ON_VALUES = "INSERT INTO messages ('trip', 'name', 'hash', 'message', 'created_on') VALUES (?, ?, ?, ?, ?);";
    public static final String INSERT_INTO_MAIL_OWNER_RECEIVER_MESSAGE_STATUS_IS_WHISPER_CREATED_ON_VALUES = "INSERT INTO mail ('owner','receiver','message','status','is_whisper', 'created_on') VALUES (?, ?, ?, ?, ?, ?);";
    public static final String SELECT_MAIL_BY_NICK_OR_TRIP = "SELECT owner, receiver, message, status, is_whisper, created_on FROM mail WHERE receiver IN (?,?) AND status = 'PENDING';";
    public static final String UPDATE_MAIL_SET_STATUS_DELIVERED_WHERE_RECEIVER = "UPDATE mail SET status='DELIVERED' WHERE receiver = ?";
    public static final String INSERT_INTO_BANNED_USERS_TRIP_NAME_HASH_REASON_CREATED_ON_VALUES = "INSERT INTO banned_users(trip,name,hash,reason,created_on) VALUES (?,?,?,?,?);";
    public static final String DELETE_FROM_BANNED_USERS_WHERE_NAME_OR_TRIP_OR_HASH = "DELETE FROM banned_users WHERE name == ? OR trip == ? OR hash = '?';";
    public static final String SELECT_BANNED_USERS = "SELECT trip,name,hash,reason FROM banned_users;";
    public static final String DELETE_FROM_BANNED_USERS = "DELETE FROM banned_users;";
    public static final String INSERT_INTO_NOTES_TRIP_NOTE_CREATED_ON_VALUES = "INSERT INTO notes ('trip', 'note','created_on') VALUES (?, ?, ?);";
    public static final String SELECT_NOTES_BY_TRIP = "SELECT * FROM notes WHERE trip = ?";
    public static final String DELETE_FROM_NOTES_WHERE_TRIP = "DELETE FROM notes WHERE trip = ?";
    public static final String SELECT_DISTINCT_HASH_NAME_FROM_MESSAGES_WHERE_TRIP = "select distinct hash,name from messages where trip = '";
    public static final String SELECT_DISTINCT_HASH_NAME_FROM_MESSAGES_WHERE_HASH = "select distinct hash,name from messages where hash = '";
    public static final String SELECT_LAST_SEEN = "SELECT message,created_on FROM messages WHERE (name = ? or trip = ?) and (message not in ('LEFT','JOINED')) order by created_on desc limit 1;";
    public static final String SELECT_SESSION_JOINED = "SELECT created_on FROM messages WHERE (name = ? or trip = ?) and message = 'JOINED' order by created_on desc limit 1;";
}
