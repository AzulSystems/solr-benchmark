#!/usr/bin/env bash

SETUP_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

source ${SETUP_SCRIPT_DIR}/init.sh

${JAVA_HOME}/bin/java -version 2>/dev/null || fail "'JAVA_HOME' env not set. Install jdk11 on the current host and set 'JAVA_HOME' env"

setupSolr() {

    log_wrap "Setting up all Solr Nodes"

    local COMMAND="sudo yum install -y wget vim lsof;\
    echo \"* hard nofile 65000\" | sudo tee -a /etc/security/limits.conf;\
    echo \"* soft nofile 65000\" | sudo tee -a /etc/security/limits.conf;\
    echo \"* hard nproc 65000\" | sudo tee -a /etc/security/limits.conf;\
    echo \"* soft nproc 65000\" | sudo tee -a /etc/security/limits.conf;\
    echo \"*          soft    nproc     65000\" | sudo tee -a /etc/security/limits.d/20-nproc.conf
    "
    runCommandOnSolrNodes "${COMMAND}"
    runCommandOnSolrNodes "${ZULU_INSTALL_COMMAND}"
    runCommandOnSolrNodes "${ZING_INSTALL_COMMAND}"
    runCommandOnSolrNodes "${SOLR_INSTALL_COMMAND}"
}

