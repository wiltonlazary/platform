variable "aws_region" {
  description = "The AWS region to create things in."
  default     = "eu-west-1"
}

variable "az_count" {
  description = "Number of AZs to cover in a given AWS region"
  default     = "2"
}

variable "key_name" {
  description = "Name of AWS key pair"
}

variable "admin_cidr_ingress" {
  description = "CIDR to allow tcp/22 ingress to EC2 instance"
}

variable "release_ids" {
  description = "Release tags for platform apps"
  type        = "map"
}

variable "infra_bucket" {
  description = "S3 bucket storing our configuration"
  default     = "platform-infra"
}

variable "api_host" {
  description = "hostname to use for api"
  default     = "api.wellcomecollection.org"
}

variable "es_config" {
  description = "Elasticcloud config"
  type        = "map"
  default     = {}
}

variable "build_env" {
  description = "Build environment (prod, dev, stage, ...)"
  default     = "prod"
}
