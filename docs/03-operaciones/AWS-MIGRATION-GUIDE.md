# Guía de Migración Cloud: LocalStack a AWS Real

> **Documento:** `docs/03-operaciones/AWS-MIGRATION-GUIDE.md`  
> **Área:** Infraestructura como Código (IaC), DevOps y Despliegue Cloud  
> **Estado:** Preparado para transición sin fricción desde el entorno local

---

## 1. Visión General

La arquitectura de **UrbanMove** en la rama `prototipo1` fue diseñada para funcionar tanto en **LocalStack** (desarrollo local sin costes ni dependencias externas) como en **AWS Real** (producción/staging).

Con las mejoras aplicadas a Terraform y al pipeline de CI/CD:
1. **El código HCL es 100% agnóstico**: Mediante la variable `is_localstack = true/false` se alternan los endpoints personalizados (`http://localstack:4566`) o los endpoints nativos de AWS.
2. **Seguridad y Permisos IAM preparados**: Se añadieron las políticas mínimas (`AWSLambdaBasicExecutionRole`, lectura/escritura en SQS y S3, publicación en EventBridge) para que las Lambdas funcionen en AWS IAM real sin errores de permisos `403 AccessDenied`.
3. **Validación automática en CI**: Cada `push` y `pull_request` a `prototipo1` o `main` valida la sintaxis, empaqueta las Lambdas y ejecuta `terraform plan` contra el perfil de desarrollo.

---

## 2. Matriz de Componentes: Local vs AWS

| Componente | Desarrollo Local (`local.tfvars`) | AWS Staging / Producción (`staging.tfvars`) |
|---|---|---|
| **Bus de Eventos** | EventBridge en LocalStack (`urbanmove-bus`) | Amazon EventBridge (`urbanmove-bus-staging`) |
| **Colas y DLQs** | SQS en LocalStack (11 colas + 11 DLQs) | Amazon SQS con retención de 14 días en DLQ |
| **Data Lake** | S3 en LocalStack (`force_destroy = true`) | Amazon S3 con versionado, cifrado AES256 y Public Access Block |
| **Computación Serverless**| 5 Lambdas Python 3.11 en LocalStack | AWS Lambda con rol IAM `urbanmove-lambda-exec-role` |
| **Base de Datos** | PostgreSQL 16 Alpine en Docker Compose | Amazon Aurora Serverless v2 PostgreSQL o RDS PostgreSQL |
| **Identidad / Auth** | Keycloak en Docker (`/auth`) | Keycloak en ECS/Fargate o Amazon Cognito con JWKS |
| **Microservicios Java** | Contenedores Docker locales (red `urbanmove-network`)| AWS ECS Fargate / EKS con App Runner o ALB |
| **API Gateway** | Nginx Proxy inverso en puerto 8080 | AWS Application Load Balancer (ALB) o Amazon API Gateway |

---

## 3. Estructura de Entornos en Terraform

Se crearon perfiles `.tfvars` desacoplados bajo `infra/terraform/environments/`:

```text
infra/terraform/
├── main.tf                 # Provider AWS con default_tags y endpoints condicionales
├── variables.tf            # Definición tipada con defaults seguros
├── events.tf               # EventBridge, SQS, DLQ, Rules, Targets y Policies
├── lambda.tf               # Lambdas, Event Source Mappings y Roles IAM
├── s3.tf                   # Data Lake con cifrado y bloqueo público
├── outputs.tf              # ARNs y URLs para consumo de servicios
└── environments/
    ├── local.tfvars        # is_localstack = true, endpoints a LocalStack
    └── staging.tfvars      # is_localstack = false, AWS real sin endpoints mock
```

---

## 4. Pasos para Ejecutar la Migración a AWS

### Paso 1: Configurar el Backend Remoto de Terraform
En `infra/terraform/main.tf`, habilitar el bloque de backend remoto para almacenar el estado (`tfstate`) en S3 con bloqueo en DynamoDB:
```hcl
terraform {
  backend "s3" {
    bucket         = "urbanmove-tfstate-<account-id>"
    key            = "environments/staging/terraform.tfstate"
    region         = "eu-west-1"
    dynamodb_table = "urbanmove-tflocks"
    encrypt        = true
  }
}
```

### Paso 2: Autenticación en AWS
En local con AWS CLI o en GitHub Actions usando **OIDC (OpenID Connect)** sin claves estáticas:
```bash
aws configure --profile urbanmove-staging
export AWS_PROFILE=urbanmove-staging
```

### Paso 3: Empaquetar Lambdas y Planificar
```bash
# 1. Empaquetar artefactos de las 5 Lambdas
./infra/lambdas/build.sh

# 2. Inicializar Terraform con el backend de AWS
cd infra/terraform
terraform init

# 3. Revisar el plan contra AWS Real
terraform plan -var-file=environments/staging.tfvars -out=tfplan-staging

# 4. Aplicar los cambios
terraform apply tfplan-staging
```

### Paso 4: Configurar los Microservicios Spring Boot
Al desplegar los microservicios en ECS/EKS:
- `AWS_ENDPOINT_URL`: **Debe dejarse vacío** (el SDK de AWS conectará a AWS automáticamente).
- `AWS_DEFAULT_REGION`: `eu-west-1`.
- `DB_HOST`: Endpoint del clúster de Amazon Aurora o RDS.
- `KEYCLOAK_JWKS_URI`: URL pública HTTPS del servidor de autenticación.

---

## 5. Integración Continua (CI Pipeline)

El archivo `.github/workflows/ci.yml` ahora ejecuta dos jobs en paralelo:
1. **`test`**: Compila con Java 21 Temurin y ejecuta toda la batería de tests unitarios y de integración con Maven.
2. **`terraform`**:
   - Empaqueta las Lambdas en Python 3.11.
   - Ejecuta `terraform fmt -check -diff` (formato canónico).
   - Ejecuta `terraform init -backend=false` y `terraform validate`.
   - Ejecuta `terraform plan -var-file=environments/local.tfvars` para asegurar que ningún cambio rompe la infraestructura.
