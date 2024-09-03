#!/bin/bash

cd ~/workspace/projects/saturn/database
# Check if exactly two arguments are provided
if [ $# -ne 2 ]; then
    echo "Usage: $0 <password> <ssh_command>"
    exit 1
fi

# First argument is the password
PASSWORD=$1
SSH_COMMAND=$2

# Upload the files using sftp
sshpass -p "$PASSWORD" sftp -oBatchMode=no -b - $SSH_COMMAND << !
cd workspace/
put database.db
bye
!
