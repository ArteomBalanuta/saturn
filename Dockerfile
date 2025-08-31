# Use a base image with JDK 24 (Eclipse Temurin is a good source)
FROM eclipse-temurin:24-jdk AS build

WORKDIR /app

# Install Maven, sqlite3, and dos2unix
RUN apt-get update && \
    apt-get install -y maven sqlite3 dos2unix && \
    rm -rf /var/lib/apt/lists/*

COPY . /app

# Fix line endings for shell scripts
RUN dos2unix /app/deploy/*.sh

# Make the script executable and run it
RUN chmod +x /app/deploy/create_db.sh && /app/deploy/create_db.sh

# Package the Maven project
RUN mvn clean package -DskipTests

FROM eclipse-temurin:24-jre
COPY --from=build /app/target/saturn.jar saturn.jar
COPY --from=build /app/database/database.db database/database.db
COPY --from=build /app/log4j2.xml log4j2.xml
COPY --from=build /app/config.toml config.toml

# Run the bot
CMD ["java", "-Dlog4j.configurationFile=./log4j2.xml", "-jar", "saturn.jar"]

