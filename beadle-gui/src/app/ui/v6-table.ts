import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ColDef,
  GridApi,
  GridOptions,
  ICellRendererParams,
  SideBarDef,
  ToolPanelDef,
} from 'ag-grid-community';
import { ICellRendererAngularComp } from 'ag-grid-angular';

/**
 * V6 Table Types & Interfaces according to official BBH V6 Design System.
 */
export interface V6TableMenuOption {
  label: string;
  action?: string;
  icon?: string;
  disabled?: boolean | ((data: any) => boolean);
  onClick?: (data: any) => void;
}

export interface V6ActionsButtonOptions {
  colId?: string;
  headerName?: string;
  width?: number;
  pinned?: 'left' | 'right' | boolean;
  suppressMenu?: boolean;
}

export interface V6ExternalValidationOptions {
  [key: string]: any;
}

export interface V6FilterModel {
  filterType?: string;
  type?: string;
  filter?: any;
  values?: any[];
  [key: string]: any;
}

export interface V6TablePreferences {
  columnState?: any[];
  filterModel?: { [key: string]: any };
  sortModel?: any[];
  groupState?: any[];
  [key: string]: any;
}

export interface V6UserPreferencesChangeEvent {
  type: string;
  preferences: V6TablePreferences;
}

export interface V6TablePreferencesStorage {
  save?(event: V6UserPreferencesChangeEvent): void;
  recover?(): V6TablePreferences;
  recoverColumnDefs?(): ColDef[];
}

export interface V6ApiDataLevel {
  url: string;
  httpMethod?: 'GET' | 'POST';
  requestTransform?: (req: any) => any;
  responseTransform?: (res: any) => any;
  [key: string]: any;
}

export interface V6RequestExtraData {
  [key: string]: any;
}

export interface IServerSideGetRowsRequest {
  startRow?: number;
  endRow?: number;
  rowGroupCols?: any[];
  valueCols?: any[];
  pivotCols?: any[];
  pivotMode?: boolean;
  groupKeys?: string[];
  filterModel?: any;
  sortModel?: any[];
}

export interface V6ServerSideDatasource {
  setAdditionalQueryParams(params: HttpParams): V6ServerSideDatasource;
  setSuppressRequests(value: boolean): void;
  onGridReady(api: GridApi): void;
}

export interface V6TableDefaultDatasourceOptions {
  apiData: V6ApiDataLevel[];
  additionalQueryParams?: HttpParams;
  getTotalRowsAmount?: (
    response: any,
    request: IServerSideGetRowsRequest,
    extraData: V6RequestExtraData,
  ) => Observable<number>;
  errorCallback?: (
    error: HttpErrorResponse,
    request: IServerSideGetRowsRequest,
    extraData: V6RequestExtraData,
  ) => void;
  defaultHttpMethod?: 'GET' | 'POST';
  withCredentials?: boolean;
}

export interface SizeColumnsToFitGridStrategy {
  type: 'fitGridWidth';
  defaultMinWidth?: number;
  defaultMaxWidth?: number;
}

export interface V6TableConfig {
  options: GridOptions;
  autoSizeStrategy?: SizeColumnsToFitGridStrategy;
  preferencesStorage?: V6TablePreferencesStorage;
  serverSideDatasource?: V6ServerSideDatasource;
  specialColumnsPreferencesInternalHandling?: boolean;
  applyOrder?: boolean;
}

/**
 * V6 Table Helpers providing utilities for column IDs, custom headers, and filters.
 */
export class V6TableHelpers {
  static getColId(colDef: ColDef): string {
    return (colDef?.colId ?? (typeof colDef?.field === 'string' ? colDef.field : '')) || '';
  }

  static setCustomHeaderName(colDef: ColDef, customHeaderName: string | undefined): void {
    if (!colDef) return;
    if (customHeaderName === undefined) {
      delete (colDef as any).customHeaderName;
    } else {
      (colDef as any).customHeaderName = customHeaderName;
    }
  }

  static getCustomHeaderName(colDef: ColDef): string | undefined {
    return (colDef as any)?.customHeaderName;
  }

  static getOriginalHeaderName(colDef: ColDef): string {
    if (!colDef) return '';
    return colDef.headerName ?? (typeof colDef.field === 'string' ? colDef.field : '');
  }

