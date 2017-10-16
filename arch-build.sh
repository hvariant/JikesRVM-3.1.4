#!/bin/bash

set -x

build_project=false
build_eclipse_project=false
build_harness=false

HOST=localhost
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/

while getopts peh opt; do
    case $opt in
        "p") build_project=true;;
        "e") build_eclipse_project=true;;
        "h") build_harness=true;;
    esac
done

# remove parsed options
shift $(( OPTIND - 1 ))

#config=${1:-FullAdaptiveRCImmixConcurrent}
config=${1:-production}

if [ "$build_project" = true ]; then
    ./bin/buildit -j $JAVA_HOME $HOST ${config}
fi

if [ "$build_eclipse_project" = true ]; then
    ./bin/buildit -j $JAVA_HOME --eclipse $HOST
fi

if [ "$build_harness" = true ]; then
    ant clean compile-mmtk mmtk-harness
fi
