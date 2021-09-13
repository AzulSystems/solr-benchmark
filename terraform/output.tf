output "zookeeper_node_details" {
  value = module.zookeeper-nodes.instance_name_publicIp_privateIp
}

output "solr_server_node_details" {
  value = module.solr-server-nodes.instance_name_publicIp_privateIp
}

output "solrj_client_node_details" {
  value = module.solrj-client-nodes.instance_name_publicIp_privateIp
}
