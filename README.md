# Beadle-GUI

Beadle raises ProTech changes for production releases at BBH, with their change tasks and their CyberTrack secure
coding ticket, from the change template of each product. Beadle-GUI is the Angular interface and the Spring Boot
service behind it: the system of record of business services, products, change templates and raised changes, and the
audit trail of what every user did. It packs into one jar and one container image.

## How it fits with the other two services

```
Browser ──> Beadle-GUI (8081) ──REST──> Beadle-Internal (8082) ──> Jira, CyberTrack, Confluence, qTest
                 │   ^      └───REST──> Beadle-ProTech (8083)  ──> ProTech (BBH's ServiceNow)
                 │   └── callbacks from both: a ticket or a change moved
                 └──> H2 (laptop) or Oracle (rd, qc, prod)
```

Users sign in with Ping. Beadle-GUI calls the two services with the shared `BEADLE_SERVICE_TOKEN`, the signed-in user
in `X-Beadle-User` and the user action in `X-Correlation-Id`; they sign in to the external systems with Beadle's
generic service accounts and call Beadle-GUI back when something moved. The endpoints, bodies, headers, callbacks and
the audit trail are specified in [docs/CONTRACTS.md](docs/CONTRACTS.md), which is the same file in all three
repositories, and [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) runs and deploys the three together.

## Repository layout

```
beadle-backend/      the Spring Boot service, port 8081, with the Liquibase changelog
beadle-gui/          the Angular interface and its Groovy browser tests
deploy/openshift/    Kustomize base and the rd, qc and prod overlays; Dockerfile in the root
deploy/docker/       Docker Compose test environment of the three services
docs/                CONTRACTS.md and DEPLOYMENT.md, shared by the three repositories
```

The service is hexagonal, and `ArchitectureSpec` (ArchUnit) enforces the layers:

```
com.bbh.itss.beadle
├── domain/<area>            records and rules, no framework: audit, callback, catalog, change, shared
├── application/<area>       use cases behind port/in, ports to the outside in port/out
├── adapter/in/web           controllers, callbacks, security filters, error handling
├── adapter/in/startup       the demo data of the laptop
├── adapter/out/persistence  JPA entities and repositories
├── adapter/out/internal     the client of Beadle-Internal (Jira, CyberTrack)
├── adapter/out/protech      the client of Beadle-ProTech (changes, lookups)
├── adapter/out/http, user   the headers of every service call, the signed-in user
└── config                   Spring wiring only
```

The interface (`beadle-gui/src/app`) has the same cut: `shell` (header, menu, user, footer), `core` (API clients,
models, sign-in), `ui` (form fields, grid, dialogs, toasts), `shared` (field definitions, lookups),
`business-services`, `products`, `admin`, `changes` (Changes and New Change) and `action-history`.

## Look

Beadle keeps its own navy theme on Bootstrap 5.3, with the BBH V6 styles (`@v6/v6-themes`, `@v6/v6-table`,
`@v6/v6-icons`) loaded underneath it in `angular.json`; Beadle's theme is the top of `beadle-gui/src/styles.scss`.
Tables are AG Grid 33 with the balham theme and the BBH table theme `v6-table-theme--bbh`; a grid wider than its card
scrolls sideways inside the card, never the page. The pages use sober business wording, one type scale and no icons
but the magnifier of a lookup field.

## Run on a BBH laptop

Needs Java 21 and Node.js 20.19 or 22.12 or later. Start Beadle-Internal and Beadle-ProTech first, each in its own
clone with `./gradlew bootRun` and the same token, then:

```bash
export BEADLE_SERVICE_TOKEN=local-$(whoami)     # the same value in all three terminals
./gradlew :beadle-backend:bootRun               # http://localhost:8081, profile local, with the GUI
```

The `local` profile keeps an H2 file database in Oracle mode in `~/beadle/beadle.mv.db` (`BEADLE_DATA_DIR` moves
it) with demo products, and signs everybody in as `Mateusz Matan` without Ping (`BEADLE_SIGNED_IN_USER` names
someone else). Beadle-GUI also starts while the two services are down; its pages then say which one cannot be
reached. For work on the interface, `npm start` serves it on http://localhost:4201 and proxies the API to port 8081.

## Build and tests

The build uses the BBH Nexus and the BBH npm registry only (`gradle.properties` holds the registry, the proxy and the
CA file), so it runs inside the BBH network or on the VPN. `installDesignSystem` installs the V6 packages, which
exist only there.

