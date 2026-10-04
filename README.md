# BBH DevSecOps Management Portal

A web portal to onboard products to DevSecOps and to watch their pipelines.

- **Product Management**: add a product with all of its services and the settings the DevSecOps library
  ([DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)) reads from `config.yaml`; give each service
  its own pipelines, each with a unique key that can be invalidated and reissued.
- **Pipeline Monitoring**: every product with the status of its pipelines, and per pipeline its DORA
  metrics, daily activity, latest runs and the Grafana panels, all read from the InfluxDB the pipelines
  write to.

The configuration lives in the portal's database. Once the DevSecOps library reads it from the portal, a
service needs only the generic Jenkinsfile and its pipeline key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')
```

DSOEnhanced does not read from the portal yet; that integration is the next step.

## Architecture

```
frontend/   Angular 22 + Angular Material, standalone components and signals
backend/    Spring Boot 4.1, Java 21, Spring Data JPA, Flyway; serves the API and the built frontend
grafana/    the DORA dashboard and the provisioning of the local Grafana
tools/      seed-influx-demo.py writes demo pipeline metrics to a local InfluxDB
```

The domain, in `backend/src/main/java/com/bbh/dso/portal`:

| Package      | Holds                                                                                          |
|--------------|-------------------------------------------------------------------------------------------------|
| `catalog`    | `Product` and its `ServiceDefinition`s; each section of a service's `config.yaml` entry is an embeddable value object (`BuildSettings`, `DeploymentSettings`, `AppScanSettings`, ...) |
| `pipeline`   | `Pipeline` per service and type (full, security, extended, SAST) and its `PipelineKey` history; key changes run under a row lock, so a pipeline never has two active keys |
| `dsoconfig`  | renders the `config.yaml` a pipeline gets for its key, with the BBH-wide defaults filled in      |
| `monitoring` | reads the runs from InfluxDB (Flux over HTTP), computes the DORA metrics and builds the Grafana panel links |
| `demo`       | sample products and pipelines for local runs                                                   |
| `web`        | serves the Angular app for its page paths                                                      |

Secrets never reach the portal: services name Jenkins credentials IDs, and the generated configuration
carries only those IDs.

## Running it

The portal is one jar. Build it with the frontend included (needs Node.js 22.22.3 or newer and npm):

```bash
cd backend
mvn -Pfrontend package
```

### With Java alone

The `local` profile uses an embedded H2 database in Oracle mode, stored in `./data`, and creates demo
products on its first start:

```bash
java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

Open http://localhost:8080.

### With MySQL or Oracle

`docker-compose.yml` starts local databases with the credentials the profiles expect:

```bash
docker compose up -d mysql
java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql

docker compose up -d oracle      # Oracle Free, the production database; default profile
java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar
```

Flyway creates the schema on each database (`db/migration/oracle`, `db/migration/mysql`).

### With monitoring

Start InfluxDB and Grafana, point the portal at them and write demo metrics for every pipeline:

```bash
docker compose up -d influxdb grafana
INFLUX_URL=http://localhost:8086 INFLUX_TOKEN=dso-local-token GRAFANA_URL=http://localhost:3000 \
  java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
python3 tools/seed-influx-demo.py --portal http://localhost:8080
```

Grafana must allow embedding (`GF_SECURITY_ALLOW_EMBEDDING=true`) and let portal users see the dashboard
(anonymous viewer locally, single sign-on in BBH). The dashboard to import is
`grafana/dashboards/dso-portal-dora.json`.

### Frontend development

```bash
cd frontend
npm ci
npm start          # http://localhost:4200, /api is proxied to the backend on port 8080
```

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Oracle on `localhost:1521/FREEPDB1` | database of the active profile |
| `PORT` | `8080` | HTTP port |
| `INFLUX_URL`, `INFLUX_TOKEN` | empty | InfluxDB the pipelines write to; empty switches monitoring off |
| `INFLUX_ORG`, `INFLUX_BUCKET` | `DevSecOps`, `DORA-metrics` | where the metrics are |
| `GRAFANA_URL`, `GRAFANA_ORG_ID` | empty, `1` | Grafana whose panels are embedded; empty hides them |
| `DSO_ASOC_URL`, `DSO_SONAR_URL`, `DSO_NEXUS_IQ_URL`, `DSO_NEXUS_IQ_CREDENTIALS_ID`, `DSO_INFLUX_WRITE_URL`, `DSO_INFLUX_CREDENTIALS_ID` | BBH tools | defaults written into every generated `config.yaml` |
| `dso.demo-data` | `true` with `local` and `mysql` | create sample products when the database has none |

The Grafana dashboard UID, slug, theme and panels are set under `dso.grafana` in `application.yml`.

## DevSecOps integration

| Request | Answer |
|---------|--------|
| `GET /api/dso/config/{key}` | 200 with the pipeline's `config.yaml` (`?format=json` for JSON) and records the key's last use; 403 with the reason once the key is invalidated; 404 for a key never issued |
| `GET /api/pipelines/{id}/config` | the same configuration for the portal UI, without recording a use |
| `GET /api/products/{id}/config` | every service of a product as one `config.yaml` |

A pipeline's metrics are matched by the InfluxDB tags the library writes: `project` (the service's metrics
project tag plus the pipeline type suffix: none for full, `security`, `extended`, `sast`) and `env`.

## REST API

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/products?search=` | products with service and pipeline counts |
| `POST /api/products`, `GET`/`PUT`/`DELETE /api/products/{id}` | a product with its complete list of services; `PUT` carries the `version` it was read at |
| `GET /api/products/{id}/pipelines` | each service of a product with its pipelines |
| `POST /api/services/{id}/pipelines` | add a pipeline; it starts with an active key |
| `GET`/`PUT`/`DELETE /api/pipelines/{id}` | a pipeline with its key history; the type cannot change |
| `POST /api/pipelines/{id}/keys` | issue a new key, invalidating the active one |
| `POST /api/pipelines/{id}/keys/revoke` | invalidate the active key, with a reason |
| `GET /api/monitoring/status`, `/products`, `/products/{id}`, `/pipelines/{id}?range=30d` | monitoring data |

Errors are RFC 9457 problem details; validation errors list the failing fields, for example
`services[2].build.javaPath`.

## Tests

```bash
cd backend
mvn clean verify
```

runs four Spock suites:

| Suite | Where | What |
|-------|-------|------|
| unit | `src/test/groovy/**/*Spec` | domain, services and controllers; JaCoCo fails the build below 90% line coverage (`-Dcoverage.minimum=`) |
| regression | `regression/` | the API end to end on H2 in Oracle mode, including the pinned `config.yaml` contract (`-Dregression.updateExpected=true` rewrites the expected files) |
| smoke | `smoke/` | starts the portal with demo data and checks health, the API and the UI; `-Dsmoke.baseUrl=https://...` checks a deployed portal instead |
| performance | `performance/` | p95 latencies of the main calls on 25 products × 16 services; `-Dperformance.factor=2` scales the limits; report in `target/performance-report.md` |

Skip a suite with `-Dskip.regression=true`, `-Dskip.smoke=true` or `-Dskip.performance=true`. Use `clean`,
since failsafe keeps summaries between runs.

Frontend unit tests (Vitest):

```bash
cd frontend
npm test -- --watch=false
```

## Known gaps

- The portal has no sign-in yet. Pipeline keys are bearer secrets, so put it behind BBH single sign-on
  before it is used beyond a local machine.
- DSOEnhanced does not call `/api/dso/config/{key}` yet.
