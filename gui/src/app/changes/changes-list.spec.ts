import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MY_DEPARTMENT_KEY } from '../beadle/my-department';
import {
  changeSchedule,
  changeTask,
  changeUpdate,
  productionChange,
} from '../testing/change-fixtures';
import { text } from '../testing/dom';
import { department } from '../testing/fixtures';
import { ProductionChange } from './change-api';
import { ChangeFilters, ChangesList, changeRow, matches, sorted } from './changes-list';

const zoneNote = `Times are in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;

const certScanner = productionChange();
const payments = productionChange({
  id: 8,
  number: 'CHG0012346',
  productName: 'Payments Hub',
  fixVersion: 'PAY 1.0',
  state: 'CLOSED',
  schedule: changeSchedule({
    installationStart: '2026-09-01T12:00:00Z',
    installationEnd: '2026-09-01T13:00:00Z',
  }),
  tasks: [
    changeTask({ state: 'CLOSED' }),
    changeTask({ number: 'CTASK0020002', state: 'CANCELED' }),
  ],
  createdAt: '2026-08-30T09:00:00Z',
});
const draft = productionChange({
  id: 9,
  number: 'CHG0012347',
  fixVersion: 'CERT 4.3',
  state: 'DRAFT',
  schedule: changeSchedule({
    installationStart: '2026-11-01T12:00:00Z',
    installationEnd: '2026-11-01T13:00:00Z',
  }),
  tasks: [changeTask(), changeTask({ number: null })],
  createdAt: '2026-10-08T08:00:00Z',
});

const noFilters: ChangeFilters = {
  number: '',
  product: '',
  fixVersion: '',
  state: 'ALL',
  installation: '',
  shortDescription: '',
};

describe('the rows of the changes table', () => {
  const rows = [certScanner, payments, draft].map(changeRow);
  const numbers = (list: { change: ProductionChange }[]) => list.map((row) => row.change.number);

  it('counts the tasks that are not canceled and names the state', () => {
    expect(changeRow(payments)).toMatchObject({ open: false, state: 'Closed', tasks: 1 });
    expect(changeRow(draft)).toMatchObject({ open: true, state: 'Draft', tasks: 2 });
  });

  it('lets an open change be edited unless its last update still waits for ProTech', () => {
    expect(changeRow(draft).editable).toBe(true);
    expect(changeRow(payments).editable).toBe(false);
    expect(changeRow({ ...draft, update: changeUpdate() }).editable).toBe(false);
    expect(changeRow({ ...draft, update: changeUpdate({ status: 'NOT_APPLIED' }) }).editable).toBe(
      true,
    );
  });

  it('filters by every column, the state by stage or by being open', () => {
    const filtered = (filters: Partial<ChangeFilters>) =>
      numbers(rows.filter((row) => matches(row, { ...noFilters, ...filters })));

    expect(filtered({})).toEqual(['CHG0012345', 'CHG0012346', 'CHG0012347']);
    expect(filtered({ state: 'OPEN' })).toEqual(['CHG0012345', 'CHG0012347']);
    expect(filtered({ state: 'CLOSED' })).toEqual(['CHG0012346']);
    expect(filtered({ product: ' PAY ' })).toEqual(['CHG0012346']);
    expect(filtered({ fixVersion: '4.3' })).toEqual(['CHG0012347']);
    expect(filtered({ number: '2345' })).toEqual(['CHG0012345']);
    expect(filtered({ installation: 'nov' })).toEqual(['CHG0012347']);
    expect(filtered({ shortDescription: 'nothing like it' })).toEqual([]);
  });

  it('sorts the state by the workflow and the installation and raise by time', () => {
    expect(numbers(sorted(rows, { active: 'state', direction: 'asc' }))).toEqual([
      'CHG0012347',
      'CHG0012345',
      'CHG0012346',
    ]);
    expect(numbers(sorted(rows, { active: 'state', direction: 'desc' }))).toEqual([
      'CHG0012346',
      'CHG0012345',
      'CHG0012347',
    ]);
    expect(numbers(sorted(rows, { active: 'installation', direction: 'asc' }))).toEqual([
      'CHG0012346',
      'CHG0012345',
      'CHG0012347',
    ]);
    expect(numbers(sorted(rows, { active: 'raised', direction: 'desc' }))).toEqual([
      'CHG0012347',
      'CHG0012345',
      'CHG0012346',
    ]);
    expect(numbers(sorted(rows, { active: 'product', direction: 'desc' }))).toEqual([
      'CHG0012346',
      'CHG0012345',
      'CHG0012347',
    ]);
    expect(numbers(sorted(rows, { active: 'tasks', direction: '' }))).toEqual(numbers(rows));
  });
});

describe('ChangesList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangesList>;

  const page = () => fixture.nativeElement as HTMLElement;
  const shownNumbers = () => [...page().querySelectorAll('tbody tr td:first-child')].map(text);

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function open(departmentId: string | null) {
    if (departmentId === null) {
      localStorage.removeItem(MY_DEPARTMENT_KEY);
    } else {
      localStorage.setItem(MY_DEPARTMENT_KEY, departmentId);
    }
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangesList);
    await settle();
    http
      .expectOne('/api/changes/integrations')
      .flush({ jiraConnected: true, serviceNowConnected: false });
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    await settle();
  }

  async function choose(label: string, option: string) {
    const select = [...page().querySelectorAll<HTMLElement>('mat-select')].find(
      (element) =>
        element.getAttribute('aria-label') === label ||
        text(element.closest('mat-form-field')?.querySelector('mat-label')) === label,
    )!;
    select.click();
    await settle();
    [...document.querySelectorAll<HTMLElement>('mat-option')]
      .find((element) => text(element) === option)!
      .click();
    await settle();
  }

  async function filter(label: string, value: string) {
    const input = page().querySelector<HTMLInputElement>(`input[aria-label="${label}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  afterEach(() => {
    http.verify();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('asks for the department first and remembers the one chosen', async () => {
    await open(null);

    expect(text(page().querySelector('h1'))).toBe('ProTech Changes');
    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'Choose your department to see its ProTech changes.',
    );
    expect(page().querySelector('table')).toBeNull();
    expect(text(page().querySelector('dso-integration-note'))).toContain(
      'ProTech is not connected yet',
    );

    await choose('Your department', 'Fund Services');
    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBe('5');
    http.expectOne('/api/changes?departmentId=5').flush([]);
    await settle();

    expect(text(page().querySelector('.empty-state h3'))).toBe(
      'No ProTech change of Fund Services yet',
    );
    expect(page().querySelector('.empty-state a')?.getAttribute('href')).toBe('/beadle/new-change');
  });

  it('lists the changes of the remembered department with their state, tasks and an edit link when open', async () => {
    await open('3');
    http.expectOne('/api/changes?departmentId=3').flush([certScanner, payments, draft]);
    await settle();

    expect([...page().querySelectorAll('thead tr:first-child th')].map(text)).toEqual([
      'Change',
      'Product',
      'FixVersion',
      'State',
      'Installation',
      'Short description',
      'Tasks',
      'Raised',
      '',
    ]);
    const cells = [...page().querySelectorAll('tbody tr:first-child td')].map(text);
    expect(cells.slice(0, 7)).toEqual([
      'CHG0012345',
      'CertScanner',
      'CERT 4.2',
      'Primary Approval',
      expect.stringContaining('10 Oct 2026'),
      'CertScanner CERT 4.2: Expiry alerts',
      '1',
    ]);
    expect(cells[8]).toBe('Edit');
    expect(text(page().querySelector('.shown'))).toBe('3 of 3 changes');
    expect(
      [...page().querySelectorAll<HTMLAnchorElement>('a[aria-label^="Edit "]')].map((link) =>
        link.getAttribute('href'),
      ),
    ).toEqual(['/beadle/changes/7/edit', '/beadle/changes/9/edit']);
    expect(page().querySelector('a[aria-label="Edit CHG0012346"]')).toBeNull();
    expect(text(page().querySelector('.zone'))).toBe(zoneNote);
    expect(text(page().querySelector('dso-integration-note'))).not.toContain(
      'Jira is not connected',
    );

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    page().querySelector<HTMLElement>('tbody tr:nth-child(2)')!.click();
    expect(navigate).toHaveBeenCalledWith(['/beadle/changes', 8]);
  });

  it('filters and sorts the table in its header', async () => {
    await open('3');
    http.expectOne('/api/changes?departmentId=3').flush([certScanner, payments, draft]);
    await settle();

    await filter('Filter by product', 'pay');
    expect(shownNumbers()).toEqual(['CHG0012346']);
    expect(text(page().querySelector('.shown'))).toBe('1 of 3 changes');
    await filter('Filter by product', '');

    await choose('Filter by state', 'Open');
    expect(shownNumbers()).toEqual(['CHG0012345', 'CHG0012347']);
    await choose('Filter by state', 'Closed');
    expect(shownNumbers()).toEqual(['CHG0012346']);
    await filter('Filter by change', '2345');
    expect(text(page().querySelector('.no-match'))).toBe('No change matches the filters.');
    await filter('Filter by change', '');
    await choose('Filter by state', 'All');

    const header = (label: string) =>
      [...page().querySelectorAll<HTMLElement>('thead tr:first-child th')].find(
        (cell) => text(cell) === label,
      )!;
    header('State').click();
    await settle();
    expect(shownNumbers()).toEqual(['CHG0012347', 'CHG0012345', 'CHG0012346']);
    header('State').click();
    await settle();
    expect(shownNumbers()).toEqual(['CHG0012346', 'CHG0012345', 'CHG0012347']);
    header('Installation').click();
    await settle();
    expect(shownNumbers()).toEqual(['CHG0012346', 'CHG0012345', 'CHG0012347']);

    await choose('Your department', 'Fund Services');
    http.expectOne('/api/changes?departmentId=5').flush([certScanner, payments, draft]);
    await settle();
    expect(header('Installation').getAttribute('aria-sort')).toBe('ascending');
    expect(shownNumbers()).toEqual(['CHG0012346', 'CHG0012345', 'CHG0012347']);
    header('Installation').click();
    await settle();
    expect(shownNumbers()).toEqual(['CHG0012347', 'CHG0012345', 'CHG0012346']);
  });

  it('says when ProTech could not be reached and the table shows what Beadle last read', async () => {
    await open('3');
    http
      .expectOne('/api/changes?departmentId=3')
      .flush([
        payments,
        productionChange({ syncProblem: 'ProTech could not be reached: timed out.' }),
        { ...draft, syncProblem: 'ProTech could not be reached: timed out.' },
      ]);
    await settle();

    expect(text(page().querySelector('.banner[role="status"]'))).toBe(
      'ProTech could not be reached: timed out. The table shows what Beadle last read from ProTech.',
    );
  });

  it('names each change ProTech does not hold', async () => {
    await open('3');
    http
      .expectOne('/api/changes?departmentId=3')
      .flush([
        { ...payments, syncProblem: 'ProTech has no change CHG0012346.' },
        productionChange(),
        { ...draft, syncProblem: 'ProTech has no change CHG0012347.' },
      ]);
    await settle();

    expect(text(page().querySelector('.banner[role="status"]'))).toBe(
      'ProTech has no change CHG0012346. ProTech has no change CHG0012347. ' +
        'The table shows what Beadle last read from ProTech.',
    );
  });

  it('says why the changes or the departments could not be loaded', async () => {
    localStorage.setItem(MY_DEPARTMENT_KEY, '3');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangesList);
    await settle();
    http
      .expectOne('/api/changes/integrations')
      .flush({ jiraConnected: true, serviceNowConnected: true });
    http
      .expectOne('/api/departments')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    http
      .expectOne('/api/changes?departmentId=3')
      .flush({ detail: 'ProTech is down' }, { status: 503, statusText: 'Service Unavailable' });
    await settle();

    expect([...page().querySelectorAll('.banner')].map(text)).toEqual([
      'The departments could not be loaded: Database unavailable',
      'ProTech is down',
    ]);
    expect(page().querySelector('dso-integration-note .banner')).toBeNull();
  });
});