| Task | What | Typical time |
|---|---|---|
| `./gradlew build` | the jar with the GUI inside, every suite and both coverage gates | about 3.5 min |
| `./gradlew beadleJar` | `beadle-backend/build/libs/beadle-<version>.jar`, the GUI inside | 30 s |
| `./gradlew :beadle-backend:test` | unit specs (Spock), JaCoCo gate of 60% of lines | 30 s |
| `./gradlew :beadle-backend:smokeTest` | the jar on a fresh database: health, the API, the callbacks, the GUI | 10 s |
| `./gradlew :beadle-backend:regressionTest` | the key journeys over HTTP on H2 with stand-ins for the two services | 20 s |
| `./gradlew :beadle-backend:performanceTest` | the key calls within their limits at the 95th percentile | 20 s |
| `./gradlew :beadle-gui:testAngular` | Vitest unit tests with a 60% gate | 30 s |
| `./gradlew :beadle-gui:smokeTest` | every page in Chromium against a stub API | 20 s |
| `./gradlew :beadle-gui:regressionTest` | the user journeys in Chromium, with the requests they send | 50 s |

`jacocoSuitesReport` gates the line coverage of the backend suites together at 30%. `-Dsmoke.baseUrl=https://...`
runs the smoke suites against a deployed Beadle-GUI, and `-Dperformance.factor=2` relaxes the limits on a slow
machine.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` on H2; `rd`, `qc`, `prod` on Oracle with Ping |
| `PORT` | `8081` | HTTP port |
| `BEADLE_DATA_DIR` | `~/beadle` | folder of the H2 database (`local`) |
| `BEADLE_DEMO_DATA` | `true` with `local` | seed the demo products and change templates |
| `BEADLE_SIGNED_IN_USER` | `Mateusz Matan` | the user everybody is signed in as with `local` (`beadle.auth.user`) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | H2 with `local` | the Oracle database of `rd`, `qc`, `prod` (pool 5, 10, 20) |
| `PING_ISSUER_URI`, `PING_CLIENT_ID`, `PING_CLIENT_SECRET` | none | Beadle's OpenID Connect client at Ping; required on `rd`, `qc`, `prod` |
| `BEADLE_INTERNAL_URL`, `BEADLE_PROTECH_URL` | `http://localhost:8082`, `:8083` with `local` | where the two services answer |
| `BEADLE_SERVICE_TOKEN` | blank: every call and callback refused | the token shared by the three services |
| `LOG_PATH` | `./logs` | folder of the rolling log file `beadle.log` |

On `rd`, `qc` and `prod` the service does not start without the database, the Ping client and the two service URLs,
and it refuses the laptop's sign-in. `rd` logs Beadle at DEBUG. Liquibase updates the schema at every start and
Hibernate validates it; a changeset on `main` is never edited, a schema change is a new changeset with a rollback.

## Sign-in and audit trail

Users sign in with Ping (OpenID Connect); the name in the ID token is the user Beadle records. An API call without a
session answers `401`, and the interface then sends the browser to Ping. Every action that changes something or
calls another system writes one row to `BEADLE_ACTION_HISTORY`: who, what, on which object, when, how long, the
request as JSON with secrets masked, and the outcome with the error. Sign-in and sign-out are recorded too, and a
refused request of a signed-in user is a `FAILURE` row. The Action History page lists the rows newest first,
filtered by user, action, object, outcome and day.

## Deployment

```bash
./gradlew beadleJar && docker build -t beadle:0.1.0-SNAPSHOT .
```

The image (UBI 9 OpenJDK 21) runs as user 185 on port 8081, keeps the local H2 database in `/application/data` and
writes logs to `/application/logs`. The three services run in one OpenShift project, one pod each.
`deploy/openshift/beadle` holds the base (Deployment, Service, Route, ConfigMap `beadle-config`) and the overlays
`rd`, `qc` and `prod`, which set the profile and the Ping issuer. Before the first deployment of an environment:

1. Replace the placeholder `PING_ISSUER_URI=https://set-the-ping-issuer-of-<env>.invalid` in its overlay with the
   issuer Ping names for Beadle's client; with the placeholder the pod does not start.
2. Register `https://<route>/login/oauth2/code/ping` as the redirect URI of the client at Ping, and `https://<route>/`
   as its address after signing out.
3. Create the secrets `beadle-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `beadle-services`
   (`BEADLE_SERVICE_TOKEN`, shared by the three services) and `beadle-oidc` (`PING_CLIENT_ID`, `PING_CLIENT_SECRET`).

Then `oc apply -k deploy/openshift/beadle/overlays/<env>`. The overlays point Beadle-GUI at
`http://beadle-internal:8082` and `http://beadle-protech:8083`. The probes use `/actuator/health/liveness` and
`/readiness`, the startup probe allows three minutes for Liquibase, and the Route terminates TLS at the edge.

## Known gaps

- Ping sign-in and the connections to the real Jira, CyberTrack and ProTech have not been tried yet; the Ping issuer,
  the client and the service accounts must be filled in for each environment.
- Admin and Action History are open to every signed-in user until Ping's groups give Beadle an administrator role.
- "Your business service" is remembered in the browser, not per user.
- The category, type and risk answer lists are fixed in Beadle until ProTech's value lists can be read.
- The base image tag in the Dockerfile must be set to the BBH-approved UBI 9 OpenJDK 21 image, the same in all three
  repositories.
