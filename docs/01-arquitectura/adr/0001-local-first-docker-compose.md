# 0001. Local-first con Docker Compose y LocalStack

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Decisores:** Equipo UrbanMove

## Contexto

El equipo empieza el proyecto desde cero, con tres personas y un plazo de 1-2 meses.
No está confirmado que la asignatura proporcione una cuenta de AWS. El enunciado permite
explícitamente desplegar en AWS Academy, AWS real, LocalStack o un entorno local con
servicios equivalentes, siempre que se justifique.

Además, el equipo tiene poca experiencia previa en AWS: se conocen EC2, S3 y EFS a nivel
teórico, pero no ECS, ALB, IAM avanzado ni Kubernetes.

## Decisión

Se adopta una estrategia **local-first**:

- Docker Compose para los microservicios, PostgreSQL, Keycloak, Grafana y Prometheus.
- LocalStack para simular S3, SQS, SNS, EventBridge, Lambda y CloudWatch.
- GitHub Actions para CI y para una parte de CD.
- Terraform con el provider de AWS apuntando a LocalStack en local.
- El despliegue en AWS Academy o en una cuenta real se hace **después** de que el
  sistema funcione en local, y solo con los servicios estrictamente necesarios.

## Alternativas consideradas

### A. AWS desde el día 1 (ECS/EKS + RDS + MSK)

- **Ventaja:** experiencia cloud real y arquitectura más "profesional".
- **Inconveniente:** curva de aprendizaje, permisos, coste y dependencia de una cuenta
  que no está confirmada. EKS y MSK son caros; ECS requiere IAM, VPC, ALB y ECR.
- **Descartada:** el riesgo de no llegar a los mínimos es alto.

### B. Solo local, sin simulación cloud

- **Ventaja:** máxima simplicidad.
- **Inconveniente:** no se practican S3, SQS, EventBridge ni Lambda, que son requisitos
  del enunciado.
- **Descartada:** LocalStack cubre esa carencia sin coste.

### C. Kubernetes local (kind/k3d)

- **Ventaja:** demuestra Kubernetes.
- **Inconveniente:** no es necesario para el enunciado y añade complejidad de red,
  volúmenes e ingress.
- **Descartada:** se puede añadir como ampliación, no como base.

## Consecuencias

- El equipo puede trabajar sin cuenta AWS y sin coste.
- Los mismos contenedores y comandos se usan en local y en CI.
- La migración a AWS es incremental: primero S3/SQS/EventBridge/Lambda reales, luego
  cómputo gestionado si hay tiempo.
- Hay que documentar las diferencias entre LocalStack y AWS (permisos, límites,
  comportamientos no soportados).
- El informe debe justificar la decisión y explicar qué se probó en local y qué en AWS.

## Referencias

- Enunciado, sección 9 (Despliegue).
- [`docs/00-gestion/PLAN.md`](../../00-gestion/PLAN.md), sección 6.
- [`docs/01-arquitectura/ARCHITECTURE.md`](../../01-arquitectura/ARCHITECTURE.md), sección 10.
