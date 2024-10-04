#!/bin/bash

# Print the current directory
echo "Current directory: $(pwd)"

cd workspace

echo "Killing saturn.jar..."
pkill -f 'java -Dlog4j.configurationFile=log4j2.xml -jar saturn.jar'

# Wait a few seconds to ensure the process has terminated
sleep 3

# Start the new Java process
echo "Starting saturn.jar..."
nohup java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof -XX:+PrintGCDetails -XX:ErrorFile=./hs_err_pid%p.log -Dlog4j.configurationFile=log4j2.xml -jar saturn.jar > saturn_stdout.log 2> saturn_stderr.log &

