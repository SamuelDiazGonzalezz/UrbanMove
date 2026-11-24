# Terraform Full — plataforma completa como código

> **Fecha:** 2026-11-24
> **Rama:** `prototipo1`
> **Estado:** plan aprobado para ejecución. Sustituye a la idea de “Terraform solo para la
> mensajería”.
> **Documento principal:** [DEUDA-Y-ROADMAP-2026-11-24.md](../00-gestion/DEUDA-Y-ROADMAP-2026-11-24.md)
> **Relacionados:** [ADR 0011](../01-arquitectura/adr/0011-terraform-localstack-iac.md),
> [AWS-MIGRATION-GUIDE.md](AWS-MIGRATION-GUIDE.md), [CI-CD.md](CI-CD.md),
> [CONTAINERS.md](../02-desarrollo/CONTAINERS.md), [DATA-ARCHITECTURE.md](../01-arquitectura/DATA-ARCHITECTURE.md).

---

## 0. Decisión en una frase

**Pasar de “Terraform de mensajería” a “Terraform de toda la plataforma”**: un `terraform apply`
debe crear red, base de datos, registro de imágenes, cómputo, bus de eventos, colas, Lambdas,
Data Lake, secretos, logs y alarmas; y un pipeline debe construir, publicar y desplegar los 13
servicios, Keycloak y el gateway.

Hoy Terraform solo cubre una parte:

| Gestionado por Terraform | **No** gestionado por Terraform (vive en Compose) |
|---|---|
| EventBridge (`urbanmove-bus`) | VPC, subredes, security groups |
| 11 colas SQS + 11 DLQ + reglas + políticas | RDS / PostgreSQL y las 14 bases |
| 5 Lambdas + IAM + event source mappings | ECR (registro de imágenes) |
| Bucket S3 del Data Lake | ECS / EC2, ALB, gateway, Keycloak |
| Variables de entorno básicas | Secretos, CloudWatch, alarmas, autoscaling |
| — | Backend remoto de estado y OIDC de GitHub |

Referencias del estado actual: `infra/terraform/events.tf`, `lambda.tf`, `s3.tf`, `main.tf`,
`variables.tf`, `outputs.tf` y `environments/{local,staging}.tfvars`.

---

## 1. Qué significa “full” y qué no

“Terraform full” **no** significa que LocalStack pueda simular todo. LocalStack Community no
ofrece ECS, RDS ni ALB con la fidelidad necesaria. Significa:

1. **AWS real**: todo lo anterior se crea con Terraform, sin pasos manuales en consola.
2. **Local**: se mantiene Docker Compose para el día a día y Terraform/LocalStack solo para la
   mensajería y las Lambdas. Es una diferencia explícita y documentada, no una deuda oculta.
3. **Mismo código, dos stacks**: módulos compartidos y dos *root modules* (`localstack` y `aws`)
   para que un `plan` local no intente crear una VPC.

---

## 2. Arquitectura objetivo en AWS

