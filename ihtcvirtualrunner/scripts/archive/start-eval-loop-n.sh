#!/bin/bash

#
# This script can be used to execute the virtualized GIPS-based solution for all of the IHTC 2024 instances `n` times.
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

# Configuration taken by the arguments
export repetitions=$1
export randomSeed=0
export callback="./callback.json"
export parameter="./parameter.json"
export preprocessing="nogt"
export timeoutPerExperiment=60
export constraintCleanup="u"

if [ -z "$repetitions" ]; then
    echo "# Script error: Missing parameter for repetitions."
    exit 1
fi

function run_loop_script {
    ./start-eval-loop.sh \
        $randomSeed \
        $callback \
        $parameter \
        $preprocessing \
        $timeoutPerExperiment \
        $constraintCleanup
}

echo "#"
echo "# => Repetition script start."
echo "# Number of repetitions: ${repetitions}."
echo "#"

for ((i=1;i<=${repetitions};i++));
do
    # Create output folder for this repetition run
    mkdir -p output/repetition_$i
    # Run loop script to try to calculate all 30 solutions
    # Its output must be saved to the output folder specific for this repetition
    run_loop_script 2>&1 | tee output/repetition_$i/eval-loop.log
    # Move Gurobi's log file to the repetition's output folder
    mv Gurobi*.log output/repetition_$i/
    # Create output folder for the respective solutions
    mkdir -p output/repetition_$i/solutions
    mv ihtc2024_competition_instances/i*_solution.json output/repetition_$i/solutions
    mv logs output/repetition_$i/
done

echo "#"
echo "# => Repetition script done."
echo "#"
