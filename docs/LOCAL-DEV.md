# Entorno de desarrollo local de UrbanMove

> **Para quién:** los tres integrantes del equipo.
> **Objetivo:** que cualquier persona clone el repositorio y tenga el mismo entorno,
> las mismas versiones y los mismos comandos, sin depender de su sistema operativo.
> **Estado:** guía previa a la Fase 0. Algunos comandos se activarán cuando exista el
> `docker-compose.yml` y el POM padre.

---

## 1. Principios

1. **Docker para la infraestructura, IDE para el servicio que estás tocando.**
   No se instalan PostgreSQL, Keycloak, Grafana ni LocalStack en el sistema anfitrión.
2. **Versiones fijadas.** Nada de `latest` en imágenes ni de "la última versión" en
   librerías. Lo que funciona hoy debe funcionar en la demo.
3. **Los mismos comandos en Windows, macOS y Linux.** Si un comando solo funciona en
   PowerShell, se documenta su equivalente.
4. **Nada de secretos en el repositorio.** Cada persona crea su `.env` a partir de
   `.env.example`.
5. **El entorno se versiona.** Las herramientas y sus versiones viven en este documento
   y, cuando exista, en el repositorio (`docker-compose.yml`, `.mvnw`, `.tool-versions`).

---

## 2. Requisitos de hardware

| Recurso | Mínimo | Recomendado |
|---|---|---|
| RAM | 8 GB | **16 GB** |
| CPU | 4 núcleos | 6-8 núcleos |
| Disco libre | 15 GB | 30 GB |
| Virtualización | Activada en BIOS | Activada en BIOS |

### 2.1. Por qué 16 GB

| Componente | RAM aproximada |
|---|---|
| PostgreSQL (14 bases) | 300-500 MB |
| Keycloak | 700 MB - 1 GB |
| LocalStack | 700 MB - 1 GB |
| Prometheus + Grafana | 400-600 MB |
| Cada microservicio Spring Boot | 250-350 MB con `-Xmx256m` |
| 13 microservicios | ~4 GB |
| IDE + navegador | 2-3 GB |

Con 8 GB se puede trabajar, pero hay que usar **perfiles de Docker Compose** y no
levantar los 13 servicios a la vez. Con 16 GB se levanta todo sin sufrimiento.

### 2.2. Configuración de Docker Desktop

- WSL2 activado (Windows).
- Memoria asignada a Docker: **6-8 GB**.
- CPUs: la mitad de los núcleos disponibles.
- Disco: al menos 20 GB para imágenes y volúmenes.

En macOS y Linux, Docker Desktop o Docker Engine usan la memoria del sistema; conviene
limitar contenedores con `--memory` si el equipo tiene 8 GB.

---

## 3. Herramientas del equipo

Cada herramienta se explica con tres preguntas: **qué es**, **qué permite** y **por qué
la usamos en UrbanMove**.

### 3.1. Base del equipo

#### Git

- **Qué es:** sistema de control de versiones distribuido. Guarda la historia completa
  del proyecto en cada clon.
- **Qué permite:** ramas, commits, merges, reversión, trabajo en paralelo sin pisarse y
  trazabilidad de quién cambió qué.
- **Por qué lo usamos:** todo el proyecto se entrega en GitHub. Sin Git no hay PRs, ni
  CI, ni historial que enseñar en la defensa.
- **Versión:** 2.45 o superior.
- **Descarga:** <https://git-scm.com/downloads>

#### GitHub CLI (`gh`)

- **Qué es:** cliente oficial de GitHub para terminal.
- **Qué permite:** autenticarse, crear repositorios, abrir y revisar PRs, consultar
  Actions y lanzar workflows sin salir de la consola.
- **Por qué lo usamos:** evita manejar tokens a mano y permite que el equipo cree PRs
  con un comando. También sirve para comprobar el estado del pipeline.
- **Versión:** 2.x.
- **Descarga:** <https://cli.github.com/>

```bash
gh auth login
gh pr create --fill
gh run list
```

#### JDK 21 (Eclipse Temurin)

