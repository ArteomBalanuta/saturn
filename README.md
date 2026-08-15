## Saturn

**Saturn** is a moderator bot for [Hack.Chat](https://github.com/hack-chat).

It supports moderation commands, user utilities, room replicas, persistence with SQLite, and Docker-based deployment.

---

## Requirements

- JDK 23+
- `sqlite3`
- Docker (optional, for containerized runs)

Check your Java version:

```bash
java --version
```

---

## Quick Start

```bash
git clone https://github.com/ArteomBalanuta/saturn.git
cd saturn
cp config.example.toml config.toml
make fresh-db
./mvnw package
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

---

## Configuration

Main runtime configuration lives in `config.toml`.

Start from the template:

```bash
cp config.example.toml config.toml
```

Example:

```toml
dbPath = "database/database.db"
wsUrl = "wss://hack.chat/chat-ws"
cmdPrefix = "*"
channel = "programming"
nick = "alphaBot"
trip = "secret13"
userTrips = ""
adminTrips = "g0KY09"
autoReconnect = true
healthCheckInterval = 5
autorunCommands = "replica lounge, say hello lads!!"

[agent]
enabled = true
endpoint = "http://83.218.196.156:16261"
apiKeyEnv = "SATURN_AGENT_API_KEY"
timeoutSeconds = 30
maxCompletionTokens = 768
thinkingEnabled = false
maxConcurrentRequests = 2
maxToolCalls = 4
memoryTurns = 10
memoryTtlHours = 168
dynamicSqlEnabled = true
dynamicSqlMaxSqlChars = 4000
dynamicSqlMaxRows = 50
dynamicSqlMaxColumns = 32
dynamicSqlMaxCellChars = 2000
dynamicSqlMaxResultChars = 32000
dynamicSqlTimeoutMillis = 1000
```

Important fields:

- `dbPath`: path to the SQLite database
- `cmdPrefix`: command prefix used in chat
- `channel`: room Saturn joins on startup
- `trip`: bot trip password
- `userTrips`: comma-separated trips with basic access
- `adminTrips`: comma-separated admin trips
- `autoReconnect`: enables health-check restart behavior
- `autorunCommands`: commands executed after startup
- `agent.endpoint`: OpenAI-compatible router base URL; Saturn calls `/v1/chat/completions`
- `agent.apiKeyEnv`: environment variable containing an optional bearer token
- `agent.maxCompletionTokens`: provider-side output-token limit for each completion
- `agent.thinkingEnabled`: enables model thinking mode; disabled by default for predictable latency
- `agent.maxConcurrentRequests`: maximum accepted active and queued agent requests per engine
- `agent.maxToolCalls`: total tool-call budget for one request
- `agent.memoryTurns` and `agent.memoryTtlHours`: bounded SQLite conversation memory
- `agent.dynamicSqlEnabled`: enables admin-only schema inspection and generated read-only SQL
- `agent.dynamicSqlMax*`: bounds SQL length, rows, columns, cells, and serialized results
- `agent.dynamicSqlTimeoutMillis`: interrupts generated queries after the configured deadline

The endpoint selects the model by default, so `agent.model` may remain empty. Set
`SATURN_AGENT_API_KEY` only when the endpoint requires authentication. The configured endpoint
currently uses unencrypted HTTP; prompts and tool results should only cross a trusted private
network or an HTTPS reverse proxy.

### Agent Command

```text
*l how many users are in the room right now?
```

The agent can inspect live users in any Saturn-managed room, retrieve bounded public message history
for a named user across all rooms or within one named room, run named read-only database queries, and
execute an allowlist of non-destructive Saturn commands under the requesting user's existing
authorization. It cannot invoke admin commands or recursively invoke `l`.

Public conversation memory is shared by everyone in the same room, so another participant can
continue an earlier exchange. Each room uses one FIFO agent worker so routing and replies preserve
submission order. Whispers use private per-user memory and are never added to the public room
session. Rooms remain separate, and memory expires according to the configured TTL.

New message audit rows carry an explicit `PUBLIC` or `WHISPER` visibility. Regular agent history
tools only read `PUBLIC` rows. Existing rows from before this migration remain unclassified and are
excluded because Saturn cannot safely infer whether they came from public chat or a whisper.

Configured admin trips and users with a persisted `ADMIN` role also receive a generated-SQL
fallback. The agent must inspect the schema first and may then run one bounded, AST-validated
`SELECT` on a dedicated read-only SQLite connection. This admin capability can read every Saturn
application table and column, including cross-room messages, trip/hash identity data, mail, notes,
moderation data, command history, agent memory, whispers, and unclassified legacy message rows.
SQLite internal tables, writes, schema changes, pragmas, attached databases, and extension loading
remain blocked. Logs contain only a query fingerprint, duration, row count, and outcome; raw
generated SQL is not logged.

`config.example.toml` is tracked in git. Your local `config.toml` is intentionally ignored.

---

## Local Development

### Create a Fresh Database

```bash
make fresh-db
```

This stops the Docker container, removes the database and any SQLite sidecar files, recreates
`database/database.db` from `schema.sql`, and applies migrations from `database/migrations/`.

### Build

Linux/macOS:

```bash
./mvnw package
```

Windows:

```powershell
mvnw.cmd package
```

The build output is:

```text
target/saturn.jar
```

### Run

```bash
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

When running outside Docker, keep these files available together:

- `target/saturn.jar`
- `config.toml`
- `config.example.toml` (optional, as a template)
- `log4j2.xml`
- `database/database.db`

---

## Docker

The easiest Docker workflow uses the included `Makefile`.

### First Run

```bash
make fresh-db
make rebuild
make logs
```

### Common Commands

```bash
make build      # build the image
make run        # recreate and run the container
make rebuild    # clean, build, and run
make stop       # stop the container
make rm         # remove the container
make rmi        # remove the image
make clean      # remove both container and image
make db-check   # stop Saturn and verify SQLite integrity
make backup-db  # stop Saturn and create a consistent database backup
make logs       # follow container logs
make shell      # open a shell in the container
make status     # show container status
make help       # list all targets
```

`make run` and `make rebuild` mount:

- `./config.toml` to `/app/config.toml`
- `./database` to `/app/database`

Container removal is graceful: Saturn receives up to 30 seconds to close its WebSocket, replicas,
agent executor, and SQLite connections before Docker removes it.

Do not open the bind-mounted database with host-side `sqlite3` while Saturn is running. Docker
Desktop and the host can observe different file-lock state for the same SQLite WAL files, which can
corrupt the database. Use `make db-check` or `make backup-db`; both stop Saturn before touching the
database. Run `make start` afterward to resume the existing container.

### Manual Docker Commands

```bash
docker build -t saturn .

docker run -d \
  --name saturn \
  --env SATURN_AGENT_API_KEY \
  -v $(pwd)/config.toml:/app/config.toml \
  -v $(pwd)/database:/app/database \
  saturn
```

### Docker Compose

```bash
docker compose up --build -d
```

If you use the local bind-mounted setup, the container will use your local `config.toml` and `database/`.

---

## Project Notes

- SQLite is used for persistence.
- Startup adds the message-visibility column and matching agent query indexes to legacy databases.
- The local database is not recreated automatically unless you run `make fresh-db`.
- Docker image builds also create a database inside the image, but your local mounted `database/` overrides it at runtime.

---

## Have Fun
