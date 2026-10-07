# BBH DevSecOps Management Portal

A web portal to onboard products to DevSecOps and to watch their pipelines. Its header has two menus:
**DevSecOps Management** and **Beadle**, where new features land.

DevSecOps Management:

- **Self-service**: a step-by-step wizard for app owners who do not know DevSecOps. It sets up a new product or
  changes one already in the portal: choose the department and the product, then the Static scan, Security or Full
  pipeline (for a product in the portal the step shows which pipelines each service has today and how many services
  already have each one), then the services: add new ones (name, AppScan application, Gradle or Maven, virtual
  machines or OpenShift), change the existing ones (including their build tool and where they run) or remove them.
  The review lists what is added, changed and removed, which services gain the pipeline and which pipelines a removal
  deletes; the last step lists what to do next in order, with the Jenkinsfile of each service ready to copy.
  Everything else comes from the BBH library defaults and can be fine-tuned in DevSecOps Admin.
- **Pipeline Monitoring**: the DORA metrics and daily runs of all pipelines over the last 30 days, a chart of pipeline
  status per department, every product with the status of its pipelines grouped by department, and per pipeline its
  DORA metrics, daily activity, latest runs, the Jenkins job and your DSOEnhanced Grafana dashboard, all read from the
  InfluxDB the pipelines write to.
- **Change Evidence**: a read-only view of a product for ServiceNow change requests: per pipeline the unit, smoke,
  regression and performance tests, the SAST, DAST, SonarQube and Nexus IQ results, the release gate and the Jenkins
  build that produced them, with its artifact version and the portal configuration it ran with.
