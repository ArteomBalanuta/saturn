## Saturn Project

**Saturn** is a Moderator Bot built for the [Hack.Chat](https://github.com/hack-chat) project.

---

### Getting Started

#### 1. Clone the Project
```bash
  git clone https://github.com/ArteomBalanuta/saturn.git
  cd saturn
```

Make sure **JDK 24 or above** is installed:
```bash
  java --version
```

---

#### 2. Database Setup
Before proceeding, ensure `sqlite3` is installed and available in your terminal.

Run the database setup script:
```bash
  /bin/bash deploy/create_db.sh
```

Afterwards, confirm that `database.db` was created inside the `database/' directory.  

In the root `config.toml` file, set the database path:
```toml
dbPath = "database/database.db"
```

---

#### 3. Building the Application

*Linux:*
```bash
  ./mvnw package
```

*Windows:*
```commandline
mvnw.cmd package
```

After the build, the Fat/Uber JAR will be generated at: target/saturn.jar

---
#### 4. Configuration (`config.toml`)

Example configuration:

```toml
dbPath = "database/database.db"          # Path to the database file
wsUrl = "wss://hack.chat/chat-ws"         # Chat's WebSocket address
cmdPrefix = "*"                       # Command prefix used to trigger the bot
channel = "programming"                ## Channel where the bot will join
nick = "alphaBot"                  # Bot nickname
trip = "secret13"                   # Trip password (used for server-side trip code)
userTrips = ""                     # List of trips with basic command access
adminTrips = "g0KY09"               # Trips with ADMIN role
autoReconnect = true               # Enable/disable auto-reconnect
healthCheckInterval = 5            # Interval (minutes) for health checks
autorunCommands = "replica lounge, say hello lads!!"  # Commands run on startup
```

#### 5. Running the Bot

Run directly with Java:
```bash
 java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

You must keep the following files together when deploying:
- `saturn.jar`
- `config.toml`
- `log4j2.xml` ( optional )
- `database.db`

---

## [<img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/docker/docker-original.svg" alt="docker" width="40" height="40"/>](https://hub.docker.com/r/yourusername/saturn) Running with Docker

#### Build the Image

```bash
  docker build -t saturn .
```

#### Run the Container

In case you want to use your db, map it:
```bash
  docker run -d \
      --name saturn \
      -v $(pwd)/config.toml:/app/config.toml \
      -v $(pwd)/database:/app/database \
      saturn
```

#### Run with Docker Compose
This `docker-compose.yml` should be present:

```yaml
version: "3.9"

services:
  saturn:
    image: saturn:latest
    container_name: saturn
    build: .
    environment:
      - JAVA_OPTS=-Xms128m -Xmx256m
    restart: unless-stopped
```

```bash
  docker compose up --build -d
```

- Be aware **DB** will be setup automatically and will be **empty** withing the container.

---

## Notes
- Configurations are handled through `config.toml`.
- Database setup is required before running locally.
- Docker support is provided for easier deployment.

---

# _Have fun!_
