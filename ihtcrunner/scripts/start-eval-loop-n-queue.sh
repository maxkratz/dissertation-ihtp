#!/bin/bash

#
# This script can be used to execute the non-virtualized GIPS-based solution for all of the IHTC 2024 instances `n` times.
#
# It assumes that in the current directory there is a folder called
# `ihtc2024_competition_instances` containing all instance files.
#
# Example: `./start-eval-loop-n.sh $numberOfRepetitions
#                                  3
#                                  $1
#
# If you have any questions, feel free to write us an email.
#
# @author Maximilian Kratz (maximilian.kratz@es.tu-darmstadt.de)
#

# Disable OOM killer on system level
echo "# Disable OOM killer on system level."
sudo bash -c 'echo 2 > /proc/sys/vm/overcommit_memory'
sudo bash -c 'echo 99 > /proc/sys/vm/overcommit_ratio'

# Configuration taken by the arguments
export repetitions=$1
export randomSeed=0
export callback="./callback.json"
export parameter="./parameter.json"
export timeoutPerExperiment=600
export timeoutBuild=300
export constraintCleanup="u"

if [ -z "$repetitions" ]; then
    echo "# Script error: Missing parameter for repetitions."
    exit 1
fi

function create_job {
    ./ts-helper.sh \
        add \
        ./start-args-gips-queue.sh \
            $1 \
            $2 \
            $3 \
            $randomSeed \
            $callback \
            $parameter \
            $timeoutPerExperiment \
            $timeoutBuild \
            $constraintCleanup
}

function expand_i {
    if [ $1 -lt 10 ]; then
        echo "0$1"
    else
        echo "$1"
    fi
}

echo "#"
echo "# => Repetition queuing script start."
echo "# Number of repetitions: ${repetitions}."
echo "#"

# Do for $repetitions number of repetitions
for ((r=1;r<=${repetitions};r++));
do
    # Output folder of this repetition's run will be created below

    # Do for all 30 instances
    for ((i=1;i<=30;i++));
    do
        # Create output folder for this $i
        iexp=$(expand_i $i)
        output_folder=./output/repetition_$r/$iexp
        mkdir -p $output_folder

        # Schedule job
        create_job \
            "./ihtc2024_competition_instances/" \
            "i$iexp.json" \
            "$output_folder" \
            "$output_folder/$iexp_solution.json"
    done
done

echo "#"
echo "# => Repetition queuing script done."
echo "#"
