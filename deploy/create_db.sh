#!/bin/bash
# Print the current directory
echo "Current directory: $(pwd)"

# Kill any running Java processes
echo "Recreating sqlite3 database file..."

sqlite3 ../database/database.db <<EOF
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
    "channel" TEXT
);

CREATE TABLE "notes" (
	"id"	INTEGER PRIMARY KEY AUTOINCREMENT,
	"trip"	TEXT,
	"note"	TEXT,
	"created_on" INTEGER NOT NULL
);

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
    "created_on" INTEGER NOT NULL
);

CREATE TABLE "trip_names" (
    "id" INTEGER PRIMARY KEY AUTOINCREMENT,
    "trip_id" INTEGER NOT NULL,
    "name_id" INTEGER NOT NULL,
    FOREIGN KEY ("trip_id") REFERENCES "trips" ("id"),
    FOREIGN KEY ("name_id") REFERENCES "names" ("id"),
    UNIQUE ("trip_id", "name_id")
);
EOF