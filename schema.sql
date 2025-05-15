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

## DML, useful

select distinct t.trip, n.name
from trip_names tn
inner join names n on tn.name_id  = n.id
inner join trips t on tn.trip_id = t.id order by n.name desc;


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


-- Delete from trip_names first due to foreign key constraints
DELETE FROM trip_names
WHERE trip_id IN (
    SELECT id FROM trips WHERE trip = 'TARGET_TRIP'
) OR name_id IN (
    SELECT id FROM names WHERE name = 'TARGET_NAME'
);

-- Delete from trips
DELETE FROM trips
WHERE trip = 'TARGET_TRIP';

-- Delete from names
DELETE FROM names
WHERE name = 'TARGET_NAME';


UPDATE sqlite_sequence
SET seq = 0
WHERE name = 'trip_names';


-- fun


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
FROM Probability where trip !='' and trip !='null'
ORDER BY trip, day_number, hour;

-- cmd history
select trip,command_name,channel,datetime(created_on / 1000, 'unixepoch') as dt from executed_commands where channel = 'lounge' order by created_on desc limit 30