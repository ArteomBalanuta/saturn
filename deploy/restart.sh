#!/bin/bash

# Check if exactly two arguments are provided
if [ $# -ne 2 ]; then
    echo "Usage: $0 <password> <ssh_command>"
    exit 1
fi

# First argument is the password
PASSWORD=$1
SSH_COMMAND=$2

sshpass -p "$PASSWORD" ssh $SSH_COMMAND 'bash ~/workspace/rerun.sh'