- **Qué es:** implementación libre del Java Development Kit, mantenida por Adoptium.
- **Qué permite:** compilar y ejecutar Java 21, con records, sealed interfaces, pattern
  matching y virtual threads.
- **Por qué lo usamos:** es la versión LTS fijada en `AGENTS.md` y en el plan. Temurin es
  la misma distribución que usan las imágenes Docker, así que local y contenedor
  coinciden.
- **Versión:** 21.0.x LTS.
- **Descarga:** <https://adoptium.net/temurin/releases/?version=21>

Comprobar:

```bash
java -version
javac -version
```

#### Maven Wrapper (`mvnw` / `mvnw.cmd`)

- **Qué es:** script que descarga y ejecuta una versión concreta de Maven, sin instalarlo
  en el sistema.
- **Qué permite:** compilar, testear, empaquetar y ejecutar análisis con una versión
  idéntica para todo el equipo y para CI.
- **Por qué lo usamos:** evita el clásico "a mí me compila" por tener Maven 3.6 y otro
  3.9. El wrapper se versiona en el repositorio.
- **Versión:** Maven 3.9.16 a través del wrapper.
- **Descarga:** no se instala; se genera con `mvn wrapper:wrapper` o se incluye en el
  repositorio.

```bash
./mvnw -v          # Linux/macOS
.\mvnw.cmd -v      # Windows
./mvnw verify
```

#### Docker Desktop / Docker Engine

- **Qué es:** plataforma de contenedores. Docker Desktop incluye el motor, la CLI y
  Docker Compose.
- **Qué permite:** ejecutar PostgreSQL, Keycloak, LocalStack, Grafana y los servicios en
  entornos aislados y reproducibles.
- **Por qué lo usamos:** es la base del entorno local y de la integración. Sin Docker no
  hay LocalStack, ni Testcontainers, ni paridad con CI.
- **Versión:** Docker Desktop 4.x con Compose v2 o superior.
- **Descarga:** <https://www.docker.com/products/docker-desktop/>

Comprobar:

```bash
docker version
docker compose version
```

#### Docker Compose

- **Qué es:** herramienta declarativa para definir y arrancar varios contenedores como
  un conjunto.
- **Qué permite:** describir servicios, redes, volúmenes, healthchecks y dependencias en
  un `docker-compose.yml`, y levantar todo con un comando.
- **Por qué lo usamos:** es el contrato del entorno local. Cualquier integrante ejecuta
  `docker compose up` y obtiene la misma plataforma.
- **Versión:** Compose v2 (incluido en Docker Desktop).
- **Descarga:** incluido con Docker Desktop; en Linux, plugin `docker-compose-plugin`.

### 3.2. IDE y cliente de APIs

#### IntelliJ IDEA

- **Qué es:** IDE para Java y Spring Boot.
- **Qué permite:** navegación de código, refactorización, depuración, ejecución de tests,
  cliente HTTP integrado, conexión a bases de datos y soporte de Maven.
- **Por qué lo usamos:** es el que mejor entiende Spring, JPA y los tests. La versión
  Community es suficiente para el proyecto.
- **Versión:** última estable (Community o Ultimate).
- **Descarga:** <https://www.jetbrains.com/idea/download/>

#### Visual Studio Code (opcional)

- **Qué es:** editor ligero y extensible.
- **Qué permite:** editar YAML, Markdown, Terraform, Python y Dockerfiles con extensiones.
- **Por qué lo usamos:** útil para los ficheros de infraestructura y documentación, y
  para quien prefiera un editor rápido.
- **Descarga:** <https://code.visualstudio.com/>

#### Postman o Bruno

- **Qué es:** cliente para probar APIs HTTP.
- **Qué permite:** guardar peticiones en colecciones, encadenar tests, gestionar entornos
  (local/AWS) y ejecutar colecciones en CI con Newman (Postman) o el CLI de Bruno.
- **Por qué lo usamos:** es una de las tres interfaces exigidas. Las colecciones se
  versionan en `clients/` y sirven como evidencia y como test E2E.
- **Versión:** última estable.
- **Descarga:** <https://www.postman.com/downloads/> · <https://www.usebruno.com/downloads>

