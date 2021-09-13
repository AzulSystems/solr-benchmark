module "solr-server-nodes" {
  source               = "./template"
  instance_name        = "solr-node"
  ami                  = var.ami
  instance_type        = var.solr_server_node-instance_type
  instance_count       = 4
  instance_volume_size = 50
  security_group       = aws_security_group.solr_security_group
  key_name             = aws_key_pair.new_key_pair.key_name
}

module "zookeeper-nodes" {
  source               = "./template"
  instance_name        = "zoo-node"
  ami                  = var.ami
  instance_type        = var.zookeeper-node-instance_type
  instance_count       = 3
  instance_volume_size = 50
  security_group       = aws_security_group.solr_security_group
  key_name             = aws_key_pair.new_key_pair.key_name
}

module "solrj-client-nodes" {
  source               = "./template"
  instance_name        = "solrj-client"
  ami                  = var.ami
  instance_type        = var.solrj-client-node-instance_type
  instance_count       = 1
  instance_volume_size = 100
  security_group       = aws_security_group.solr_security_group
  key_name             = aws_key_pair.new_key_pair.key_name
}

resource "aws_security_group" "solr_security_group" {
  name        = "solr_security_group" # the name is coincidently is same as resource name. It need not be same
  description = "Security group for Solr"

  # vpc_id = "<VPC Id>" Assume that a default VPC already exists

  ingress {
    description      = "Solr default port"
    from_port        = 8983
    to_port          = 8983
    protocol         = "tcp"
    cidr_blocks      = ["${var.my_ip}/32"]
  }

  ingress {
    description      = "Solr embedded Zookeeper default port"
    from_port        = 9983
    to_port          = 9983
    protocol         = "tcp"
    cidr_blocks      = ["${var.my_ip}/32"]
  }

  ingress {
    description      = "External Zookeeper default port"
    from_port        = 2181
    to_port          = 2181
    protocol         = "tcp"
    cidr_blocks      = ["${var.my_ip}/32"]
  }

  ingress {
    description      = "SSH from localhost"
    from_port        = 22
    to_port          = 22
    protocol         = "tcp"
    cidr_blocks      = ["${var.my_ip}/32"]
  }

  ingress {
    description      = "Add this security group to the ingress rule"
    from_port        = 0
    to_port          = 65535
    protocol         = "tcp"
    self             = true
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name = "solr_security_group"
  }
}

resource "aws_key_pair" "new_key_pair" {
  key_name   = "my_new_temp_unique_key"
  public_key = var.temp_public_key
}
