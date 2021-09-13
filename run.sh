#!/usr/bin/env bash

RUN_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
source ${RUN_SCRIPT_DIR}/scripts/init.sh

for queryType in "field" "phrase" "proximity" "range" "fuzzy"
do
    for i in 1 2 3
    do
        HEADER=zing-${queryType}-run${i}
        COMMON_LOG_DIR=${HEADER} QUERY_TYPE=${queryType} JAVA_HOME=/home/${AWS_USER}/${ZING} bash scripts/main.sh startBenchmark

        HEADER=zulu-${queryType}-run${i}
        COMMON_LOG_DIR=${HEADER} QUERY_TYPE=${queryType} JAVA_HOME=/home/${AWS_USER}/${ZULU} bash scripts/main.sh startBenchmark
    done
done