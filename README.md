# BBH DevSecOps Management Portal

A web portal to onboard products to DevSecOps and to watch their pipelines.

- **DevSecOps Product Management**: add a product with all of its services and every setting the
  DevSecOps library ([DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)) reads from `config.yaml`
  today; give each service its own pipelines, each with a unique key that can be invalidated and reissued,
  and link each pipeline to its Jenkins job.
- **DevSecOps Pipeline Monitoring**: every product with the status of its pipelines, and per pipeline its
  DORA metrics, daily activity, latest runs, the Jenkins job and the Grafana panels, all read from the
  InfluxDB the pipelines write to.
- **DevSecOps Change Evidence**: a read-only view of a product for ServiceNow change requests. Per
  pipeline, on one screen: product and service, unit test coverage, smoke, regression and performance
  results, SAST, DAST, SonarQube and Nexus IQ results with links to their reports, the release gate, and
  the Jenkins build that produced them.
- **DevSecOps Global Settings**: the settings every pipeline shares and no service can override: the BBH
  tool servers, the severity limits of each scanner, scan timeouts, the release gate, the deployment
  defaults and the GoldenFix policy. They replace the library's `defaults.yaml`.

No `config.yaml` remains in the product repositories: everything lives in the portal's database. Once the
DevSecOps library reads it from the portal, a service needs only the generic Jenkinsfile and its pipeline
key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')
```

DSOEnhanced does not read from the portal yet; that integration is the next step.

## Architecture

```
frontend/   Angular 22 + Angular Material, standalone components and signals
backend/    Spring Boot 4.1, Java 21, Spring Data JPA, Liquibase; serves the API and the built frontend
grafana/    the DORA dashboard and the provisioning of the local Grafana
tools/      seed-influx-demo.py writes demo pipeline metrics to a local InfluxDB
```

The domain, in `backend/src/main/java/com/bbh/itss/dso/portal`:

| Package      | Holds                                                                                          |
|--------------|-------------------------------------------------------------------------------------------------|
| `catalog`    | `Product` and its `ServiceDefinition`s; each section of a service's `config.yaml` entry is an embeddable value object (`BuildSettings`, `DeploymentSettings`, `AppScanSettings`, ...) |
| `pipeline`   | `Pipeline` per service and type (full, security, extended, SAST) and its `PipelineKey` history; a service starts with a full pipeline on the `linux-agent` label when it is created; key changes run under a row lock, so a pipeline never has two active keys |
| `settings`   | the global settings, a single row with its severity limits, edited in the Global Settings tab   |
| `dsoconfig`  | renders the configuration a pipeline gets for its key and publishes it to `DSO_PIPELINE_CONFIG` whenever a product, a pipeline or the global settings change |
| `monitoring` | reads the runs from InfluxDB (Flux over HTTP), computes the DORA metrics and builds the Grafana panel links |
| `evidence`   | reads what the latest run of each pipeline recorded in InfluxDB and builds the links to its reports |
| `demo`       | sample products and pipelines for local runs                                                   |
| `web`        | serves the Angular app for its page paths                                                      |

Secrets never reach the portal: services name Jenkins credentials IDs, and the generated configuration
carries only those IDs.

## Running it

The portal is one jar. Build it with the frontend included (needs Java 21, Maven and Node.js 22.22.3 or
newer with npm):

```bash
cd backend
mvn -Pfrontend package
java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar
```

Open http://localhost:8080. Without a profile the portal runs with `local`: an embedded H2 database in
Oracle mode, stored in `./data`, with demo products on its first start. Every feature works on it. After
upgrading the portal, delete `./data` if it was created by an older version.

### Profiles

| Profile | Where | Database |
|---------|-------|----------|
| `local` (default) | a developer's machine | embedded H2 in `./data`, demo data |
| `rd` | the lower test environment | Oracle |
| `qc` | UAT | Oracle |
| `prod` | production | Oracle |

`rd`, `qc` and `prod` read the Oracle address and account from `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
and do not start without them:

```bash
DB_URL=jdbc:oracle:thin:@//<host>:1521/<service> DB_USERNAME=DSO_PORTAL DB_PASSWORD=... \
  java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=qc
```

They differ in the connection pool size (5, 10 and 20, or `DB_POOL_SIZE`), in the health details
`/actuator/health` shows (all in `rd` and `qc`, none in `prod`) and in logging (`DEBUG` for the portal in
`rd`). To try Oracle locally, `docker compose up -d oracle` starts Oracle Free with the account
`DSO_PORTAL`/`dso_portal` on `localhost:1521/FREEPDB1`.

Liquibase creates and updates the schema at start-up from `db/changelog` (`db.changelog-master.yaml`),
on Oracle and on H2.

### With monitoring

Start InfluxDB and Grafana, point the portal at them and write demo metrics for every pipeline:

```bash
docker compose up -d influxdb grafana
INFLUX_URL=http://localhost:8086 INFLUX_TOKEN=dso-local-token GRAFANA_URL=http://localhost:3000 \
  java -jar target/dso-portal-backend-0.1.0-SNAPSHOT.jar
python3 tools/seed-influx-demo.py --portal http://localhost:8080 --days 30
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
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | H2 in `./data` with `local`; required with `rd`, `qc`, `prod` | database of the active profile |
| `DB_POOL_SIZE` | 5, 10, 20 in `rd`, `qc`, `prod` | Oracle connection pool size |
| `PORT` | `8080` | HTTP port |
| `INFLUX_URL`, `INFLUX_TOKEN` | empty | InfluxDB the pipelines write to; empty switches monitoring off |
| `INFLUX_ORG`, `INFLUX_BUCKET` | `DevSecOps`, `DORA-metrics` | where the metrics are |
| `GRAFANA_URL`, `GRAFANA_ORG_ID` | empty, `1` | Grafana whose panels are embedded; empty hides them |
| `GRAFANA_DATASOURCE_UID` | `dso-influxdb` | InfluxDB datasource the embedded panels read; the links also pass `INFLUX_BUCKET` |
| `DSO_DEMO_DATA` | `true` with `local` | create sample products when the database has none |

The BBH tool servers and every other setting shared by all pipelines are not configured here: they are
stored in the database and edited in the DevSecOps Global Settings tab. The Jenkins URL set there turns a
pipeline's job path, such as `DevSecOps/TARA/app-full`, into a link.

The Grafana dashboard UID, slug, theme and panels are set under `dso.grafana` in `application.yml`.

## DevSecOps integration

| Request | Answer |
|---------|--------|
| `GET /api/dso/config/{key}` | 200 with the pipeline's `config.yaml` (`?format=json` for JSON) and records the key's last use; 403 with the reason once the key is invalidated; 404 for a key never issued |
| `GET /api/pipelines/{id}/config` | the same configuration for the portal UI, without recording a use |
| `GET /api/products/{id}/config` | every service of a product as one `config.yaml` |
| `GET /api/settings/config` | the part of every configuration that comes from the global settings |

The library can also read its configuration straight from the database, with an account that may only
read the view `DSO_LIBRARY_CONFIG_V` (`PIPELINE_KEY`, `KEY_STATUS`, `REVOKE_REASON`, `CONFIG_JSON`,
`RENDERED_AT`). `CONFIG_JSON` is empty once the key is invalidated. On Oracle:

```sql
CREATE USER DSO_LIBRARY IDENTIFIED BY "...";
GRANT CREATE SESSION TO DSO_LIBRARY;
GRANT SELECT ON DSO_PORTAL.DSO_LIBRARY_CONFIG_V TO DSO_LIBRARY;
```

The library then needs only the database address and that account.

A pipeline's metrics are matched by the InfluxDB tags the library writes: `project` (the service's metrics
project tag plus the pipeline type suffix: none for full, `security`, `extended`, `sast`) and `env`.

## REST API

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/products?search=` | products with service and pipeline counts |
| `POST /api/products`, `GET`/`PUT`/`DELETE /api/products/{id}` | a product with its complete list of services; `PUT` carries the `version` it was read at; every service the save creates gets a full pipeline with an active key |
| `GET /api/products/{id}/pipelines` | each service of a product with its pipelines |
| `POST /api/services/{id}/pipelines` | add a pipeline; it starts with an active key |
| `GET`/`PUT`/`DELETE /api/pipelines/{id}` | a pipeline with its key history; the type cannot change |
| `POST /api/pipelines/{id}/keys` | issue a new key, invalidating the active one; on a pipeline whose key was invalidated this is the Regenerate button, and the invalidated keys stay refused |
| `POST /api/pipelines/{id}/keys/revoke` | invalidate the active key, with a reason |
| `GET /api/monitoring/status`, `/products`, `/products/{id}`, `/pipelines/{id}?range=30d` | monitoring data |
| `GET /api/evidence/products/{id}` | the change evidence of a product's pipelines |
| `GET`/`PUT /api/settings` | the global settings; `PUT` carries the `version` it was read at |

Errors are RFC 9457 problem details, including the ones the framework raises for an unknown path, a method
or content type an endpoint does not serve and a body that cannot be read; validation errors list the
failing fields, for example `services[2].build.javaPath`. A problem detail never carries a class, package
or method name.

## Tests

```bash
cd backend
mvn clean verify
```

runs four Spock suites:

| Suite | Where | What |
|-------|-------|------|
| unit | `src/test/groovy/**/*Spec` | domain, services and controllers; JaCoCo fails the build below 90% line coverage (`-Dcoverage.minimum=`) |
| regression | `regression/` | the API end to end on H2 in Oracle mode, including the pinned configuration contract, the database view, the global settings and the change evidence (`-Dregression.updateExpected=true` rewrites the expected files) |
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
- DSOEnhanced does not read its configuration from the portal yet.
- The change evidence shows what the library records in InfluxDB today. It records no unit test counts,
  no artifact version and no SonarQube quality gate, and no links of its own, so the links are built from
  the portal's settings and the Jenkins build number.
