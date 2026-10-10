# Beadle GUI

The Angular app of BBH Beadle, one of the three projects of the Angular workspace in the repository root
(`angular.json`), next to [common-gui](../common-gui/README.md), the code it shares with the DevSecOps Management
Portal, and [dso-gui](../dso-gui/README.md). See the [main README](../README.md) for the whole of Beadle.

```bash
./gradlew installNpmCIDeps            # npm ci from package-lock.json, for every GUI project
./gradlew installDesignSystem         # the BBH Design System packages from the BBH npm registry, inside BBH only
./gradlew :beadle-gui:buildAngular    # production bundle in beadle-gui/dist, checked against the angular.json budgets
./gradlew :beadle-gui:testAngular     # Vitest unit tests with coverage, thresholds in angular.json
./gradlew :beadle-gui:smokeTest       # Spock and Playwright: every page in a browser against a stub API
./gradlew :beadle-gui:regressionTest  # Spock and Playwright: user journeys against a stub API, with the requests they send
./gradlew :beadle-gui:check           # all of the above
npm run start:beadle                  # http://localhost:4200, /api is proxied to the Beadle service on port 8081
npm run start:beadle:public           # the same without the BBH Design System, for a machine outside BBH
```

`./gradlew beadleJar` builds the bundle and packs it into the Beadle service, which serves it on port 8081. The
tasks run `npm` and `npx` from the `PATH`: Node.js 20.19 or a later 20.x with npm 10.8 or a later 10.x, as `engines`
in `package.json` requires and `.npmrc` enforces; outside BBH the Gradle build switches to `registry.npmjs.org` by
itself, as "Inside and outside the BBH network" in the main README describes.

| Folder (`src/app`) | Holds |
|--------------------|-------|
| `core/`    | the API clients (products, lookups, the signed-in user), the models mirroring Beadle's DTOs and the three sections of the menu (`sections.ts`) with the tabs of Beadle Admin |
| `changes/` | Changes and New Change: the ProTech changes of a department in a table filtered and sorted in its header, the change page with where the change is, its approvals with their reminders (`change-approvals.ts`), its update status and its change tasks, the edit page that publishes to ProTech, the change wizard, the secure coding page, and the ProTech fields form and change tasks editor shared with Beadle Admin |
| `beadle/`  | Beadle Admin: the products list by department with the state of their change templates, the product dialog, the product page with its details and change template, and what the Departments tab counts (`department-usage.ts`) |
| `testing/` | fixtures for the unit tests |

The shell, the UI kit, the form field definitions with their lookups, the departments and the Admin page come from
`common-gui`, imported as `@common/...` (see its README). `main.ts` is `appConfig('Beadle', routes)`: the header
reads "BBH Beadle", the menu lists Changes, New Change and Admin, and nothing of the DevSecOps Management Portal
appears anywhere. "Your department" is the department last picked in Changes or New Change, remembered in the browser
(`@common/departments/my-department.ts`).

## Browser tests

The smoke and regression suites are Spock specifications that drive Chromium with Playwright against the production
build. `GuiServer` (in `common-gui/src/testFixtures`) serves `dist` and answers `/api` from `BeadleStubApi`
(`src/testFixtures`), which serves the departments, the products, the signed-in user and the lookups from recorded
answers (`src/testFixtures/resources/.../api`), keeps a demo ProTech in memory (`ChangeStubs`) that raises changes,
creates change tasks and secure coding tickets, names the approvers of each change task, records reminders and moves
changes through the workflow with their approvals as the tests ask, and records
every request, so a specification checks both what the page shows and the exact JSON the GUI sends. Every
specification extends `BeadleSpecification`, which knows Beadle's menu, the fields of a release task and of any other
task, and how to use a lookup. Screenshots of the last state of every feature land in
`build/reports/frontend/screenshots`.

- Smoke (`src/smokeTest`): the title, the three menu entries and the footer without any trace of DevSecOps; every
  section and every Admin tab from the menu and from a direct link; the pages that read the API; the pages without
  icons but the magnifiers of the lookups; every page in an 800 and a 600 pixel window without sideways scrolling;
  an unknown address falling back to the changes.
