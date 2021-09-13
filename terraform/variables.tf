variable "region" {
  default = "Which AWS region ?"
}

variable "ami" {
  description = "Instance AMI"
  # default = "ami-01e36b7901e884a10" # CentOS Linux 7 x86_64 on us-west-2 ohio
  # default = "ami-0686851c4e7b1a8e1" # CentOS Linux 7 x86_64 on us-west-2 oregon
}

variable "temp_public_key" {
  description = "Temporary public key used for accessing the AWS nodes"
}

variable "my_ip" {
  description = "Enter the public ip address of your host. On linux, run: 'curl ifconfig.co' or 'curl ifconfig.me'"
}

variable "solr_server_node-instance_type" {
  description = "Instance type to be used by Solr server nodes"
  default = "m5.8xlarge"
}

variable "zookeeper-node-instance_type" {
  description = "Instance type to be used by Zookeeper server nodes"
  default = "m5.large"
}

variable "solrj-client-node-instance_type" {
  description = "Instance type to be used by Sorlj client nodes"
  default = "m5.8xlarge"
}
