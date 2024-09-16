/* TODO: implement the code changes. wip */
# DDL
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

## DML, useful

select t.trip
from trip_names tn
inner join names n on tn.name_id  = n.id
inner join trips t on tn.trip_id = t.id
where LOWER(name) = 'xen0';

select tn.id,name,trip from names n
inner join trip_names tn on n.id = tn.name_id
inner join trips t on t.id = tn.trip_id;

INSERT INTO names (name, created_on) VALUES ('nathan',strftime('%s', 'now'));
INSERT INTO trips (type, trip, created_on) VALUES ('MODERATOR','//////',strftime('%s', 'now'));

INSERT INTO trip_names (trip_id,name_id) VALUES (20, 13);

UPDATE sqlite_sequence
SET seq = 0
WHERE name = 'trip_names';


-- fun

WITH DateRange AS (
    SELECT
        strftime('%Y-%m-%d', 'now', '-7 days') AS start_date,
        strftime('%Y-%m-%d', 'now') AS end_date
),

-- Count messages for each trip per day
MessagesPerTrip AS (
    SELECT
        trip,
        strftime('%Y-%m-%d', created_on / 1000, 'unixepoch') AS message_date,
        COUNT(*) AS message_count
    FROM messages
    JOIN DateRange ON created_on BETWEEN strftime('%s', start_date) * 1000 AND strftime('%s', end_date) * 1000
    WHERE created_on >= (strftime('%s', 'now') * 1000 - 7 * 24 * 60 * 60 * 1000)
    GROUP BY trip, message_date
),

-- Count total messages for each day
TotalMessagesPerDay AS (
    SELECT
        strftime('%Y-%m-%d', created_on / 1000, 'unixepoch') AS message_date,
        COUNT(*) AS total_message_count
    FROM messages
    JOIN DateRange ON created_on BETWEEN strftime('%s', start_date) * 1000 AND strftime('%s', end_date) * 1000
    GROUP BY message_date
),

-- Calculate the probability of each trip being active on each day
Probability AS (
    SELECT
        m.trip,
        m.message_date,
        (m.message_count * 1.0 / t.total_message_count) * 100 AS probability_percentage,
        strftime('%w', m.message_date) AS day_number, -- Numeric day of the week (0 = Sunday, 6 = Saturday)
        CASE strftime('%w', m.message_date)
            WHEN '0' THEN 'Sunday'
            WHEN '1' THEN 'Monday'
            WHEN '2' THEN 'Tuesday'
            WHEN '3' THEN 'Wednesday'
            WHEN '4' THEN 'Thursday'
            WHEN '5' THEN 'Friday'
            WHEN '6' THEN 'Saturday'
        END AS day_full
    FROM MessagesPerTrip m
    JOIN TotalMessagesPerDay t ON m.message_date = t.message_date
)

-- Final result
SELECT
    trip,
    day_full AS day_of_week,
    probability_percentage AS probability_percentage
FROM Probability
ORDER BY trip, message_date;