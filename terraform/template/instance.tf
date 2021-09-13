resource "aws_instance" "solr_benchmarking" {
  ami           = var.ami
  instance_type = var.instance_type
  count         = var.instance_count

  root_block_device {
    volume_type = "gp2"
    volume_size = var.instance_volume_size
  }

  vpc_security_group_ids = [var.security_group.id]
  key_name = var.key_name

  tags = {
    Name         = "${var.instance_name}-${count.index + 1}"
  }
}