> Bruno guarda las colecciones como ficheros de texto, lo que facilita los diffs en Git.
> Postman es más conocido. Cualquiera de los dos vale; el equipo debe elegir uno.

### 3.3. Nube local

#### LocalStack

- **Qué es:** emulador local de servicios de AWS.
- **Qué permite:** usar S3, SQS, SNS, EventBridge, Lambda, CloudWatch, IAM y DynamoDB sin
  cuenta de AWS y sin coste.
- **Por qué lo usamos:** permite desarrollar y demostrar eventos, Data Lake y Lambdas
  antes de tener AWS Academy. El código cambia solo el endpoint.
- **Versión:** `localstack/localstack:4.14.0`.
- **Descarga:** imagen Docker; también <https://www.localstack.cloud/>

```bash
docker run --rm -p 4566:4566 localstack/localstack:4.14.0
```

> LocalStack no es AWS al 100 %. Hay diferencias de paridad en permisos, límites y
> algunos comportamientos. Se documentan en `docs/DATA-ARCHITECTURE.md`.

#### AWS CLI v2

- **Qué es:** cliente oficial de línea de comandos de AWS.
- **Qué permite:** crear buckets, colas, buses y funciones; consultar recursos; depurar
  respuestas.
- **Por qué lo usamos:** los scripts de inicialización de LocalStack y las comprobaciones
  de la demo se hacen con AWS CLI. En AWS real se usa la misma herramienta.
- **Versión:** 2.x.
- **Descarga:** <https://aws.amazon.com/cli/>

Con LocalStack se usa un endpoint local y credenciales de prueba:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

#### `awslocal` (opcional)

- **Qué es:** envoltorio de AWS CLI que añade automáticamente `--endpoint-url` y las
  credenciales de LocalStack.
- **Qué permite:** escribir scripts más cortos y menos propensos a errores.
- **Por qué lo usamos:** comodidad en los scripts de inicialización y demo.
- **Instalación:** `pipx install awscli-local`.

#### Terraform

- **Qué es:** herramienta de infraestructura como código.
- **Qué permite:** declarar recursos (S3, SQS, RDS, Lambda, IAM) y crearlos, modificarlos
  o destruirlos de forma reproducible. `plan` muestra el cambio antes de aplicarlo.
- **Por qué lo usamos:** el enunciado pide DevOps e IaC. Terraform permite reproducir el
  entorno de AWS y destruirlo para no gastar crédito.
- **Versión:** última 1.x estable.
- **Descarga:** <https://developer.hashicorp.com/terraform/install>

```bash
terraform fmt -check
terraform validate
terraform plan
terraform apply
terraform destroy
```

> Terraform se incorpora en la Fase 0 tardía o al empezar con AWS. No bloquea el
> desarrollo local.

### 3.4. Datos y Lambdas

#### Python 3.12 o 3.13

- **Qué es:** lenguaje de programación.
- **Qué permite:** escribir Lambdas de ingesta, marts y detección con `boto3` y pandas.
- **Por qué lo usamos:** las Lambdas de datos son más rápidas de escribir y desplegar en
  Python que en Java, y el enunciado no impone lenguaje.
- **Versión:** 3.12 o 3.13.
- **Descarga:** <https://www.python.org/downloads/>

```bash
python --version
pip install boto3 pandas pyarrow
```

#### jq y yq

- **Qué es:** procesadores de JSON y YAML en línea de comandos.
- **Qué permite:** consultar y transformar respuestas JSON, y editar YAML de Compose o
  Kubernetes en scripts.
- **Por qué lo usamos:** los scripts de inicialización y las comprobaciones de la demo
  necesitan extraer campos concretos sin escribir un programa.
- **Versión:** jq 1.7; yq 4.x.
- **Descarga:** <https://jqlang.github.io/jq/download/> ·
  <https://github.com/mikefarah/yq/releases>

```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues | jq '.QueueUrls'
```

#### Node.js 22 LTS (opcional)

- **Qué es:** entorno de ejecución de JavaScript.
- **Qué permite:** ejecutar Newman (Postman en CI), herramientas de documentación y,
  si se hiciera, un frontend.
- **Por qué lo usamos:** solo si el equipo elige Newman para los tests E2E o añade una
  interfaz web. No es necesario para el backend.
