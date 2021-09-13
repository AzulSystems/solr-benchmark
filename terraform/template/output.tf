resource "null_resource" "instance_name_publicIp_privateIp" {
  count = length(aws_instance.solr_benchmarking)
  triggers = {
    name = "${aws_instance.solr_benchmarking[count.index].tags.Name} ${aws_instance.solr_benchmarking[count.index].private_ip} ${aws_instance.solr_benchmarking[count.index].public_ip}"
  }
}

output "instance_name_publicIp_privateIp" {
  value = join("\n", null_resource.instance_name_publicIp_privateIp.*.triggers.name)
}