- **Admin**, for the portal administrator, in three tabs:
  - **Departments**: add, rename and delete departments (only an empty one can be deleted), each with its products,
    services and DevSecOps pipelines, and a chart of the active and invalidated pipelines of every department. The
    five BBH departments (AI Lab, Capital Partners, Corporate Technology, Custody and Fund Services) come with the
    database.
  - **Products**: add a product with all of its services and every setting the DevSecOps library
    ([DSOEnhanced](https://github.com/mateuszmatan/DSOEnhanced)) reads from `config.yaml` today, edit or delete it.
    Every new service gets a full pipeline with its own key; keys can be invalidated, regenerated and linked to a
    Jenkins job, and each service names the Bitbucket repository where DSOEnhanced raises its GoldenFix pull
    requests. A product saved before departments existed shows as "Not in a department" until it is edited, which
    means choosing one.
  - **Library defaults**: the DSOEnhanced library defaults every pipeline shares and no service can override. They
    replace the library's `defaults.yaml`.

Beadle:

- **Production Change**: raises a ServiceNow (ProTech) change for a production release. Choose the department and the
  product, then type the Jira FixVersion of the release (the known versions are offered, unreleased first): its epics
  are listed, and choosing epics loads their stories. Then check the ServiceNow fields, filled in from the product's
  defaults and editable for this change, and the schedule: the installation date turns the default start time and
  durations into the installation, post-install validation and first usage times, each editable. The portal writes
  the short description and the description from Jira, lets you edit them, and raises one change (CHG) with one
  change task (CTASK) per service. See [ServiceNow production changes](#servicenow-production-changes).
- **Admin**, in two tabs: **Departments** (the same departments as DevSecOps Admin, without the pipeline counts) and
  **Products**: every product by department with the state of its ServiceNow defaults. Add a product; on its page
  change its name, department, owner team and contact e-mail, delete it, add, change and remove its services, and
  keep its ServiceNow defaults (see [ServiceNow production changes](#servicenow-production-changes)).

Departments, products and services are one data set: both Admin areas edit the same records.

No `config.yaml` remains in the product repositories. The portal-integrated library reads each pipeline's
configuration from the portal by its key, so a service needs only the generic Jenkinsfile and its pipeline key:

```groovy
@Library('DevSecOpsJenkinsLibrary') _

devSecOpsPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')
```

A run that builds several services of one product passes the keys of their pipelines of that type, the primary service
first; the product page in DevSecOps Admin offers this Jenkinsfile in the menu of a pipeline. Extended pipelines join only when they name
the same security pipeline, since the run reads the security run state of the primary's only:

```groovy
devSecOpsPipeline(pipelineKeys: ['6f1c2d3e-0000-4abc-9def-123456789abc', 'a1b2c3d4-0000-4abc-9def-123456789abc'])
```

During the cutover, pin the portal-integrated library version (for example `DevSecOpsJenkinsLibrary@main`)
in the shared library of Admin > Library defaults (`platform.jenkinsLibrary`, the name the generated Jenkinsfiles load) and in
the `@Library` line of every migrated job, until every job carries a key.

## Running it locally

Needs Java 21. The Gradle wrapper downloads Gradle, and the build downloads its own Node.js for the GUI.

```bash
./gradlew :backend:bootJar
java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar
```

Open http://localhost:8080, or http://localhost:8080/self-service for the Self-service wizard. Without a profile
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

## Demo data

The `local` profile sets `dso.demo-data=true` (`DSO_DEMO_DATA`). On the first start it fills the H2 database in
`./data` with ten products in the five departments: 23 services and 46 pipelines, 44 of them with an active key. The
loader (`adapter/in/startup/DemoDataLoader.java`) adds the demo products that are missing and does nothing once the
database holds all of them or any product of its own; it also sets the Jenkins URL of the library defaults to
`https://jenkins.bbh.com` when none is set, so that job and build links work. `rd`, `qc` and `prod` (Oracle) get only
the five departments, from Liquibase, and the BBH library defaults; no product, service, pipeline or metric is
seeded there. Every demo product also gets filled ServiceNow defaults
(`adapter/in/startup/DemoChangeProfiles.java`): approvers, schedule defaults, planning texts and a risk assessment,
picked with a fixed seed per product code, and privileged access for Payments Hub. The demo Jira knows two released
and one or two unreleased FixVersions per project, for example `PAYHUB 2.4`.

| Department | Product (code) | Services | Build and deploy | Pipelines |
|------------|----------------|----------|------------------|-----------|
| AI Lab | DocSense (`DOCSENSE`) | `extraction-api`<br>`review-ui` | Gradle, OpenShift<br>Gradle, VMs | FULL, SECURITY, EXTENDED<br>FULL, SAST |
| AI Lab | Advisor Assistant (`ADVISORAI`) | `assistant-api`<br>`content-indexer` | Maven, OpenShift<br>Gradle, OpenShift | FULL, SECURITY<br>FULL |
| Capital Partners | DealFlow (`DEALFLOW`) | `deals-web`<br>`deals-api` | Gradle, VMs<br>Maven, OpenShift | FULL<br>FULL, SECURITY, EXTENDED |
| Capital Partners | LP Portal (`LPPORTAL`) | `portal-web`<br>`statements`<br>`lp-mobile` | Gradle, OpenShift<br>Maven, OpenShift<br>Flutter | FULL<br>FULL, SAST<br>FULL, SAST |
| Corporate Technology | Access Hub (`ACCESSHUB`) | `requests-ui`<br>`workflow` | Gradle, VMs<br>Maven, OpenShift | FULL<br>FULL, SECURITY |
| Corporate Technology | CertScanner (`CERTSCANNER`) | `gui`<br>`backend-api` | Gradle, VMs<br>Maven, OpenShift | FULL, SECURITY, EXTENDED, SAST<br>FULL, SECURITY, EXTENDED |
| Custody | Safekeeping Ledger (`SAFEKEEP`) | `positions-api`<br>`recon-batch` | Maven, OpenShift<br>Gradle, VMs | FULL, SECURITY, EXTENDED<br>FULL, SAST (key revoked) |
| Custody | Corporate Actions (`CORPACT`) | `events-api`<br>`elections-ui` | Gradle, OpenShift<br>Gradle, VMs | FULL, SECURITY<br>FULL |
| Fund Services | Payments Hub (`PAYHUB`) | `gateway`<br>`ledger`<br>`notifications`<br>`mobile-app` | Maven, OpenShift<br>Gradle, VMs<br>Gradle, VMs<br>Flutter | FULL, SECURITY, EXTENDED<br>FULL<br>FULL<br>FULL, SAST (key revoked) |
| Fund Services | NAV Calculator (`NAVCALC`) | `pricing-engine`<br>`nav-api` | Maven, OpenShift<br>Gradle, OpenShift | FULL, SECURITY<br>FULL, EXTENDED |

Build and deploy: *Gradle, VMs* is a Gradle build deployed to virtual machines with UrbanCode Deploy and the SSH
deployment script; *Gradle, OpenShift* and *Maven, OpenShift* build with Gradle or Maven and deploy a container to the
RD and QC OpenShift projects; *Flutter* is a Flutter mobile app built as an APK. Every service has a full pipeline
plus the types listed after `FULL`. The keys of two SAST pipelines are revoked, so those pipelines show as disabled:
Payments Hub `mobile-app` ("Mobile app moved to the new mobile platform pipeline") and Safekeeping Ledger `recon-batch`
("Reconciliation moved to the mainframe scheduler"). Jenkins jobs are named `DevSecOps/<CODE>/<service>-<type>`, for
example `DevSecOps/PAYHUB/gateway-full`; CertScanner's full, security and extended pipelines share the jobs
`DevSecOps/CertScanner-pipeline`, `-security-pipeline` and `-extended-pipeline` and the metrics project `CertScanner`
for both services.

```mermaid
pie showData title Pipelines per department
    "AI Lab" : 8
    "Capital Partners" : 9
    "Corporate Technology" : 10
    "Custody" : 8
    "Fund Services" : 11
```

```mermaid
xychart-beta
    title "Pipelines per type"
    x-axis [FULL, SECURITY, EXTENDED, SAST]
    y-axis "Pipelines" 0 --> 25
    bar [23, 10, 7, 6]
```

### Generated run history

With demo data on and `INFLUX_URL` empty, the portal reads its metrics from the table `DSO_METRIC_POINT` in H2
instead of InfluxDB (changeset `013-local-metrics`, `dbms:h2`, so the table never exists on Oracle). When that table
is empty at start-up, the portal records a random run history of the last 120 days for every pipeline, generated with
a fixed seed per pipeline, so monitoring, DORA metrics and change evidence show data out of the box and
`/api/monitoring/status` reports the metrics store as configured and reachable. Pipelines that share a metrics tag,
a type and a Jenkins job (CertScanner's two services) share one history, so 46 pipelines get 43 histories. The
history is recorded once and does not grow while the portal runs; delete `./data` (`backend/data` with `bootRun`) to
start over with a new catalogue and a history that ends at the new start.

| Type | Runs per day, on average | Duration | Stages |
|------|--------------------------|----------|--------|
| `FULL` | 1.4 | 45 to 95 minutes | Checkout, Build, Unit Tests, SonarQube, AppScan SAST, Nexus IQ, Publish Artifact, Deploy RD, Smoke Tests, Regression Tests, Deploy QC, Release Gate |
| `SECURITY` | 0.7 | 20 to 45 minutes | Checkout, Build, Unit Tests, AppScan SAST, Nexus IQ, SonarQube, AppScan DAST, Release Gate |
| `EXTENDED` | 0.4 | 70 to 130 minutes | Checkout, Read Security Run, Deploy RD, Smoke Tests, Regression Tests, Performance Tests, AppScan DAST, Deploy QC, Release Gate |
| `SAST` | 0.9 | 6 to 18 minutes | Checkout, Build, AppScan SAST, Release Gate |

- Runs start in working hours, 06:00 to 21:00 UTC. A run that would start on a Saturday or Sunday moves to Monday
  four times out of five, so most runs fall on weekdays.
- Each pipeline gets a health between 75% and 97%, the chance that a run succeeds. After a failed run the next one
  succeeds only 45% of the time, so failures tend to repeat. A run that does not succeed fails (half of them), is
  unstable with one warning check (four in ten) or is aborted (one in ten).
- Runs build `develop` (55%), `main` (15%), a `feature/` branch (22%) or a `release/` branch (8%). A run is a
  deployment when it reached Deploy RD on a branch other than `feature/`, so only full and extended pipelines deploy;
  a deployment whose run failed is a change failure. Each pipeline has its own typical lead time of 2 to 47 hours.
- Every run writes `pipeline_run` and `dora` points. The last two runs of each pipeline also carry the full evidence:
  `stage_event` per stage; per service `build_evidence`, and `test_execution`, `code_coverage` and
  `security_findings` for the stages the run reached; and `policy_status`, `vulnerabilities` and `release_gate`.
- The history of a pipeline whose key is revoked ends nine days before the first start.

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
| `INFLUX_URL`, `INFLUX_TOKEN` | empty | your InfluxDB; empty switches the monitoring off, except with demo data, which then reads the local metrics store (see [Demo data](#demo-data)) |
| `INFLUX_ORG`, `INFLUX_BUCKET` | `DevSecOps`, `DORA-metrics` | where DSOEnhanced writes its metrics |
| `GRAFANA_DASHBOARD_URL` | empty | link to the DSOEnhanced pipeline dashboard; empty hides the dashboard |
| `GRAFANA_SECURITY_DASHBOARD_URL` | empty | link to the security dashboard for `SECURITY` and `SAST` pipelines; empty uses the pipeline dashboard |
| `DSO_DEMO_DATA` | `true` with `local` | create the demo products when the database holds no other product, and without `INFLUX_URL` a run history; see [Demo data](#demo-data) |

```bash
SPRING_PROFILES_ACTIVE=qc DB_URL=jdbc:oracle:thin:@//<host>:1521/<service> DB_USERNAME=DSO_PORTAL DB_PASSWORD=... \
  java -jar backend/build/libs/dso-portal-0.1.0-SNAPSHOT.jar
```

Liquibase creates and updates the schema at start-up, on Oracle and on H2. The BBH tool servers and everything else
pipelines share are stored in the database and edited in Admin > Library defaults. Secrets never reach the portal:
services name Jenkins credentials IDs, and the generated configuration carries only those IDs.

## OpenShift

The portal ships as one container image (UBI 9 with Java 21, any non-root UID). `Dockerfile` and the Kustomize
manifests for `rd`, `qc` and `prod` are described in [deploy/openshift/README.md](deploy/openshift/README.md).

## Architecture

```
gui/        Angular 22 + Angular Material: views, shared components, API services
backend/    Spring Boot 4.1, Java 21, Spring Data JPA, Liquibase; serves the API and the built GUI
deploy/     the OpenShift manifests
examples/   Jenkinsfiles, API calls, a rendered configuration and GUI screenshots; see Examples below
```

The portal is one Spring Boot jar that serves the API and the built Angular GUI. Jenkins jobs never reach its
database: the DSOEnhanced library asks the portal for each pipeline's configuration by key, writes its run metrics to
InfluxDB, and raises its GoldenFix pull requests in the service's Bitbucket repository. The portal reads those metrics
back for the monitoring and change evidence pages and links the DSOEnhanced Grafana dashboards.

```mermaid
flowchart LR
    users(["Portal users<br/>in a browser"])
    subgraph jenkins["Jenkins"]
        job["Service job<br/>Jenkinsfile with a pipeline key"]
        lib["DSOEnhanced<br/>shared library"]
        job --> lib
    end
    subgraph jar["DSO portal: one Spring Boot jar"]
        gui["Angular GUI<br/>static files"]
        subgraph ain["adapter.in"]
            web["web<br/>REST controllers"]
            startup["startup<br/>global settings, demo data"]
        end
        usecases["application<br/>use cases behind ports"]
        domain["domain<br/>business rules"]
        subgraph aout["adapter.out"]
            persistence["persistence<br/>JPA"]
            influxq["influx<br/>Flux queries"]
            localm["localmetrics<br/>H2 metric store"]
            grafana["grafana<br/>dashboard links"]
        end
        web --> usecases
        startup --> usecases
        usecases --> domain
        usecases --> persistence
        usecases --> influxq
        usecases --> localm
        usecases --> grafana
    end
    oracle[("Oracle<br/>rd, qc, prod")]
    h2[("H2 in ./data<br/>local")]
    influx[("InfluxDB")]
    dashboards["Grafana dashboards"]
    bitbucket["Bitbucket repository<br/>of each service"]
    users --> gui
    gui -- "/api" --> web
    lib -- "GET $DSO_PORTAL_URL/api/dso/config/{key}?format=json" --> web
    lib -- "run metrics" --> influx
    lib -- "GoldenFix pull request" --> bitbucket
    persistence --> oracle
    persistence --> h2
    localm -- "only with demo data and no INFLUX_URL" --> h2
    influxq -- "INFLUX_URL" --> influx
    grafana -. "links" .-> dashboards
    dashboards --> influx
```

The backend (`backend/src/main/java/com/bbh/itss/dso/portal`) is hexagonal:

| Package | Holds |
|---------|-------|
| `domain` | plain Java: products, services and their settings, pipelines and keys, the global settings, the rendered configuration, DORA metrics, change evidence and ServiceNow production changes; every business rule lives here |
| `application` | the use cases behind ports, per area (`catalog`, `change`, `dsoconfig`, `evidence`, `monitoring`, `pipeline`, `settings`), each with its `port.in` and `port.out` packages; `@UseCase` classes become transactional beans, without Spring in the code |
| `adapter.in` | Spring MVC controllers with the request and response records (`web`), and the start-up tasks (`startup`) |
| `adapter.out` | JPA entities and Spring Data repositories (`persistence`), the InfluxDB client (`influx`), the Grafana links (`grafana`), the local metrics store of the demo data (`localmetrics`), the demo Jira and ServiceNow adapters (`jira`, `servicenow`) and the key generator (`key`) |
| `config` | the wiring of use cases and transactions |

```mermaid
flowchart LR
    ain["adapter.in<br/>web, startup"]
    pin["application.*.port.in<br/>use case interfaces, commands, views"]
    usecases["application.*<br/>@UseCase services"]
    domain["domain<br/>catalog, pipeline, settings, dsoconfig,<br/>monitoring, evidence, shared"]
    pout["application.*.port.out<br/>repository, metrics and link ports"]
    aout["adapter.out<br/>persistence, influx, localmetrics,<br/>grafana, key"]
    ain -- "calls" --> pin
    pin -- "implemented by" --> usecases
    usecases --> domain
    usecases -- "uses" --> pout
    aout -- "implements" --> pout
```

ArchUnit tests keep the domain and the use cases free of Spring, JPA and Jackson, and keep the adapters apart: the
domain depends only on the JDK, Lombok and Apache Commons, the application only on the domain and the same libraries,
`adapter.in` never on `adapter.out` or a `port.out`, `adapter.out` never on `adapter.in`, and every `@UseCase` class
implements a `port.in` interface. The key generator's port, `KeyGenerator`, lives in `domain.pipeline`.

### Code conventions

- Static imports wherever the name stays clear on its own: enum constants, constants, `Collectors`, `Comparator`,
  `requireNonNull`, the Commons helpers. Generic names such as `List.of`, `Optional.empty` or `Product.restore` keep
  their class.
- Apache Commons (`StringUtils`, `ObjectUtils`, `BooleanUtils`, `CollectionUtils`, `ListUtils`) instead of repeated
  null and empty checks, for example `trimToNull(name)`, `getIfNull(tests, TestSettings.DEFAULTS)` or
  `List.copyOf(emptyIfNull(jobs))`.
- Lombok instead of hand-written constructors, accessors, builders and loggers. `backend/lombok.config` makes accessors
  fluent (`id()`, like the records), and utility classes use `@NoArgsConstructor(access = PRIVATE)` rather than
  `@UtilityClass`, whose members javac cannot import statically. Entities never get `@Data` or `@EqualsAndHashCode`.
- No exception classes that only add a name. The code throws JDK exceptions with a message, and
  `ApiExceptionHandler` maps them: `NoSuchElementException` is 404, `IllegalStateException` is 409 (a clash with
  stored data or an outdated `version`), `SecurityException` is 403 (an invalidated pipeline key) and
  `InvalidRequestException`, the one portal exception because it carries the failing fields, is 400. An
  `UncheckedIOException` from the metrics store becomes the metrics error of the page; anything else is a 500 and
  is logged, so a programming error throws `IllegalArgumentException` (for example `Validate.isTrue`), never
  `IllegalStateException`.
- Groovy specs and fixtures use Groovy's own `?.`, `?:` and the records' builders instead of Commons and Lombok, and
  keep GDK names such as `min` or `round` qualified.
- No comments in the code.

### Data model

Liquibase creates the tables from `backend/src/main/resources/db/changelog/oracle`, one SQL file per change; most
changesets run on Oracle and on H2, a few on one of them only. The main tables and a few of their columns:

```mermaid
erDiagram
    DSO_DEPARTMENT |o--o{ DSO_PRODUCT : groups
    DSO_PRODUCT ||--o{ DSO_SERVICE : has
    DSO_SERVICE ||--o{ DSO_PIPELINE : has
    DSO_PIPELINE ||--o{ DSO_PIPELINE_KEY : has
    DSO_DEPARTMENT {
        NUMBER ID PK
        VARCHAR2 NAME UK
        NUMBER VERSION
    }
    DSO_PRODUCT {
        NUMBER ID PK
        NUMBER DEPARTMENT_ID FK
        VARCHAR2 CODE UK "such as PAYHUB"
        VARCHAR2 NAME UK
        VARCHAR2 OWNER_TEAM
        VARCHAR2 ASOC_KEY_ID "AppScan API key ID"
        NUMBER VERSION
    }
    DSO_SERVICE {
        NUMBER ID PK
        NUMBER PRODUCT_ID FK
        VARCHAR2 NAME "unique in the product"
        VARCHAR2 BUILD_TOOL "GRADLE, MAVEN, FLUTTER"
        VARCHAR2 DEPLOY_TARGET "VM, OPENSHIFT"
        VARCHAR2 APPSCAN_APP_ID
        VARCHAR2 REPOSITORY_URL "Bitbucket, for GoldenFix"
        VARCHAR2 INFLUX_PROJECT
        VARCHAR2 INFLUX_ENV
    }
    DSO_PIPELINE {
        NUMBER ID PK
        NUMBER SERVICE_ID FK
        VARCHAR2 PIPELINE_TYPE "FULL, SECURITY, EXTENDED, SAST"
        VARCHAR2 AGENT_LABELS
        VARCHAR2 JENKINS_JOB
        VARCHAR2 SECURITY_PIPELINE_JOB
        VARCHAR2 EXTENDED_PIPELINE_JOB
    }
    DSO_PIPELINE_KEY {
        NUMBER ID PK
        NUMBER PIPELINE_ID FK
        VARCHAR2 KEY_VALUE UK "random UUID"
        VARCHAR2 STATUS "ACTIVE, REVOKED"
        TIMESTAMP ISSUED_AT
        TIMESTAMP REVOKED_AT
        VARCHAR2 REVOKE_REASON
        TIMESTAMP LAST_USED_AT
    }
    DSO_GLOBAL_SETTINGS {
        NUMBER ID PK "always 1"
        VARCHAR2 JENKINS_URL
        VARCHAR2 JENKINS_LIBRARY
        VARCHAR2 SONAR_SERVER_URL
        VARCHAR2 NEXUS_IQ_SERVER_URL
        VARCHAR2 INFLUX_WRITE_URL
        NUMBER COVERAGE_MIN_LINE
        NUMBER VERSION
    }
```

A service has at most one pipeline of each type, and a pipeline at most one `ACTIVE` key; revoked keys stay as its
key history. `DEPARTMENT_ID` is empty only for a product saved before departments existed. The service settings that
repeat live in child tables of `DSO_SERVICE` (`DSO_SERVICE_TEST_JOB`, `DSO_SERVICE_SSH_TARGET`,
`DSO_SERVICE_OPENSHIFT_TARGET`, `DSO_UCD_APPLICATION` with `DSO_UCD_COMPONENT`, `DSO_SERVICE_NEXUS_IQ_APP`), and the
scanners' severity limits in `DSO_GLOBAL_SEVERITY_LIMIT`. `DSO_METRIC_POINT` exists on H2 only; see
[Demo data](#demo-data).

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

```mermaid
sequenceDiagram
    autonumber
    participant J as Jenkins job
    participant L as DSOEnhanced library
    participant P as DSO portal
    participant D as Portal database
    participant I as InfluxDB
    J->>L: devSecOpsPipeline(pipelineKey: key)
    L->>P: GET /api/dso/config/{key}?format=json
    P->>D: find the key (trimmed, lower case)
    alt active key
        P->>D: record the key's last use
        P->>D: read the product, service, pipeline and global settings
        P-->>L: 200 with the configuration as JSON
        L->>L: run the stages of the pipeline type
        L->>I: pipeline_run, dora, stage_event, security_findings, code_coverage
        L->>I: test_execution, release_gate, build_evidence, policy_status, vulnerabilities
    else invalidated key
        P-->>L: 403 problem detail "Pipeline key invalidated" with the date and the reason
        L-->>J: the build stops before its stages
    else key never issued
        P-->>L: 404 problem detail "Unknown DevSecOps pipeline key"
        L-->>J: the build stops before its stages
    end
```

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

## ServiceNow production changes

Every product has ServiceNow defaults, kept by the administrator in Beadle Admin on the product's page:

- **Change**: Jira project key, assignment group, category, type (normal, standard or emergency), affected CI,
  release, incident, problem, affected clients and a description of the product.
- **Approvers**: L1 manager, L2 manager and business approver.
- **Schedule defaults**: downtime yes or no, the installation start time, how many hours the installation takes and
  how many hours the post-install validation takes.
- **Planning**: test summary, implementation plan, validation plan, backout plan and first use plan.
- **Privileged access**: yes or no; when yes, up to seven users, each with the name of their privileged account.
- **Risk assessment**: the numbers of BBH workgroups, BBH users and BBH applications impacted, of impacted clients and
  of impacted clients outside BBH, the business impact, the complexity of the change and of its validation, backout
  testing and duration, and the platform status. Business impact, the two complexities and the platform status offer
  Low/Medium/High and Existing platform/New platform/Platform upgrade as suggestions until the ProTech value lists are
  known; any text is accepted.

A product without saved defaults gets suggested values from its code, name, owner team and description, and the
demo data fills in every demo product.

The Production Change wizard starts from those defaults and lets the app owner change any field for this change. It
adds what changes this time: the services (one change task each, in the order of the product), the Jira FixVersion
with its epics (the epics that carry the FixVersion or have a story that does) and the chosen epics' stories that carry
it, and the schedule: installation start and end, post-install validation start and end and first usage, in that
order, the installation in the future. The portal asks Jira again when the change is previewed or raised and refuses
an epic or story the FixVersion does not list. The release is the FixVersion unless the defaults or the user name
another.
The short description names the product, the FixVersion and the epics; the description names the product, its
department, the schedule, downtime and the change tasks, lists every epic with its chosen stories, then the planning
texts, privileged access, the risk assessment and the product description, cut to the 160 and 4000 characters
ServiceNow takes. Both stay editable until the change is raised. A raised change is stored in the portal with its
numbers, its texts and a copy of the fields it used, so it outlives later edits of the defaults and the product itself.

Jira and ServiceNow sit behind two ports, `JiraPort` and `ServiceNowPort`. The portal ships demo adapters only: the
Jira one makes up a steady set of epics and stories per project key, and the ServiceNow one hands out demo `CHG` and
`CTASK` numbers without calling anything. The pages say so. Connecting the real systems needs:

- **Jira**: an adapter that lists the project's versions and searches it with JQL (`fixVersion = "..."` for the
  stories, the epics by that FixVersion or as the parents of those stories, and the stories of the chosen epics), the Jira base URL and a service account token in an OpenShift secret,
  and HTTPS access from the portal pods to Jira.
- **ServiceNow (ProTech)**: an adapter that creates the change with the Change Management API
  (`POST /api/sn_chg_rest/change/normal`, or `standard`/`emergency` by type) and one change task per service, the
  instance URL and an integration user allowed to create changes and change tasks (OAuth client or basic credentials
  in a secret), the lookup of the configuration item and the assignment group by name, and BBH's rule for approvals
  (the approval policy of the change model, or the approvers sent as approval records). The adapter returns the
  numbers and the link of the change, which the change page then opens.

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
rendered configuration; the owner decides on them later. Both Admin areas are open to every user of the portal until
an administrator role exists. Until they exist the portal must not be
reachable outside the test network, because a portal edit now steers every build and pipeline keys are bearer
secrets.

## REST API

| Method and path | Purpose |
|-----------------|---------|
| `GET /api/departments` | departments by name, each with the number of its products, their services, their DevSecOps pipelines and the pipelines with an active key |
| `POST /api/departments`, `PUT`/`DELETE /api/departments/{id}` | add, rename or delete a department; `PUT` carries the `version` it was read at; a department that still has products is not deleted (409) |
| `GET /api/products?search=` | products with their department, service and pipeline counts; the search also matches the department name |
| `POST /api/products`, `GET`/`PUT`/`DELETE /api/products/{id}` | a product in its department (`departmentId`, required on every save) with its complete list of services; `PUT` carries the `version` it was read at; every service the save creates gets a full pipeline with an active key, or with `?pipelineType=SAST\|SECURITY\|FULL` every service of the product without a pipeline of that type gets one |
| `GET /api/products/code-suggestion?name=` | the code the portal suggests for a new product's name: its letters and digits in upper case, with a number added when another product has that code |
| `GET /api/products/{id}/pipelines` | each service of a product with its pipelines |
| `POST /api/services/{id}/pipelines` | add a pipeline; it starts with an active key |
| `GET`/`PUT`/`DELETE /api/pipelines/{id}` | a pipeline with its key history |
| `POST /api/pipelines/{id}/keys` | issue a new key; an active key is invalidated with the reason "Replaced by a new key"; on a pipeline whose key was invalidated this is Regenerate, and the old keys stay refused |
| `POST /api/pipelines/{id}/keys/revoke` | invalidate the active key, with a `reason` of at most 500 characters; 409 when the pipeline has no active key |
| `GET /api/dso/config/{key}`, `GET /api/pipelines/{id}/config`, `GET /api/products/{id}/config`, `GET /api/settings/config` | the DSOEnhanced configuration as YAML, or as JSON with `?format=json`; see [DevSecOps integration](#devsecops-integration) |
| `GET /api/monitoring/status`, `/products`, `/products/{id}`, `/pipelines/{id}?range=30d` | monitoring data |
| `GET /api/monitoring/activity?range=30d` | the DORA summary and the daily activity of all pipelines together, as `{pipelines, dora, metricsError}`: the number of pipelines, the DORA metrics over the range with `dora.daily` (runs, failures and deployments per day), and the metrics error, if any |
| `GET /api/evidence/products/{id}` | the change evidence of a product's pipelines |
| `GET`/`PUT /api/settings` | the DSOEnhanced library defaults (Admin > Library defaults); `PUT` carries the `version` it was read at |
| `GET`/`PUT /api/products/{id}/change-profile` | the ServiceNow defaults of a product; `version` is `null` until they are saved (the template then holds the suggestion), and `PUT` carries the `version` it was read at |
| `GET /api/change-profiles` | the products with saved ServiceNow defaults: `productId`, `productName`, `version`, `updatedAt` |
| `GET /api/products/{id}/jira/versions`, `/jira/epics?fixVersion=`, `/jira/stories?fixVersion=&epics=` | the FixVersions of the product's Jira project (unreleased first), the epics of a FixVersion and the stories of the chosen epics that carry it; `project=` names another Jira project key |
| `POST /api/changes/preview`, `POST /api/changes` | draft a production change, or raise it with one change task per service (`productId`, `serviceIds`, `fixVersion`, `epicKeys`, `storyKeys`, `schedule` with `installationStart`, `installationEnd`, `validationStart`, `validationEnd` and `firstUsage`, the ServiceNow fields as `template`, and optionally the edited `shortDescription` and `description`) |
| `GET /api/changes`, `/api/changes/{id}`, `/api/changes/integrations` | the raised changes, newest first, one change, and whether Jira and ServiceNow are connected |

A `range` is a number of days from `1d` to `730d`, `30d` when left out. Errors are RFC 9457 problem details;
validation errors name the failing fields, for example `services[2].build.javaPath`. Key values are only sent by the
product management endpoints, and only for the active key; everywhere else a key shows as its hint.

## Examples

[examples/README.md](examples/README.md) describes the Jenkinsfiles of each pipeline type and of a run that builds
several services, `curl` scripts for the REST API, the configuration the library receives for the demo service
Payments Hub `gateway`, and the screenshots of the GUI per version.

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
- Production changes use demo Jira and ServiceNow adapters until the real ones are connected; see
  [ServiceNow production changes](#servicenow-production-changes).
- The change evidence of runs made by a library older than the portal integration has no unit test counts, artifact
  version, SonarQube quality gate, report links or configuration hint, so its links are built from the global settings
  and the Jenkins build number.
