import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { debounce, map, of, timer } from 'rxjs';
import { RETRY, errorMessage } from '../core/errors';
import { ACTION_HISTORY } from '../core/sections';
import { Field, Fields, FieldOption, choice, line, optionsOf } from '../shared/fields';
import { text } from '../shared/form-controls';
import { TIME_ZONE_NOTE } from '../shared/formatting';
import { FORM_FIELD } from '../ui/form-field';
import { GRID, GridColumn } from '../ui/grid';
import { BeadleLoading } from '../ui/loading';
import { openDetails } from './action-details';
import { ActionHistoryApi } from './action-history-api';
import {
  ActionFilters,
  ActionHistoryPage,
  ActionRecord,
  DEFAULT_PAGE_SIZE,
  NO_FILTERS,
  OUTCOMES,
  PAGE_SIZES,
  actionText,
  atText,
  lastPage,
  objectText,
  outcomeText,
  queryOf,
  rangeText,
  reversed,
} from './action-history-model';

/** How long the page waits after the last key typed in the object key before it asks again. */
export const TYPING_DELAY = 300;

export const RANGE_PROBLEM = 'The last day comes before the first';

export interface ActionRow {
  record: ActionRecord;
  at: string;
  action: string;
  object: string;
  outcome: string;
  success: boolean;
}

export function actionRow(record: ActionRecord): ActionRow {
  return {
    record,
    at: atText(record.at),
    action: actionText(record.action),
    object: objectText(record),
    outcome: outcomeText(record.outcome),
    success: record.outcome === 'SUCCESS',
  };
}

const notBeforeFrom: ValidatorFn = (control) =>
  reversed({ from: control.parent?.get('from')?.value ?? '', to: control.value })
    ? { rule: RANGE_PROBLEM }
    : null;

const everything = (label: string, options: FieldOption[]): FieldOption[] => [
  { value: '', label },
  ...options,
];

const sameFilters = (x: ActionFilters, y: ActionFilters) => JSON.stringify(x) === JSON.stringify(y);

function filtersOf(form: FormGroup): ActionFilters {
  const value = form.getRawValue() as ActionFilters;
  return { ...value, objectKey: value.objectKey.trim() };
}