  static getHeaderName(colDef: ColDef): string {
    if (!colDef) return '';
    const custom = V6TableHelpers.getCustomHeaderName(colDef);
    if (custom !== undefined && custom !== null && custom !== '') {
      return custom;
    }
    return V6TableHelpers.getOriginalHeaderName(colDef);
  }

  static getStrippedSetFilterModel(colIds: string[]): { [colId: string]: V6FilterModel } {
    const result: { [colId: string]: V6FilterModel } = {};
    for (const id of colIds) {
      result[id] = { filterType: 'set', values: [] };
    }
    return result;
  }

  static isFilterIn(filterModel: { [colId: string]: V6FilterModel }): (colDef: ColDef) => boolean {
    return (colDef: ColDef) => {
      if (!filterModel || !colDef) return false;
      const colId = V6TableHelpers.getColId(colDef);
      return colId in filterModel;
    };
  }

  static hasAsyncSetFilter(): (colDef: ColDef) => boolean {
    return (colDef: ColDef) => {
      if (!colDef) return false;
      const params = (colDef as any).filterParams;
      return !!params?.async || (colDef as any).isAsyncFilter === true;
    };
  }

  static getColumnDefsWithAsyncFilters(
    columnDefs: ColDef[],
    filterModel: { [colId: string]: V6FilterModel },
  ): ColDef[] {
    const filterFn = V6TableHelpers.isFilterIn(filterModel);
    const asyncFn = V6TableHelpers.hasAsyncSetFilter();
    return (columnDefs ?? []).filter((colDef) => filterFn(colDef) && asyncFn(colDef));
  }

  static defaultFilterParams(): V6DefaultFilterParams {
    return {
      buttons: ['apply', 'reset'],
      closeOnApply: true,
      debounceMs: 200,
    };
  }
}

export interface V6DefaultFilterParams {
  buttons: string[];
  closeOnApply: boolean;
  debounceMs: number;
  [key: string]: any;
}

/**
 * Key storage provider for hashing grid column configurations.
 */
export class V6TableKeyStorageProvider {
  static getGridPreferenceKey(options: GridOptions): string {
    const colDefs = (options?.columnDefs as ColDef[]) || [];
    const ids = colDefs
      .map((col) => V6TableHelpers.getColId(col))
      .filter((id) => id && id.length > 0)
      .sort();
    const joined = ids.join('|');
    let hash = 0;
    for (let i = 0; i < joined.length; i++) {
      hash = (hash << 5) - hash + joined.charCodeAt(i);
      hash |= 0;
    }
    return `v6_grid_pref_${Math.abs(hash).toString(16)}`;
  }
}

/**
 * LocalStorage implementation of table preferences storage.
 */
export class V6TablePreferencesLocalStorage implements V6TablePreferencesStorage {
  private static readonly memoryFallback = new Map<string, string>();
  private readonly storageKey: string;

  constructor(options: GridOptions, customKey?: string) {
    this.storageKey = customKey || V6TableKeyStorageProvider.getGridPreferenceKey(options);
  }

  save(event: V6UserPreferencesChangeEvent): void {
    const serialized = JSON.stringify(event.preferences);
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem(this.storageKey, serialized);
        return;
      }
    } catch {}
    V6TablePreferencesLocalStorage.memoryFallback.set(this.storageKey, serialized);
  }

  recover(): V6TablePreferences {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const item = localStorage.getItem(this.storageKey);
        if (item) return JSON.parse(item);
      }
    } catch {}
    const memItem = V6TablePreferencesLocalStorage.memoryFallback.get(this.storageKey);
    if (memItem) {
      try {
        return JSON.parse(memItem);
      } catch {}
    }
    return {};
  }

  recoverColumnDefs(): ColDef[] {
    return [];
  }
}

/**
 * Default Server-side datasource implementation for V6 Table.
 */
export class V6TableDefaultDatasource implements V6ServerSideDatasource {
  private additionalQueryParams?: HttpParams;
  private suppressRequests = true;
  private gridApi?: GridApi;

  constructor(
    private readonly httpClient: HttpClient,
    private readonly options: V6TableDefaultDatasourceOptions,
  ) {
    this.additionalQueryParams = options.additionalQueryParams;
  }

