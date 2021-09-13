#!/usr/bin/env bash

INIT_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
source ${INIT_SCRIPT_DIR}/utils.sh

if [[ -z "${INIT_ALREADY_SOURCED}" ]];then # Don't source if already sourced
    declare -r WORKING_DIR=${INIT_SCRIPT_DIR}/../
    cd ${WORKING_DIR}

    declare -r AWS_PRIVATE_KEY=${WORKING_DIR}/my_temporary_key
    declare -r MY_PUBLIC_IP=$(curl ifconfig.me)
    declare -r AWS_USER=centos
    #declare -r AWS_PRIVATE_KEY=${AWS_PRIVATE_KEY}
    declare -r COLLECTION_NAME="test"
    declare -r NUMBER_OF_SHARDS=2
    declare -r REPLICATION_FACTOR=2

    declare -r SOLR_DIST_URL=https://archive.apache.org/dist/lucene/solr/7.7.3/solr-7.7.3.tgz
    declare -r ZULU_DIST_URL=https://cdn.azul.com/zulu/bin/zulu11.50.19-ca-jdk11.0.12-linux_x64.tar.gz
    declare -r ZING_DIST_URL=https://cdn.azul.com/zing/releases/tgz/zing21.07.0.0-3-ca-jdk11.0.12-linux_x64.tar.gz
    declare -r ZOOKEEPER_DIST_URL=https://archive.apache.org/dist/zookeeper/zookeeper-3.4.13/zookeeper-3.4.13.tar.gz
    declare -r MAVEN_DIST_URL=https://mirrors.estointernet.in/apache/maven/maven-3/3.8.1/binaries/apache-maven-3.8.1-bin.tar.gz

    declare -r SOLR_DIST=`echo ${SOLR_DIST_URL##*/}`
    declare -r ZULU_DIST=`echo ${ZULU_DIST_URL##*/}`
    declare -r ZING_DIST=`echo ${ZING_DIST_URL##*/}`
    declare -r ZOOKEEPER_DIST=`echo ${ZOOKEEPER_DIST_URL##*/}`
    declare -r MAVEN_DIST=`echo ${MAVEN_DIST_URL##*/}`

    declare -r SOLR=`echo ${SOLR_DIST/.tgz/}`
    declare -r ZULU=`echo ${ZULU_DIST/.tar.gz/}`
    declare -r ZING=`echo ${ZING_DIST/.tar.gz/}`
    declare -r ZOOKEEPER=`echo ${ZOOKEEPER_DIST/.tar.gz/}`
    declare -r MAVEN=`echo ${MAVEN_DIST/-bin.tar.gz/}`

    declare -r WIKI_DUMP_URL=https://cdn.azul.com/blogs/datasets/solr/wiki.json.gz
    declare -r QUERY_FILES_URL=https://cdn.azul.com/blogs/datasets/solr/queryFiles.tar

    declare -r ZOOKEEPER_AWS_NODES=(
    `grep "zoo-node-1" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    `grep "zoo-node-2" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    `grep "zoo-node-3" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    )

    declare -r SOLR_AWS_SERVER=(
    `grep "solr-node-1" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    `grep "solr-node-2" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    `grep "solr-node-3" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    `grep "solr-node-4" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    )

    declare -r SOLR_CLIENTS=(
    `grep "solrj-client-1" ${WORKING_DIR}/awsInstanceDetails.txt | awk '{print $NF}'`
    )

    declare -r ZOOKEEPER_AWS_NODE_NAMES=(
    zoo-node-1
    zoo-node-2
    zoo-node-3
    )

    declare -r SOLR_AWS_SERVER_NAMES=(
    solr-node-1
    solr-node-2
    solr-node-3
    solr-node-4
    )

    declare -r SOLR_AWS_CLIENT_NAMES=(
    solrj-client-1
    )

    declare -r SOLR_BENCHMARK_JAR=solr-benchmark.jar

    declare -r ZING_INSTALL_COMMAND=$(cat <<- END
/home/\${USER}/${ZING}/bin/java -version 2>/dev/null || (
    echo "'/home/\${USER}/${ZING}/bin/java' not found. Downloading and installing it ...";
    wget -q ${ZING_DIST_URL} -O ${ZING_DIST};tar -xf ${ZING_DIST}
)
END
)

    declare -r ZULU_INSTALL_COMMAND=$(cat <<- END
/home/\${USER}/${ZULU}/bin/java -version 2>/dev/null || (
    echo "'/home/\${USER}/${ZULU}/bin/java' not found. Downloading and installing it ...";
    wget -q ${ZULU_DIST_URL} -O ${ZULU_DIST};tar -xf ${ZULU_DIST}
)
END
)

    declare -r SOLR_INSTALL_COMMAND=$(cat <<- END
ls /opt/solr/bin/solr 1>/dev/null 2>&1 || (
    echo "Solr may not have been installed. Downloading and installing it ...";
    wget -q ${SOLR_DIST_URL} -O ${SOLR_DIST};
    tar -xf ${SOLR_DIST};
    sudo bash \${HOME}/${SOLR}/bin/install_solr_service.sh \${HOME}/${SOLR}.tgz -i /opt -d /var/solr -u \${USER} -s solr -p 8983 -n;
)
END
)
    declare -r INIT_ALREADY_SOURCED=true
fi
