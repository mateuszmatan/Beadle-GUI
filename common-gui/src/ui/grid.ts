import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  TemplateRef,
  computed,
  contentChildren,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  AgGridAngular,
  ICellRendererAngularComp,
  IFloatingFilterAngularComp,
} from 'ag-grid-angular';
import {
  CellStyleModule,
  ClientSideRowModelModule,
  ColDef,
  CsvExportModule,
  CustomFilterModule,
  GridOptions,
  ICellRendererParams,
  IFilterComp,
  IFloatingFilterParams,
  ModuleRegistry,
  RowAutoHeightModule,
  RowClassParams,
  RowClickedEvent,
  RowStyleModule,
} from 'ag-grid-community';
import { LicenseManager } from 'ag-grid-enterprise';
import { GRID_THEME } from './design-system';

ModuleRegistry.registerModules([
  ClientSideRowModelModule,
  CellStyleModule,
  RowStyleModule,
  RowAutoHeightModule,
  CustomFilterModule,
  CsvExportModule,
]);
LicenseManager.setLicenseKey(AG_GRID_LICENSE_KEY);

export interface GridOption {
  value: unknown;
  label: string;
}

export interface GridFilter {
  control: FormControl;
  label: string;
  options?: readonly GridOption[];
}

export interface GridColumn<T> {
  key: string;
  header: string;
  value?: (row: T) => unknown;
  sortValue?: (row: T) => string | number;
  sort?: 'asc' | 'desc';
  filter?: GridFilter;
  cellClass?: string;
  numeric?: boolean;
  wrap?: boolean;
  width?: number;
  minWidth?: number;
  flex?: number;
}

export function compare(x: string | number, y: string | number): number {
  return typeof x === 'number' && typeof y === 'number'
    ? x - y
    : String(x).localeCompare(String(y), 'en', { numeric: true });
}

@Directive({ selector: 'ng-template[dsoCell]' })
export class DsoCell<T> {
  readonly key = input.required<string>({ alias: 'dsoCell' });
  readonly rows = input<readonly T[]>();
  readonly template = inject<TemplateRef<{ $implicit: T }>>(TemplateRef);

  static ngTemplateContextGuard<T>(
    cell: DsoCell<T>,
    context: unknown,
  ): context is { $implicit: T } {
    return true;
  }
}

interface CellParams<T> extends ICellRendererParams<T> {
  template: TemplateRef<{ $implicit: T }>;
}

