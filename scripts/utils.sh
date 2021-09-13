#!/usr/bin/env bash

sys_time() {
    date +%s
}

get_stamp() {
    local p=$(date -u "+%Y-%m-%d %H:%M:%S,%N")
    echo ${p::23}
}

log_gap_sep() {
    printf '\n\n%*s\n' "${COLUMNS:-$(tput cols)}" '' | tr ' ' =
}

log_sep_gap() {
    printf '%*s\n\n\n' "${COLUMNS:-$(tput cols)}" '' | tr ' ' =
}

log() {
    echo "$(get_stamp) [${HOSTNAME%%.*}] [$$] ${@}"
}

log_wrap1() {
    local symbol=${1}
    local out_line=$(log "${@:2}")
    local len_of_out_line=${#out_line}
    [[ ${len_of_out_line} -gt "${COLUMNS:-$(tput cols)}" ]] && len_of_out_line="${COLUMNS:-$(tput cols)}"

    printf '%*s\n' "${len_of_out_line}" '' | tr ' ' ${symbol}
    echo "${out_line}"
    printf '%*s\n' "${len_of_out_line}" '' | tr ' ' ${symbol}
}

log_wrap() {
    local out_line=$(log "${@}")
    local len_of_out_line=${#out_line}
    [[ ${len_of_out_line} -gt "${COLUMNS:-$(tput cols)}" ]] && len_of_out_line="${COLUMNS:-$(tput cols)}"

    printf '\n\n%*s\n' "${len_of_out_line}" '' | tr ' ' =
    echo "${out_line}"
    printf '%*s\n' "${len_of_out_line}" '' | tr ' ' =
}

log_with_upper_margin() {
    local out_line=$(log "${@}")
    local len_of_out_line=${#out_line}
    [[ ${len_of_out_line} -gt "${COLUMNS:-$(tput cols)}" ]] && len_of_out_line="${COLUMNS:-$(tput cols)}"

    printf '\n\n%*s\n' "${len_of_out_line}" '' | tr ' ' =
    echo "${out_line}"
}

fail() {
    local out_line=$(log "FAILURE: ${@}")
    local len_of_out_line=${#out_line}
    [[ ${len_of_out_line} -gt "${COLUMNS:-$(tput cols)}" ]] && len_of_out_line="${COLUMNS:-$(tput cols)}"

    printf '\n%*s\n' "${len_of_out_line}" '' | tr ' ' x
    echo "${out_line}"
    printf '%*s\n\n' "${len_of_out_line}" '' | tr ' ' x
    exit 1
}

warn() {
    local out_line=$(log "WARNING: ${@}")
    local len_of_out_line=${#out_line}
    [[ ${len_of_out_line} -gt "${COLUMNS:-$(tput cols)}" ]] && len_of_out_line="${COLUMNS:-$(tput cols)}"

    printf '\n%*s\n' "${len_of_out_line}" '' | tr ' ' !
    echo "${out_line}"
    printf '%*s\n\n' "${len_of_out_line}" '' | tr ' ' !
}

dropCacheOnSolrNodes() {
    log_wrap "Dropping caches on all Solr Nodes"
    runCommandOnSolrNodes "echo 3 | sudo tee /proc/sys/vm/drop_caches"
}

checkAndInstallJDKBundles() {
    log "Installing Zing JDK build on all Nodes"
    runCommandOnAllNodes "${ZING_INSTALL_COMMAND}"

    log "Installing Zulu JDK build on all Nodes"
    runCommandOnAllNodes "${ZULU_INSTALL_COMMAND}"
}

runCommandOnClientNode() {
    local COMMAND="${1}"
    for ((i = 0; i < ${#SOLR_CLIENTS[@]}; i++)); do
        log_with_upper_margin "Running the below command on ${SOLR_CLIENTS[i]} (${SOLR_AWS_CLIENT_NAMES[i]})"
        log_wrap1 "-" "Command : ${COMMAND}"

        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${SOLR_CLIENTS[i]} \
            "${COMMAND}"
    done
}

runCommandOnSolrNodes() {
    local COMMAND="${1}"
    for ((i = 0; i < ${#SOLR_AWS_SERVER[@]}; i++)); do
        log_with_upper_margin "Running the below command on ${SOLR_AWS_SERVER[i]} (${SOLR_AWS_SERVER_NAMES[i]})"
        log_wrap1 "-" "Command : ${COMMAND}"
        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${SOLR_AWS_SERVER[i]} \
            "${COMMAND}"
    done
}

runCommandOnZookeeperNodes() {
    local COMMAND="${1}"
    for ((i = 0; i < ${#ZOOKEEPER_AWS_NODES[@]}; i++)); do
        log_with_upper_margin "Running the below command on ${ZOOKEEPER_AWS_NODES[i]} (${ZOOKEEPER_AWS_NODE_NAMES[i]})"
        log_wrap1 "-" "Command : ${COMMAND}"
        ssh -i ${AWS_PRIVATE_KEY} -o StrictHostKeyChecking=no \
            ${AWS_USER}@${ZOOKEEPER_AWS_NODES[i]} \
            "${COMMAND}"
    done
}

runCommandOnAllNodes() {
    local COMMAND="${1}"
    runCommandOnClientNode "${COMMAND}"
    runCommandOnSolrNodes "${COMMAND}"
    runCommandOnZookeeperNodes "${COMMAND}"
}
