import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { actionPage, actionRecord } from '../testing/fixtures';
import { ActionHistoryApi } from './action-history-api';

describe('ActionHistoryApi', () => {
  let api: ActionHistoryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ActionHistoryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a page of the history with the filters in use only', () => {
    const page = actionPage([actionRecord()], { page: 1, size: 25, total: 26 });
    let answer: unknown;
    api
      .find({
        user: 'Ann Lee',
        action: 'CHANGE_CREATED',
        objectKey: '',
        outcome: undefined,
        from: '2026-10-13T22:00:00.000Z',
        page: 1,
        size: 25,
      })
      .subscribe((value) => (answer = value));

    const request = http.expectOne((call) => call.url === '/api/action-history');
    expect(request.request.method).toBe('GET');
    expect(request.request.urlWithParams).toBe(
      '/api/action-history?user=Ann%20Lee&action=CHANGE_CREATED&from=2026-10-13T22:00:00.000Z&page=1&size=25',
    );
    request.flush(page);
    expect(answer).toEqual(page);
  });

  it('asks for the users and the actions of the filter lists', () => {
    let users: unknown;
    let actions: unknown;
    api.users().subscribe((value) => (users = value));
    api.actions().subscribe((value) => (actions = value));

    http.expectOne({ method: 'GET', url: '/api/action-history/users' }).flush(['Ann Lee']);
    http
      .expectOne({ method: 'GET', url: '/api/action-history/actions' })
      .flush(['CHANGE_CREATED', 'USER_SIGNED_IN']);
    expect(users).toEqual(['Ann Lee']);
    expect(actions).toEqual(['CHANGE_CREATED', 'USER_SIGNED_IN']);
  });
});
