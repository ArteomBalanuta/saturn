#!/bin/bash

# Print the current directory
echo "Current directory: $(pwd)"

cd workspace

# Kill any running Java processes
echo "Killing saturn.jar..."
pkill -f 'java -Dlog4j.configurationFile=log4j2.xml -jar saturn.jar'

# Wait a few seconds to ensure the process has terminated
sleep 3

# Start the new Java process
echo "Starting saturn.jar..."
nohup java -Dlog4j.configurationFile=log4j2.xml -jar saturn.jar > /dev/null 2>&1 &
echo "done"
