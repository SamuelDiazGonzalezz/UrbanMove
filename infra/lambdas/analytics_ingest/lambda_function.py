import json
import os
import boto3
from datetime import datetime

s3 = boto3.client('s3')
sqs = boto3.client('sqs')

BUCKET_NAME = os.environ.get('S3_DATALAKE_BUCKET', 'urbanmove-data-lake')

def handler(event, context):
    """
    Lambda Ingest: consume eventos de SQS y los escribe en S3 en formato Kappa/Medallion (raw/)
    """
    print(f"Recibidos {len(event.get('Records', []))} registros de SQS")
    
    for record in event.get('Records', []):
        body = record.get('body', '{}')
        try:
            data = json.loads(body)
            event_id = data.get('eventId', 'unknown')
            event_type = 'trips' if 'rideId' in data else 'reservations'
            
            # Particionado por fecha: raw/<event_type>/year=YYYY/month=MM/day=DD/<event_id>.json
            now = datetime.utcnow()
            s3_key = (
                f"raw/{event_type}/"
                f"year={now.year}/month={now.month:02d}/day={now.day:02d}/"
                f"{event_id}.json"
            )
            
            s3.put_object(
                Bucket=BUCKET_NAME,
                Key=s3_key,
                Body=json.dumps(data, indent=2),
                ContentType='application/json'
            )
            print(f"Evento guardado en S3 exitosamente: s3://{BUCKET_NAME}/{s3_key}")
            
        except Exception as e:
            print(f"Error procesando registro SQS: {str(e)}")
            raise e
            
    return {"statusCode": 200, "body": "OK"}