- Regression (`src/regressionTest`): the tabs of Beadle Admin; the departments with the changes they own, and adding,
  renaming and deleting them; the products by department with the state of their change template, searching them,
  adding one, editing its details with a version conflict and deleting it; the change template section by section,
  with a lookup, the downtime, the risk answers and the risk they give, privileged accounts, default change tasks
  and a version conflict; the ProTech changes of a department filtered and sorted in the table header, the change
  page with where the change is, its approvals reminded one at a time and all at once and the change going In
  Progress once every change task is approved, an update published to ProTech from PENDING to APPLIED with a lookup, a downtime
  window and a risk answer, a NOT_APPLIED update, a stale update, a closed change and the change of another
  department; the new change walked step by step from the product through the lookups, the FixVersion, the
  approvers, the schedule with its downtime window, the planning, the privileged accounts and the risk lists to the
  review, the created change, its change tasks, its secure coding ticket and the summary, a product without a
  template, ProTech's field errors marked on the steps and on the change tasks, and change tasks and the ticket left
  for later and created on the change page; and failing API calls.

## Look and layout

The header holds the name of the application and one flat menu of short labels: Changes, New Change and Admin; the
entry of the current page is underlined. A new feature is a `PortalSection` added to `SECTIONS` in
`core/sections.ts`, routed in `app.routes.ts`, and to `MENU` in `BeadleSpecification`. Beadle Admin is an
`AdminArea` of tabs in `core/sections.ts`, routed as children of the common `AdminPage`; its tabs render without their
own page heading. Each page starts with the full name of its section as the heading and the section description under
it. The pages show no icons but the magnifier of each lookup field. Fields are compact, forms reflow to two columns
below 760px and to one below 480px, and no page scrolls sideways at 800px or 600px. Expandable panels say what they
open: Show or Hide for the collapsed parts of a change page. An error says what could not be loaded or saved, then
the reason, then what to do (`errorMessage()` in `@common/core/errors.ts`); a page that can load again offers Try
again, and a change ProTech could not be read from shows what was read last with the reason.

## New ProTech change

The New Change wizard follows the sections of a ProTech change, listed once in `SECTIONS` of
`changes/change-sections.ts`: Request data (Request details), Jira, Approval (Approval and notification), Schedule,
Planning, Privileged access and Risk assessment, then Review: eight steps until the change exists. The step bar names
each step, the line under it says where you are ("Step 3 of 8"), and the main button names the next step (**Next:
Schedule**). On Review it reads **Create and add CTASKs and SecureCoding ticket**: it creates the change, and Add
CTASKs and Secure coding join the bar ("Step 9 of 10"), because both need the change number. Add CTASKs offers
**Create the CTASKs in ProTech** next to **Add CTASKs later**, Secure coding **Create the secure coding ticket in
CyberTrack** next to **Create the ticket later**, and Summary ends the wizard. Request data starts with the department
and product pickers and the read-only facts (Change number, Approval, Opened by, State), then the request fields in
two columns. Requested for, Requested by and Assigned to default to the signed-in user of `/api/me` and the Department
to the department of the product. Jira shows the Jira project of the template and asks for the FixVersion and its
epics and stories; once an epic is chosen it shows the "Text sent to ProTech" written from them. Schedule asks for
the start of the installation, the post-install validation and the first use as date and time, with hours for the
installation and the validation, and a Yes/No downtime with its own start and hours; the end of each window is shown
under its hours. Privileged access asks how many accounts (none to seven) and then, one line each, the person on the
left and the privileged access on the right. Risk assessment offers the nine lists of `/api/changes/options`; the
Risk field of Request data is read-only ("Worked out from the risk assessment") and follows the answers (High when an
answer is the last of its list, Moderate when one is past the first, else Low). Review shows "Every value of the
change" and the "Text sent to ProTech" (the short description and description), which can be changed there before
the change is raised. Add CTASKs then fills in the product's default change tasks in `dso-change-tasks-form`
(`changes/change-tasks-form.ts`) and adds them to the created change. Secure coding (`dso-secure-coding-form`,
`changes/secure-coding-form.ts`) fills in the APO number and the three links from the template and the
implementation date (MMDDYYYY) from the installation start, all required, and shows the ticket name
`APO-ID_APP-NAME-IMPLEMENTATION-DATE` as you type. Summary shows the change number and the secure coding ticket
number read-only, with **Open the change** and **Create another change**.

