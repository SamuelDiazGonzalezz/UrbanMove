#!/bin/bash
echo "=== Inicializando recursos de LocalStack para UrbanMove ==="

# Crear bucket para Data Lake
awslocal s3 mb s3://urbanmove-data-lake
echo "Bucket s3://urbanmove-data-lake creado."

# Crear colas SQS para eventos
awslocal sqs create-queue --queue-name trip-recorded-queue
awslocal sqs create-queue --queue-name vehicle-telemetry-queue
awslocal sqs create-queue --queue-name urbanmove-dlq
echo "Colas SQS creadas."

echo "=== LocalStack listo ==="
