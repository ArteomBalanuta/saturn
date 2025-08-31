# Use a base image with JDK 24 (Eclipse Temurin is a good source)
FROM eclipse-temurin:24-jdk

# Set working directory
WORKDIR /app

# Install Maven, sqlite3, and dos2unix
RUN apt-get update && \
    apt-get install -y maven sqlite3 dos2unix && \
    rm -rf /var/lib/apt/lists/*

# Copy source code
COPY . /app

# Fix line endings for shell scripts
RUN dos2unix /app/deploy/*.sh

# Make the script executable and run it
RUN chmod +x /app/deploy/create_db.sh && /app/deploy/create_db.sh

# Package the Maven project
RUN mvn clean package

RUN mv target/saturn.jar saturn.jar

# Run the bot
CMD ["java", "-Dlog4j.configurationFile=./log4j2.xml", "-jar", "saturn.jar"]

