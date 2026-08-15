# Use a base image with JDK 24 (Eclipse Temurin is a good source)
FROM eclipse-temurin:24-jdk AS build

# Install Maven and dos2unix
RUN apt-get update && \
    apt-get install -y maven dos2unix && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src src
COPY log4j2.xml .

COPY deploy deploy
COPY VERSION .
COPY config.example.toml .

# Fix line endings for shell scripts
RUN dos2unix deploy/*.sh

# Make the script executable
RUN chmod +x deploy/create_db.sh

# Package the project
RUN mvn clean package -DskipTests

FROM eclipse-temurin:24-jre

WORKDIR /app
COPY --from=build /app/target/saturn.jar .
COPY --from=build /app/log4j2.xml .
COPY --from=build /app/config.example.toml ./config.toml

# Run
CMD ["java", "-Dlog4j.configurationFile=log4j2.xml", "-jar", "saturn.jar"]
