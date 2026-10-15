import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  buttonOf,
  choose,
  fieldOf,
  gridCell,
  gridColumn,
  gridHeaders,
  gridRows,
  inputOf,
  optionsOf,
  selectOf,
  text,
} from '../testing/dom';
import { actionPage, actionRecord } from '../testing/fixtures';
import { ActionDetails } from './action-details';
import { ActionHistory, TYPING_DELAY, actionRow } from './action-history';
import { ActionHistoryPage } from './action-history-model';

const zoneNote = `Times are in your time zone, ${Intl.DateTimeFormat().resolvedOptions().timeZone}.`;
const FIRST = 'page=0&size=50';

const raised = actionRecord();
const signedIn = actionRecord({
  id: 40,
  at: '2026-10-15T08:58:00Z',
  user: 'Ann Lee',
  action: 'USER_SIGNED_IN',
  objectType: 'USER',
  objectId: null,
  objectKey: 'Ann Lee',
  details: '{}',
  correlationId: null,
  durationMs: 3,
});
const failed = actionRecord({
  id: 39,
  at: '2026-10-14T16:20:00Z',
  user: 'system:internal',
  action: 'CALLBACK_INTERNAL',
  objectType: 'TICKET',
  objectId: null,
  objectKey: 'SCP-123',
  outcome: 'FAILURE',
  message: 'No change waits for ticket SCP-123',
});

describe('the rows of the action history', () => {
  it('name the action, the object and the outcome', () => {
    expect(actionRow(failed)).toMatchObject({
      action: 'Beadle-Internal callback',
      object: 'Ticket SCP-123',
      outcome: 'Failure',
      success: false,
    });
    expect(actionRow(raised)).toMatchObject({ outcome: 'Success', success: true });
  });
});