```text
                              Internet
                                 │
                          ┌──────▼──────┐
                          │     ALB     │  (HTTP/HTTPS, ACM opcional)
                          └──────┬──────┘
                                 │  /  /api/*  /usuario  /operaciones  /dashboard
                          ┌──────▼──────┐
                          │  gateway    │  nginx (ECS Fargate)
                          │  (nginx)    │  bloquea /internal/*
                          └──┬───┬───┬──┘
              ┌──────────────┘   │   └──────────────┐
       ┌──────▼─────┐    ┌───────▼──────┐    ┌──────▼──────┐
       │ identity   │ …  │ reservations │ …  │ analytics   │   13 servicios (ECS Fargate)
       └──────┬─────┘    └───────┬──────┘    └──────┬──────┘
              │ Cloud Map (DNS privado)             │
              └──────────────┬──────────────────────┘
                             │
                  ┌──────────▼──────────┐
                  │ RDS PostgreSQL      │  operacional: 13 bases
                  │ (Multi-AZ opcional) │  analítica: analytics_db (ADR 0006)
                  └──────────┬──────────┘
                             │
        ┌────────────────────┼─────────────────────┐
        │                    │                     │
 ┌──────▼──────┐     ┌───────▼───────┐     ┌───────▼───────┐
 │ EventBridge │────▶│ SQS + DLQ     │────▶│ 5 Lambdas     │
 │  bus        │     │ por consumidor│     │ (VPC)         │
 └─────────────┘     └───────────────┘     └───────┬───────┘
                                                   │
                                            ┌──────▼──────┐
                                            │ S3 Data Lake│
                                            │ raw/trusted │
                                            └─────────────┘

  Keycloak (ECS) ── keycloak_db ── RDS
  ECR: 13 servicios + gateway (+ Keycloak si se empaqueta)
  Secrets Manager: contraseñas de BD y admin de Keycloak
  CloudWatch: logs, métricas, alarmas y dashboard
```

### 2.1. Por qué esta topología

- **ALB → gateway → servicios**: replica el enrutado actual de `infra/gateway/nginx.conf` y evita
  13 target groups y 13 reglas de ALB. Un solo punto de entrada y `/internal/*` sigue bloqueado.
- **Cloud Map** para service discovery: los servicios se llaman por DNS (`reservations.urbanmove.local`),
  igual que hoy se llaman por nombre de contenedor. Evita acoplar IPs.
- **RDS con una base por servicio**: mantiene la propiedad de datos del diseño y el `init-databases.sql`
  actual, pero con backups, cifrado y réplica gestionados.
- **Lambdas en VPC**: 4 de las 5 necesitan RDS o servicios internos. Sin VPC no funcionan en AWS.
  Es el punto que hoy rompería una migración “directa”.
- **Keycloak en ECS**: mismo realm y mismos roles; el issuer cambia a una URL pública o al DNS del ALB.

---

## 3. Estructura de Terraform propuesta

```text
infra/terraform/
├── modules/
│   ├── network/          # VPC, subredes, IGW/NAT, SGs, endpoints
│   ├── data/             # RDS, Secrets Manager, bootstrap de bases
│   ├── messaging/        # events.tf + lambda.tf + s3.tf actuales
│   ├── registry/         # ECR por servicio
│   ├── compute/          # ECS, task definitions, services, ALB, Cloud Map
│   └── observability/    # log groups, alarmas, dashboard
├── stacks/
│   ├── localstack/       # root actual, solo mensajería/S3/Lambdas
│   └── aws/              # root nuevo, plataforma completa
└── environments/
    ├── local.tfvars
    └── staging.tfvars
```

Reglas:

1. Los módulos no leen variables de entorno ni rutas absolutas; reciben todo por variables.
2. `stacks/localstack` mantiene el `apply.sh` y el servicio `terraform` de Compose.
3. `stacks/aws` usa backend S3 + DynamoDB y **no** tiene credenciales estáticas.
4. `terraform fmt -check -recursive` y `terraform validate` en cada stack.
5. El estado nunca se versiona; el `.terraform.lock.hcl` sí.

> **Nota de migración:** mover los `events.tf`, `lambda.tf`, `s3.tf` actuales a `modules/messaging`
> es parte de TF-1. Mientras se hace, el stack local sigue funcionando con la ruta actual.

---

## 4. Decisiones técnicas y trampas conocidas

### 4.1. Lambdas: hoy no funcionarían en AWS

`infra/terraform/lambda.tf` define un único `lambda_env` para las 5 funciones:

```hcl
RESERVATIONS_URL = "http://reservations:8080"
MAINTENANCE_URL  = "http://maintenance:8080"
DB_HOST          = "postgres"
DB_NAME          = "analytics_db"
```

En AWS no existen `postgres`, `reservations` ni `maintenance`. Además:

| Lambda | Necesita | Configuración AWS |
|---|---|---|
| `analytics-ingest` | S3 | Sin VPC; permisos S3 |
| `maintenance-detection` | HTTP a `maintenance` | VPC + Cloud Map/ALB interno |
| `reservation-expiration` | HTTP a `reservations` | VPC + Cloud Map/ALB interno |
| `mart-builder` | RDS `analytics_db` | VPC + SG de RDS |
| `demand-relocation` | RDS `analytics_db` | VPC + SG de RDS |

**Solución:**

1. Parametrizar el entorno por Lambda (`locals.lambda_env_by_name`) en lugar de un mapa único.
2. Añadir `vpc_config` con subredes privadas y un SG de Lambdas.
3. Resolver servicios con Cloud Map (`http://reservations.urbanmove.local:8080`) y la BD con el
   endpoint de RDS.
4. Inyectar contraseñas desde Secrets Manager (o `aws_secretsmanager_secret_version` en el
   despliegue, nunca en texto plano en el repo).
5. Si las Lambdas van en subredes privadas, necesitan **NAT Gateway** o **VPC endpoints** para
   S3, SQS, EventBridge y CloudWatch Logs. Elegir NAT (simple) o endpoints (más seguro y caro).

**Aceptación:** un evento real recorre EventBridge → SQS → Lambda → S3/RDS en AWS.

### 4.2. RDS no crea 13 bases

`aws_db_instance` solo crea **una** base inicial (`db_name`). El proyecto necesita 13 bases de
servicio + `keycloak_db`. Opciones:

| Opción | Pros | Contras | Recomendación |
|---|---|---|---|
| **Tarea ECS one-off con `psql`** | Sin abrir RDS a CI; idempotente; usa la misma VPC | Un recurso más y un paso de orden | **Recomendada** |
| Provider `cyrilgdn/postgresql` desde CI | Declarativo | CI necesita red/VPN o RDS público; más superficie | Alternativa |
| Flyway con `createDatabaseIfNotExist` | Cero infra extra | JDBC no crea bases en PostgreSQL | No válido |
| Una sola base con esquemas | Menos recursos | Rompe “una base por servicio” del diseño | Plan B |

La tarea de bootstrap ejecuta un SQL idempotente:

```sql
SELECT 'CREATE DATABASE identity_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'identity_db')\gexec
-- repetir por cada base
```

Debe ejecutarse **antes** de arrancar los servicios y ser segura si se repite.

### 4.3. Secretos

- No usar `TF_VAR_db_password` con el valor real en CI.
- Generar la contraseña con `random_password` y guardarla en Secrets Manager.
- Para RDS, valorar `manage_master_user_password = true` (secreto gestionado por AWS).
- Inyectar en ECS con `secrets` (`valueFrom`) y en Lambdas con Secrets Manager/SSM.
- El estado de Terraform contiene secretos: backend cifrado, acceso restringido y sin `terraform
  output` con valores sensibles.
- `sensitive = true` en todas las variables y outputs que correspondan.

### 4.4. Keycloak

- Local usa `start-dev`; en AWS usar `start` con `KC_DB=postgres`, `KC_HOSTNAME` y
  `KC_PROXY_HEADERS=xforwarded` detrás del ALB.
- El realm se importa con `--import-realm` y el JSON horneado en una imagen propia (ECR) o montado
  desde S3/EFS. Para la demo, una imagen `urbanmove/keycloak:25.0.6-realm` es lo más simple.
- `KEYCLOAK_JWKS_URI` de los servicios debe apuntar a la URL pública o al DNS interno del ALB.
- Sin dominio propio, usar el DNS del ALB y documentar que el issuer es HTTP; para la rúbrica es
  suficiente, pero no para producción.

### 4.5. Gateway y estáticos

- Opción A (recomendada): imagen nginx con `clients/` copiado y `nginx.conf`; ECS service detrás
  del ALB. Reproduce el comportamiento local.
