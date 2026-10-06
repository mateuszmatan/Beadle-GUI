# BBH DevSecOps Management Portal

A web portal to onboard products to DevSecOps and to watch their pipelines.

- **DevSecOps Product Management**: add a product with all of its services and every setting the DevSecOps library
  ([DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)) reads from `config.yaml` today. Every new service
  gets a full pipeline with its own key; keys can be invalidated, regenerated and linked to a Jenkins job, and each
  service names the Bitbucket repository where DSOEnhanced raises its GoldenFix pull requests.
- **DevSecOps Pipeline Monitoring**: every product with the status of its pipelines, and per pipeline its DORA
  metrics, daily activity, latest runs, the Jenkins job and your DSOEnhanced Grafana dashboard, all read from the
  InfluxDB the pipelines write to.
- **DevSecOps Change Evidence**: a read-only view of a product for ServiceNow change requests: per pipeline the
  test, SAST, DAST, SonarQube and Nexus IQ results, the release gate and the Jenkins build that produced them.
- **DevSecOps Global Settings**: the settings every pipeline shares and no service can override. They replace the
  library's `defaults.yaml`.

No `config.yaml` remains in the product repositories. Once the library reads from the portal, a service needs only
the generic Jenkinsfile and its pipeline key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')
```

A run that builds several services of one product passes the keys of their pipelines of that type, the primary service
first; the product page offers this Jenkinsfile in the menu of a pipeline:

```groovy
devSecOpsPipeline(pipelineKeys: ['6f1c2d3e-0000-4abc-9def-123456789abc', 'a1b2c3d4-0000-4abc-9def-123456789abc'])
```

During the cutover, pin the portal-integrated library version (for example `DevSecOpsJenkinsLibrary@DSOwithMgmtPortal`)
in the Global Settings' shared library (`platform.jenkinsLibrary`, the name the generated Jenkinsfiles load) and in
the `@Library` line of every migrated job, until every job carries a key.

## Running it locally

Needs Java 21. The Gradle wrapper downloads Gradle, and the build downloads its own Node.js for the GUI.

```bash
./gradlew :backend:bootJar
java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar
```

Open http://localhost:8080. Without a profile the portal runs with `local`: an embedded H2 database in Oracle mode
in `./data`, with demo products on the first start. Every feature works on it. `./gradlew :backend:bootRun` does the
same without building the jar (the database then lives in `backend/data`).

To see metrics, point the portal at your InfluxDB and Grafana:

```bash
INFLUX_URL=https://influx.example.com INFLUX_TOKEN=... \
GRAFANA_DASHBOARD_URL=https://grafana.example.com/d/adzfc54123/devsecops-pipeline-long \
GRAFANA_SECURITY_DASHBOARD_URL=https://grafana.example.com/d/ad2trcm/devsecops-pipeline-security \
  java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar
```

The dashboards are the ones DSOEnhanced ships (`grafana/` in that repository); the portal opens them with the
service's `project` variable and the selected time range. Grafana must let portal users view them.

## Profiles and configuration

| Profile | Where | Database |
|---------|-------|----------|
| `local` (default) | a developer's machine | embedded H2 in `./data`, demo data |
| `rd` | the lower test environment | Oracle |
| `qc` | UAT | Oracle |
| `prod` | production | Oracle |

| Variable | Default | Meaning |
|----------|---------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | H2 with `local`; required with `rd`, `qc`, `prod` | database of the active profile |
| `DB_POOL_SIZE` | 5, 10, 20 in `rd`, `qc`, `prod` | Oracle connection pool size |
| `PORT` | `8080` | HTTP port |
| `INFLUX_URL`, `INFLUX_TOKEN` | empty | your InfluxDB; empty switches the monitoring off |
| `INFLUX_ORG`, `INFLUX_BUCKET` | `DevSecOps`, `DORA-metrics` | where DSOEnhanced writes its metrics |
| `GRAFANA_DASHBOARD_URL` | empty | link to the DSOEnhanced pipeline dashboard; empty hides the dashboard |
| `GRAFANA_SECURITY_DASHBOARD_URL` | empty | link to the security dashboard for `SECURITY` and `SAST` pipelines; empty uses the pipeline dashboard |
| `DSO_DEMO_DATA` | `true` with `local` | create sample products when the database has none |

```bash
SPRING_PROFILES_ACTIVE=qc DB_URL=jdbc:oracle:thin:@//<host>:1521/<service> DB_USERNAME=DSO_PORTAL DB_PASSWORD=... \
  java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar
```

Liquibase creates and updates the schema at start-up, on Oracle and on H2. The BBH tool servers and everything else
pipelines share are stored in the database and edited in the Global Settings tab. Secrets never reach the portal:
services name Jenkins credentials IDs, and the generated configuration carries only those IDs.

## OpenShift

The portal ships as one container image (UBI 9 with Java 21, any non-root UID). `Dockerfile` and the Kustomize
manifests for `rd`, `qc` and `prod` are described in [deploy/openshift/README.md](deploy/openshift/README.md).

## Architecture

```
gui/        Angular 22 + Angular Material: views, shared components, API services
backend/    Spring Boot 4.1, Java 21, Spring Data JPA, Liquibase; serves the API and the built GUI
deploy/     the OpenShift manifests
```

The backend (`backend/src/main/java/com/bbh/itss/dso/portal`) is hexagonal:

| Package | Holds |
|---------|-------|
| `domain` | plain Java: products, services and their settings, pipelines and keys, the global settings, the rendered configuration, DORA metrics and change evidence; every business rule lives here |
| `application` | the use cases behind ports; `@UseCase` classes become transactional beans, without Spring in the code |
| `adapter.in` | Spring MVC controllers with the request and response records, and the start-up tasks |
| `adapter.out` | JPA entities and Spring Data repositories, the InfluxDB client and the Grafana links |
| `config` | the wiring of use cases and transactions |

ArchUnit tests keep the domain and the use cases free of Spring, JPA and Jackson, and keep the adapters apart.

## DevSecOps integration

| Request | Answer |
|---------|--------|
| `GET /api/dso/config/{key}` | 200 with the pipeline's `config.yaml` (`?format=json` for JSON), recording the key's last use; 403 with the reason once the key is invalidated; 404 for a key never issued |
| `GET /api/pipelines/{id}/config` | the same configuration for the portal UI, without recording a use |
| `GET /api/products/{id}/config` | every service of a product as one `config.yaml` |
| `GET /api/settings/config` | the part of every configuration that comes from the global settings |

Every key the library reads per service is a setting of the service. A service may name its own Nexus IQ server and
credentials, SonarQube server, InfluxDB write URL and credentials, and AppScan secret (a Secret text credentials ID);
left empty, the configuration carries the global setting or, for the AppScan secret, the product's. One Nexus IQ
application is written as `tools.nexusIq.application: <name>`; several are written as a map of applications, each
entry with its scan patterns, stage and the effective server and credentials.

### The library's database account

The library reads its configuration straight from the Oracle database, one key at a time, through the function
`DSO_PORTAL.DSO_LIBRARY_CONFIG(p_key)` that Liquibase creates (Oracle only; H2 has only the view below). The
function returns NULL for a key the portal never issued. Otherwise it returns one JSON object without JSON nulls:
`keyStatus` (`ACTIVE` or `REVOKED`), `revokeReason`, `renderedAt` (UTC, ISO-8601 with `Z`) and `config`, the published
configuration, present only while the key is active. The key is matched trimmed and in lower case. Reading through the
function does not touch the key's last use; only the REST endpoint records it.

A DBA creates the reader account once:

```bash
sqlplus <dba user>@//<host>:1521/<service> @deploy/db/dso-library-reader.sql
```

The script asks for the reader's password (or takes it from `DEFINE reader_password = ...`) and creates:

- the profile `DSO_LIBRARY_PROFILE`, which never locks the account after failed logins, so one wrong Jenkins
  credential cannot stop every pipeline; failed logins are audited instead (audit policy
  `DSO_LIBRARY_LOGON_FAILURES`), and the password life time follows the BBH default profile;
- the user `DSO_LIBRARY` with `CREATE SESSION` and `EXECUTE` on the function, nothing else.

Why a function and not a grant on the view: with `EXECUTE` alone the account cannot list keys, cannot read the view
or any table and cannot lock a row or a table. It learns only the document of a key it already holds. The script
assumes the schema `DSO_PORTAL`; for another schema change its `GRANT EXECUTE` line and set `DSO_PORTAL_DB_SCHEMA` in
Jenkins.

Rotate the reader's password together with the Jenkins credential that holds it (`dso-portal-db-reader`). Turn on
Oracle native network encryption (or TCPS) for the listener, and let only the Jenkins agents of the
`DSO_PORTAL_AGENT` label reach it, with a listener access control list (valid node checking).

The view `DSO_LIBRARY_CONFIG_V` (`PIPELINE_KEY`, `KEY_STATUS`, `REVOKE_REASON`, `CONFIG_JSON`, `RENDERED_AT`) stays
for the portal's own use; grant nothing on it.

A pipeline's metrics are matched by the InfluxDB tags the library writes: `project` (the service's metrics project
plus the pipeline type suffix: none for full, `security`, `extended`, `sast`) and `env`.

## REST API

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/products?search=` | products with service and pipeline counts |
| `POST /api/products`, `GET`/`PUT`/`DELETE /api/products/{id}` | a product with its complete list of services; `PUT` carries the `version` it was read at; every service the save creates gets a full pipeline with an active key |
| `GET /api/products/{id}/pipelines` | each service of a product with its pipelines |
| `POST /api/services/{id}/pipelines` | add a pipeline; it starts with an active key |
| `GET`/`PUT`/`DELETE /api/pipelines/{id}` | a pipeline with its key history |
| `POST /api/pipelines/{id}/keys` | issue a new key; on a pipeline whose key was invalidated this is Regenerate, and the old keys stay refused |
| `POST /api/pipelines/{id}/keys/revoke` | invalidate the active key, with a reason |
| `GET /api/monitoring/status`, `/products`, `/products/{id}`, `/pipelines/{id}?range=30d` | monitoring data |
| `GET /api/evidence/products/{id}` | the change evidence of a product's pipelines |
| `GET`/`PUT /api/settings` | the global settings; `PUT` carries the `version` it was read at |

