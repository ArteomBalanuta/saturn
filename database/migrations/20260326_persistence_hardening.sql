PRAGMA foreign_keys = ON;

CREATE INDEX IF NOT EXISTS idx_messages_trip_created_on ON messages (trip, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_name_created_on ON messages (name, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_hash_created_on ON messages (hash, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_channel_created_on ON messages (channel, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_mail_status_receiver ON mail (status, receiver);
CREATE INDEX IF NOT EXISTS idx_notes_trip_created_on ON notes (trip, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_executed_commands_channel_created_on
  ON executed_commands (channel, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_banned_users_name ON banned_users (name);
CREATE INDEX IF NOT EXISTS idx_banned_users_trip ON banned_users (trip);
CREATE INDEX IF NOT EXISTS idx_banned_users_hash ON banned_users (hash);
