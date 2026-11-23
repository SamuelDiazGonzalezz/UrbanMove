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

variable "environment" {
  type        = string
  description = "Entorno (dev, staging, prod)"
  default     = "dev"
}

variable "event_bus_name" {
  type        = string
  description = "Bus de eventos de dominio"
  default     = "urbanmove-bus"
}

variable "reservation_ttl_seconds" {
  type        = number
  description = "Tiempo de vida de una reserva; es el retraso de la cola de expiración (máx. 900)"
  default     = 300
}

variable "db_user" {
  type        = string
  description = "Usuario de PostgreSQL para las Lambdas de analítica"
  default     = "urbanmove"
}

variable "db_password" {
  type        = string
  description = "Contraseña de PostgreSQL (TF_VAR_db_password, se toma de POSTGRES_PASSWORD en .env)"
  sensitive   = true
}

variable "demand_min_available" {
  type        = number
  description = "Regla de recolocación: una zona con menos vehículos disponibles que esto está en déficit"
  default     = 3
}

variable "demand_min_events" {
  type        = number
  description = "Regla de recolocación: hay déficit si la demanda de la última hora (reservas, intentos rechazados y viajes iniciados) es MAYOR que esto. El enunciado propone 10; se baja para la demo"
  default     = 4
}
