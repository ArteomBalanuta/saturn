# Saturn Project

`Saturn` is a Moderator Bot made for `Hack.Chat` (_https://github.com/hack-chat_ ) project. 

## Getting started

You may clone the project and step into the `saturn/` directory before proceeding.

Make sure Java 17 or above is installed.
```bash
git clone https://github.com/ArteomBalanuta/saturn.git
cd saturn
```

### 1. Creating and setting up the database
Before proceeding make sure `sqlite3` is installed and available in your terminal.
```bash
/bin/bash /deploy/create_db.sh
```
Check `database.db` is created in `database/` directory.

Adjust `application.properties` configuration file in root directory, set the flag:

`dbPath=database/database.db`

### 2. Compiling the application
Make sure `maven` is set up and is discovered:
```bash
mvn -version
```

Build the project, Jar file `saturn.jar` will be generated in `target/` directory:
```bash
mvn clean package
```

### 3. Adjust the application.properties configuration

Example:

![example](./readme/configuration_example.png)


### 4. Run the application:

```bash
java -Dlog4j.configurationFile=./log4j2.xml -jar target/saturn.jar
```

### Notes

`Fat/Uber` JAR is generated: `/target/saturn.jar`. 

Feel free to move/deploy the application along with: 

`database.db, application.properties, log4j2.xml` files.



# _Have fun_
