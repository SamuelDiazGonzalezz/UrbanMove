terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }

  # Descomentar y configurar para migración a AWS real con estado remoto seguro:
  # backend "s3" {
  #   bucket         = "urbanmove-tfstate-<account-id>"
  #   key            = "environments/dev/terraform.tfstate"
  #   region         = "eu-west-1"
  #   dynamodb_table = "urbanmove-tflocks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region                      = var.aws_region
  access_key                  = var.is_localstack ? var.aws_access_key : null
  secret_key                  = var.is_localstack ? var.aws_secret_key : null
  skip_credentials_validation = var.is_localstack
  skip_metadata_api_check     = var.is_localstack
  skip_requesting_account_id  = var.is_localstack
  s3_use_path_style           = var.is_localstack

  # Si estamos en LocalStack, redirige los endpoints
  dynamic "endpoints" {
    for_each = var.is_localstack ? [1] : []
    content {
      s3         = var.aws_endpoint_url
      sqs        = var.aws_endpoint_url
      sns        = var.aws_endpoint_url
      lambda     = var.aws_endpoint_url
      iam        = var.aws_endpoint_url
      cloudwatch = var.aws_endpoint_url
      events     = var.aws_endpoint_url
      sts        = var.aws_endpoint_url
    }
  }

  default_tags {
    tags = {
      Environment = var.environment
      Project     = "UrbanMove"
      ManagedBy   = "Terraform"
    }
  }
}