- **Descarga:** <https://nodejs.org/>

### 3.5. Calidad, pruebas y seguridad

#### JUnit 5

- **Qué es:** framework de pruebas para Java.
- **Qué permite:** escribir tests unitarios y de integración, con extensiones y
  parametrización.
- **Por qué lo usamos:** es el estándar del ecosistema Spring y el que exige `AGENTS.md`.

#### AssertJ

- **Qué es:** librería de aserciones fluidas.
- **Qué permite:** escribir comprobaciones legibles (`assertThat(...).isEqualTo(...)`) y
  mensajes de error claros.
- **Por qué lo usamos:** sustituye a los `assertEquals` crípticos y hace que los tests se
  lean como especificaciones.

#### Mockito

- **Qué es:** framework de mocks.
- **Qué permite:** simular colaboradores en tests unitarios sin levantar infraestructura.
- **Por qué lo usamos:** aislar el caso de uso de adaptadores lentos o no disponibles.
- **Regla:** no se mockea el dominio; se mockean puertos.

#### Testcontainers

- **Qué es:** librería que arranca contenedores Docker reales durante los tests.
- **Qué permite:** probar contra PostgreSQL, LocalStack o Keycloak reales, no contra H2.
- **Por qué lo usamos:** `AGENTS.md` prohíbe H2. Testcontainers da paridad con producción
  y evita mocks frágiles.
