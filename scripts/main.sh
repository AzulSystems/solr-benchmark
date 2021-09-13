#!/usr/bin/env bash

MAIN_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
source ${MAIN_SCRIPT_DIR}/init.sh

if [[ -z ${COMMON_LOG_DIR} ]];then
    COMMON_LOG_DIR=${WORKING_DIR}/$(date +"%d-%m-%Y_%T.%N")
    declare -r COMMON_LOG_DIR=${COMMON_LOG_DIR}
fi
mkdir -p ${COMMON_LOG_DIR}

if [[ ! -f ${AWS_PRIVATE_KEY} ]];then
    fail "Private key: '${AWS_PRIVATE_KEY}' not found.
    Expected '${AWS_PRIVATE_KEY}' to have been created as part of terraform provision step.
    See '${WORKING_DIR}/terraform/setup.sh' file"
fi

trap trapHandler INT QUIT TERM
trapHandler() {
    log "In trap..."
    stopClient
    stopCluster
    postFinishActions
    exit 1
}

# Check Zookeeper node status
zookeeperStatus() {
    local COMMAND
    COMMAND="JAVA_HOME=${JAVA_HOME} bash \${HOME}/${ZOOKEEPER}/bin/zkServer.sh status"

    log_wrap "Checking status on all Zookeeper Nodes"
    runCommandOnZookeeperNodes "${COMMAND}"
}

# Start Zookeeper nodes
startZookeeper() {
    local COMMAND
    COMMAND="JAVA_HOME=${JAVA_HOME} bash ${ZOOKEEPER}/bin/zkServer.sh start"

    log_wrap "Starting all Zookeeper Nodes"
    runCommandOnZookeeperNodes "${COMMAND}"
    zookeeperStatus
}

# Stop Zookeeper nodes
stopZookeeper() {
    local COMMAND
    COMMAND="JAVA_HOME=${JAVA_HOME} bash ${ZOOKEEPER}/bin/zkServer.sh stop"

    log_wrap "Stopping all Zookeeper Nodes"
    runCommandOnZookeeperNodes "${COMMAND}"
}