setupZookeeper() {
    for (( i=0; i<${#ZOOKEEPER_AWS_NODES[@]}; i++ ));do
        log_wrap "Setting up Zookeeper node : ${ZOOKEEPER_AWS_NODES[i]} (${ZOOKEEPER_AWS_NODE_NAMES[i]})"

        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${ZOOKEEPER_AWS_NODES[i]} \
            "sudo yum install -y wget vim lsof;\
            wget -q ${ZOOKEEPER_DIST_URL} -O ${ZOOKEEPER_DIST};\
            tar -xf ${ZOOKEEPER_DIST};\
            "

        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${ZOOKEEPER_AWS_NODES[i]} \
            "cp ${ZOOKEEPER}/conf/zoo_sample.cfg ${ZOOKEEPER}/conf/zoo.cfg;\
            echo autopurge.snapRetainCount=3 >> ${ZOOKEEPER}/conf/zoo.cfg;\
            echo autopurge.purgeInterval=1 >> ${ZOOKEEPER}/conf/zoo.cfg;\
            \
            mkdir -p /tmp/zookeeper/;\
            \
            for (( k=0; k<${#ZOOKEEPER_AWS_NODES[@]}; k++ ));do echo server.\$((k + 1))=zoo-node-\$((k + 1)):2888:3888 >> ${ZOOKEEPER}/conf/zoo.cfg;done;\
            \
            echo $((i + 1)) > /tmp/zookeeper/myid;\
            \
            echo \"ZOO_LOG_DIR=\${HOME}/${ZOOKEEPER}/logs/\" > \${HOME}/${ZOOKEEPER}/conf/zookeeper-env.sh;\
            echo \"ZOO_LOG4J_PROP=\\\"INFO,ROLLINGFILE\\\"\" >> \${HOME}/${ZOOKEEPER}/conf/zookeeper-env.sh;\
            echo \"SERVER_JVMFLAGS=\\\"-Xmx3g -Xloggc:gc.log \\\${OTHER_JVM_OPTS}\\\"\" >> \${HOME}/${ZOOKEEPER}/conf/zookeeper-env.sh
            "
	done

    runCommandOnZookeeperNodes "${ZULU_INSTALL_COMMAND}"
    runCommandOnZookeeperNodes "${ZING_INSTALL_COMMAND}"
}

updateEtcHosts() {
    resetEtcHosts

    log_wrap "Updating /etc/hosts file on all Nodes"

    tmpFileName="/tmp/tmpEtcHostInfo-$(date +%s)"
    touch ${tmpFileName}

    for (( i=0; i<${#ZOOKEEPER_AWS_NODES[@]}; i++ ));do
        echo `grep "zoo-node-$((i + 1))" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $2}'` zoo-node-$((i + 1)) >> ${tmpFileName}
    done

    for (( i=0; i<${#SOLR_AWS_SERVER[@]}; i++ ));do
         echo `grep "solr-node-$((i + 1))" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $2}'` solr-node-$((i + 1)) >> ${tmpFileName}
    done

    for (( i=0; i<${#SOLR_CLIENTS[@]}; i++ ));do
         echo `grep "solrj-client-$((i + 1))" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $2}'` solrj-client-$((i + 1)) >> ${tmpFileName}
    done

    local COMMAND="echo \"`cat ${tmpFileName}`\" | sudo tee -a /etc/hosts"
    runCommandOnAllNodes "${COMMAND}"

    rm ${tmpFileName}
}

resetEtcHosts() {
    local COMMAND="sudo sed -i '/zoo-node/d' /etc/hosts;\
         sudo sed -i '/solrj-client/d' /etc/hosts;\
         sudo sed -i '/solr-node/d' /etc/hosts"

    log_wrap "Resetting /etc/hosts file on all nodes"
    runCommandOnAllNodes "${COMMAND}"
}

deleteCollection() {
    log_wrap "Deleting collection ${COLLECTION_NAME}"
    log_wrap1 "-" "JAVA_HOME=\${HOME}/${ZING}/ /opt/solr/bin/solr delete -p 8983 -c ${COLLECTION_NAME}"
    ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${AWS_USER}@${SOLR_AWS_SERVER[0]} \
        "JAVA_HOME=\${HOME}/${ZING}/ \
        /opt/solr/bin/solr delete -p 8983 -c ${COLLECTION_NAME}"

    log_wrap "Removing wikiconfig (if it exists)"
    log "JAVA_HOME=\${HOME}/${ZING}/ /opt/solr/bin/solr zk rm -r /configs/wikiconfig -z zoo-node-1"
    ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${AWS_USER}@${SOLR_AWS_SERVER[0]} \
        "JAVA_HOME=\${HOME}/${ZING}/ \
        /opt/solr/bin/solr zk rm -r /configs/wikiconfig -z zoo-node-1"

    dropCacheOnSolrNodes
}

createCollection() {
    log_wrap "Uploading a new config: 'wikiconfig' to Zookeeper"
    log_wrap1 "-" "JAVA_HOME=\${HOME}/${ZING}/ /opt/solr/bin/solr zk upconfig -z zoo-node-1  -n wikiconfig -d /opt/solr/server/solr/configsets/_default/conf"
    ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${AWS_USER}@${SOLR_AWS_SERVER[0]} \
        "JAVA_HOME=\${HOME}/${ZING}/ \
        /opt/solr/bin/solr zk upconfig -z zoo-node-1 -n wikiconfig -d /opt/solr/server/solr/configsets/_default/conf/"

    log_wrap "Creating collection '${COLLECTION_NAME}' using the config 'wikiconfig' with ${NUMBER_OF_SHARDS} shards and replicationFactor ${REPLICATION_FACTOR}"
    log_wrap1 "-" "JAVA_HOME=\${HOME}/${ZING}/ /opt/solr/bin/solr create -p 8983 -c ${COLLECTION_NAME} -n wikiconfig -shards ${NUMBER_OF_SHARDS} -replicationFactor ${REPLICATION_FACTOR}"
    ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${AWS_USER}@${SOLR_AWS_SERVER[0]} \
        "JAVA_HOME=\${HOME}/${ZING}/ \
        /opt/solr/bin/solr create -p 8983 -c ${COLLECTION_NAME} -n wikiconfig -shards ${NUMBER_OF_SHARDS} -replicationFactor ${REPLICATION_FACTOR}"
}

setupSchema() {
    log_wrap "Creating Solr schema ..."
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"comment",      "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"format",       "type":"text_general",                        "indexed":"false", "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"model",        "type":"text_general", "multiValued":"false", "indexed":"false", "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"ns",           "type":"plongs",       "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"restrictions", "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"sha1",         "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"text",         "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"false"}}' http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"timestamp",    "type":"pdate",        "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"title",        "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
    curl -X POST -H 'Content-type:application/json' --data-binary '{"add-field": {"name":"username",     "type":"text_general", "multiValued":"false", "indexed":"true",  "stored":"true"}}'  http://${SOLR_AWS_SERVER[0]}:8983/solr/${COLLECTION_NAME}/schema
}

uploadWikiDump() {
    local COMMAND

    setupSchema

    log_wrap "Downloading wiki.json (This will take some time. Please wait)"
    COMMAND="[[ ! -f wiki.json ]] && (wget -q ${WIKI_DUMP_URL} -O wiki.json.gz;gzip -d wiki.json.gz) || echo \"wiki.json already exists\""
    runCommandOnClientNode "${COMMAND}"

    log_wrap "Indexing wiki.json into Solr"
    COMMAND="/home/${AWS_USER}/${ZING}/bin/java \
    -Xmx10g \
    -Dhp=zoo-node-1:2181,zoo-node-2:2181,zoo-node-3:2181 \
    -Dt=100 \
    -cp /home/${AWS_USER}/${SOLR_BENCHMARK_JAR} \
    Upload wiki.json"

    runCommandOnClientNode "${COMMAND}"

    dropCacheOnSolrNodes
}

clusterSetup() {
    setupZookeeper
    setupSolr
    updateEtcHosts
}

buildBenchmarkJar() {
    local COMMAND
    log_wrap "Building client/loadGenerator jar"

    wget -q ${MAVEN_DIST_URL} -O ${MAVEN_DIST}
    tar -xf ${MAVEN_DIST}

    COMMAND="${WORKING_DIR}/${MAVEN}/bin/mvn clean package"
    log "${COMMAND}"
    eval "${COMMAND}"

    mv ${SETUP_SCRIPT_DIR}/../target/solr-benchmark-jar-with-dependencies.jar ${WORKING_DIR}/${SOLR_BENCHMARK_JAR}
    [[ $? -ne 0 ]] && fail "Building ${WORKING_DIR}/${SOLR_BENCHMARK_JAR} failed"

    scp -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
        ${WORKING_DIR}/${SOLR_BENCHMARK_JAR} \
        ${MAIN_SCRIPT_DIR}/../bench-config.yaml \
        ${MAIN_SCRIPT_DIR}/../test-config.yaml \
        ${MAIN_SCRIPT_DIR}/../logging.properties \
        ${AWS_USER}@${SOLR_CLIENTS[0]}:/home/${AWS_USER}/

}

setupLoadGenerator() {
    log_wrap "Setting up client/loadGenerator node"

    local COMMAND="sudo yum install -y wget vim lsof;\
        wget ${QUERY_FILES_URL} -O queryFiles.tar;\
        tar -xf queryFiles.tar
        "
    runCommandOnClientNode "${COMMAND}"
    runCommandOnClientNode "${ZULU_INSTALL_COMMAND}"
    runCommandOnClientNode "${ZING_INSTALL_COMMAND}"
}

clientSetup() {

    setupLoadGenerator
    buildBenchmarkJar

    JAVA_HOME=/home/${AWS_USER}/${ZING}/ bash ${SETUP_SCRIPT_DIR}/main.sh startCluster
    deleteCollection
    createCollection
    uploadWikiDump
    JAVA_HOME=/home/${AWS_USER}/${ZING}/ bash ${SETUP_SCRIPT_DIR}/main.sh stopCluster
}

clean() {
    log_wrap "Cleaning up all nodes"
    runCommandOnAllNodes "rm -rf \${HOME}/*"
}

all() {
    clusterSetup
    clientSetup
}

${1}