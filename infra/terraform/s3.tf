resource "aws_s3_bucket" "datalake" {
  bucket        = var.datalake_bucket_name
  force_destroy = true

  tags = {
    Environment = var.environment
    Project     = "UrbanMove"
    ManagedBy   = "Terraform"
  }
}

resource "aws_s3_bucket_versioning" "datalake_versioning" {
  bucket = aws_s3_bucket.datalake.id

  versioning_configuration {
    status = "Enabled"
  }
}
