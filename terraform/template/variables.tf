variable "instance_count" {
  description = "Number of instances"
  default     = 1
}

variable "instance_type" {
  description = "Instance type"
  default     = "t2.micro"
}

variable "ami" {
  description = "Instance AMI"
  # default = "ami-01e36b7901e884a10" # CentOS Linux 7 on us-east-2 ohio
  # default = "ami-0686851c4e7b1a8e1" # CentOS Linux 7 on us-west-2 oregon
}

variable "instance_name" {
  description = "Instance name"
}

variable "instance_volume_size" {
  description = "Size of each instance in GB"
  default     = 8
}

variable "key_name" {
  description = "Key pair name"
}

variable "security_group" {
  default = "Solr security group. All instances will fall under this group"
}