- **Versión:** 1.21.x (gestionada por el BOM de Spring Boot).
- **Descarga:** dependencia Maven; requiere Docker en ejecución.

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
```

#### Awaitility

- **Qué es:** librería para esperar condiciones de forma controlada.
- **Qué permite:** testear procesos asíncronos sin `Thread.sleep()`.
- **Por qué lo usamos:** los eventos y las colas son asíncronos; `sleep` produce tests
  lentos y frágiles.

#### ArchUnit

- **Qué es:** librería para verificar reglas de arquitectura en tests.
- **Qué permite:** comprobar que el dominio no importa Spring, que los adaptadores no se
  cruzan y que se respeta la arquitectura hexagonal.
- **Por qué lo usamos:** la arquitectura se degrada sin reglas automáticas. Un test de
  ArchUnit la protege en cada PR.

#### JaCoCo

- **Qué es:** herramienta de cobertura de código.
- **Qué permite:** medir qué porcentaje del código ejecutan los tests y generar informes.
- **Por qué lo usamos:** el enunciado pide calidad y el CI necesita un umbral objetivo.
- **Versión:** 0.8.13.
- **Umbral:** 70-80 % en dominio, 50 % global.

#### gitleaks

- **Qué es:** escáner de secretos en el repositorio.
- **Qué permite:** detectar claves, tokens y contraseñas antes de subirlas.
- **Por qué lo usamos:** el repositorio es público. Un secreto filtrado es un incidente.
- **Descarga:** <https://github.com/gitleaks/gitleaks/releases>

```bash
gitleaks detect --source . --verbose
```

#### Trivy

- **Qué es:** escáner de vulnerabilidades.
- **Qué permite:** analizar dependencias, imágenes Docker y configuraciones de
  infraestructura.
- **Por qué lo usamos:** el pipeline de seguridad debe fallar ante vulnerabilidades
  críticas.
- **Descarga:** <https://trivy.dev/latest/getting-started/installation/>

#### k6 o Locust

- **Qué es:** herramientas de prueba de carga.
- **Qué permite:** simular usuarios concurrentes y medir latencia, errores y throughput.
- **Por qué lo usamos:** el enunciado pide justificar escalabilidad con evidencia. k6 es
  más ligero; Locust es Python y encaja si el equipo ya usa Python.
- **Descarga:** <https://k6.io/docs/get-started/installation/> ·
  <https://locust.io/>

---

## 4. Versiones fijadas

### 4.1. Host

| Herramienta | Versión | Comprobación |
|---|---|---|
| Git | 2.45+ | `git --version` |
| JDK | Temurin 21.0.x | `java -version` |
| Maven | 3.9.16 vía wrapper | `./mvnw -v` |
| Docker | 4.x Desktop / Engine 27+ | `docker version` |
| Docker Compose | v2+ | `docker compose version` |
| GitHub CLI | 2.x | `gh --version` |
| AWS CLI | 2.x | `aws --version` |
| Terraform | 1.x | `terraform version` |
| Python | 3.12 o 3.13 | `python --version` |
| Node.js (opcional) | 22 LTS | `node --version` |
| jq / yq | 1.7 / 4.x | `jq --version`, `yq --version` |

### 4.2. Imágenes Docker

| Imagen | Versión | Uso |
|---|---|---|
| `postgres` | `16-alpine` | Bases de datos del sistema y de Keycloak |
| `keycloak/keycloak` | `26.8.2` | Login OIDC |
| `localstack/localstack` | `4.14.0` | S3, SQS, EventBridge, Lambda |
| `grafana/grafana` | `13.3.1` | Dashboard |
| `prom/prometheus` | `v3.16.0` | Métricas |
| `nginx` | `1.27-alpine` | Edge local (opcional) |

> **Nunca `latest`.** Se fija la versión y se actualiza con un commit consciente.

### 4.3. Java y librerías

| Componente | Versión | Nota |
|---|---|---|
| Java | 21 | LTS |
| Spring Boot | 3.5.16 | Última 3.5.x |
| springdoc | 2.8.x | OpenAPI para Boot 3.5 |
| Testcontainers | 1.21.x | Gestionado por el BOM |
| JaCoCo | 0.8.13 | Cobertura |
| ArchUnit | 1.4.x | Arquitectura |
| Flyway | la del BOM de Boot | Migraciones |
| Resilience4j | la del BOM o 2.3.x | Tolerancia a fallos |

**No se fijan a mano** JUnit, AssertJ, Mockito ni Flyway: los gestiona el BOM de Spring
Boot. Fijarlos por separado provoca conflictos.

> Spring Boot 4.x existe, pero el proyecto se queda en 3.5.16 hasta que springdoc 3.x y el
> resto del ecosistema estén asentados. Cambiar de major a mitad de curso es un riesgo
> innecesario.

---

## 5. Instalación por sistema operativo

### 5.1. Windows

```powershell
winget install Git.Git
winget install EclipseAdoptium.Temurin.21.JDK
winget install Docker.DockerDesktop
winget install JetBrains.IntelliJIDEA.Community
winget install GitHub.cli
winget install Amazon.AWSCLI
winget install Python.Python.3.12
winget install jqlang.jq
winget install MikeFarah.yq
winget install Hashicorp.Terraform
```

Después:

1. Reiniciar para que Docker y WSL2 terminen de configurarse.
2. Abrir Docker Desktop y esperar a que el motor esté en verde.
3. En Git Bash o PowerShell, comprobar `docker version`.

### 5.2. macOS

```bash
brew install --cask temurin@21
brew install --cask docker
brew install --cask intellij-idea-ce
brew install gh awscli jq yq terraform python@3.12
```

### 5.3. Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk docker.io docker-compose-plugin \
  jq python3 python3-pip
```

Para yq, Terraform y AWS CLI, usar los instaladores oficiales enlazados arriba.

### 5.4. Configuración común de Git

Cada integrante, una vez:

```bash
git config --global user.name "Nombre Apellido"
git config --global user.email "correo@ejemplo.com"
git config --global init.defaultBranch main
git config --global core.autocrlf input
```

En Windows, `core.autocrlf input` evita que los scripts se suban con CRLF. Además, el
repositorio debe incluir un `.gitattributes` con `* text=auto eol=lf` cuando se cree el
código.

---

## 6. Arranque local con Docker

### 6.1. Qué se levanta

| Contenedor | Imagen | Puerto | Papel |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | 12 bases de servicio + `keycloak_db` + `analytics_db` |
| `keycloak` | `keycloak/keycloak:26.8.2` | 8180 | Login, roles y JWT |
| `localstack` | `localstack/localstack:4.14.0` | 4566 | S3, SQS, EventBridge, Lambda |
| `localstack-init` | `amazon/aws-cli` | — | Crea bucket, colas, DLQ, bus y reglas |
| `prometheus` | `prom/prometheus:v3.16.0` | 9090 | Métricas |
| `grafana` | `grafana/grafana:13.3.1` | 3000 | Dashboard |
| `nginx` | `nginx:1.27-alpine` | 8000 | Punto de entrada (opcional) |

