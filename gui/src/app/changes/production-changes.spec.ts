import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { productionChange } from '../testing/change-fixtures';
import { text } from '../testing/dom';
import { ChangeDetail } from './change-detail';
import { ProductionChanges } from './production-changes';

async function settle(fixture: ComponentFixture<unknown>) {
  TestBed.tick();
  await new Promise((resolve) => setTimeout(resolve));
  TestBed.tick();
  fixture.detectChanges();
}

describe('production changes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the raised changes with their product, window, risk and tasks', async () => {
    const fixture = TestBed.createComponent(ProductionChanges);
    await settle(fixture);
    http
      .expectOne('/api/changes/integrations')
      .flush({ jiraConnected: true, serviceNowConnected: false });
    http
      .expectOne('/api/changes')
      .flush([productionChange(), productionChange({ id: 8, number: 'CHG0012346' })]);
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    const cells = [...page.querySelectorAll('tbody tr:first-child td')].map(text);

    expect(cells.slice(0, 6)).toEqual([
      'CHG0012345',
      'CertScanner Corporate Technology',
      expect.stringContaining('10 Oct 2026'),
      'CertScanner release: Expiry alerts',
      'Moderate',
      '1',
    ]);
    expect(page.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(text(page.querySelector('dso-integration-note'))).not.toContain('Jira is not connected');
    expect(text(page.querySelector('dso-integration-note'))).toContain(
      'ServiceNow is not connected',
    );
  });

  it('invites to raise the first change when there is none', async () => {
    const fixture = TestBed.createComponent(ProductionChanges);
    await settle(fixture);
    http
      .expectOne('/api/changes/integrations')
      .flush({ jiraConnected: true, serviceNowConnected: true });
    http.expectOne('/api/changes').flush([]);
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page.querySelector('.empty-state h3'))).toBe('No production change yet');
    expect(page.querySelector('dso-integration-note .banner')).toBeNull();
  });

  it('shows a change with its template, its tasks and its description', async () => {
    const fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    await settle(fixture);
    http
      .expectOne('/api/changes/7')
      .flush(productionChange({ url: 'https://bbh.service-now.com/CHG0012345' }));
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page.querySelector('h1'))).toBe('CHG0012345');
    expect(text(page.querySelector('dl.rows'))).toContain('Normal · Software');
    expect(text(page.querySelector('dl.rows'))).toContain('CERT-1 CERT-2');
    expect(text(page.querySelector('.tasks'))).toContain('CTASK0020001');
    expect(text(page.querySelector('pre'))).toBe('Production release of CertScanner (CERT).');
    expect(page.querySelector('a[href="https://bbh.service-now.com/CHG0012345"]')).not.toBeNull();
  });

  it('says when a change cannot be found', async () => {
    const fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 99);
    await settle(fixture);
    http
      .expectOne('/api/changes/99')
      .flush({ detail: 'Change 99 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle(fixture);

    expect(text((fixture.nativeElement as HTMLElement).querySelector('.banner'))).toBe(
      'Change 99 does not exist',
    );
  });
});
