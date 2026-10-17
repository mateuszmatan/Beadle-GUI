import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ColDef, GridOptions } from 'ag-grid-community';
import {
  V6ActionsButtonCellComponent,
  V6TableHeaderFilterSummaryComponent,
  V6TableHeaderSearchBoxComponent,
  V6TableHeaderSortSummaryComponent,
  V6TableHeaderTitleComponent,
  V6TableSelectedRowsCountComponent,
  V6Table,
  V6TableHelpers,
  V6TableKeyStorageProvider,
  V6TablePreferencesLocalStorage,
  V6_TABLE_COMPONENTS,
} from './v6-table';

describe('V6Table and V6TableHelpers (Beadle)', () => {
  describe('V6TableHelpers', () => {
    it('gets colId from colId or field', () => {
      expect(V6TableHelpers.getColId({ colId: 'test-id' })).toBe('test-id');
      expect(V6TableHelpers.getColId({ field: 'fieldName' })).toBe('fieldName');
      expect(V6TableHelpers.getColId({ colId: 'c1', field: 'f1' })).toBe('c1');
      expect(V6TableHelpers.getColId({} as ColDef)).toBe('');
    });

    it('sets, gets and deletes customHeaderName', () => {
      const colDef: ColDef = { field: 'status' };
      expect(V6TableHelpers.getCustomHeaderName(colDef)).toBeUndefined();

      V6TableHelpers.setCustomHeaderName(colDef, 'Current Status');
      expect(V6TableHelpers.getCustomHeaderName(colDef)).toBe('Current Status');
      expect(V6TableHelpers.getHeaderName(colDef)).toBe('Current Status');

      V6TableHelpers.setCustomHeaderName(colDef, undefined);
      expect(V6TableHelpers.getCustomHeaderName(colDef)).toBeUndefined();
    });

    it('resolves header name from custom first, then headerName, then field', () => {
      const colDef1: ColDef = { field: 'name', headerName: 'Product Name' };
      expect(V6TableHelpers.getHeaderName(colDef1)).toBe('Product Name');

      V6TableHelpers.setCustomHeaderName(colDef1, 'Application Name');
      expect(V6TableHelpers.getHeaderName(colDef1)).toBe('Application Name');

      const colDef2: ColDef = { field: 'version' };
      expect(V6TableHelpers.getHeaderName(colDef2)).toBe('version');
    });

    it('creates stripped set filter model', () => {
      const model = V6TableHelpers.getStrippedSetFilterModel(['colA', 'colB']);
      expect(model['colA']).toEqual({ filterType: 'set', values: [] });
      expect(model['colB']).toEqual({ filterType: 'set', values: [] });
    });

    it('checks if filter is in filterModel', () => {
      const model = { colA: { filterType: 'set', values: ['ACTIVE'] } };
      const filterIn = V6TableHelpers.isFilterIn(model);

      expect(filterIn({ colId: 'colA' })).toBe(true);
      expect(filterIn({ colId: 'colB' })).toBe(false);
    });

    it('identifies async set filters and filters column definitions', () => {
      const col1: ColDef = { colId: 'c1', filterParams: { async: true } };
      const col2: ColDef = { colId: 'c2' };
      const col3: ColDef = { colId: 'c3', isAsyncFilter: true } as any;

      expect(V6TableHelpers.hasAsyncSetFilter()(col1)).toBe(true);
      expect(V6TableHelpers.hasAsyncSetFilter()(col2)).toBe(false);
      expect(V6TableHelpers.hasAsyncSetFilter()(col3)).toBe(true);

      const filterModel = { c1: {}, c2: {}, c3: {} };
      const filtered = V6TableHelpers.getColumnDefsWithAsyncFilters([col1, col2, col3], filterModel);
      expect(filtered.map((c) => c.colId)).toEqual(['c1', 'c3']);
    });

    it('provides default filter params', () => {
      const params = V6TableHelpers.defaultFilterParams();
      expect(params.buttons).toEqual(['apply', 'reset']);
      expect(params.closeOnApply).toBe(true);
      expect(params.debounceMs).toBe(200);
    });
  });

  describe('V6TableKeyStorageProvider & Preferences Storage', () => {
    it('generates consistent grid preference key', () => {
      const options: GridOptions = {
        columnDefs: [
          { colId: 'colB' },
          { colId: 'colA' },
        ],
      };
      const key1 = V6TableKeyStorageProvider.getGridPreferenceKey(options);
      const key2 = V6TableKeyStorageProvider.getGridPreferenceKey(options);
      expect(key1).toBe(key2);
      expect(key1.startsWith('v6_grid_pref_')).toBe(true);
    });

    it('saves and recovers user preferences', () => {
      const options: GridOptions = { columnDefs: [{ colId: 'name' }] };
      const storage = new V6TablePreferencesLocalStorage(options, 'test_beadle_pref_storage');

      storage.save({
        type: 'change',
        preferences: { columnState: [{ colId: 'name', hide: false }] },
      });

      const recovered = storage.recover();
      expect(recovered.columnState).toEqual([{ colId: 'name', hide: false }]);
      expect(storage.recoverColumnDefs()).toEqual([]);
    });
  });

  describe('V6Table static methods', () => {
    it('provides defaultServerSideSelectionColumnDef', () => {
      const selCol = V6Table.defaultServerSideSelectionColumnDef();
      expect(selCol.colId).toBe('selection');
      expect(selCol.checkboxSelection).toBe(true);
      expect(selCol.pinned).toBe('left');
      expect(selCol.width).toBe(48);
    });

    it('provides defaultActionsButtonColumnDef', () => {
      const actions = [{ label: 'Edit' }, { label: 'Delete' }];
      const actCol = V6Table.defaultActionsButtonColumnDef(actions, { pinned: 'right', width: 85 });

      expect(actCol.colId).toBe('actions');
      expect(actCol.headerName).toBe('Actions');
      expect(actCol.pinned).toBe('right');
      expect(actCol.width).toBe(85);
      expect(actCol.cellRenderer).toBe(V6ActionsButtonCellComponent);
    });

    it('provides defaultClientSideGridOptions and defaultServerSideGridOptions', () => {
      const clientOpts = V6Table.defaultClientSideGridOptions();
      expect(clientOpts.theme).toBe('legacy');
      expect(clientOpts.rowHeight).toBe(34);
      expect(clientOpts.headerHeight).toBe(34);
      expect(clientOpts.defaultColDef?.sortable).toBe(true);

      const serverOpts = V6Table.defaultServerSideGridOptions();
      expect(serverOpts.rowModelType).toBe('serverSide');
      expect(serverOpts.cacheBlockSize).toBe(50);
    });

    it('provides defaultHeaderValueGetter', () => {
      const getter = V6Table.defaultHeaderValueGetter();
      expect(getter({ colDef: { headerName: 'Status' } })).toBe('Status');
    });

    it('provides tool panels and sidebar configuration', () => {
      const editPanel = V6Table.editTableToolPanel();
      expect(editPanel.id).toBe('editTable');
      expect(editPanel.toolPanel).toBe('agColumnsToolPanel');

      const formatPanel = V6Table.formatColumnToolPanel();
      expect(formatPanel.id).toBe('formatColumn');
      expect(formatPanel.toolPanel).toBe('agFiltersToolPanel');

      const sideBar = V6Table.defaultSideBar();
      expect(sideBar.toolPanels?.length).toBe(2);
    });
  });

  describe('Header Toolbar & Actions Components', () => {
    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [...V6_TABLE_COMPONENTS],
      });
    });

    it('renders and interacts with V6TableHeaderSearchBoxComponent', () => {
      const fixture = TestBed.createComponent(V6TableHeaderSearchBoxComponent);
      fixture.componentRef.setInput('placeholder', 'Filter changes...');
      fixture.detectChanges();

      const inputEl = fixture.nativeElement.querySelector('input') as HTMLInputElement;
      expect(inputEl.placeholder).toBe('Filter changes...');

      let emitted = '';
      fixture.componentInstance.searchChange.subscribe((val) => (emitted = val));

      inputEl.value = 'build';
      inputEl.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(emitted).toBe('build');

      const clearBtn = fixture.nativeElement.querySelector('.v6-search-clear-btn');
      expect(clearBtn).not.toBeNull();
      clearBtn.click();
      fixture.detectChanges();

      expect(emitted).toBe('');
      expect(inputEl.value).toBe('');
    });

    it('renders V6TableHeaderTitleComponent with count badge', () => {
      const fixture = TestBed.createComponent(V6TableHeaderTitleComponent);
      fixture.componentRef.setInput('title', 'Changes');
      fixture.componentRef.setInput('count', 18);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.v6-table-title').textContent.trim()).toBe('Changes');
      expect(fixture.nativeElement.querySelector('.v6-table-count-badge').textContent.trim()).toBe('18');
    });

    it('renders V6TableHeaderFilterSummaryComponent and emits remove and clear', () => {
      const fixture = TestBed.createComponent(V6TableHeaderFilterSummaryComponent);
      fixture.componentRef.setInput('filters', [
        { key: 'status', label: 'Status', value: 'Open' },
      ]);
      fixture.detectChanges();

      let removedKey = '';
      fixture.componentInstance.removeFilter.subscribe((k) => (removedKey = k));
      let cleared = false;
      fixture.componentInstance.clearAll.subscribe(() => (cleared = true));

      const removeBtn = fixture.nativeElement.querySelector('.v6-chip-remove');
      removeBtn.click();
      expect(removedKey).toBe('status');

      const clearAllBtn = fixture.nativeElement.querySelector('.v6-clear-all-filters');
      clearAllBtn.click();
      expect(cleared).toBe(true);
    });

    it('renders V6TableHeaderSortSummaryComponent', () => {
      const fixture = TestBed.createComponent(V6TableHeaderSortSummaryComponent);
      fixture.componentRef.setInput('sorts', [
        { colId: 'created', colName: 'Date', direction: 'desc' },
      ]);
      fixture.detectChanges();

      let removedSort = '';
      fixture.componentInstance.removeSort.subscribe((col) => (removedSort = col));

      const chip = fixture.nativeElement.querySelector('.v6-sort-chip');
      expect(chip.textContent).toContain('Date');
      expect(chip.textContent).toContain('Descending');

      fixture.nativeElement.querySelector('.v6-chip-remove').click();
      expect(removedSort).toBe('created');
    });

    it('renders V6TableSelectedRowsCountComponent', () => {
      const fixture = TestBed.createComponent(V6TableSelectedRowsCountComponent);
      fixture.componentRef.setInput('selectedCount', 3);
      fixture.componentRef.setInput('totalCount', 15);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('3 of 15 selected');

      let deselected = false;
      fixture.componentInstance.clearSelection.subscribe(() => (deselected = true));

      fixture.nativeElement.querySelector('.v6-clear-selection').click();
      expect(deselected).toBe(true);
    });

    it('interacts with V6ActionsButtonCellComponent', () => {
      const fixture = TestBed.createComponent(V6ActionsButtonCellComponent);
      let executedAction = '';
      const menuOptions = [
        {
          label: 'Approve',
          onClick: (data: any) => {
            executedAction = `Approve-${data.id}`;
          },
        },
      ];

      fixture.componentInstance.agInit({
        data: { id: 101 },
        rowMenuOptions: menuOptions,
        options: { headerName: 'Actions' },
      } as any);
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('.v6-actions-trigger');
      btn.click();
      fixture.detectChanges();

      const dropdownItem = fixture.nativeElement.querySelector('.v6-dropdown-item');
      expect(dropdownItem.textContent.trim()).toBe('Approve');
      dropdownItem.click();
      fixture.detectChanges();

      expect(executedAction).toBe('Approve-101');
    });
  });
});
