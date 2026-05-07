output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "fhir_service_name" {
  value = aws_ecs_service.fhir.name
}

output "keycloak_service_name" {
  value = aws_ecs_service.keycloak.name
}
