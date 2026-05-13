#!/bin/bash

#
# This script can be used to run the GIPS-based, virtualized
# IHTC/IHTP solution via tsp.
#
# @author Maximilian Kratz (maximilian.kratz@es.tu-darmstadt.de)
#

# set -e

function setup {
    # Make sure that log folders exists
    mkdir -p $outputFolder

    # Extract needed XMI files
    echo "# Script info: Applying GIPS XMI workarounds."

    # Extract XMI files
    unzip -qq -o $JAR "ihtcvirtualgipssolution/hipe/*/hipe-network.xmi"
    unzip -qq -o $JAR "ihtcvirtualgipssolution/api/*/gips-model.xmi"
    unzip -qq -o $JAR "ihtcvirtualgipssolution/api/ibex-patterns.xmi"
    unzip -qq -o $JAR "ihtcvirtualpreprocessing/hipe/*/hipe-network.xmi"
    unzip -qq -o $JAR "ihtcvirtualpreprocessing/api/ibex-patterns.xmi"
    unzip -qq -o $JAR "ihtcvirtualpostprocessing/hipe/*/hipe-network.xmi"
    unzip -qq -o $JAR "ihtcvirtualpostprocessing/api/ibex-patterns.xmi"
}

function run_experiment {
    # Execute the program itself and save its output to log file
    echo "# Script info: Using global timeout: ${globalTimeout}s"
    TIMEFORMAT='# Script info: Process execution took %R seconds to complete.'
    time {
        # Run timeout in the background
        timeout $globalTimeout java -Xmx240g -XX:+ExitOnOutOfMemoryError -jar $JAR $ARGS &
        # Assign timeout's PID to a variable
        pid=$!
        # Wait until timeout has finished
        wait $pid
        if [ $? -eq 124 ]; then
            echo "# Script info: Timelimit of ${globalTimeout}s for GIPS run violated. Run was killed before finishing."
        fi
    }

    # Move Gurobi's log file to the repetition's output folder
    mv Gurobi*.log $outputFolder
}

function cleanup {
    rm -r ./ihtcvirtualgipssolution
    rm -r ./ihtcvirtualpreprocessing
    rm -r ./ihtcvirtualpostprocessing
    rm -r ../ihtcvirtualmetamodel
}

function run_wrap_all {
    # Run setup
    setup

    # Actual run
    export ARGS="-i $inputJson -o $outputJson --verbose --randomseed $randomSeed --callback $callback --parameter $parameter --preprocessing $preprocessing"

    echo "# Script info: Using ARGS: $ARGS"
    run_experiment
    # Finished actual run

    # Clean up extracted files
    cleanup

    echo "# => Arg queuing script done."
}

# Set env vars
source env.sh

# Config
export JAR="gips-ihtc.jar"

# Example arguments:
# ./input i01.json ./output 0  ./callback.json ./parameter.json gt 3600 u
# $1      $2       $3       $4 $5              $6               $7 $8   $9

export inputFolder=$1
export inputJsonName=$2
export inputJson="$inputFolder/$inputJsonName"
export outputFolder=$3
export outputJson="$outputFolder/${inputJsonName%.json}_solution.json"
export randomSeed=$4
export callback=$5
export parameter=$6
export preprocessing=$7
export globalTimeout=$8
export constraintCleanUp=$9

# Verify arguments
if [ -z ${inputFolder+x} ]; then
    echo "Error: no input folder given. Exit."
    exit 1;
fi
if [ -z ${inputJsonName+x} ]; then
    echo "Error: no input JSON name given. Exit."
    exit 1;
fi
if [ -z ${outputFolder+x} ]; then
    echo "Error: no output folder given. Exit."
    exit 1;
fi
if [ -z ${randomSeed+x} ]; then
    echo "Error: no random seed given. Exit."
    exit 1;
fi
if [ -z ${callback+x} ]; then
    echo "Error: no callback JSON path given. Exit."
    exit 1;
fi
if [ -z ${parameter+x} ]; then
    echo "Error: no parameter JSON path given. Exit."
    exit 1;
fi
if [ -z ${preprocessing+x} ]; then
    echo "Error: no preprocessing configuration given. Exit."
    exit 1;
fi
if [ -z ${globalTimeout+x} ]; then
    echo "Error: no global timeout value given. Exit."
    exit 1;
fi

# Run wrapping function
export RUN_NAME=$(date +%Y-%m-%d"_"%H-%M-%S)
run_wrap_all 2>&1 | tee "./$outputFolder/$RUN_NAME.log"