Los 13 microservicios se añaden en la Fase 1. En el día a día se ejecutan desde el IDE.

### 6.2. Orden de arranque

Este es el orden que debe respetar el `docker-compose.yml` mediante healthchecks y
`depends_on`:

| Etapa | Qué se levanta | Puerto | Espera a | Por qué |
|---|---|---|---|---|
| 0 | Volúmenes y red | — | — | Persistencia y aislamiento |
| 1 | `postgres` | 5432 | — | Todo depende de ella |
| 2 | `keycloak` | 8180 | Postgres `healthy` | Necesita `keycloak_db` |
| 3 | `localstack` | 4566 | — | S3/SQS/EventBridge |
| 4 | `localstack-init` (one-shot) | — | LocalStack `healthy` | Crea bucket, colas, DLQ, bus y reglas |
| 5 | `prometheus` | 9090 | — | Métricas |
| 6 | `grafana` | 3000 | Prometheus + Postgres | Datasources |
| 7 | Servicios core | 8101-8112 | Postgres, Keycloak, init | APIs |
| 8 | Lambdas | — | LocalStack + init | Ingesta, marts, expiración |
| 9 | `nginx` / edge | 8000 | Servicios `healthy` | Punto único de entrada |
| 10 | `seed` (one-shot) | — | Servicios `healthy` | Zonas, flota y usuarios demo |

#### Por qué este orden

- **Postgres primero:** Keycloak y todos los servicios la necesitan. Si se levanta
  Keycloak antes, falla al conectar.
- **Keycloak antes que los servicios:** los servicios descargan el JWKS al arrancar. Si
  Keycloak no está, algunos fallan al validar el primer token.
- **LocalStack y su init antes de los servicios:** los servicios publican eventos al
  arrancar y las Lambdas necesitan bucket y colas creados.
- **Prometheus antes que Grafana:** Grafana aprovisiona el datasource al iniciar.
- **Servicios core en orden de dependencia:** `zones` e `identity` no dependen de nadie;
  `availability` necesita inventario; `reservations` y `rides` necesitan disponibilidad.
- **Seed al final:** requiere que las APIs estén listas.

#### Healthchecks

| Servicio | Comando |
|---|---|
| Postgres | `pg_isready -U urbanmove` |
| Keycloak | `curl -f http://localhost:9000/health/ready` |
| LocalStack | `curl -f http://localhost:4566/_localstack/health` |
| Prometheus | `wget -qO- http://localhost:9090/-/healthy` |
| Grafana | `curl -f http://localhost:3000/api/health` |
| Spring Boot | `curl -f http://localhost:810X/actuator/health/readiness` |

#### Regla de diseño

El orden es una comodidad, no una garantía. Un servicio debe arrancar aunque Keycloak o
SQS estén caídos: conexión perezosa, reintentos y readiness en falso. Si un servicio
necesita que todo esté arriba para no romperse, hay un acoplamiento escondido.

### 6.3. Perfiles de Compose

Con 13 JVM no se levanta todo cada día. Perfiles previstos:

| Perfil | Contenido | Uso |
|---|---|---|
| `infra` | Postgres, Keycloak, LocalStack, init, Prometheus, Grafana | Día a día |
| `core` | 8 primeros servicios | Integración temprana |
| `analytics` | analytics, marts, Grafana | Fase 3 |
| `all` | Todo | Demo y CI de integración |

```bash
docker compose --profile infra up -d
docker compose --profile core up -d
docker compose --profile all up -d
```

### 6.4. Comandos habituales

```bash
# Levantar infraestructura
docker compose --profile infra up -d

# Estado y salud
docker compose ps
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"

# Logs de un servicio
docker compose logs -f keycloak

# Reiniciar uno solo
docker compose restart keycloak

# Parar sin borrar datos
docker compose down

# Parar y borrar volúmenes (entorno limpio)
docker compose down -v

# Ejecutar el seed
docker compose run --rm seed

# Abrir psql en la base de reservas
docker compose exec postgres psql -U urbanmove -d reservations_db
```

