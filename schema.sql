/* Core schema */
CREATE TABLE banned_users (
	"id" INTEGER PRIMARY KEY AUTOINCREMENT,
	"trip" TEXT,
	"name" TEXT,
	"hash" TEXT,
	"reason" TEXT,
    "created_on" INTEGER NOT NULL
);

CREATE TABLE "executed_commands" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "trip" TEXT,
	"command_name"	TEXT,
	"arguments"	TEXT,
	"status"	TEXT,
	"created_on" INTEGER NOT NULL,
	"channel" TEXT
);

CREATE TABLE "mail" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
	"owner"	TEXT NOT NULL,
	"receiver"	TEXT NOT NULL,
	"message"	TEXT,
	"status"	TEXT NOT NULL,
	"created_on"	INTEGER NOT NULL,
	"is_whisper"	TEXT);

CREATE TABLE "messages" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "trip" TEXT,
    "name" TEXT NOT NULL,
    "hash" TEXT,
    "message" TEXT,
    "created_on" INTEGER NOT NULL,
    "channel" TEXT,
    "visibility" TEXT CHECK(visibility IN ('PUBLIC', 'WHISPER'))
);

CREATE TABLE "notes" (
	"id"	INTEGER PRIMARY KEY AUTOINCREMENT,
	"trip"	TEXT,
	"note"	TEXT,
	"created_on" INTEGER NOT NULL
);

-- USER type is used for whitelisted ?lounge users (*automove cmd)
CREATE TABLE "trips" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "type" TEXT NOT NULL CHECK(type IN ('ADMIN', 'MODERATOR', 'TRUSTED', 'USER', 'REGULAR')),
    "trip" TEXT,
    "created_on" INTEGER NOT NULL,
    UNIQUE ("trip")
);

CREATE TABLE "names" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "name" TEXT,
    "created_on" INTEGER NOT NULL,
    UNIQUE ("name")
);

CREATE TABLE "trip_names" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "trip_id" INTEGER NOT NULL,
    "name_id" INTEGER NOT NULL,
    FOREIGN KEY ("trip_id") REFERENCES "trips" ("id"),
    FOREIGN KEY ("name_id") REFERENCES "names" ("id"),
    UNIQUE ("trip_id", "name_id")
);

CREATE TABLE "dbz_characters" (
	"id"	INTEGER PRIMARY KEY AUTOINCREMENT,
	"name"	TEXT,
	"level"	INTEGER,
	"created_on" INTEGER NOT NULL
);

CREATE TABLE "dbz_stats" (
	"id"	INTEGER PRIMARY KEY AUTOINCREMENT,
	"char_id" INTEGER NOT NULL,
	"str"	INTEGER,
	"agi"	INTEGER,
	"vit"	INTEGER,
	"ene"	INTEGER,
	"free_stats" INTEGER,
	"created_on" INTEGER NOT NULL,
	FOREIGN KEY ("char_id") REFERENCES "dbz_characters" ("id")
);

CREATE TABLE "agent_memory" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "identity_key" TEXT NOT NULL,
    "role" TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
    "content" TEXT NOT NULL,
    "created_on" INTEGER NOT NULL,
    "expires_on" INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_trip_created_on ON messages (trip, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_name_created_on ON messages (name, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_hash_created_on ON messages (hash, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_messages_channel_created_on ON messages (channel, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_agent_messages_name_room_visibility_created
  ON messages (
    name COLLATE NOCASE,
    channel COLLATE NOCASE,
    visibility,
    created_on DESC,
    id DESC);
CREATE INDEX IF NOT EXISTS idx_agent_messages_name_visibility_created
  ON messages (name COLLATE NOCASE, visibility, created_on DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_agent_messages_room_visibility_created
  ON messages (channel COLLATE NOCASE, visibility, created_on DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_agent_messages_trip_visibility_created
  ON messages (trip, visibility, created_on DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_agent_messages_visibility ON messages (visibility);
CREATE INDEX IF NOT EXISTS idx_mail_status_receiver ON mail (status, receiver);
CREATE INDEX IF NOT EXISTS idx_notes_trip_created_on ON notes (trip, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_executed_commands_channel_created_on
  ON executed_commands (channel, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_banned_users_name ON banned_users (name);
CREATE INDEX IF NOT EXISTS idx_banned_users_trip ON banned_users (trip);
CREATE INDEX IF NOT EXISTS idx_banned_users_hash ON banned_users (hash);
CREATE INDEX IF NOT EXISTS idx_agent_memory_identity_created
  ON agent_memory (identity_key, created_on DESC);
CREATE INDEX IF NOT EXISTS idx_agent_memory_expires ON agent_memory (expires_on);
