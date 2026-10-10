import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MyDepartment } from '@common/departments/my-department';
import { DepartmentsApi } from '../core/api';
import { RETRY, errorMessage } from '@common/core/errors';
import { CHANGES, NEW_CHANGE, beadleChange } from '../core/sections';
import { text } from '@common/shared/form-controls';
import { TIME_ZONE_NOTE, counted, formatRelative } from '@common/shared/formatting';
import { FORM_FIELD } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { ChangeState, ChangesApi, ProductionChange, STATES, isOpen, labelOf } from './change-api';
import { PENDING_HINT, activeTasks, editHint, windowText } from './change-model';
import { IntegrationNote } from './integration-note';
import { STATE_MEANINGS } from './workflow-progress';

export type StateFilter = 'ALL' | 'OPEN' | ChangeState;

export interface ChangeFilters {
  number: string;
  product: string;
  fixVersion: string;
  state: StateFilter;
  installation: string;
  shortDescription: string;
}

export interface ChangeRow {
  change: ProductionChange;
  open: boolean;
  editable: boolean;
  pending: boolean;
  state: string;
  meaning: string;
  installation: string;
  tasks: number;
}

export const STATE_FILTERS: { value: StateFilter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'OPEN', label: 'Open' },
  ...STATES,
];

const contains = (value: string | null, typed: string) =>
  (value ?? '').toLowerCase().includes(typed.trim().toLowerCase());

export function changeRow(change: ProductionChange, departmentId: number | null): ChangeRow {
  return {
    change,
    open: isOpen(change),
    editable: isOpen(change) && !editHint(change, departmentId),
    pending: change.update?.status === 'PENDING',
    state: labelOf(STATES, change.state),
    meaning: STATE_MEANINGS[change.state],
    installation: windowText(change.schedule.installationStart, change.schedule.installationEnd),
    tasks: activeTasks(change.tasks).length,
  };
}

export function matches(row: ChangeRow, filters: ChangeFilters): boolean {
  const { change } = row;
  const state =
    filters.state === 'ALL' ||
    (filters.state === 'OPEN' ? row.open : change.state === filters.state);
  return (
    state &&
    contains(change.number, filters.number) &&
    contains(change.productName, filters.product) &&
    contains(change.fixVersion, filters.fixVersion) &&
    contains(row.installation, filters.installation) &&
    contains(change.shortDescription, filters.shortDescription)
  );
}