### 6.5. Puertos

| Puerto | Servicio |
|---|---|
| 5432 | PostgreSQL |
| 8180 | Keycloak |
| 4566 | LocalStack |
| 3000 | Grafana |
| 9090 | Prometheus |
| 8000 | Nginx / edge |
| 8101-8113 | Microservicios |

Keycloak se mueve a 8180 a propósito: el 8080 suele estar ocupado por otras
herramientas y por el propio servicio de identidad.

---

## 7. Recomendación de TDD

### 7.1. Decisión

**Sí a TDD en el dominio y en los contratos. No a TDD ceremonial en toda la
infraestructura.**

Con tres personas y dos meses, TDD estricto en los 13 servicios frena más de lo que
protege. Pero hay zonas donde un bug cuesta horas de demo: las reglas de reserva, la
máquina de estados, la idempotencia y los agregados. Ahí TDD es obligatorio.

El detalle de la pirámide, Testcontainers, LocalStack, contratos y cobertura está en
[TESTING.md](TESTING.md).

### 7.2. Qué se escribe test-first

| Zona | Por qué |
|---|---|
| Contratos OpenAPI y JSON Schema | Ambos lados se generan y validan contra el mismo contrato |
| Máquina de estados de `availability` | 9 estados y transiciones; un test por transición |
| Reglas de reserva | Doble reserva, expiración, cancelación |
| Idempotencia | Procesar dos veces el mismo `eventId` da el mismo resultado |
| Regla de recolocación | Umbral de déficit y exceso |
| Agregados de marts | `count()` y reconstrucción desde eventos |
| Cálculo de coste y duración | Fácil de romper y de testear |

### 7.3. Qué no hace falta test-first

- Configuración de Spring, `application.yml`, Docker y Flyway.
- Mapeos DTO ↔ dominio triviales.
- Controllers que solo delegan.
- Getters y records sin lógica.

### 7.4. Pirámide

| Nivel | Herramienta | Cantidad | Momento |
|---|---|---|---|
| Contrato | JSON Schema + OpenAPI | 1 por evento/API | Antes |
| Dominio | JUnit 5 + AssertJ | Muchos | Test-first |
| Adaptadores | Testcontainers | Por repositorio y consumidor | Después, obligatorio |
| Slice web | `@WebMvcTest` | 1-2 por controller | Después |
| E2E | Postman/Newman o REST Assured | 5 flujos | Al cerrar fase |

### 7.5. Ejemplo

```java
@Test
void no_permite_dos_reservas_activas_para_el_mismo_vehiculo() {
    var vehicle = VehicleId.of("bici-42");
    Reservation.create(UserId.of("u1"), vehicle, clock);

    assertThatThrownBy(() ->
        Reservation.create(UserId.of("u2"), vehicle, clock)
    ).isInstanceOf(VehicleAlreadyReservedException.class);
}
```

El test se escribe antes que `Reservation`. Es donde TDD paga.

### 7.6. Reglas

- Dominio sin Spring: tests rápidos.
- Testcontainers con `withReuse(true)` y `@ServiceConnection`.
- Nada de H2.
- Nada de `Thread.sleep()`; Awaitility.
- Cobertura: 70-80 % en dominio, 50 % global.
- Un test de ArchUnit por servicio.
- No se mergea código sin tests ni contratos actualizados.

### 7.7. Flujo por caso de uso

```text
1. Actualizar el contrato (OpenAPI / JSON Schema)
2. Escribir el test de dominio que falla
3. Implementar el mínimo para que pase
4. Refactorizar
5. Escribir el test de adaptador con Testcontainers
6. Añadir el test de arquitectura
7. Comprobar en local el flujo completo
```

---

## 8. Orden de implementación