Errors are RFC 9457 problem details; validation errors name the failing fields, for example
`services[2].build.javaPath`. Key values are only sent by the product management endpoints, and only for the active
key; everywhere else a key shows as its hint.

## Tests

```bash
./gradlew check
```

builds everything and runs every suite of both modules:

| Module | Suite | What |
|--------|-------|------|
| backend | unit (`src/test`) | domain rules, use cases, adapters and the architecture rules; JaCoCo fails below 60% line coverage (`-Pcoverage.minimum=`) |
| backend | regression (`src/regressionTest`) | the API end to end on H2 in Oracle mode, with the pinned configuration contract (`-Dregression.updateExpected=true` rewrites the expected files) |
| backend | smoke (`src/smokeTest`) | starts the portal and checks health, the API and the UI; `-Dsmoke.baseUrl=https://...` checks a deployed portal |
| backend | performance (`src/performanceTest`) | p95 latencies of the main calls on 25 products x 16 services |
| gui | unit (Vitest) | components and form models; fails below 60% of lines and statements |
| gui | smoke, regression, performance | Spock and Playwright in Chromium against a stub API: every page, the user journeys with the requests they send, and page timings on a large catalogue |

`-Dperformance.factor=2` relaxes the performance limits on a slow machine. Run one suite with, for example,
`./gradlew :backend:regressionTest` or `./gradlew :gui:smokeTest`; [gui/README.md](gui/README.md) covers the GUI in
detail, including its development server.

## Known gaps

- The portal has no sign-in yet. Pipeline keys are bearer secrets, so put it behind BBH single sign-on before it is
  used beyond a local machine.
- DSOEnhanced does not read its configuration from the portal yet.
- The change evidence shows what the library records in InfluxDB today. It records no unit test counts, artifact
  version, SonarQube quality gate or report links, so links are built from the global settings and the Jenkins
  build number.
