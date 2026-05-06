#!/bin/bash

#
# This script can be used to execute our GIPS-based solution for
# the IHTC 2024 with only one argument, i.e., the input model to
# load.
#
# Example:
# `./start-args-gips.sh ./i01.json ./i01_solution.json 0 /tmp/callback.json /tmp/parameter.json`
#
# If you have any questions, feel free to write us an email.
#
# @author Maximilian Kratz (maximilian.kratz@es.tu-darmstadt.de)
#

set -e

function setup {
    # Make sure that log folders exists
    mkdir -p logs
}

function run {
    # Execute the program itself and save its output to logfile
    java -Xmx250g -jar $JAR $ARGS 2>&1 | tee "./logs/$RUN_NAME.log"
}

# Set env vars
source env.sh

# Config
export JAR="gips-ihtc.jar"

setup

# Example arguments:
# ./i01.json ./i01_solution.json 0 ./callback.json ./parameter.json gt
# $1         #$2                 $3 $4             $5               $6
#
# or
#
# ./i01.json ./i01_solution.json 0 ./callback.json ./parameter.json gt u
# $1         #$2                 $3 $4             $5               $6 $7

export inputJson=$1
export outputJson=$2
export randomSeed=$3
export callback=$4
export parameter=$5
export preprocessing=$6
export constraintCleanUp=$7

# Extract needed XMI files
echo "# Script info: Applying GIPS XMI workarounds."

# Extract XMI files
unzip -qq -o $JAR "ihtcvirtualgipssolution/hipe/*/hipe-network.xmi"
unzip -qq -o $JAR "ihtcvirtualgipssolution/api/*/gips-model.xmi"
unzip -qq -o $JAR "ihtcvirtualgipssolution/api/ibex-patterns.xmi"
unzip -qq -o $JAR "ihtcvirtualpreprocessing/hipe/*/hipe-network.xmi"
unzip -qq -o $JAR "ihtcvirtualpreprocessing/api/ibex-patterns.xmi"

# Actual run
export RUN_NAME=$(date +%Y-%m-%d"_"%H-%M-%S)
if [ ! -z "$parameter" ] && [ ! -z "$callback" ] && [ ! -z "$preprocessing" ] && [ ! -z "$constraintCleanUp" ]; then
    export ARGS="-i $inputJson -o $outputJson --verbose --randomseed $randomSeed --callback $callback --parameter $parameter --preprocessing $preprocessing u"
else
    if [ ! -z "$parameter" ] && [ ! -z "$callback" ] && [ ! -z "$preprocessing" ]; then
        export ARGS="-i $inputJson -o $outputJson --verbose --randomseed $randomSeed --callback $callback --parameter $parameter --preprocessing $preprocessing"
    else
        echo "# Script error: Invalid combination of parameters."
        exit 1
    fi
fi

echo "#"
echo "# Script info: Using ARGS: $ARGS"
echo "#"
run
# Finished actual run

# Clean up extracted files that are not relevant for the
# produced JSON solution file.
rm -r ./ihtcvirtualgipssolution
rm -r ./ihtcvirtualpreprocessing

echo "#"
echo "# => Arg script done."
echo "#"