@Component({
  selector: 'dso-changes-list',
  imports: [ReactiveFormsModule, RouterLink, FORM_FIELD, GRID, DsoLoading, IntegrationNote],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a class="btn btn-primary" [routerLink]="newChange.path">Raise a change</a>
        </div>
      </header>
      <dso-integration-note />
      <section class="card toolbar">
        <dso-form-field class="department">
          <dso-label>Your department</dso-label>
          <select dsoInput [formControl]="department">
            @for (department of departmentList(); track department.id) {
              <option [ngValue]="department.id">{{ department.name }}</option>
            }
          </select>
        </dso-form-field>
      </section>
      @if (departments.error(); as error) {
        <div class="banner" role="alert">
          <span>The departments could not be loaded: {{ errorMessage(error) }}</span>
          <span class="spacer"></span>
          <button type="button" class="btn btn-link" (click)="departments.reload()">
            Try again
          </button>
        </div>
      }
      @if (departmentId() === null) {
        <section class="card empty-state">
          <h3>Choose your department to see its ProTech changes.</h3>
          <p>Beadle remembers your department in this browser.</p>
        </section>
      } @else {
        @if (changes.isLoading()) {
          <dso-loading />
        }
        @if (changes.error(); as error) {
          <div class="banner" role="alert">
            <span>The changes could not be loaded: {{ errorMessage(error) }}</span>
            <span class="spacer"></span>
            <button type="button" class="btn btn-link" (click)="changes.reload()">Try again</button>
          </div>
        }
        @if (syncProblem(); as problem) {
          <div class="banner" role="status">
            <span>
              {{ problem }} The table shows what Beadle last read from ProTech. {{ retry }}
            </span>
            <span class="spacer"></span>
            <button type="button" class="btn btn-link" (click)="changes.reload()">Try again</button>
          </div>
        }
        <section class="card list" [hidden]="!all().length">
          <div class="list-head">
            <h2>Changes of {{ departmentName() }}</h2>
            <span class="muted shown">{{ shown() }}</span>
          </div>
          <p class="section-help zone">
            Select a change to see where it is in its workflow and what it needs next. FixVersion is
            the Jira release the change delivers. {{ timeZoneNote }}
          </p>
          <dso-grid
            label="ProTech changes"
            empty="No change matches the filters."
            [rows]="rows()"
            [columns]="columns"
            [rowId]="rowId"
            [rowClass]="rowClass"
            (rowClick)="open($event.change)"
          >
            <ng-template dsoCell="number" let-row>
              <a class="mono quiet-link" [routerLink]="changeLink(row.change.id)">{{
                row.change.number
              }}</a>
            </ng-template>
            <ng-template dsoCell="state" let-row>
              <span class="state">
                <span class="chip" [class.neutral]="!row.open" [class.stage]="row.open">{{
                  row.state
                }}</span>
                <span class="meaning">{{ row.meaning }}</span>
              </span>
            </ng-template>
            <ng-template dsoCell="actions" let-row>
              @if (row.editable) {
                <a
                  class="btn btn-link"
                  [routerLink]="changeLink(row.change.id, 'edit')"
                  [attr.aria-label]="'Edit ' + row.change.number"
                  >Edit change</a
                >
              } @else if (row.pending) {
                <span class="muted pending" [attr.title]="pendingHint">Update pending</span>
              }
            </ng-template>
          </dso-grid>
        </section>
        @if (changes.hasValue() && !all().length) {
          <div class="card empty-state">
            <h3>No ProTech change of {{ departmentName() }} yet</h3>
            <p>
              Raise one for a production release: choose the product and its Jira release, and
              Beadle writes the change and its change tasks for ProTech.
            </p>
            <a class="btn btn-primary" [routerLink]="newChange.path">Raise a change</a>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 16px;
      margin-bottom: 12px;
      padding: 10px 12px;
    }

    .department {
      width: 280px;
      max-width: 100%;
    }

    .shown {
      font-size: 12px;
    }

    .list {
      padding: 10px 12px 0;

      h2 {
        margin: 0 0 4px;
        font-size: 15px;
      }
    }

    .list-head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 4px 16px;
    }

    .state {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 2px;
      line-height: 1.3;
    }

    .meaning {
      color: var(--dso-muted);
      font-size: 11.5px;
      white-space: normal;
    }

    .chip.stage {
      background: var(--dso-info-bg);
      color: var(--dso-navy);
    }

    .pending {
      font-size: 12px;
    }
  `,
})
export class ChangesList {
  private readonly api = inject(ChangesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly router = inject(Router);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = CHANGES;
  protected readonly newChange = NEW_CHANGE;
  protected readonly changeLink = beadleChange;
  protected readonly errorMessage = errorMessage;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;
  protected readonly retry = RETRY;
  protected readonly pendingHint = PENDING_HINT;

  protected readonly departmentId = this.myDepartment.departmentId;
  protected readonly department = new FormControl(this.departmentId());
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly departmentList = computed(() =>
    this.departments.hasValue() ? this.departments.value() : [],
  );
  protected readonly departmentName = computed(
    () =>
      this.departmentList().find((department) => department.id === this.departmentId())?.name ??
      'your department',
  );

  protected readonly changes = rxResource({
    params: () => this.departmentId() ?? undefined,
    stream: ({ params }) => this.api.list(params),
  });

  protected readonly filters = new FormGroup({
    number: text(''),
    product: text(''),
    fixVersion: text(''),
    state: new FormControl<StateFilter>('ALL', { nonNullable: true }),
    installation: text(''),
    shortDescription: text(''),
  });
  private readonly filterValue = toSignal(
    this.filters.valueChanges.pipe(map(() => this.filters.getRawValue())),
    { initialValue: this.filters.getRawValue() },
  );

  protected readonly rowId = (row: ChangeRow) => row.change.id!;
  protected readonly rowClass = () => 'clickable';
  protected readonly columns: GridColumn<ChangeRow>[] = [
    {
      key: 'number',
      header: 'Change number',
      value: (row) => row.change.number ?? '',
      filter: { control: this.filters.controls.number, label: 'change' },
      width: 140,
    },
    {
      key: 'product',
      header: 'Product',
      value: (row) => row.change.productName,
      filter: { control: this.filters.controls.product, label: 'product' },
      minWidth: 140,
    },
    {
      key: 'fixVersion',
      header: 'FixVersion',
      value: (row) => row.change.fixVersion,
      filter: { control: this.filters.controls.fixVersion, label: 'FixVersion' },
      cellClass: 'mono',
      minWidth: 120,
    },
    {
      key: 'state',
      header: 'State',
      value: (row) => row.state,
      sortValue: (row) => STATES.findIndex((state) => state.value === row.change.state),
      filter: { control: this.filters.controls.state, label: 'state', options: STATE_FILTERS },
      wrap: true,
      minWidth: 170,
    },
    {
      key: 'installation',
      header: 'Installation',
      value: (row) => row.installation,
      sortValue: (row) => Date.parse(row.change.schedule.installationStart),
      filter: { control: this.filters.controls.installation, label: 'installation' },
      wrap: true,
      minWidth: 230,
    },
    {
      key: 'shortDescription',
      header: 'Short description',
      value: (row) => row.change.shortDescription,
      filter: { control: this.filters.controls.shortDescription, label: 'short description' },
      wrap: true,
      flex: 2,
      minWidth: 200,
    },
    { key: 'tasks', header: 'Tasks', value: (row) => row.tasks, numeric: true, width: 80 },
    {
      key: 'raised',
      header: 'Raised',
      value: (row) => formatRelative(row.change.createdAt),
      sortValue: (row) => Date.parse(row.change.createdAt ?? '') || 0,
      width: 110,
    },
    { key: 'actions', header: '', width: 120 },
  ];

  protected readonly all = computed(() =>
    (this.changes.hasValue() ? this.changes.value() : []).map((change) =>
      changeRow(change, this.departmentId()),
    ),
  );
  protected readonly rows = computed(() =>
    this.all().filter((row) => matches(row, this.filterValue())),
  );
  protected readonly shown = computed(
    () => `${this.rows().length} of ${counted(this.all().length, 'change')}`,
  );
  protected readonly syncProblem = computed(
    () => [...new Set(this.all().flatMap((row) => row.change.syncProblem ?? []))].join(' ') || null,
  );

  constructor() {
    this.department.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => this.myDepartment.choose(id));
  }

  protected open(change: ProductionChange): void {
    this.router.navigate(beadleChange(change.id!));
  }
}