- Opción B: S3 + CloudFront para los estáticos y ALB solo para `/api/*`. Más “cloud native”, más
  recursos y más configuración.
- Mantener el bloqueo de `/internal/*` en nginx; los servicios siguen exponiendo `/actuator/**`
  solo dentro de la VPC (SG del ALB no lo enruta).

### 4.6. Cómputo: Fargate vs EC2

| Criterio | ECS Fargate (recomendado) | EC2 + Docker Compose (plan B) |
|---|---|---|
| Rúbrica / “cloud native” | Alta | Media |
| Coste mensual estimado | ~150–220 € | ~40–70 € |
| Complejidad Terraform | Alta (15 task definitions, ALB, Cloud Map) | Baja (1 instancia + user_data) |
| Escalabilidad demostrable | Sí (autoscaling) | Limitada (una instancia) |
| Riesgo de demo | Medio | Bajo |
| AWS Academy | Ajustado; usar Spot y apagar | Cómodo |

**Decisión:** Fargate para la entrega; EC2 + Compose como plan B documentado si el crédito no
alcanza. El módulo `compute` debe permitir `compute_mode = "fargate" | "ec2"` para no reescribir
el resto.

### 4.7. Imágenes y orden de despliegue

1. CI construye las 13 imágenes + gateway (+ Keycloak si se empaqueta) y las publica en ECR con
   el SHA del commit.
2. Terraform crea RDS y ejecuta el bootstrap de bases.
3. Terraform crea ECS con `desired_count = 0` o espera a que las imágenes existan.
4. Se aplican los servicios con `image_tag` del commit.
5. Flyway migra al arrancar cada servicio.
6. Smoke test contra el ALB.

La primera vez hay una dependencia circular: ECR debe existir antes de publicar; RDS antes de
arrancar; el ALB antes de saber la URL de Keycloak. Resolver con `targeted apply` por fases o con
`depends_on` explícitos y una fase de bootstrap. Documentar el orden en el README de `stacks/aws`.

---

## 5. Fases de ejecución

### TF-0 — Fundaciones (S)

- Backend remoto: bucket S3 + tabla DynamoDB (bootstrap manual o `stacks/bootstrap`).
- Separar `modules/` y `stacks/`; mover la mensajería actual a `modules/messaging`.
- `environments/local.tfvars` y `staging.tfvars` sin secretos.
- Rol OIDC de GitHub (`token.actions.githubusercontent.com`) con trust restrictivo al repo y rama.
- `terraform fmt -check -recursive`, `validate` y `plan` en CI.

**Aceptación:** `terraform init` remoto en `stacks/aws` y `plan` reproducible desde CI sin claves
estáticas.

### TF-1 — Red (S/M)

- VPC con 2 AZ, subredes públicas (ALB/NAT) y privadas (ECS/RDS/Lambdas).
- IGW, NAT Gateway (o endpoints), route tables.
- Security groups: ALB → gateway, gateway → servicios, servicios/Lambdas → RDS, ECS → ECR/Logs.
- Opcional: VPC endpoints para S3 (gateway, gratis) y ECR/Logs (interface).

**Aceptación:** `terraform plan` sin recursos de cómputo; SG con mínimo privilegio documentado.

### TF-2 — Datos (M)

- RDS PostgreSQL 16 (operacional) y, si el presupuesto lo permite, segunda instancia analítica
  (ADR 0006). Plan B: una instancia con `analytics_db`.
- Subnet group privado, cifrado, backups, `deletion_protection` en staging, `skip_final_snapshot`
  solo en local/demo.
- Secrets Manager y `random_password`.
- Bootstrap idempotente de las 14 bases.
- Parameter group con `max_connections` suficiente (13 servicios × pool 4 + Lambdas).

**Aceptación:** las 14 bases existen; un `psql` desde una tarea de la VPC conecta; los secretos
no aparecen en el repo.