# Start solr servers
startServers() {

    dropCacheOnSolrNodes

    local ZOOKEEPER_ENSEMBLE_NODES_PORTS=""
    local SEP=""
    for (( i=0; i<${#ZOOKEEPER_AWS_NODES[@]}; i++ ));do
        [[ $i -eq 0 ]] || SEP=","
        ZOOKEEPER_ENSEMBLE_NODES_PORTS="${ZOOKEEPER_ENSEMBLE_NODES_PORTS}${SEP}${ZOOKEEPER_AWS_NODE_NAMES[i]}:2181"
    done

    for (( i=0; i<${#SOLR_AWS_SERVER[@]}; i++ ));do
        log_wrap "Starting Solr node : ${SOLR_AWS_SERVER[i]} (${SOLR_AWS_SERVER_NAMES[i]})"

         COMMAND="JAVA_HOME=${JAVA_HOME} \
            SOLR_JAVA_MEM='${SOLR_JAVA_MEM:-"-Xms60g -Xmx60g"}' \
            GC_TUNE='${GC_TUNE}' \
            /opt/solr/bin/solr restart -h ${SOLR_AWS_SERVER_NAMES[i]} -p 8983 -z ${ZOOKEEPER_ENSEMBLE_NODES_PORTS}"

        log_wrap1 "-" "${COMMAND}"
        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${SOLR_AWS_SERVER[i]} \
            "${COMMAND}"
        [[ $? -ne 0 ]] && fail "Solr node start failed"
    done
}

stopServers() {
    local COMMAND
    COMMAND="JAVA_HOME=${JAVA_HOME} /opt/solr/bin/solr stop -p 8983"

    log_wrap "Stopping all Solr nodes"
    runCommandOnSolrNodes "${COMMAND}"
}

updateCacheSettings() {
    log_wrap "Updating the Cache configuration on Solr"
    if [[ -z ${1} ]];then
        warn "'updateCacheSettings' expects the first argument to be the cache settings (comma separated if multiple settings are expected)"
#        echo "Eg: If u want to set query.queryResultCache.size to 1024, then start the script something likes this:"
        log "bash cacheSetting.sh updateCacheSettings query.queryResultCache.size=1024"
        log "OR"
        echo "bash cacheSetting.sh updateCacheSettings query.queryResultCache.size=1024,query.filterCache.maxRamMB=1024"
        echo "OR"
        echo "bash cacheSetting.sh updateCacheSettings query.documentCache.size=5120"
        exit 3
    fi

    local property
    local value

    cacheSettings=(${1//,/ })
    for cacheSetting in "${cacheSettings[@]}"; do
        log "Applying setting : ${cacheSetting}"
        property=$(echo ${cacheSetting} | awk -F= '{print $1}')
        value=$(echo ${cacheSetting} | awk -F= '{print $2}')

        curl -X POST -H 'Content-type: application/json' -d '{"set-property":{"'${property}'":"'${value}'"}}' http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/config
    done
}

startClient() {
    local COMMAND

    local CACHE_SETTINGS="query.queryResultCache.size=10000,query.queryResultCache.autowarmCount=10000,updateHandler.autoCommit.maxTime=60000"
    [[ -z ${CACHE_SETTINGS} ]] || (
        updateCacheSettings "${CACHE_SETTINGS}"
    )

    log_wrap "Starting client/loadgenerator on node : ${SOLR_AWS_CLIENT_NAMES[0]} (${SOLR_CLIENTS[0]})"

    scp -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${WORKING_DIR}/${SOLR_BENCHMARK_JAR} \
        ${MAIN_SCRIPT_DIR}/../bench-config.yaml \
        ${MAIN_SCRIPT_DIR}/../test-config.yaml \
        ${MAIN_SCRIPT_DIR}/../logging.properties \
        ${AWS_USER}@${SOLR_CLIENTS[0]}:/home/${AWS_USER}/

    COMMAND="${JAVA_HOME}/bin/java \
        -Xmx10g \
        -DqueryType=${QUERY_TYPE} \
        -Xloggc:/home/${AWS_USER}/gc_client.log \
        -Djava.util.logging.config.file=/home/${AWS_USER}/logging.properties \
        -cp /home/${AWS_USER}/${SOLR_BENCHMARK_JAR} \
        org.bench.solr.SolrBenchmark \
        /home/${AWS_USER}/bench-config.yaml"

    runCommandOnClientNode "${COMMAND}"
    stopClient
}

stopClient() {
    log_wrap "Stopping client on: ${SOLR_AWS_CLIENT_NAMES[0]} (${SOLR_CLIENTS[0]})"
    runCommandOnClientNode "kill -9 \`ps -ef | grep java | grep -v grep | awk '{print \$2}'\`"
}

copyLogsFromAWS() {
    log_wrap "Copying logs from server(s)"
    for (( i=0; i<${#SOLR_AWS_SERVER[@]}; i++ ));do
        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${SOLR_AWS_SERVER[i]} \
            "rm /var/solr/logs/solr.log*"

        scp -i ${AWS_PRIVATE_KEY} -r -o StrictHostKeyChecking=no \
        	${AWS_USER}@${SOLR_AWS_SERVER[i]}:/var/solr/logs ${COMMON_LOG_DIR}/${SOLR_AWS_SERVER_NAMES[i]}
    done

    log_wrap "Copying logs from client"
    for (( i=0; i<${#SOLR_CLIENTS[@]}; i++ ));do
        scp -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${SOLR_CLIENTS[i]}:/home/${AWS_USER}/*.log ${COMMON_LOG_DIR}/
    done
}

cleanLogsOnAWS() {
    log_wrap "Cleaning logs on server(s)"
    runCommandOnSolrNodes "rm -r /var/solr/logs/*"

    log_wrap "Cleaning logs on client"
    runCommandOnClientNode "rm -r *.log;"
}

killJavaProcs() {
    log_wrap "Killing JAVA processes on all nodes"
    local COMMAND="kill -9 \$(ps -ef | pgrep -f \"java\")"
    runCommandOnAllNodes "${COMMAND}"
}

startCluster() {
    startZookeeper
    startServers
}

stopCluster() {
    stopServers
    stopZookeeper
}

preStartActions() {
    checkAndInstallJDKBundles
    killJavaProcs
    cleanLogsOnAWS
}

postFinishActions() {
    copyLogsFromAWS
    cleanLogsOnAWS
    killJavaProcs
}

startBenchmark() {
    preStartActions

    startCluster

    startClient

    stopCluster

    postFinishActions
}

${1}