#!/bin/bash

set -e

# Get command
command=$1

if [ -z "$command" ]; then
    echo "No command given: Missing parameter(s) to do anything."
    echo "  clear:          clears the list of all finished jobs."
    echo "  killall:        kills all jobs of the task spool server."
    echo "  list:           lists all jobs within the job list."
    echo "  cat [id]:       cats the complete output of the job."
    echo "  remove [id]:    removed the job from queue."
    echo "  add:            adds the given command to the queue."
    exit 1;
fi

# Get remains of the argument(s)
shift;
remains="$@"
echo "ts-helper: parameters: $command $remains"

# If the remains are an ID, save it
re='^[0-9]+$'
if [[ $remains =~ $re ]]; then
   id=$remains
   echo "ts-helper: found job ID: $id"
fi

# Function to check if an ID could be found in parameters
check_id () {
    if [ -z ${id+x} ]; then
        echo "No job ID given. Exit."
        exit 1;
    fi
}

tspe="systemd-run --user --unit=tsp-protected -p OOMScoreAdjust=-900 tsp"

# Determine what to do
if [ $command = "clear" ]; then
    echo "ts-helper: clear"
    $tspe -C
elif [ $command = "killall" ]; then
    echo "ts-helper: kill server"
    $tspe -K
elif [ $command = "list" ]; then
    echo "ts-helper: list"
    $tspe -l
elif [ $command = "cat" ]; then
    check_id
    echo "ts-helper: cat job $id"
    $tspe -c $id
elif [ $command = "remove" ]; then
    check_id
    echo "ts-helper: remove job $id"
    $tspe -r $id
elif [ $command = "add" ]; then
    echo "ts-helper: add job"
    $tspe $remains
else
    echo "Command $command not recognized. Exit."
    exit 1;
fi