describe('ActionHistory', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ActionHistory>;

  const page = () => fixture.nativeElement as HTMLElement;

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  function answer(params: string, history: ActionHistoryPage) {
    http.expectOne(`/api/action-history?${params}`).flush(history);
  }

  async function open(history = actionPage([raised, signedIn, failed])) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActionHistory);
    await settle();
    http
      .expectOne('/api/action-history/users')
      .flush(['Ann Lee', 'Mateusz Matan', 'system:internal']);
    http.expectOne('/api/action-history/actions').flush(['CALLBACK_INTERNAL', 'CHANGE_RAISED']);
    answer(FIRST, history);
    await settle();
  }

  async function pick(label: string, option: string) {
    choose(selectOf(page(), label), option);
    await settle();
  }

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  async function click(label: string) {
    buttonOf(page(), label).click();
    await settle();
  }

  const position = () => text(page().querySelector('.position'));
  const disabled = (label: string) => buttonOf(page(), label).disabled;

  afterEach(() => http.verify());

  it('lists the newest actions with who did them, to which object, how they ended and their correlation id', async () => {
    await open();

    expect(text(page().querySelector('h1'))).toBe('Action History');
    expect(text(page().querySelector('.page-description'))).toContain(
      'What people did in Beadle and what Beadle-ProTech and Beadle-Internal reported back',
    );
    expect(gridHeaders(page())).toEqual([
      'At',
      'User',
      'Action',
      'Object',
      'Outcome',
      'Correlation',
    ]);
    expect(page().querySelectorAll('.ag-header-cell-sortable')).toHaveLength(0);
    const [first, , third] = gridRows(page());
    expect(
      ['user', 'action', 'object', 'outcome', 'correlation'].map((column) =>
        text(gridCell(first, column)),
      ),
    ).toEqual([
      'Mateusz Matan',
      'Change raised',
      'Change CHG0031001',
      'Success',
      '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10',
    ]);
    expect(text(gridCell(first, 'at'))).toMatch(/^15 Oct 2026, \d\d:00:05$/);
    expect(gridCell(third, 'outcome').querySelector('.chip')?.classList).toContain('danger');
    expect(gridColumn(page(), 'user')).toEqual(['Mateusz Matan', 'Ann Lee', 'system:internal']);
    expect(text(page().querySelector('.shown'))).toBe('Rows 1–3 of 3');
    expect(text(page().querySelector('.zone'))).toBe(
      `Select a row to see the request the action was given and its message. ${zoneNote}`,
    );
    expect(position()).toBe('Page 1 of 1');
    expect(disabled('Previous')).toBe(true);
    expect(disabled('Next')).toBe(true);
    expect(disabled('Clear filters')).toBe(true);
    expect(optionsOf(selectOf(page(), 'User'))).toEqual([
      'Everyone',
      'Ann Lee',
      'Mateusz Matan',
      'system:internal',
    ]);
    expect(optionsOf(selectOf(page(), 'Action'))).toEqual([
      'All actions',
      'Beadle-Internal callback',
      'Change raised',
    ]);
    expect(optionsOf(selectOf(page(), 'Outcome'))).toEqual(['Any', 'Success', 'Failure']);
    expect(inputOf(page(), 'From').type).toBe('date');
    expect(inputOf(page(), 'To').type).toBe('date');
  });

  it('says so when nothing was recorded yet', async () => {
    await open(actionPage([]));

    expect(text(page().querySelector('.dso-grid-empty'))).toBe('No action recorded yet.');
    expect(text(page().querySelector('.shown'))).toBe('No rows');
  });

  it('asks again with every filter chosen, from the first page, and clears them', async () => {
    await open(actionPage([raised], { total: 120 }));
    await click('Next');
    answer('page=1&size=50', actionPage([signedIn], { page: 1, total: 120 }));
    await settle();

    await pick('User', 'Ann Lee');
    answer('user=Ann%20Lee&page=0&size=50', actionPage([signedIn]));
    await settle();
    expect(position()).toBe('Page 1 of 1');
    expect(disabled('Clear filters')).toBe(false);

    await pick('Action', 'Change raised');
    answer('user=Ann%20Lee&action=CHANGE_RAISED&page=0&size=50', actionPage([]));
    await settle();
    expect(text(page().querySelector('.dso-grid-empty'))).toBe('No action matches the filters.');

    await pick('Outcome', 'Failure');
    answer('user=Ann%20Lee&action=CHANGE_RAISED&outcome=FAILURE&page=0&size=50', actionPage([]));

    await type('From', '2026-10-14');
    const from = new Date(2026, 9, 14).toISOString();
    answer(
      `user=Ann%20Lee&action=CHANGE_RAISED&outcome=FAILURE&from=${from}&page=0&size=50`,
      actionPage([]),
    );
    await type('To', '2026-10-15');
    const to = new Date(2026, 9, 16).toISOString();
    answer(
      `user=Ann%20Lee&action=CHANGE_RAISED&outcome=FAILURE&from=${from}&to=${to}&page=0&size=50`,
      actionPage([]),
    );

    await type('Object key', ' chg003 ');
    http.expectNone((request) => request.url === '/api/action-history');
    await new Promise((resolve) => setTimeout(resolve, TYPING_DELAY + 50));
    await settle();
    answer(
      `user=Ann%20Lee&action=CHANGE_RAISED&objectKey=chg003&outcome=FAILURE&from=${from}&to=${to}&page=0&size=50`,
      actionPage([failed]),
    );
    await settle();
    expect(gridColumn(page(), 'object')).toEqual(['Ticket SCP-123']);

    await click('Clear filters');
    answer(FIRST, actionPage([raised, signedIn, failed]));
    await settle();
    expect(selectOf(page(), 'User').value).toBe(selectOf(page(), 'User').options[0].value);
    expect(inputOf(page(), 'Object key').value).toBe('');
    expect(disabled('Clear filters')).toBe(true);
  });

  it('asks nothing while the last day comes before the first and says why', async () => {
    await open();

    await type('To', '2026-10-14');
    answer(`to=${new Date(2026, 9, 15).toISOString()}&page=0&size=50`, actionPage([raised]));
    await type('From', '2026-10-15');

    http.expectNone((request) => request.url === '/api/action-history');
    expect(text(fieldOf(page(), 'To')?.querySelector('.invalid-feedback'))).toBe(
      'The last day comes before the first',
    );
    expect(gridRows(page())).toHaveLength(0);
    expect(text(page().querySelector('.dso-grid-empty'))).toBe(
      'The last day comes before the first: change From or To.',
    );

    await type('To', '2026-10-15');
    answer(
      `from=${new Date(2026, 9, 15).toISOString()}&to=${new Date(2026, 9, 16).toISOString()}&page=0&size=50`,
      actionPage([raised]),
    );
    await settle();
    expect(fieldOf(page(), 'To')?.querySelector('.invalid-feedback')).toBeNull();
  });

  it('pages through the history and keeps the rows on screen while the next page loads', async () => {
    await open(actionPage([raised, signedIn], { size: 50, total: 120 }));
    expect(position()).toBe('Page 1 of 3');
    expect(text(page().querySelector('.shown'))).toBe('Rows 1–2 of 120');
    expect(disabled('Previous')).toBe(true);

    await click('Next');
    expect(gridRows(page())).toHaveLength(2);
    answer('page=1&size=50', actionPage([failed], { page: 1, total: 120 }));
    await settle();
    expect(position()).toBe('Page 2 of 3');
    expect(text(page().querySelector('.shown'))).toBe('Rows 51–51 of 120');

    await click('Next');
    answer('page=2&size=50', actionPage([raised], { page: 2, total: 120 }));
    await settle();
    expect(position()).toBe('Page 3 of 3');
    expect(disabled('Next')).toBe(true);

    await click('Previous');
    answer('page=1&size=50', actionPage([failed], { page: 1, total: 120 }));
    await settle();
    expect(position()).toBe('Page 2 of 3');

    expect(optionsOf(selectOf(page(), 'Rows per page'))).toEqual(['25', '50', '100', '200']);
    await pick('Rows per page', '100');
    answer('page=0&size=100', actionPage([raised, signedIn, failed], { size: 100, total: 120 }));
    await settle();
    expect(position()).toBe('Page 1 of 2');
  });

  it('shows the details of a row clicked or of its time', async () => {
    await open();
    const dialog = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({} as DialogRef<unknown, unknown>);

    gridCell(gridRows(page())[2], 'action').click();
    await settle();
    expect(dialog).toHaveBeenCalledTimes(1);
    expect(dialog.mock.calls[0][0]).toBe(ActionDetails);
    expect(dialog.mock.calls[0][1]?.data).toEqual(failed);

    const at = gridCell(gridRows(page())[0], 'at').querySelector('button')!;
    expect(at.getAttribute('aria-label')).toMatch(/^Details of Change raised at 15 Oct 2026/);
    at.click();
    await settle();
    expect(dialog).toHaveBeenCalledTimes(2);
    expect(dialog.mock.calls[1][1]?.data).toEqual(raised);
  });

  it('offers to try again when the history or its filter lists cannot be loaded', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActionHistory);
    await settle();
    http
      .expectOne('/api/action-history/users')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    http.expectOne('/api/action-history/actions').flush(['CHANGE_RAISED']);
    http
      .expectOne(`/api/action-history?${FIRST}`)
      .flush({ detail: 'The database is not answering.' }, { status: 500, statusText: 'Error' });
    await settle();

    const banners = [...page().querySelectorAll('.banner')].map(text);
    expect(banners).toEqual([
      'The lists of users and actions could not be loaded. Try again in a moment; if it keeps failing, tell the Beadle administrator.Try again',
      'The action history could not be loaded: The database is not answering.Try again',
    ]);

    const [lists, history] = [...page().querySelectorAll<HTMLElement>('.banner')].map((banner) =>
      banner.querySelector('button')!,
    );
    history.click();
    await settle();
    answer(FIRST, actionPage([raised]));
    lists.click();
    await settle();
    http.expectOne('/api/action-history/users').flush(['Mateusz Matan']);
    http.expectOne('/api/action-history/actions').flush(['CHANGE_RAISED']);
    await settle();

    expect(page().querySelector('.banner')).toBeNull();
    expect(gridRows(page())).toHaveLength(1);
    expect(optionsOf(selectOf(page(), 'User'))).toEqual(['Everyone', 'Mateusz Matan']);
  });
});
