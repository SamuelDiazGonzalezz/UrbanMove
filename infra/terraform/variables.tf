variable "aws_region" {
  type        = string
  description = "Región de AWS"
  default     = "eu-west-1"
}

variable "aws_endpoint_url" {
  type        = string
  description = "Endpoint para LocalStack"
  default     = "http://localhost:4566"
}

variable "is_localstack" {
  type        = bool
  description = "Indica si el despliegue es en LocalStack o AWS real"
  default     = true
}

variable "aws_access_key" {
  type        = string
  description = "AWS Access Key"
  default     = "test"
  sensitive   = true
}

variable "aws_secret_key" {
  type        = string
  description = "AWS Secret Key"
  default     = "test"
  sensitive   = true
}

variable "datalake_bucket_name" {
  type        = string
  description = "Nombre del bucket para el Data Lake"
  default     = "urbanmove-data-lake"
}

variable "trip_recorded_queue_name" {
  type        = string
  description = "Nombre de la cola SQS de viajes"
  default     = "trip-recorded-queue"
}

variable "environment" {
  type        = string
  description = "Entorno (dev, staging, prod)"
  default     = "dev"
}
