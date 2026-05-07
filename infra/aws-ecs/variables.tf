variable "project" {
  type    = string
  default = "clinivault"
}

variable "environment" {
  type    = string
  default = "prod"
}

variable "aws_region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "task_subnet_ids" {
  type = list(string)
}

variable "acm_certificate_arn" {
  type    = string
  default = ""
}

variable "task_assign_public_ip" {
  type    = bool
  default = true
}

variable "fhir_domain" {
  type    = string
  default = ""
}

variable "keycloak_domain" {
  type    = string
  default = ""
}

variable "keycloak_hostname" {
  description = "KC_HOSTNAME — set to ALB DNS or custom domain. Used so Keycloak knows its public address."
  type        = string
  default     = ""
}

variable "enable_host_routing" {
  type    = bool
  default = false
}

variable "fhir_image" {
  type = string
}

variable "keycloak_image" {
  type = string
}

variable "smart_client_id" {
  type = string
}

variable "smart_realm" {
  type = string
}

variable "fhir_base_url" {
  type = string
}

variable "internal_fhir_url" {
  type = string
}

variable "smart_client_redirect_uri" {
  type = string
}

variable "fhir_db_host" {
  type = string
}

variable "fhir_db_port" {
  type    = number
  default = 5432
}

variable "fhir_db_name" {
  type = string
}

variable "fhir_db_schema" {
  type = string
}

variable "fhir_db_user" {
  type = string
}

variable "fhir_db_ssl_mode" {
  type    = string
  default = "require"
}

variable "kc_db_host" {
  type = string
}

variable "kc_db_port" {
  type    = number
  default = 5432
}

variable "kc_db_database" {
  type = string
}

variable "kc_db_schema" {
  type = string
}

variable "kc_db_username" {
  type = string
}

variable "kc_db_ssl_mode" {
  type    = string
  default = "require"
}

variable "kc_bootstrap_admin_username" {
  type = string
}

variable "fhir_db_pass_param_arn" {
  type = string
}

variable "kc_db_password_param_arn" {
  type = string
}

variable "kc_bootstrap_admin_password_param_arn" {
  type = string
}

variable "abac_enabled" {
  type    = string
  default = "false"
}

variable "abac_require_tenant" {
  type    = string
  default = "false"
}

variable "abac_require_org" {
  type    = string
  default = "false"
}

variable "abac_allowed_purposes" {
  type    = string
  default = "TREAT,HPAYMT,HOPERAT"
}

variable "abac_resource_tenant_system" {
  type    = string
  default = "https://linuxforhealth.org/fhir/abac/tenant"
}

variable "abac_resource_org_system" {
  type    = string
  default = "https://linuxforhealth.org/fhir/abac/org"
}

variable "fhir_cpu" {
  type    = number
  default = 1024
}

variable "fhir_memory" {
  type    = number
  default = 2048
}

variable "keycloak_cpu" {
  type    = number
  default = 1024
}

variable "keycloak_memory" {
  type    = number
  default = 2048
}

variable "fhir_min_capacity" {
  type    = number
  default = 0
}

variable "fhir_max_capacity" {
  type    = number
  default = 20
}

variable "keycloak_min_capacity" {
  type    = number
  default = 1
}

variable "keycloak_max_capacity" {
  type    = number
  default = 10
}

variable "fhir_target_cpu" {
  type    = number
  default = 55
}

variable "keycloak_target_cpu" {
  type    = number
  default = 50
}

variable "fargate_weight" {
  type    = number
  default = 1
}

variable "fargate_spot_weight_fhir" {
  type    = number
  default = 4
}

variable "fargate_spot_weight_keycloak" {
  type    = number
  default = 0
}
