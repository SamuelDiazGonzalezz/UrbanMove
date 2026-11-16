#!/bin/bash
echo "=== Inicializando recursos de LocalStack para UrbanMove ==="

# 1. Crear bucket para Data Lake
awslocal s3 mb s3://urbanmove-data-lake
echo "Bucket s3://urbanmove-data-lake creado."

# 2. Crear colas SQS
awslocal sqs create-queue --queue-name trip-recorded-queue
awslocal sqs create-queue --queue-name vehicle-telemetry-queue
awslocal sqs create-queue --queue-name urbanmove-dlq
echo "Colas SQS creadas."

# 3. Empaquetar y desplegar Lambda de Ingesta a S3
cd /opt/lambdas/analytics_ingest || cd /infra/lambdas/analytics_ingest || true
if [ -f "/infra/lambdas/analytics_ingest/lambda_function.py" ]; then
    echo "Empaquetando Lambda analytics-ingest..."
    zip -j /tmp/analytics_ingest.zip /infra/lambdas/analytics_ingest/lambda_function.py
    
    awslocal lambda create-function \
        --function-name analytics-ingest \
        --runtime python3.11 \
        --role arn:aws:iam::000000000000:role/lambda-role \
        --handler lambda_function.handler \
        --zip-file fileb:///tmp/analytics_ingest.zip \
        --environment "Variables={S3_DATALAKE_BUCKET=urbanmove-data-lake}"
    
    echo "Lambda analytics-ingest creada."
    
    # 4. Configurar Event Source Mapping (SQS -> Lambda automática)
    QUEUE_ARN="arn:aws:sqs:eu-west-1:000000000000:trip-recorded-queue"
    awslocal lambda create-event-source-mapping \
        --function-name analytics-ingest \
        --batch-size 10 \
        --event-source-arn "$QUEUE_ARN"
        
    echo "Trigger SQS -> Lambda configurado exitosamente."
fi

echo "=== LocalStack listo ==="