@Component({
  selector: 'dso-grid-cell',
  imports: [NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<ng-container *ngTemplateOutlet="template(); context: { $implicit: row() }" />`,
})
export class GridCell<T> implements ICellRendererAngularComp {
  protected readonly template = signal<TemplateRef<{ $implicit: T }> | null>(null);
  protected readonly row = signal<T | undefined>(undefined);

  agInit(params: CellParams<T>): void {
    this.template.set(params.template);
    this.row.set(params.data);
  }

  refresh(params: CellParams<T>): boolean {
    this.agInit(params);
    return true;
  }
}

interface FilterParams extends IFloatingFilterParams {
  filter: GridFilter;
}

@Component({
  selector: 'dso-grid-filter',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (filter(); as filter) {
      @if (filter.options; as options) {
        <select
          class="form-select form-select-sm"
          [formControl]="filter.control"
          [attr.aria-label]="'Filter by ' + filter.label"
        >
          @for (option of options; track option.label) {
            <option [ngValue]="option.value">{{ option.label }}</option>
          }
        </select>
      } @else {
        <input
          class="form-control form-control-sm"
          autocomplete="off"
          placeholder="Filter"
          [formControl]="filter.control"
          [attr.aria-label]="'Filter by ' + filter.label"
        />
      }
    }
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
    }
  `,
})
export class GridFilterCell implements IFloatingFilterAngularComp {
  protected readonly filter = signal<GridFilter | null>(null);

  agInit(params: FilterParams): void {
    this.filter.set(params.filter);
  }

  onParentModelChanged(): void {}
}

class PassingFilter implements IFilterComp {
  private readonly element = document.createElement('span');

  getGui(): HTMLElement {
    return this.element;
  }

  isFilterActive(): boolean {
    return false;
  }

  doesFilterPass(): boolean {
    return true;
  }

  getModel(): null {
    return null;
  }

  setModel(): void {}
}

const INTERACTIVE = 'a, button, input, select, textarea, label';

@Component({
  selector: 'dso-grid',
  imports: [AgGridAngular],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'dso-grid', role: 'region', '[attr.aria-label]': 'label()' },
  template: `
    <ag-grid-angular
      [class]="theme"
      [gridOptions]="options"
      [columnDefs]="columnDefs()"
      [rowData]="rowData()"
      [overlayNoRowsTemplate]="emptyTemplate()"
      (rowClicked)="clicked($event)"
    />
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class DsoGrid<T> {
  readonly rows = input.required<readonly T[]>();
  readonly columns = input.required<readonly GridColumn<T>[]>();
  readonly rowId = input.required<(row: T) => string | number>();
  readonly rowClass = input<(row: T) => string | string[] | undefined>();
  readonly empty = input('');
  readonly label = input<string>();
  readonly templates = input<readonly DsoCell<T>[], readonly DsoCell<T>[] | undefined>([], {
    transform: (templates) => templates ?? [],
  });
  readonly rowClick = output<T>();

  private readonly cells = contentChildren<DsoCell<T>>(DsoCell);

  protected readonly theme = GRID_THEME;
  protected readonly rowData = computed(() => [...this.rows()]);
  protected readonly emptyTemplate = computed(
    () => `<span class="dso-grid-empty">${escapeHtml(this.empty())}</span>`,
  );

  protected readonly options: GridOptions<T> = {
    theme: 'legacy',
    domLayout: 'autoHeight',
    suppressColumnVirtualisation: true,
    suppressRowVirtualisation: true,
    ensureDomOrder: true,
    suppressAnimationFrame: true,
    suppressMovableColumns: true,
    suppressDragLeaveHidesColumns: true,
    enableCellTextSelection: true,
    suppressCellFocus: true,
    rowHeight: 34,
    headerHeight: 34,
    floatingFiltersHeight: 38,
    defaultColDef: {
      resizable: true,
      suppressHeaderMenuButton: true,
      suppressHeaderFilterButton: true,
      suppressFloatingFilterButton: true,
    },
    getRowId: ({ data }) => String(this.rowId()(data)),
    getRowClass: (params: RowClassParams<T>) =>
      params.data ? this.rowClass()?.(params.data) : undefined,
  };

  protected clicked(event: RowClickedEvent<T>): void {
    const target = event.event?.target;
    if (event.data && !(target instanceof Element && target.closest(INTERACTIVE))) {
      this.rowClick.emit(event.data);
    }
  }

  protected readonly columnDefs = computed<ColDef<T>[]>(() => {
    const templates = new Map(
      [...this.templates(), ...this.cells()].map((cell) => [cell.key(), cell.template]),
    );
    return this.columns().map((column) => columnDef(column, templates.get(column.key)));
  });
}

function columnDef<T>(
  column: GridColumn<T>,
  template: TemplateRef<{ $implicit: T }> | undefined,
): ColDef<T> {
  const value = column.value;
  const sortValue = column.sortValue ?? value;
  const classes = [column.cellClass, column.numeric ? 'number' : null].filter(Boolean).join(' ');
  return {
    colId: column.key,
    headerName: column.header,
    headerClass: column.numeric ? 'number' : undefined,
    cellClass: classes || undefined,
    valueGetter: value ? ({ data }) => (data ? value(data) : null) : () => null,
    sortable: !!sortValue,
    sort: column.sort ?? null,
    comparator: sortValue
      ? (_x, _y, a, b) => compare(sortKey(sortValue, a.data), sortKey(sortValue, b.data))
      : undefined,
    filter: column.filter ? PassingFilter : false,
    floatingFilter: !!column.filter,
    floatingFilterComponent: column.filter ? GridFilterCell : undefined,
    floatingFilterComponentParams: column.filter ? { filter: column.filter } : undefined,
    cellRenderer: template ? GridCell : undefined,
    cellRendererParams: template ? { template } : undefined,
    wrapText: column.wrap,
    autoHeight: column.wrap,
    width: column.width,
    minWidth: column.minWidth ?? 80,
    flex: column.width ? undefined : (column.flex ?? 1),
  };
}

function sortKey<T>(read: (row: T) => unknown, row: T | undefined): string | number {
  const value = row === undefined ? '' : read(row);
  return typeof value === 'number' ? value : String(value ?? '');
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"]/g, (character) => `&#${character.charCodeAt(0)};`);
}

export const GRID = [DsoGrid, DsoCell] as const;
