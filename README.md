# BBH DevSecOps Management Portal

A web portal to onboard products to DevSecOps and to watch their pipelines. Its header has two menus: **Beadle**,
where new features land, and **DevSecOps Management**, with the four pages below.

- **Product Onboarding** (Beadle): a wizard for product managers who do not know DevSecOps. Five steps, each with at
  most three choices: pick the Static scan, Security or Full pipeline, then a new product or one already in the
  portal, then its services (name, AppScan application, Gradle or Maven, virtual machines or OpenShift), check and
  save. Every service gets a pipeline of the chosen type with its own key, and the last step lists what to do next
  in order, with the Jenkinsfile of each service ready to copy. Everything else comes from BBH defaults and the
  Global Settings, and can be fine-tuned in Product Management.
- **DevSecOps Product Management**: add a product with all of its services and every setting the DevSecOps library
  ([DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)) reads from `config.yaml` today. Every new service
  gets a full pipeline with its own key; keys can be invalidated, regenerated and linked to a Jenkins job, and each
  service names the Bitbucket repository where DSOEnhanced raises its GoldenFix pull requests. Products live in
  departments, and each department shows how many DevSecOps pipelines it has for how many products. The five BBH
  departments (AI Lab, Capital Partners, Corporate Technology, Custody and Fund Services) come with the database; a
  product saved before departments existed shows as "Not in a department" until it is edited, which means choosing one.
- **DevSecOps Pipeline Monitoring**: every product with the status of its pipelines, and per pipeline its DORA
  metrics, daily activity, latest runs, the Jenkins job and your DSOEnhanced Grafana dashboard, all read from the
  InfluxDB the pipelines write to.
- **DevSecOps Change Evidence**: a read-only view of a product for ServiceNow change requests: per pipeline the
  unit, smoke, regression and performance tests, the SAST, DAST, SonarQube and Nexus IQ results, the release gate and
  the Jenkins build that produced them, with its artifact version and the portal configuration it ran with.
- **DevSecOps Global Settings**: the settings every pipeline shares and no service can override. They replace the
  library's `defaults.yaml`.

No `config.yaml` remains in the product repositories. The portal-integrated library reads each pipeline's
configuration from the portal by its key, so a service needs only the generic Jenkinsfile and its pipeline key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')
```

A run that builds several services of one product passes the keys of their pipelines of that type, the primary service
first; the product page offers this Jenkinsfile in the menu of a pipeline. Extended pipelines join only when they name
the same security pipeline, since the run reads the security run state of the primary's only:

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

Open http://localhost:8080, or http://localhost:8080/beadle/onboarding for the onboarding wizard. Without a profile
the portal runs with `local`: an embedded H2 database in Oracle mode in `./data`, with demo products on the first
start. Every feature works on it. `./gradlew :backend:bootRun` does the same without building the jar (the database
then lives in `backend/data`).

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

### How the library reads its configuration

The library reads each pipeline's configuration from this portal over HTTPS, with one
`GET /api/dso/config/<key>?format=json` per key, sent with `curl` from a Jenkins agent before the pipeline starts.
The portal renders the configuration from its database at that moment and records the key's last use. Jenkins needs
only the portal's address, the global environment variable `DSO_PORTAL_URL` (for example
`https://dso-portal.apps.bbh.com`); it holds no database account, no credentials and no driver, and only the portal
reaches its database.

The key is the only check. Whoever holds a key reads the configuration of that one pipeline, which names Jenkins
credentials IDs and no passwords, much as anyone who could read a product repository could read its `config.yaml`.
Keys are random UUIDs, the API lists none of them by this request, and an invalidated key is refused with 403 at once.
When the portal moves behind BBH SSO, `/api/dso/config/**` stays outside the sign-in.

A pipeline's metrics are matched by the InfluxDB tags the library writes: `project` (the service's metrics project
plus the pipeline type suffix: none for full, `security`, `extended`, `sast`) and `env`.

Several services may share one metrics project and env. Under a shared tag a `pipeline_run` belongs to the pipeline
whose Jenkins job (or a branch of it) recorded it in the `job` field; a pipeline without a Jenkins job shows no run,
and a run of one job building several services belongs to each of them. DORA metrics and the Grafana dashboard stay
per tag. Concurrent runs under one tag can still mix the points the library writes without a `module` tag.

### Change evidence