### TF-3 — Registro y cómputo (L)

- ECR: 13 repos + gateway (+ Keycloak si aplica), lifecycle policy y escaneo de imágenes.
- ECS cluster, Cloud Map, task definitions y services (CPU/memoria, health checks, logs).
- Keycloak ECS + realm.
- Gateway nginx ECS + ALB + listener (HTTP para demo, HTTPS/ACM si hay dominio).
- Autoscaling: CPU 70%, min 1, max 2 en servicios del núcleo.
- CloudWatch log groups con retención (7–14 días).

**Aceptación:** 15 tareas sanas; `/` sirve la interfaz; `/api/analytics/health` responde;
Keycloak emite token y los servicios lo validan.

### TF-4 — Mensajería y Lambdas en AWS (M)

- Extraer la mensajería actual a `modules/messaging` y reutilizarla en `stacks/aws`.
- Añadir `vpc_config`, SG y entorno parametrizado a las Lambdas.
- Permisos IAM mínimos por Lambda (S3, SQS, EventBridge, Secrets Manager, logs).
- Alarmas de DLQ y de errores de Lambda.
- Verificar el flujo completo EventBridge → SQS → Lambda → S3/RDS.

**Aceptación:** los 5 flujos de `PROTOTIPO-1.md` funcionan en AWS, no solo en local.

### TF-5 — CI/CD y observabilidad (M)

- Workflow `infra.yml`: `fmt`, `validate`, `plan` en PR; `apply` manual en `main`.
- Workflow `cd.yml`: matriz de 13 imágenes + gateway → ECR; `terraform apply` con `image_tag`;
  smoke test y rollback a la task definition anterior.
- Alarmas: DLQ > 0, errores de Lambda, 5xx del ALB, CPU/memoria de ECS, conexiones y almacenamiento
  de RDS.
- Dashboard de CloudWatch con las mismas señales que Grafana en local.
- Migraciones: Flyway al arrancar; verificar que un servicio nuevo no bloquea el despliegue.

**Aceptación:** un push a `main` deja la plataforma desplegada y un smoke test verde; el plan de
PR se comenta o se revisa antes de aplicar.

### TF-6 — Cutover, coste y limpieza (S)

- Carga de datos/seed y smoke E2E de los 5 flujos.
- Medición de coste real durante 24–48 h y tabla en el PDF.
- Guía de `terraform destroy` y comprobación de que no quedan recursos (S3, EIP, NAT, RDS).
- Evidencias: `terraform plan`, `apply`, salida de ALB, logs, DLQ, S3, dashboard.

**Aceptación:** demo completa desde cero; coste documentado; destrucción verificada.

---

## 6. Inventario de recursos (módulo → recurso)

| Módulo | Recursos principales | Equivalente local |
|---|---|---|
| `network` | `aws_vpc`, `aws_subnet`, `aws_internet_gateway`, `aws_nat_gateway`, `aws_eip`, `aws_route_table`, `aws_route`, `aws_security_group`, `aws_vpc_endpoint` | Red `urbanmove-network` de Compose |
| `data` | `aws_db_instance` / `aws_rds_cluster`, `aws_db_subnet_group`, `aws_db_parameter_group`, `random_password`, `aws_secretsmanager_secret`, tarea de bootstrap | `postgres:16-alpine` + `init-databases.sql` |
| `registry` | `aws_ecr_repository` ×14, `aws_ecr_lifecycle_policy` | Build local de `services/Dockerfile` |
| `compute` | `aws_ecs_cluster`, `aws_ecs_task_definition` ×15, `aws_ecs_service` ×15, `aws_service_discovery_*`, `aws_lb`, `aws_lb_target_group`, `aws_lb_listener`, `aws_lb_listener_rule`, `aws_appautoscaling_*` | `docker-compose.yml` + nginx |
| `messaging` | `aws_cloudwatch_event_bus`, `aws_cloudwatch_event_rule/target`, `aws_sqs_queue` (+DLQ), `aws_sqs_queue_policy`, `aws_lambda_function`, `aws_lambda_event_source_mapping`, `aws_iam_role/policy`, `aws_s3_bucket` | LocalStack + `init-aws.sh` |
| `observability` | `aws_cloudwatch_log_group`, `aws_cloudwatch_metric_alarm`, `aws_cloudwatch_dashboard` | Prometheus + Grafana (PR-7) |