| Paso | Servicio | Qué valida |
|---|---|---|
| 1 | `zones` | Plantilla hexagonal, Flyway, OpenAPI, Testcontainers, Compose, CI |
| 2 | `identity` + Keycloak | JWT y roles |
| 3 | `inventory` + `availability` | Máquina de estados |
| 4 | `reservations` | Invariantes y doble reserva |
| 5 | `rides` + `history` + outbox | Primer flujo completo con eventos |
| 6 | `telemetry`, `incidents`, `maintenance` | Segundo flujo |
| 7 | `analytics`, marts, `relocation` | Capa analítica |
| 8 | Edge, Grafana, demo | Cierre |

---

## 9. Convenciones del equipo

| Aspecto | Regla |
|---|---|
| Ramas | `feat/<tema>`, `fix/<tema>`, `docs/<tema>` |
| Commits | `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:` |
| PR | Descripción, issue, evidencia y checklist |
| `main` | Protegida; PR obligatorio y CI verde |
| Secretos | Nunca en el repo; `.env` ignorado |
| Versiones | Fijadas; actualizar con un commit específico |
| Servicios | Un servicio no lee la base de otro |
| Tests | Sin H2, sin `Thread.sleep()`, sin tests vacíos |
| Código | Nomenclatura, legibilidad y arquitectura según [CODE-STANDARDS.md](CODE-STANDARDS.md) |

### 9.1. Definición de "hecho" por servicio

- [ ] Contrato OpenAPI y eventos en `contracts/`.
- [ ] Dominio con tests unitarios.
- [ ] Adaptadores con Testcontainers.
- [ ] Migraciones Flyway.
- [ ] `actuator/health` y métricas.
- [ ] `README` del servicio con cómo arrancarlo.
- [ ] Pasa `./mvnw verify`.
- [ ] CI verde en el PR.

---

## 10. Checklist de incorporación

Cada integrante, al empezar:

- [ ] Clonar el repositorio y configurar `user.name` y `user.email`.
- [ ] Instalar JDK 21, Docker Desktop, IntelliJ y GitHub CLI.
- [ ] Ejecutar `gh auth login`.
- [ ] Comprobar `java -version`, `docker version` y `docker compose version`.
- [ ] Copiar `.env.example` a `.env` cuando exista.
- [ ] Levantar `docker compose --profile infra up -d`.
- [ ] Comprobar Keycloak en <http://localhost:8180>, Grafana en
      <http://localhost:3000> y LocalStack en <http://localhost:4566/_localstack/health>.
- [ ] Ejecutar `./mvnw verify` cuando exista el POM padre.
- [ ] Leer `docs/PLAN.md` y `docs/DATA-ARCHITECTURE.md`.

---

## 11. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `docker` no responde | Docker Desktop parado | Abrirlo y esperar al motor |
| Keycloak reinicia en bucle | No encuentra Postgres | Revisar `depends_on` y `keycloak_db` |
| Servicio no valida JWT | Keycloak aún no está listo | Reintentos y healthcheck |
| LocalStack pierde datos | Sin volumen persistente | Añadir volumen a `/var/lib/localstack` |
| Testcontainers falla en Windows | Docker o WSL2 mal configurados | Comprobar Docker Desktop y reiniciar |
| Puerto ocupado | Otro proceso en 8080/5432 | Cambiar el mapeo (Keycloak en 8180) |
| Scripts con CRLF | Git en Windows | `core.autocrlf input` y `.gitattributes` |
| Se queda sin RAM | 13 servicios + infra | Usar perfiles y `-Xmx256m` |
| Maven descarga todo cada vez | Sin caché | Configurar caché de Maven en CI y local |

---

## 12. Referencias

- [PLAN.md](PLAN.md): plan maestro y fases.
- [ARCHITECTURE.md](ARCHITECTURE.md): arquitectura y despliegue.
- [DATA-ARCHITECTURE.md](DATA-ARCHITECTURE.md): almacenamiento, eventos e inmutabilidad.
- [CODE-STANDARDS.md](CODE-STANDARDS.md): nomenclatura, legibilidad, SOLID e inmutabilidad.
- [TESTING.md](TESTING.md): pirámide, Testcontainers, LocalStack y contratos.
- [CONTAINERS.md](CONTAINERS.md): imágenes, JVM, probes y Compose.
- [EVENTS.md](EVENTS.md): catálogo de eventos.
- [CI-CD.md](CI-CD.md): pipelines y quality gates.