The Change Evidence page reads the points of a pipeline's latest run, per service (`module` tag):

- `test_execution` with `suite=unit` gives the unit test row (total, passed, failed, skipped, duration); the smoke,
  regression and performance rows come from the suites the remote test jobs record, and the status of the stage that
  ran a suite wins over its counts;
- `build_evidence` gives the artifact version, the SonarQube quality gate (`OK`, `WARN`, `ERROR`; `NONE` reads as not
  recorded), the SAST, DAST, Nexus IQ and SonarQube report links, and when the portal rendered the configuration the
  build read with its sha256 hint;
- `security_findings`, `policy_status`, `vulnerabilities`, `code_coverage`, `release_gate` and `stage_event` give the
  scans, coverage, release gate and stages as before.

A SonarQube policy status wins over the quality gate. A run without `build_evidence` (a library older than the
portal integration) keeps the links the portal builds: the HCL AppScan scans of the application, the SonarQube
dashboard of the project and the Nexus IQ server, and the Jenkins build pages. A point without a `module` tag (an
older library) counts for the service only when it is the run's only point of its kind; a point of another module
never does.

## Accepted differences from config.yaml

Moving the configuration into the portal changes these behaviours of the library on purpose:

1. Configuration lives in the portal, not in the repository: no per-branch, per-PR or per-commit configuration; a
   replay of an old build uses today's portal values; release and develop jobs of one service share one pipeline per
   type. The console, the report header and `build_evidence` record the key hint, `renderedAt` and the sha256 hint.
2. Failures before `pipeline {}` leave no report, `release-gate.json` or InfluxDB point; `build_duration` and the DORA
   duration include the bootstrap read.
3. The archived run-state file is `pipeline-config.yaml`, not `config.yaml`; the first extended run after cutover needs
   one security build made by the new library.
4. The extended pipeline uses its own key's projects plus the security run's run-time tags, not the security run's
   whole `config.yaml` copy.
5. Keys the portal does not model are gone from `getCFG()` (custom keys a team added to `config.yaml`);
   `tests.<suite>.defaults`, bare-string test jobs and `urls` are expanded by migration into jobs; `jobs[].auth` maps
   other than credentials IDs, OpenShift `appName`/`imageNamespace`/`cluster`/`credentialsId` (only the unused
   nexusDelivery API reads them) and the keys the library no longer reads are not modelled.
6. Selecting projects through `PROJECT_NAMES`/`PROJECT_NAME` without keys is gone; the keys decide.
7. Texts that named `config.yaml`/`defaults.yaml` now name the portal, also where they reach the report,
   `release-gate.json` and InfluxDB. A Jenkinsfile `securityPipeline` in a full, security or SAST pipeline is ignored,
   which shows the Nexus IQ and SonarQube summary rows it used to hide.
8. Nexus IQ report links appear for every service, because the IQ server URL is global (GoldenFix stays governed by
   its own per-service switch).
9. Every build depends on the portal at start; the agents of the `DSO_PORTAL_AGENT` label need HTTPS access to it.

## Preconditions before wider use

This change does not bring BBH single sign-on with roles, an audit trail of who changed what, or a history of the
rendered configuration; the owner decides on them later. Until they exist the portal must not be
reachable outside the test network, because a portal edit now steers every build and pipeline keys are bearer
secrets.

## REST API

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/departments` | departments by name, each with the number of its products, their services, their DevSecOps pipelines and the pipelines with an active key |
| `POST /api/departments`, `PUT`/`DELETE /api/departments/{id}` | add, rename or delete a department; `PUT` carries the `version` it was read at; a department that still has products is not deleted (409) |
| `GET /api/products?search=` | products with their department, service and pipeline counts; the search also matches the department name |
| `POST /api/products`, `GET`/`PUT`/`DELETE /api/products/{id}` | a product in its department (`departmentId`, required on every save) with its complete list of services; `PUT` carries the `version` it was read at; every service the save creates gets a full pipeline with an active key, or with `?pipelineType=SAST\|SECURITY\|FULL` every service of the product without a pipeline of that type gets one |
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

- The portal has no sign-in yet; see the preconditions above.
- The change evidence of runs made by a library older than the portal integration has no unit test counts, artifact
  version, SonarQube quality gate, report links or configuration hint, so its links are built from the global settings
  and the Jenkins build number.