The Category, Type and risk lists come only from `/api/changes/options`. The Beadle Admin product page shows the same
sections without the facts, with the Jira project, the schedule defaults (start time and hours) and the secure coding
defaults (APO number and the three links), and notes under Requested for, Requested by and Assigned to "If left
empty: the user who opens the change" and under Department "If left empty: the department of the product". The edit
page of a change shows "ProTech fields" (the facts and the same sections without Jira, with the schedule of the
change), "Change tasks" and "Text sent to ProTech", whose counters count bytes against 160 and 4000, and publishes with
**Publish the update to ProTech**. The change page shows "Where the change is", "Approvals", "The change at a
glance" (When it installs, What it delivers) and the change tasks in the same task form, read-only and without
magnifiers, with "All ProTech fields" (every field by section) and "Text sent to ProTech" in collapsed panels.
Approvals (`dso-change-approvals`) is one table: the business, L1, L2 and support approvals, then "CTASK approvals"
with one row per change task ProTech holds, each with its approvers, a state chip (Not Approved grey, Requested
amber, Approved green) and its last reminder ("James Carter, 2 minutes ago"). Every row still awaited that names
someone has a small link-style **Remind** button ("Remind the approvers of CTASK0310011" to a screen reader), and
**Remind everyone who has not approved** sits next to the heading; both send `POST /api/changes/{id}/reminders` with
your department, show who got the reminder in a message and take the change it answers. They are disabled, with the
reason under the heading and in their tooltip, for another department, without a department and while ProTech cannot
be read; an approved row has no Remind button, Remind everyone is disabled with the reason in its tooltip once everyone
named has approved, and a closed change shows no buttons. The table scrolls sideways on its own
on a narrow screen. While
an open change of your department has no secure coding ticket, a note links to its secure coding page
(`/changes/{id}/secure-coding`, `changes/change-secure-coding.ts`), which creates the ticket and returns to the
change. One task form serves the template, the wizard, the edit page and the change page: two columns filled left
then right, the text areas one under another across the width.

## Lookups

A field with a `lookup` gets a magnifier button, "Find <label>", that opens `LookupDialog`
(`@common/shared/lookup-dialog.ts`): a search field ("Searches ProTech as you type. Enter takes the first match.")
and the matching entries of `/api/lookups/{kind}`, each with its value and detail. Picking one fills the field, or
adds it to the comma separated list when the lookup appends, and `detail` names the field the detail of the entry
fills (the Affected CI fills the Direct business service, which typing the CI by hand clears). A failed search says
"ProTech could not be searched:" with the reason and **Try again**, and a search without a match suggests other words
or typing the value into the field. The input itself stays free text.

## Products

Beadle Admin > Products lists every product by department with the state of its change template, "Filled in" (with
when it was saved) or "Not filled in yet", from `/api/products` and `/api/change-profiles`; when the templates cannot
be read, the products are still listed with "Not known" and a banner says why. A product is added with its name,
code (suggested from the name by `/api/products/code-suggestion` and editable), department, owner team and contact
e-mail; its page opens next. On its page **Edit details** changes everything but the code, sending the `version` the
product was read at: when someone else changed it meanwhile, the page reloads the latest version and says so, so the
change is made again on top of it. **Delete product** deletes the product with its change template after a
confirmation. The product page's change template is the same form as the wizard's sections, saved with
`PUT /api/products/{id}/change-profile`.