  setAdditionalQueryParams(params: HttpParams): V6ServerSideDatasource {
    this.additionalQueryParams = params;
    return this;
  }

  setSuppressRequests(value: boolean): void {
    this.suppressRequests = value;
  }

  onGridReady(api: GridApi): void {
    this.gridApi = api;
  }
}

/**
 * Component for actions dropdown button cell in V6 Table.
 */
@Component({
  selector: 'v6-actions-button-cell',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="v6-actions-cell-wrapper" (click)="$event.stopPropagation()">
      <button
        type="button"
        class="btn btn-sm v6-actions-trigger"
        [attr.aria-label]="options()?.headerName || 'Row actions'"
        [attr.aria-expanded]="menuOpen()"
        (click)="toggleMenu($event)"
      >
        <span class="v6-dots-icon" aria-hidden="true">&#8942;</span>
      </button>

      @if (menuOpen()) {
        <div class="v6-actions-dropdown-menu show" role="menu">
          @for (opt of menuOptions(); track opt.label) {
            <button
              type="button"
              class="dropdown-item v6-dropdown-item"
              role="menuitem"
              [disabled]="isOptionDisabled(opt)"
              (click)="onSelectOption(opt, $event)"
            >
              @if (opt.icon) {
                <span class="v6-opt-icon" [ngClass]="opt.icon"></span>
              }
              <span>{{ opt.label }}</span>
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      position: relative;
    }
    .v6-actions-cell-wrapper {
      position: relative;
      display: inline-block;
    }
    .v6-actions-trigger {
      background: transparent;
      border: 1px solid transparent;
      border-radius: 4px;
      padding: 2px 8px;
      line-height: 1;
      font-size: 16px;
      color: var(--v6-text-muted, #555);
      cursor: pointer;
      transition: background-color 0.15s, border-color 0.15s;
    }
    .v6-actions-trigger:hover, .v6-actions-trigger:focus {
      background-color: var(--v6-hover-bg, rgba(0,0,0,0.06));
      border-color: var(--v6-border-color, #ccc);
      color: var(--v6-text-color, #111);
    }
    .v6-dots-icon {
      font-weight: bold;
      font-size: 16px;
    }
    .v6-actions-dropdown-menu {
      position: absolute;
      top: 100%;
      right: 0;
      z-index: 1050;
      min-width: 140px;
      padding: 4px 0;
      margin: 2px 0 0;
      font-size: 13px;
      background-color: var(--v6-dropdown-bg, #fff);
      border: 1px solid var(--v6-dropdown-border, #ddd);
      border-radius: 4px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      text-align: left;
    }
    .v6-dropdown-item {
      display: flex;
      align-items: center;
      gap: 6px;
      width: 100%;
      padding: 6px 12px;
      clear: both;
      font-weight: 400;
      color: var(--v6-text-color, #212529);
      background-color: transparent;
      border: 0;
      cursor: pointer;
      font-size: 12px;
    }
    .v6-dropdown-item:hover:not(:disabled) {
      background-color: var(--v6-primary-light, #e9ecef);
      color: var(--v6-primary, #004b87);
    }
    .v6-dropdown-item:disabled {
      color: #999;
      cursor: not-allowed;
    }
  `,
})
export class V6ActionsButtonCellComponent implements ICellRendererAngularComp {
  private readonly elementRef = inject(ElementRef);
  protected readonly menuOpen = signal(false);
  protected readonly menuOptions = signal<V6TableMenuOption[]>([]);
  protected readonly options = signal<V6ActionsButtonOptions | undefined>(undefined);
  protected readonly rowData = signal<any>(null);

  agInit(params: ICellRendererParams & { rowMenuOptions?: V6TableMenuOption[]; options?: V6ActionsButtonOptions }): void {
    this.menuOptions.set(params.rowMenuOptions ?? []);
    this.options.set(params.options);
    this.rowData.set(params.data);
  }

  refresh(params: ICellRendererParams & { rowMenuOptions?: V6TableMenuOption[]; options?: V6ActionsButtonOptions }): boolean {
    this.agInit(params);
    return true;
  }

  toggleMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.menuOpen.update((open) => !open);
  }

  isOptionDisabled(opt: V6TableMenuOption): boolean {
    if (typeof opt.disabled === 'function') {
      return opt.disabled(this.rowData());
    }
    return !!opt.disabled;
  }

  onSelectOption(opt: V6TableMenuOption, event: MouseEvent): void {
    event.stopPropagation();
    this.menuOpen.set(false);
    if (opt.onClick) {
      opt.onClick(this.rowData());
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.menuOpen.set(false);
    }
  }
}

/**
 * Main V6Table static definitions conforming to BBH V6 Table standard.
 */
export class V6Table {
  static defaultServerSideSelectionColumnDef(): ColDef {
    return {
      colId: 'selection',
      headerName: '',
      checkboxSelection: true,
      headerCheckboxSelection: false,
      width: 48,
      minWidth: 48,
      maxWidth: 48,
      pinned: 'left',
      lockPosition: 'left',
      suppressColumnsToolPanel: true,
      resizable: false,
      sortable: false,
      filter: false,
      cellClass: 'v6-table-selection-cell',
    };
  }

  static defaultActionsButtonColumnDef(
    rowMenuOptions: V6TableMenuOption[],
    options?: V6ActionsButtonOptions,
  ): ColDef {
    return {
      colId: options?.colId ?? 'actions',
      headerName: options?.headerName ?? 'Actions',
      width: options?.width ?? 90,
      minWidth: 70,
      maxWidth: 140,
      pinned: options?.pinned ?? 'right',
      lockPosition: options?.pinned === 'right' ? 'right' : undefined,
      sortable: false,
      filter: false,
      resizable: false,
      suppressColumnsToolPanel: true,
      cellRenderer: V6ActionsButtonCellComponent,
      cellRendererParams: {
        rowMenuOptions,
        options,
      },
    };
  }

  static defaultClientSideGridOptions(): GridOptions {
    return {
      theme: 'legacy',
      domLayout: 'autoHeight',
      suppressColumnVirtualisation: true,
      suppressRowVirtualisation: true,
      ensureDomOrder: true,
      suppressAnimationFrame: true,
      enableCellTextSelection: true,
      suppressCellFocus: true,
      rowHeight: 34,
      headerHeight: 34,
      floatingFiltersHeight: 38,
      animateRows: true,
      defaultColDef: {
        resizable: true,
        sortable: true,
        filter: true,
        headerValueGetter: V6Table.defaultHeaderValueGetter(),
      },
    };
  }

  static defaultServerSideGridOptions(): GridOptions {
    return {
      theme: 'legacy',
      rowModelType: 'serverSide',
      domLayout: 'autoHeight',
      rowHeight: 34,
      headerHeight: 34,
      floatingFiltersHeight: 38,
      animateRows: true,
      cacheBlockSize: 50,
      maxBlocksInCache: 10,
      enableCellTextSelection: true,
      suppressCellFocus: true,
      defaultColDef: {
        resizable: true,
        sortable: true,
        filter: true,
        headerValueGetter: V6Table.defaultHeaderValueGetter(),
      },
    };
  }

  static defaultHeaderValueGetter(): (params: any) => string {
    return (params: any) => V6TableHelpers.getHeaderName(params?.colDef);
  }

  static editTableToolPanel(): ToolPanelDef {
    return {
      id: 'editTable',
      labelDefault: 'Edit Table',
      labelKey: 'editTable',
      iconKey: 'columns',
      toolPanel: 'agColumnsToolPanel',
      toolPanelParams: {
        suppressRowGroups: true,
        suppressValues: true,
        suppressPivots: true,
        suppressPivotMode: true,
      },
    };
  }

  static formatColumnToolPanel(validationOptions?: V6ExternalValidationOptions): ToolPanelDef {
    return {
      id: 'formatColumn',
      labelDefault: 'Format Column',
      labelKey: 'formatColumn',
      iconKey: 'filter',
      toolPanel: 'agFiltersToolPanel',
      toolPanelParams: {
        validationOptions,
      },
    };
  }

  static defaultSideBar(): SideBarDef {
    return {
      toolPanels: [
        V6Table.editTableToolPanel(),
        V6Table.formatColumnToolPanel(),
      ],
      defaultToolPanel: '',
    };
  }
}

/**
 * V6 Table Header Search Box Component.
 */
@Component({
  selector: 'v6-table-header-search-box',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="v6-table-search-box">
      <span class="v6-search-icon" aria-hidden="true">&#128269;</span>
      <input
        type="text"
        class="form-control form-control-sm v6-search-input"
        [placeholder]="placeholder()"
        [value]="query()"
        [attr.aria-label]="placeholder()"
        (input)="onInputChange($event)"
      />
      @if (query().length > 0) {
        <button
          type="button"
          class="v6-search-clear-btn"
          aria-label="Clear search"
          (click)="clearSearch()"
        >
          &times;
        </button>
      }
    </div>
  `,
  styles: `
    .v6-table-search-box {
      position: relative;
      display: inline-flex;
      align-items: center;
      width: 220px;
    }
    .v6-search-icon {
      position: absolute;
      left: 8px;
      font-size: 13px;
      color: #888;
      pointer-events: none;
    }
    .v6-search-input {
      padding-left: 28px;
      padding-right: 24px;
      height: 32px;
      font-size: 13px;
      border-radius: 4px;
    }
    .v6-search-clear-btn {
      position: absolute;
      right: 6px;
      background: none;
      border: none;
      font-size: 16px;
      line-height: 1;
      color: #999;
      cursor: pointer;
      padding: 0;
    }
    .v6-search-clear-btn:hover {
      color: #333;
    }
  `,
})
export class V6TableHeaderSearchBoxComponent {
  readonly placeholder = input<string>('Search table...');
  readonly value = input<string>('');
  readonly searchChange = output<string>();

  protected readonly query = signal('');

  constructor() {
    computed(() => {
      this.query.set(this.value() ?? '');
    });
  }

  onInputChange(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.query.set(val);
    this.searchChange.emit(val);
  }

  clearSearch(): void {
    this.query.set('');
    this.searchChange.emit('');
  }
}

/**
 * V6 Table Header Title Component.
 */
@Component({
  selector: 'v6-table-header-title',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="v6-table-header-title-container">
      <h3 class="v6-table-title">{{ title() }}</h3>
      @if (count() !== undefined && count() !== null) {
        <span class="badge v6-table-count-badge" [attr.aria-label]="count() + ' total items'">
          {{ count() }}
        </span>
      }
      @if (subtitle()) {
        <span class="v6-table-subtitle">{{ subtitle() }}</span>
      }
    </div>
  `,
  styles: `
    .v6-table-header-title-container {
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }
    .v6-table-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--v6-title-color, #1a2530);
    }
    .v6-table-count-badge {
      font-size: 11px;
      font-weight: 600;
      padding: 3px 7px;
      border-radius: 10px;
      background-color: var(--v6-badge-bg, #e2e8f0);
      color: var(--v6-badge-color, #334155);
    }
    .v6-table-subtitle {
      font-size: 12px;
      color: var(--v6-text-muted, #64748b);
    }
  `,
})
export class V6TableHeaderTitleComponent {
  readonly title = input.required<string>();
  readonly count = input<number | undefined>(undefined);
  readonly subtitle = input<string | undefined>(undefined);
}

/**
 * Filter summary chip interface.
 */
export interface V6ActiveFilter {
  key: string;
  label: string;
  value: string;
}

/**
 * V6 Table Header Filter Summary Component.
 */
@Component({
  selector: 'v6-table-header-filter-summary',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (filters().length > 0) {
      <div class="v6-filter-summary-bar">
        <span class="v6-filter-summary-label">Active filters:</span>
        <div class="v6-filter-chips">
          @for (f of filters(); track f.key) {
            <span class="v6-filter-chip">
              <span class="v6-chip-label">{{ f.label }}:</span>
              <strong class="v6-chip-val">{{ f.value }}</strong>
              <button
                type="button"
                class="v6-chip-remove"
                [attr.aria-label]="'Remove filter ' + f.label"
                (click)="removeFilter.emit(f.key)"
              >
                &times;
              </button>
            </span>
          }
        </div>
        <button
          type="button"
          class="btn btn-link btn-sm v6-clear-all-filters"
          (click)="clearAll.emit()"
        >
          Clear all
        </button>
      </div>
    }
  `,
  styles: `
    .v6-filter-summary-bar {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 6px;
      padding: 4px 0;
      font-size: 12px;
    }
    .v6-filter-summary-label {
      font-weight: 600;
      color: #64748b;
    }
    .v6-filter-chips {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 4px;
    }
    .v6-filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 8px;
      background-color: var(--v6-chip-bg, #e0f2fe);
      border: 1px solid var(--v6-chip-border, #bae6fd);
      border-radius: 12px;
      color: var(--v6-chip-color, #0369a1);
      font-size: 11px;
    }
    .v6-chip-remove {
      background: none;
      border: none;
      padding: 0;
      margin-left: 2px;
      font-size: 14px;
      line-height: 1;
      color: inherit;
      cursor: pointer;
    }
    .v6-clear-all-filters {
      padding: 0 4px;
      font-size: 11px;
      text-decoration: underline;
      color: #64748b;
    }
    .v6-clear-all-filters:hover {
      color: #0f172a;
    }
  `,
})
export class V6TableHeaderFilterSummaryComponent {
  readonly filters = input<readonly V6ActiveFilter[]>([]);
  readonly removeFilter = output<string>();
  readonly clearAll = output<void>();
}

/**
 * Sort summary item interface.
 */
export interface V6ActiveSort {
  colId: string;
  colName: string;
  direction: 'asc' | 'desc';
}

/**
 * V6 Table Header Sort Summary Component.
 */
@Component({
  selector: 'v6-table-header-sort-summary',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sorts().length > 0) {
      <div class="v6-sort-summary-bar">
        <span class="v6-sort-summary-label">Sorted by:</span>
        @for (s of sorts(); track s.colId) {
          <span class="v6-sort-chip">
            {{ s.colName }} ({{ s.direction === 'asc' ? 'Ascending' : 'Descending' }})
            <button
              type="button"
              class="v6-chip-remove"
              [attr.aria-label]="'Clear sort on ' + s.colName"
              (click)="removeSort.emit(s.colId)"
            >
              &times;
            </button>
          </span>
        }
      </div>
    }
  `,
  styles: `
    .v6-sort-summary-bar {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
    }
    .v6-sort-summary-label {
      font-weight: 600;
      color: #64748b;
    }
    .v6-sort-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 8px;
      background-color: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 12px;
      font-size: 11px;
      color: #334155;
    }
    .v6-chip-remove {
      background: none;
      border: none;
      padding: 0;
      font-size: 14px;
      line-height: 1;
      cursor: pointer;
    }
  `,
})
export class V6TableHeaderSortSummaryComponent {
  readonly sorts = input<readonly V6ActiveSort[]>([]);
  readonly removeSort = output<string>();
}

/**
 * V6 Table Selected Rows Count Component.
 */
@Component({
  selector: 'v6-table-selected-rows-count',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (selectedCount() > 0) {
      <div class="v6-selected-rows-count-bar">
        <span class="v6-selected-label"><strong>{{ selectedCount() }}</strong>@if (totalCount() !== undefined) { of {{ totalCount() }}} selected</span>
        <button
          type="button"
          class="btn btn-link btn-sm v6-clear-selection"
          (click)="clearSelection.emit()"
        >
          Deselect all
        </button>
      </div>
    }
  `,
  styles: `
    .v6-selected-rows-count-bar {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 2px 8px;
      background-color: #eef2ff;
      border: 1px solid #c7d2fe;
      border-radius: 4px;
      font-size: 12px;
      color: #3730a3;
    }
    .v6-clear-selection {
      padding: 0;
      font-size: 11px;
      color: #4f46e5;
    }
    .v6-clear-selection:hover {
      color: #312e81;
    }
  `,
})
export class V6TableSelectedRowsCountComponent {
  readonly selectedCount = input.required<number>();
  readonly totalCount = input<number | undefined>(undefined);
  readonly clearSelection = output<void>();
}

export const V6_TABLE_COMPONENTS = [
  V6TableHeaderSearchBoxComponent,
  V6TableHeaderTitleComponent,
  V6TableHeaderFilterSummaryComponent,
  V6TableHeaderSortSummaryComponent,
  V6TableSelectedRowsCountComponent,
  V6ActionsButtonCellComponent,
] as const;
