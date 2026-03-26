## Saturn

**Saturn** is a moderator bot for [Hack.Chat](https://github.com/hack-chat).

It supports moderation commands, user utilities, room replicas, persistence with SQLite, and Docker-based deployment.

---

## Requirements

- JDK 24+
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

`config.example.toml` is tracked in git. Your local `config.toml` is intentionally ignored.

---

## Local Development

### Create a Fresh Database

```bash
make fresh-db
```

This recreates `database/database.db` from `schema.sql` and applies SQL migrations from `database/migrations/`.

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
make logs       # follow container logs
make shell      # open a shell in the container
make status     # show container status
make help       # list all targets
```

`make run` and `make rebuild` mount:

- `./config.toml` to `/app/config.toml`
- `./database` to `/app/database`

### Manual Docker Commands

```bash
docker build -t saturn .

docker run -d \
  --name saturn \
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
- The local database is not recreated automatically unless you run `make fresh-db`.
- Docker image builds also create a database inside the image, but your local mounted `database/` overrides it at runtime.

---

## Have Fun