---

## 7. CI/CD de infraestructura

```text
PR de infraestructura
  └─ terraform fmt -check -recursive
  └─ terraform init (backend=false o rol de solo lectura)
  └─ terraform validate
  └─ terraform plan -var-file=environments/staging.tfvars
  └─ comentario con el plan (opcional)

push a main (aprobación manual)
  └─ terraform apply -var image_tag=$GITHUB_SHA
  └─ smoke test (ALB /actuator/health, /api/analytics/health)
  └─ rollback: aplicar image_tag anterior

push de código
  └─ mvn test / verify
  └─ build matrix de 14 imágenes
  └─ push a ECR con SHA
```

Reglas:

- OIDC, nunca `AWS_ACCESS_KEY_ID` de larga duración.
- `plan` en PR y `apply` solo desde `main` con aprobación.
- Un `apply` no se ejecuta si el plan contiene destrucción de RDS o S3 sin revisión explícita.
- El pipeline publica `tfplan` y el resumen como artefacto.
- GitHub Environments (`staging`) con revisores para el apply.

---

## 8. Runbook de cutover (orden exacto)

1. **Bootstrap**: crear bucket de estado + tabla de locks; `terraform init` con backend.
2. **TF-1**: aplicar red y SGs.
3. **TF-3 (parcial)**: crear ECR y publicar las 14 imágenes.
4. **TF-2**: crear RDS, secretos y ejecutar el bootstrap de bases.
5. **TF-3**: aplicar ECS/Cloud Map/ALB/Keycloak/gateway con `desired_count` mínimo.
6. **TF-4**: aplicar mensajería y Lambdas con VPC.
7. **TF-5**: activar alarmas, dashboard y workflows.
8. **Smoke E2E**: reserva → viaje → evento → S3; mantenimiento; recolocación; expiración; fallo.
9. **TF-6**: medir coste, documentar y `terraform destroy` al terminar la demo.

Comprobaciones de humo mínimas:

```bash
curl -fsS https://<alb-dns>/actuator/health
curl -fsS https://<alb-dns>/api/analytics/health
curl -fsS -X POST https://<alb-dns>/api/auth/token -d '...'
aws sqs get-queue-attributes --queue-url <dlq> --attribute-names ApproximateNumberOfMessages
aws s3 ls s3://<bucket>/raw/ --recursive | head
```

---

## 9. Estimación de coste (orden de magnitud)

> Precios orientativos para eu-west-1; recalcular antes de la entrega. AWS Academy puede tener
> restricciones de región y de servicios.

| Componente | Configuración de demo | Estimación mensual |
|---|---|---|
| ECS Fargate (13 servicios) | 0,25 vCPU / 0,5 GB, 24×7 | ~110–120 € |
| ECS Fargate (Keycloak + gateway) | 0,25–0,5 vCPU | ~20–30 € |
| ALB | 1 ALB + LCU bajo | ~20 € |
| NAT Gateway | 1 AZ | ~32 € + tráfico |
| RDS PostgreSQL | `db.t4g.micro`, 20 GB, 1 AZ | ~15 € |
| RDS analítica (opcional) | `db.t4g.micro` | ~15 € |
| S3 / SQS / Lambda / CloudWatch | Uso de demo | ~5–15 € |
| **Total on-demand** | | **~200–230 €** |
| **Con Fargate Spot y apagado** | | **~60–100 €** |
| **Plan B: EC2 + Compose + RDS** | t3.large + RDS | **~50–70 €** |

