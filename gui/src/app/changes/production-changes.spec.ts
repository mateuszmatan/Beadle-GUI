import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { changeTemplate, productionChange } from '../testing/change-fixtures';
import { text } from '../testing/dom';
import { ChangeDetail } from './change-detail';
import { ChangeSummary } from './change-summary';
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

  it('lists the raised changes with their product, FixVersion, installation and tasks', async () => {
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

    expect([...page.querySelectorAll('thead th')].map(text)).toEqual([
      'Change',
      'Product',
      'FixVersion',
      'Installation',
      'Short description',
      'Tasks',
      'Raised',
    ]);
    expect(cells.slice(0, 6)).toEqual([
      'CHG0012345',
      'CertScanner Corporate Technology',
      'CERT 4.2',
      expect.stringContaining('10 Oct 2026'),
      'CertScanner CERT 4.2: Expiry alerts',
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

  it('shows a change with its FixVersion, schedule, ServiceNow fields, tasks and description', async () => {
    const fixture = TestBed.createComponent(ChangeDetail);
    fixture.componentRef.setInput('id', 7);
    await settle(fixture);
    http.expectOne('/api/changes/7').flush(
      productionChange({
        url: 'https://bbh.service-now.com/CHG0012345',
        template: changeTemplate({
          release: 'CERT 4.2',
          incident: 'INC0012345',
          downtime: true,
          privilegedAccess: {
            required: true,
            users: [{ user: 'Jane Smith', account: 'adm_jsmith' }],
          },
        }),
      }),
    );
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    const block = (title: string) =>
      [...page.querySelectorAll('dso-change-summary section')].find(
        (section) => text(section.querySelector('h3')) === title,
      );
    const rows = (title: string) =>
      [...block(title)!.querySelectorAll('dt')].map(
        (term) => `${text(term)}: ${text(term.nextElementSibling)}`,
      );

    expect(text(page.querySelector('h1'))).toBe('CHG0012345');
    expect(rows('Change')).toEqual([
      'Product: CertScanner (CERT)',
      'Department: Corporate Technology',
      'FixVersion: CERT 4.2',
      'Type: Normal · Software',
      'Assignment group: Technology Architecture',
      'Affected CI: CertScanner',
      'Release: CERT 4.2',
      'Incident: INC0012345',
      'Problem: not set',
      'Affected clients: not set',
      'Jira project: CERT',
      'Jira: CERT-1 CERT-2',
    ]);
    expect(rows('Schedule')).toEqual([
      expect.stringMatching(/^Installation: Sat, 10 Oct 2026, \d\d:00 to \d\d:00$/),
      expect.stringMatching(/^Validation: Sat, 10 Oct 2026, /),
      expect.stringMatching(/^First usage: Mon, 12 Oct 2026, /),
      'Downtime: Yes',
    ]);
    expect(rows('Approvers')).toEqual([
      'L1 manager: Olivia Bennett',
      'L2 manager: James Carter',
      'Business approver: not set',
    ]);
    expect(rows('Privileged access')).toEqual(['Jane Smith: adm_jsmith']);
    expect(rows('Risk assessment')).toContain('BBH users: 10');
    expect(rows('Risk assessment')).toContain(
      'Backout testing & duration: Tested on QC, about 15 minutes',
    );
    expect(
      [...block('Planning')!.querySelectorAll('h4')].map(
        (title) => `${text(title)}: ${text(title.nextElementSibling)}`,
      ),
    ).toContain('Validation plan: Run the smoke tests.');
    expect(text(page.querySelector('.tasks'))).toContain('CTASK0020001');
    expect(text(page.querySelector('pre'))).toBe('Production release of CertScanner (CERT).');
    expect(page.querySelector('a[href="https://bbh.service-now.com/CHG0012345"]')).not.toBeNull();
    expect(page.querySelector('a[href="/beadle/admin/products/1"]')).not.toBeNull();
  });

  it('says when no privileged access is needed', async () => {
    const fixture = TestBed.createComponent(ChangeSummary);
    fixture.componentRef.setInput('change', productionChange());
    fixture.detectChanges();

    const blocks = [...(fixture.nativeElement as HTMLElement).querySelectorAll('section')];
    const access = blocks.find((block) => text(block.querySelector('h3')) === 'Privileged access');

    expect(text(access?.querySelector('dt'))).toBe('Needed');
    expect(text(access?.querySelector('dd'))).toBe('No');
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
