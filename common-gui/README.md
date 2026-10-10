# Common GUI code

The Angular code both interfaces share, imported by [dso-gui](../dso-gui/README.md) and
[beadle-gui](../beadle-gui/README.md) as `@common/...` (`tsconfig.json` maps it to `common-gui/src`). It is a
project of the workspace in the repository root (`angular.json`) with its own unit tests, and holds only what both
applications use; a page, a model or a helper that one application needs belongs to that application.

```bash
./gradlew :common-gui:testAngular     # Vitest unit tests with coverage, thresholds in angular.json
./gradlew :common-gui:check           # the same, as part of ./gradlew check
```

| Folder (`src`) | Holds |
|----------------|-------|
| `shell/`       | `AppShell`: the navy header with "BBH <application name>", the flat menu of the application's sections with the current one underlined, the routed page and the footer |
| `core/`        | `appConfig(name, routes)`, the `ApplicationConfig` an application starts with (the router with component input binding, the HTTP client, the title strategy "<page> · BBH <name>", the dialogs); `APP_NAME`; the `PortalSection` and `AdminArea` types; the departments API; the models both share (`Department`, `FieldProblem`, `LookupItem`); `errorMessage()`; the toast notifier; the unsaved-changes guard |
| `ui/`          | the UI kit every page builds on: form field, checkbox, toggle group, panel, dialog, menu, toast, loading bar, the AG Grid table (`dso-grid`), the Highcharts chart (`dso-chart`) and the BBH Design System icons (`design-system.ts`, swapped for `design-system.public.ts` outside BBH) |
| `shared/`      | the field definitions and the `dso-fields` component (`fields.ts`), form controls and byte-counting validators, the field problems of the API mapped onto a form, formatting (counts, relative times), the confirm dialog, the lookup dialog and the wizard styles |
| `departments/` | the department dialog, the grouping of records by department (`byDepartment`) and "your department" remembered in the browser |
| `admin/`       | `AdminPage`, the page with the tab bar of an `AdminArea`, and `DepartmentsAdmin`, the Departments tab both applications route, which each one completes with its own counts, columns and deletion rule (`DepartmentUsage`) |
| `styles.scss`, `styles/` | everything more than one page draws: the page header, cards, tables and the 12 column form grid, the chips, the fact lists, the quiet links, the side navigation, the muted help line and the look of the UI kit on top of Bootstrap, plus the navy `public-theme.scss` used outside BBH |
| `testing/`     | fixtures, DOM helpers and a stand-in for Highcharts for the unit tests of every GUI project |
| `testFixtures/` | the Groovy support of the browser tests of both applications: `GuiServer`, the abstract `StubApi` with the departments and the recording of requests, `GuiSpecification` with the Playwright browser, the menu and form helpers and the screenshots |

## BBH Design System

The GUIs follow the BBH Design System on Bootstrap 5.3.8, with no Angular Material. `angular.json` loads the styles
of each application in the order the design system asks for: Bootstrap, the other libraries (Highcharts and AG Grid),
the BBH table theme `@v6/v6-table`, `@v6/v6-themes` `main.min.css`, then the Jersey theme (Boston, London, Cracow and
Caymanes are the other BBH themes), the Angular CDK overlay styles and `styles.scss` last. The `@v6/v6-icons` SVGs are
copied to `@v6/v6-icons/assets/icons` and registered with `angular-svg-icon` at start-up (`v6RegisterIcons` in
`ui/design-system.ts`), together with the few portal icons of `ui/icons.ts` (Material Icons outlines, Apache License
2.0).

- Tables are AG Grid 33 from `ag-grid-enterprise` (`dso-grid`): a column filters in its header row and sorts on a click
  of its title; cells that hold links or buttons are `ng-template[dsoCell]` templates, or templates passed in through
  the `templates` input, and stay in the Tab order. Only community modules are registered, so the grids need no
  licence; an Enterprise module added to `ui/grid.ts` gets BBH's key from `-PagGridLicenseKey`.
- Charts are Highcharts 6 in styled mode (`dso-chart`), coloured by CSS and described by an `aria-label`. Highcharts
  renders labels as HTML, so `withPlainText` in `ui/chart.ts` escapes the axis categories and series names first: a
  department or product name always shows as plain text.
- Dialogs, the menus and the toasts use the Angular CDK (`Dialog`, `cdkMenu`, `Overlay`) with Bootstrap's modal,
  dropdown and toast styles. `provideDialogs()` in `ui/dialog.ts` starts from the CDK defaults (a backdrop,
  `role="dialog"`, closing on navigation) and adds the panel class, size limits and focus handling.
- The `public` configuration of each application (`npm run start:public`, `npm run start:beadle:public`, and the
  Gradle build outside BBH) replaces `ui/design-system.ts` with `ui/design-system.public.ts` and the design system
  styles with `styles/public-theme.scss`, a navy Bootstrap theme, so the GUIs build and run where the BBH npm registry
  is out of reach. The unit tests use that configuration too.