Medidas de contención:

- Fargate Spot para servicios no críticos.
- `desired_count = 1` y autoscaling solo en el núcleo.
- Apagar la plataforma fuera del horario de demo (`desired_count = 0` o `destroy`).
- Un solo NAT o subredes públicas con SG restrictivo para la demo (menos seguro, más barato).
- Retención de logs de 7 días y lifecycle de ECR.

---

## 10. Riesgos y mitigaciones

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| Lambdas sin VPC fallan en AWS | Alta | Alto | TF-4 con `vpc_config`, SG y entorno parametrizado |
| RDS no crea las 13 bases | Alta | Medio | Tarea de bootstrap idempotente o provider `postgresql` |
| Coste superior al crédito | Media | Alto | Plan B EC2; Spot; `destroy`; presupuesto y alarmas |
| Dependencia circular ECR ↔ ECS ↔ ALB | Media | Medio | Fases `targeted apply` y orden documentado |
| Keycloak con hostname/issuer incorrecto | Media | Alto | `KC_HOSTNAME` y `KEYCLOAK_JWKS_URI` desde el ALB; prueba de token |
| Estado remoto con secretos | Media | Alto | Backend cifrado, acceso mínimo, `sensitive`, no outputs con secretos |
| Deriva entre Compose y AWS | Alta | Medio | Módulos compartidos, mismas imágenes y variables; smoke E2E |
| Una AZ cae en la demo | Baja | Alto | Multi-AZ en RDS si el presupuesto lo permite; reintentos |
| El pipeline aplica sin revisar | Media | Alto | `plan` obligatorio + aprobación + protección de `main` |

---

## 11. Checklist “Terraform full terminado”

- [ ] `infra/terraform` tiene `modules/` y `stacks/{localstack,aws}`.
- [ ] Backend remoto S3 + DynamoDB y OIDC de GitHub funcionando.
- [ ] `terraform fmt -check -recursive` y `validate` verdes en CI.
- [ ] VPC, subredes, SGs y NAT/endpoints creados por Terraform.
- [ ] RDS con las 14 bases y secretos en Secrets Manager.
- [ ] 14 repositorios ECR con imágenes etiquetadas por SHA.
- [ ] 15 tareas ECS sanas (13 servicios + Keycloak + gateway).
- [ ] ALB enruta `/`, `/api/*` y las tres interfaces; `/internal/*` bloqueado.
- [ ] 5 Lambdas con VPC, permisos y entorno correctos.
- [ ] Flujo EventBridge → SQS → Lambda → S3/RDS verificado.
- [ ] Alarmas de DLQ, Lambda, ALB, ECS y RDS activas.
- [ ] `cd.yml` despliega y hace smoke test; rollback documentado.
- [ ] Coste medido y `destroy` verificado.
- [ ] Evidencias enlazadas desde la hoja de ruta y el PDF.

---

## 12. Enlaces

- [Hoja de ruta y deuda](../00-gestion/DEUDA-Y-ROADMAP-2026-11-24.md)
- [Guía de migración a AWS](AWS-MIGRATION-GUIDE.md)
- [CI/CD](CI-CD.md)
- [Contenedores](../02-desarrollo/CONTAINERS.md)
- [Arquitectura de datos](../01-arquitectura/DATA-ARCHITECTURE.md)
- [ADR 0002 — PostgreSQL vs DynamoDB](../01-arquitectura/adr/0002-postgresql-vs-dynamodb.md)
- [ADR 0006 — Dos instancias RDS](../01-arquitectura/adr/0006-dos-instancias-rds.md)
- [ADR 0011 — Terraform + LocalStack](../01-arquitectura/adr/0011-terraform-localstack-iac.md)
- [ADR 0013 — Prototipo 1 sustituye a core-fleet](../01-arquitectura/adr/0013-prototipo1-sustituye-core-fleet.md)
