#!/usr/bin/env bash

SETUP_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
source ${SETUP_SCRIPT_DIR}/../scripts/utils.sh

declare -r AWS_PRIVATE_KEY=${SETUP_SCRIPT_DIR}/../my_temporary_key
declare -r MY_PUBLIC_IP=$(curl ifconfig.me)
declare -r TERRAFORM_VAR_FILE=benchmark.tfvars

if [[ -z `which terraform` ]];then
    fail "terraform is not installed"
fi

if [[ -z `which aws` ]];then
    fail "AWS CLI is not installed"
fi

# Generate a new key pair to allow us to access the AWS instances
if [[ ! -f ${AWS_PRIVATE_KEY} ]];then
    log_wrap "Generating a temporary RSA key to allow accessing the AWS instances once provisioned. Don't delete it till the entire benchmark run is completed"
    ssh-keygen -f ${AWS_PRIVATE_KEY} -t rsa -b 2048 -m PEM -N ""
    [[ $? -ne 0 ]] && fail "Failed to create RSA key pair"
fi

cd ${SETUP_SCRIPT_DIR}

log_wrap "Creating terraform var file: '${TERRAFORM_VAR_FILE}'"
{
cat ${SETUP_SCRIPT_DIR}/template_tfvars
[[ ! -z "${MY_PUBLIC_IP}" ]] && echo "my_ip = \"${MY_PUBLIC_IP}\""
echo "temp_public_key = \"`cat ${AWS_PRIVATE_KEY}.pub`\""
} > ${TERRAFORM_VAR_FILE}

log_wrap "Running: terraform init"
terraform init

log_wrap "Running 'terraform apply -var-file=${TERRAFORM_VAR_FILE}'"
terraform apply -var-file="${TERRAFORM_VAR_FILE}"

log_wrap "Capturing provisioned nodes details into ${SETUP_SCRIPT_DIR}/../awsInstanceDetails.txt"
{
terraform output -raw zookeeper_node_details
echo ""
terraform output -raw solr_server_node_details
echo ""
terraform output -raw solrj_client_node_details
echo ""
} > ${SETUP_SCRIPT_DIR}/../awsInstanceDetails.txt