@Component({
  selector: 'beadle-action-history',
  imports: [ReactiveFormsModule, FORM_FIELD, Fields, GRID, BeadleLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
      </header>
      <section class="card filters" aria-label="Filters">
        <form class="form-fields" [formGroup]="filters" novalidate>
          <beadle-fields [group]="filters" [fields]="fields()" />
          <div class="span-6 filter-actions">
            <button
              type="button"
              class="btn btn-link"
              [disabled]="!filtered()"
              (click)="clearFilters()"
            >
              Clear filters
            </button>
          </div>
        </form>
      </section>
      @if (users.error() || actions.error()) {
        <div class="banner" role="alert">
          <span>The lists of users and actions could not be loaded. {{ retry }}</span>
          <span class="spacer"></span>
          <button type="button" class="btn btn-link" (click)="reloadLists()">Try again</button>
        </div>
      }
      @if (history.isLoading()) {
        <beadle-loading />
      }
      @if (history.error(); as error) {
        <div class="banner" role="alert">
          <span>The action history could not be loaded: {{ errorMessage(error) }}</span>
          <span class="spacer"></span>
          <button type="button" class="btn btn-link" (click)="history.reload()">Try again</button>
        </div>
      }
      <section class="card list">
        <div class="list-head">
          <h2>Actions</h2>
          <span class="muted shown">{{ range() }}</span>
        </div>
        <p class="section-help zone">
          Select a row to see the request the action was given and its message. {{ timeZoneNote }}
        </p>
        <beadle-grid
          label="Action history"
          [empty]="empty()"
          [rows]="rows()"
          [columns]="columns"
          [rowId]="rowId"
          [rowClass]="rowClass"
          (rowClick)="details($event)"
        >
          <ng-template beadleCell="at" let-row>
            <button
              type="button"
              class="btn btn-link at"
              [attr.aria-label]="'Details of ' + row.action + ' at ' + row.at"
              (click)="details(row)"
            >
              {{ row.at }}
            </button>
          </ng-template>
          <ng-template beadleCell="outcome" let-row>
            <span class="chip" [class.success]="row.success" [class.danger]="!row.success">{{
              row.outcome
            }}</span>
          </ng-template>
        </beadle-grid>
        <nav class="pager" aria-label="Pages of the action history">
          <beadle-form-field class="page-size">
            <beadle-label>Rows per page</beadle-label>
            <select beadleInput [formControl]="pageSize">
              @for (size of pageSizes; track size) {
                <option [ngValue]="size">{{ size }}</option>
              }
            </select>
          </beadle-form-field>
          <span class="spacer"></span>
          <span class="position">Page {{ page() + 1 }} of {{ pages() }}</span>
          <button
            type="button"
            class="btn btn-outline-primary"
            [disabled]="page() === 0"
            (click)="previous()"
          >
            Previous
          </button>
          <button
            type="button"
            class="btn btn-outline-primary"
            [disabled]="page() + 1 >= pages()"
            (click)="next()"
          >
            Next
          </button>
        </nav>
      </section>
    </div>
  `,
  styles: `
    .filters {
      margin-bottom: 12px;
      padding: 4px 12px 10px;
    }

    .filter-actions {
      display: flex;
      align-items: flex-end;
      justify-content: flex-end;
      align-self: center;
    }

    .list {
      padding: 10px 12px;

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

    .shown,
    .position {
      font-size: 12px;
    }

    .at {
      padding: 0;
      font-size: inherit;
      text-align: left;
    }

    .pager {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 8px 12px;
      padding-top: 10px;
    }

    .page-size {
      width: 120px;
    }

    .position {
      align-self: center;
    }
  `,
})
export class ActionHistory {
  private readonly api = inject(ActionHistoryApi);
  private readonly dialog = inject(Dialog);

  protected readonly section = ACTION_HISTORY;
  protected readonly errorMessage = errorMessage;
  protected readonly retry = RETRY;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;
  protected readonly pageSizes = PAGE_SIZES;

  protected readonly filters = new FormGroup({
    user: text(),
    action: text(),
    objectKey: text(),
    outcome: text(),
    from: text(),
    to: text('', notBeforeFrom),
  });
  protected readonly pageSize = new FormControl(DEFAULT_PAGE_SIZE, { nonNullable: true });

  private readonly typed = this.filters.valueChanges.pipe(map(() => filtersOf(this.filters)));
  private readonly entered = toSignal(this.typed, { initialValue: NO_FILTERS });
  private readonly applied = signal<ActionFilters>(NO_FILTERS);
  protected readonly page = signal(0);
  private readonly size = signal(DEFAULT_PAGE_SIZE);
  protected readonly filtered = computed(() => !sameFilters(this.entered(), NO_FILTERS));

  protected readonly users = rxResource({ stream: () => this.api.users() });
  protected readonly actions = rxResource({ stream: () => this.api.actions() });

  protected readonly fields = computed<Field[]>(() => [
    choice(
      'user',
      'User',
      everything(
        'Everyone',
        (this.users.hasValue() ? this.users.value() : []).map((user) => ({
          value: user,
          label: user,
        })),
      ),
      '',
      3,
    ),
    choice(
      'action',
      'Action',
      everything(
        'All actions',
        (this.actions.hasValue() ? this.actions.value() : []).map((action) => ({
          value: action,
          label: actionText(action),
        })),
      ),
      '',
      3,
    ),
    line('objectKey', 'Object key', '', 3, {
      placeholder: 'CHG0031001',
      hint: 'the start of a change number, product code, ticket key or department name',
    }),
    choice('outcome', 'Outcome', everything('Any', optionsOf(OUTCOMES)), '', 3),
    line('from', 'From', '', 3, { type: 'date', hint: 'the first day, in your time zone' }),
    line('to', 'To', '', 3, { type: 'date', hint: 'the last day, which counts in full' }),
  ]);

  private readonly query = computed(() =>
    reversed(this.applied()) ? undefined : queryOf(this.applied(), this.page(), this.size()),
  );
  protected readonly history = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.find(params),
  });

  /** The page on screen: the last one loaded stays while the next one loads, so the table does not jump. */
  private readonly shown = linkedSignal<
    { page: ActionHistoryPage | undefined; loading: boolean },
    ActionHistoryPage | undefined
  >({
    source: () => ({
      page: this.history.hasValue() ? this.history.value() : undefined,
      loading: this.history.isLoading(),
    }),
    computation: ({ page, loading }, previous) => page ?? (loading ? previous?.value : undefined),
  });

  protected readonly rows = computed(() => (this.shown()?.content ?? []).map(actionRow));
  protected readonly pages = computed(() => {
    const shown = this.shown();
    return shown ? lastPage(shown) + 1 : 1;
  });
  protected readonly range = computed(() => {
    const shown = this.shown();
    return shown ? rangeText(shown) : '';
  });
  protected readonly empty = computed(() =>
    reversed(this.applied())
      ? `${RANGE_PROBLEM}: change From or To.`
      : this.filtered()
        ? 'No action matches the filters.'
        : 'No action recorded yet.',
  );

  protected readonly rowId = (row: ActionRow) => row.record.id;
  protected readonly rowClass = () => 'clickable';
  protected readonly columns: GridColumn<ActionRow>[] = [
    { key: 'at', header: 'At', value: (row) => row.at, sortable: false, width: 200 },
    {
      key: 'user',
      header: 'User',
      value: (row) => row.record.user,
      sortable: false,
      minWidth: 140,
    },
    { key: 'action', header: 'Action', value: (row) => row.action, sortable: false, minWidth: 180 },
    {
      key: 'object',
      header: 'Object',
      value: (row) => row.object,
      sortable: false,
      wrap: true,
      minWidth: 180,
    },
    { key: 'outcome', header: 'Outcome', value: (row) => row.outcome, sortable: false, width: 110 },
    {
      key: 'correlation',
      header: 'Correlation',
      value: (row) => row.record.correlationId ?? '',
      sortable: false,
      cellClass: 'mono',
      width: 300,
    },
  ];

  constructor() {
    const { from, to } = this.filters.controls;
    from.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => to.updateValueAndValidity());
    this.typed
      .pipe(
        debounce((value) =>
          value.objectKey === this.applied().objectKey ? of(0) : timer(TYPING_DELAY),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((value) => this.apply(value));
    this.pageSize.valueChanges.pipe(takeUntilDestroyed()).subscribe((size) => {
      this.size.set(size);
      this.page.set(0);
    });
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.apply(NO_FILTERS);
  }

  private apply(filters: ActionFilters): void {
    const { to } = this.filters.controls;
    if (to.invalid) {
      to.markAsTouched();
    }
    if (!sameFilters(filters, this.applied())) {
      this.applied.set(filters);
      this.page.set(0);
    }
  }

  protected reloadLists(): void {
    this.users.reload();
    this.actions.reload();
  }

  protected previous(): void {
    this.page.update((page) => Math.max(0, page - 1));
  }

  protected next(): void {
    this.page.update((page) => Math.min(this.pages() - 1, page + 1));
  }

  protected details(row: ActionRow): void {
    openDetails(this.dialog, row.record);
  }
}
