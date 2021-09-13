Contents
========

* [Overview](#overview)
* [Prerequisites](#prerequisites)
* [Steps to provision the AWS instance](#steps-to-provision-the-aws-instances)
* [What to do after benchmarking ?](#what-to-do-after-benchmarking-)
 
## Overview
This page walks you through the steps required to replicate the cluster used for benchmarking


## Prerequisites
 * Terraform must be installed (on the host on which the user intends to use this benchmark setup)
 * AWS CLI should be installed and configured to access the AWS account (on the host on which the user intends to use this benchmark setup)
 * User should have an AWS account and have the necessary permissions to create AWS instances (see [terraform doc](https://learn.hashicorp.com/tutorials/terraform/aws-build#prerequisites) for more details)
 * The AWS account should have a default VPC already configured

 
## Steps to provision the AWS instances
The script `setup.sh` under the `terraform` directory takes care of provisioning the necessary AWS instances
```bash
cd terraform
bash setup.sh
```

Internally, the script performs the following operations:
* Creates a temporary RSA key pair
* Terraform initialization steps (`terraform init`)
* Runs the `apply` command to instruct terraform to execute the actions described in the `.tf` files (`terraform apply ...`)
* Once the AWS instances are provisioned, [instances details](#instance-details) are collected
 
The `terraform/setup.sh` script has been written to be self-reliant<br/>
It will prompt the user only once
* Enter `yes` in the prompt to approve the plan and create instances 
    ```
    Do you want to perform these actions?
      Terraform will perform the actions described above.
      Only 'yes' will be accepted to approve.
    
      Enter a value:
    ```

### Instance details
The following details of the provisioned instances are collected by the `terraform/setup.sh` script using terraform:
* instance names (_solr-node-x_/_zoo-node-x_/_solrj-client-x_)
* instance private IP's
* instance public IP's

#### How are the private and public IP addresses allotted to the provisioned AWS instances ?  
The _private and public IP addresses_ are automatically allotted to each instance at the time of instance creation

#### Where are the public IP addresses of the instances used ?
They are used by the setup and benchmark run scripts to perform operations like `ssh` and `scp` etc.

#### Where are the private IP addresses of the instances used ?
`/etc/hosts` file on all the provisioned AWS instances are updated with the following info 
```
<solr-node-1_private_ip_address>     solr-node-1
<solr-node-2_private_ip_address>     solr-node-2
<solr-node-3_private_ip_address>     solr-node-3
<solr-node-4_private_ip_address>     solr-node-4
<zoo-node-1_private_ip_address>      zoo-node-1
<zoo-node-2_private_ip_address>      zoo-node-2
<zoo-node-3_private_ip_address>      zoo-node-3
<solrj-client-1_private_ip_address>  solrj-client-1
```
This allows the instances to talk/communicate with each other using the 
_instance names_ (_solr-node-x_/_zoo-node-x_/_solrj-client-x_) instead of the private IP addresses

#### Where are the instance details stored
The `terraform/setup.sh` collects and stores the instance details in a file named `awsInstanceDetails.txt` under the 
base directory (direcotry that contains the `terraform` folder) <br/>
The details are stored in the following format:
```
solr-node-1     <solr-node-1_private_ip_address>     <solr-node-1_public_ip_address>
solr-node-2     <solr-node-2_private_ip_address>     <solr-node-2_public_ip_address>
solr-node-3     <solr-node-3_private_ip_address>     <solr-node-3_public_ip_address>
solr-node-4     <solr-node-4_private_ip_address>     <solr-node-4_public_ip_address>
zoo-node-1      <zoo-node-1_private_ip_address>      <zoo-node-1_public_ip_address>
zoo-node-2      <zoo-node-2_private_ip_address>      <zoo-node-2_public_ip_address>
zoo-node-3      <zoo-node-3_private_ip_address>      <zoo-node-3_public_ip_address>
solrj-client-1  <solrj-client-1_private_ip_address>  <solrj-client-1_public_ip_address>
```
The setup and benchmark run scripts, reads the `awsInstanceDetails.txt` to perform operations like `ssh` and `scp` etc.

## What to do after benchmarking ? 
**DO NOT FORGET TO DESTROY ALL THE INSTANCES**

Run the following command:
```bash
terraform destroy -var-file=benchmark.tfvars
```
NOTE: `benchmark.tfvars` file will be created under the `terraform` directory 
when `terraform/setup.sh` script is run <br/>

Terraform will prompt the user to approve cleaning up the instances<br/>
Enter `yes` 
```
Do you really want to destroy all resources?
  Terraform will destroy all your managed infrastructure, as shown above.
  There is no undo. Only 'yes' will be accepted to confirm.

  Enter a value: 
```
