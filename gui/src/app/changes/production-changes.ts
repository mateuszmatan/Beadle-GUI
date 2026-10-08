import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { CHANGES, NEW_CHANGE, beadleChange } from '../core/sections';
import { text } from '../shared/form-controls';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { ChangeState, ChangesApi, ProductionChange, STATES, isOpen, labelOf } from './change-api';
import { TIME_ZONE_NOTE, activeTasks, windowText } from './change-model';
import { IntegrationNote } from './integration-note';

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
  state: string;
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

const SORT_KEYS: Record<string, (row: ChangeRow) => string | number> = {
  number: (row) => row.change.number ?? '',
  product: (row) => row.change.productName,
  fixVersion: (row) => row.change.fixVersion,
  state: (row) => STATES.findIndex((state) => state.value === row.change.state),
  installation: (row) => Date.parse(row.change.schedule.installationStart),
  shortDescription: (row) => row.change.shortDescription,
  tasks: (row) => row.tasks,
  raised: (row) => Date.parse(row.change.createdAt ?? '') || 0,
};

export function changeRow(change: ProductionChange): ChangeRow {
  return {
    change,
    open: isOpen(change),
    state: labelOf(STATES, change.state),
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

export function sorted(rows: readonly ChangeRow[], sort: Sort): ChangeRow[] {
  const key = SORT_KEYS[sort.active];
  if (!key || !sort.direction) {
    return [...rows];
  }
  const sign = sort.direction === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => {
    const [x, y] = [key(a), key(b)];
    return (
      sign *
      (typeof x === 'number' && typeof y === 'number'
        ? x - y
        : String(x).localeCompare(String(y), 'en', { numeric: true }))
    );
  });
}

@Component({
  selector: 'dso-production-changes',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    RelativeTimePipe,
    IntegrationNote,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a mat-flat-button [routerLink]="newChange.path">New change</a>
        </div>
      </header>
      <dso-integration-note />
      <section class="card toolbar">
        <mat-form-field class="department" subscriptSizing="dynamic">
          <mat-label>Your department</mat-label>
          <mat-select [formControl]="department">
            @for (department of departmentList(); track department.id) {
              <mat-option [value]="department.id">{{ department.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <span class="spacer"></span>
        @if (changes.hasValue() && changes.value().length) {
          <span class="muted shown">{{ shown() }}</span>
        }
      </section>
      @if (departments.error(); as error) {
        <div class="banner">The departments could not be loaded: {{ errorMessage(error) }}</div>
      }
      @if (departmentId() === null) {
        <section class="card empty-state">
          <h3>Choose your department to see its ProTech changes.</h3>
          <p>Beadle remembers your department in this browser.</p>
        </section>
      } @else {
        @if (changes.isLoading()) {
          <mat-progress-bar mode="indeterminate" />
        }
        @if (changes.error(); as error) {
          <div class="banner">{{ errorMessage(error) }}</div>
        }
        @if (changes.hasValue()) {
          @if (syncProblem(); as problem) {
            <div class="banner" role="status">
              {{ problem }} The table shows what Beadle last read from ProTech.
            </div>
          }
          @if (changes.value().length) {
            <section class="card table-scroll">
              <table
                mat-table
                class="changes"
                [dataSource]="rows()"
                matSort
                (matSortChange)="sort.set($event)"
              >
                <ng-container matColumnDef="number">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Change</th>
                  <td mat-cell *matCellDef="let row">
                    <a
                      class="mono quiet-link"
                      [routerLink]="changeLink(row.change.id)"
                      (click)="$event.stopPropagation()"
                      >{{ row.change.number }}</a
                    >
                  </td>
                </ng-container>
                <ng-container matColumnDef="product">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Product</th>
                  <td mat-cell *matCellDef="let row">{{ row.change.productName }}</td>
                </ng-container>
                <ng-container matColumnDef="fixVersion">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>FixVersion</th>
                  <td mat-cell *matCellDef="let row" class="mono">{{ row.change.fixVersion }}</td>
                </ng-container>
                <ng-container matColumnDef="state">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>State</th>
                  <td mat-cell *matCellDef="let row">
                    <span class="chip" [class.neutral]="!row.open" [class.stage]="row.open">{{
                      row.state
                    }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="installation">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Installation</th>
                  <td mat-cell *matCellDef="let row">{{ row.installation }}</td>
                </ng-container>
                <ng-container matColumnDef="shortDescription">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Short description</th>
                  <td mat-cell *matCellDef="let row">{{ row.change.shortDescription }}</td>
                </ng-container>
                <ng-container matColumnDef="tasks">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Tasks</th>
                  <td mat-cell *matCellDef="let row" class="number">{{ row.tasks }}</td>
                </ng-container>
                <ng-container matColumnDef="raised">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Raised</th>
                  <td mat-cell *matCellDef="let row">{{ row.change.createdAt | relative }}</td>
                </ng-container>
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef></th>
                  <td mat-cell *matCellDef="let row" class="actions">
                    @if (row.open) {
                      <a
                        mat-button
                        [routerLink]="changeLink(row.change.id, 'edit')"
                        [attr.aria-label]="'Edit ' + row.change.number"
                        (click)="$event.stopPropagation()"
                        >Edit</a
                      >
                    }
                  </td>
                </ng-container>
                @for (filter of textFilters; track filter.key) {
                  <ng-container [matColumnDef]="filter.key + '-filter'">
                    <th mat-header-cell *matHeaderCellDef>
                      <mat-form-field subscriptSizing="dynamic">
                        <input
                          matInput
                          autocomplete="off"
                          placeholder="Filter"
                          [attr.aria-label]="'Filter by ' + filter.label"
                          [formControl]="filters.controls[filter.key]"
                        />
                      </mat-form-field>
                    </th>
                  </ng-container>
                }
                <ng-container matColumnDef="state-filter">
                  <th mat-header-cell *matHeaderCellDef>
                    <mat-form-field subscriptSizing="dynamic">
                      <mat-select
                        aria-label="Filter by state"
                        [formControl]="filters.controls.state"
                      >
                        @for (option of stateFilters; track option.value) {
                          <mat-option [value]="option.value">{{ option.label }}</mat-option>
                        }
                      </mat-select>
                    </mat-form-field>
                  </th>
                </ng-container>
                <ng-container matColumnDef="rest-filter">
                  <th mat-header-cell *matHeaderCellDef colspan="3"></th>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="columns"></tr>
                <tr mat-header-row *matHeaderRowDef="filterColumns" class="filters"></tr>
                <tr
                  mat-row
                  *matRowDef="let row; columns: columns"
                  class="clickable"
                  (click)="open(row.change)"
                ></tr>
                <tr class="mat-mdc-row" *matNoDataRow>
                  <td class="mat-mdc-cell no-match" [attr.colspan]="columns.length">
                    No change matches the filters.
                  </td>
                </tr>
              </table>
            </section>
            <p class="note zone">{{ timeZoneNote }}</p>
          } @else {
            <div class="card empty-state">
              <h3>No ProTech change of {{ departmentName() }} yet</h3>
              <p>
                Choose a product, the FixVersion with its Jira epics and stories and the
                installation date. Beadle writes the change and its change tasks.
              </p>
              <a mat-flat-button [routerLink]="newChange.path">New change</a>
            </div>
          }
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

    .changes {
      width: 100%;

      td.mat-mdc-cell {
        padding-top: 4px;
        padding-bottom: 4px;
        font-size: 12.5px;
      }
    }

    .filters .mat-mdc-header-cell {
      padding-top: 2px;
      padding-bottom: 6px;
      font-weight: 400;
      letter-spacing: 0;
      text-transform: none;
      vertical-align: top;

      mat-form-field {
        width: 100%;
        min-width: 72px;
      }
    }

    .mat-column-number {
      white-space: nowrap;
    }

    .mat-column-fixVersion,
    .mat-column-raised {
      white-space: nowrap;
    }

    .mat-column-installation {
      min-width: 170px;
    }

    .mat-column-shortDescription {
      min-width: 200px;
    }

    .number {
      text-align: right;
    }

    .chip.stage {
      background: var(--dso-info-bg);
      color: var(--dso-navy);
    }

    td.actions {
      width: 60px;
      text-align: right;
    }

    .no-match {
      padding: 12px;
      color: var(--dso-muted);
    }

    .zone {
      margin-top: 6px;
    }
  `,
})
export class ProductionChanges {
  private readonly api = inject(ChangesApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly router = inject(Router);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = CHANGES;
  protected readonly newChange = NEW_CHANGE;
  protected readonly changeLink = beadleChange;
  protected readonly errorMessage = errorMessage;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;
  protected readonly stateFilters = STATE_FILTERS;
  protected readonly textFilters: { key: Exclude<keyof ChangeFilters, 'state'>; label: string }[] =
    [
      { key: 'number', label: 'change' },
      { key: 'product', label: 'product' },
      { key: 'fixVersion', label: 'FixVersion' },
      { key: 'installation', label: 'installation' },
      { key: 'shortDescription', label: 'short description' },
    ];
  protected readonly columns = [
    'number',
    'product',
    'fixVersion',
    'state',
    'installation',
    'shortDescription',
    'tasks',
    'raised',
    'actions',
  ];
  protected readonly filterColumns = [
    'number-filter',
    'product-filter',
    'fixVersion-filter',
    'state-filter',
    'installation-filter',
    'shortDescription-filter',
    'rest-filter',
  ];

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
  protected readonly sort = signal<Sort>({ active: '', direction: '' });

  private readonly all = computed(() =>
    (this.changes.hasValue() ? this.changes.value() : []).map(changeRow),
  );
  protected readonly rows = computed(() =>
    sorted(
      this.all().filter((row) => matches(row, this.filterValue())),
      this.sort(),
    ),
  );
  protected readonly shown = computed(
    () => `${this.rows().length} of ${counted(this.all().length, 'change')}`,
  );
  protected readonly syncProblem = computed(
    () => this.all().find((row) => row.change.syncProblem)?.change.syncProblem ?? null,
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
