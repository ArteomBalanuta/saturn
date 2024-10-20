#!/bin/bash

# Print the current directory
echo "Current directory: $(pwd)"

cd workspace

echo "Killing saturn.jar..."

PIDS=$(ps aux | grep java | grep "\-jar saturn.jar" | grep -v grep | awk "{print $2}")
readarray -t PIDS_ARR < <(printf "%b\n" "$PIDS")
for PID in ${PIDS_ARR[@]}
do
  kill $PID
done

# Wait a few seconds to ensure the process has terminated
sleep 3

# Start the new Java process
echo "Starting saturn.jar..."
nohup java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof -XX:+PrintGCDetails -XX:ErrorFile=./hs_err_pid%p.log -Dlog4j.configurationFile=log4j2.xml -jar saturn.jar > saturn_stdout.log 2> saturn_stderr.log &

