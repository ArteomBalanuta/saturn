# Saturn Project

`Saturn` is a Moderator Bot made for `Hack.Chat` (_https://github.com/hack-chat_ ) project. 

## Getting started
Clone the project and step into the `saturn/` directory before proceeding.

```bash
git clone https://github.com/ArteomBalanuta/saturn.git
cd saturn
```

Make sure JDK 23 or above is installed.
```bash
java --version
```

### 1. Creating and setting up the database
Before proceeding make sure `sqlite3` is installed and available in your terminal.
```bash
/bin/bash /deploy/create_db.sh
```
Check `database.db` is created in `database/` directory.

Adjust `config.toml` configuration file in root directory, set the flag:

`dbPath = "database/database.db"`

### 2. Building the application
Linux:
```bash
 ./mvnw package
```

Windows:
```commandline
mvnw.cmd package
```

After build is complete Jar file `saturn.jar` will be generated in `target/` directory:

### 3. Adjust the configuration file - config.toml

Example:

`dbPath = "database/database.db"` Path do database file. Relative/absolute path can be used.

`wsUrl = "wss://hack.chat/chat-ws"` Chat's WebSocket address.

`cmdPrefix = "*"` Command prefix used to trigger the bot.

`channel = "programming"` Channel used by the bot.

`nick = "alphaBot"` Bot's nick.

`trip = "secret13"` Bot's password is used by the server to generate the trip.

`userTrips = ""`    List of trips that are granted access to run basic commands.

`adminTrips = "g0KY09"` Trip codes that are granted ADMIN role, full access.

`autoReconnect = true` Enable/disable reconnect feature.

`healthCheckInterval = 5 ` Setting the health check interval in minutes.

`autorunCommands = "replica lounge, say hello lads!"` Autorun commands to be executed on bot's startup (example uses: replica, say commands without prefix used).

### 4. Run the bot:

```bash
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

### Notes

`Fat/Uber` JAR is generated at: `/target/saturn.jar`. 

Feel free to move/deploy the application along with next required files: 

`database.db, config.toml, log4j2.xml`



# _Have fun_
