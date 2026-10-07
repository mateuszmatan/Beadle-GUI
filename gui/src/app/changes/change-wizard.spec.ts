import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  changeProfile,
  epic,
  jiraVersion,
  productionChange,
  story,
} from '../testing/change-fixtures';
import { text } from '../testing/dom';
import { department, product, productSummary, service } from '../testing/fixtures';
import { ChangeWizard, requestLabel } from './change-wizard';

describe('ChangeWizard', () => {
  let fixture: ComponentFixture<ChangeWizard>;
  let http: HttpTestingController;

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const url = (path: string) => `/api/products/1/jira/${path}`;
  const local = (time: string) => new Date(time).toISOString();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ChangeWizard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangeWizard);
    fixture.detectChanges();
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    http.expectOne('/api/products').flush([
      productSummary(),
      productSummary({
        id: 2,
        name: 'Payments Hub',
        code: 'PAY',
        departmentId: null,
        departmentName: null,
      }),
    ]);
    http
      .expectOne('/api/changes/integrations')
      .flush({ jiraConnected: false, serviceNowConnected: false });
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function next() {
    wizard()['next']();
    await settle();
  }

  function jira(path: string): TestRequest {
    return http.expectOne((request) => request.url === url(path));
  }

  async function chooseCertScanner(profile = changeProfile()) {
    wizard()['departmentId'].setValue(3);
    wizard()['productId'].setValue(1);
    await settle();
    http
      .expectOne('/api/products/1')
      .flush(
        product({ services: [service(), service({ id: 11, name: 'api', description: null })] }),
      );
    http.expectOne('/api/products/1/change-profile').flush(profile);
    await settle();
    jira('versions').flush([
      jiraVersion('CERT 4.2', false, '2030-10-20'),
      jiraVersion('CERT 4.3'),
      jiraVersion('CERT 4.1', true, '2026-09-01'),
    ]);
    await settle();
  }

  async function findEpics(version = 'CERT 4.2') {
    wizard()['fixVersion'].setValue(version);
    wizard()['findEpics']();
    await settle();
  }

  async function scope() {
    await findEpics();
    jira('epics').flush([epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]);
    await settle();
    wizard()['toggleEpic']('CERT-1', true);
    await settle();
    jira('stories').flush([
      story('CERT-2', 'E-mail the owner', 'CERT-1'),
      story('CERT-3', 'Teams alert', 'CERT-1'),
    ]);
    await settle();
  }

  async function toSchedule() {
    await chooseCertScanner();
    await next();
    await scope();
    await next();
    await next();
  }

  it('says that Jira and ServiceNow are demo ones and lists the products of the chosen department', async () => {
    expect(text(page().querySelector('dso-integration-note'))).toContain(
      'Jira is not connected yet',
    );
    expect([...page().querySelectorAll('.step-label')].map(text)).toEqual([
      'Product',
      'Jira scope',
      'Details',
      'Schedule',
      'Review',
      'Raised',
    ]);
    expect(
      wizard()
        ['groups']()
        .map((group) => group.name),
    ).toEqual(['Corporate Technology', 'Not in a department']);

    wizard()['departmentId'].setValue(null);
    expect(
      wizard()
        ['productsInDepartment']()
        .map((p) => p.name),
    ).toEqual(['Payments Hub']);
    wizard()['departmentId'].setValue(3);
    expect(
      wizard()
        ['productsInDepartment']()
        .map((p) => p.name),
    ).toEqual(['CertScanner']);

    await next();
    expect(text(page().querySelector('.choice-error'))).toBe('Choose the product');

    wizard()['productId'].setValue(1);
    await settle();
    expect(wizard()['stepProblem']()).toBe('Wait until the product is loaded');
    http.expectOne('/api/products/1').flush(product());
    http
      .expectOne('/api/products/1/change-profile')
      .flush({ detail: 'Product 1 is gone' }, { status: 404, statusText: 'Not Found' });
    await settle();
    expect(wizard()['stepProblem']()).toBe('Choose a product that can be loaded');
    expect(text(page().querySelector('.choice-error'))).toBe('The product could not be loaded.');
  });

  it('fills in the suggested values of a product without defaults and points to Beadle Admin', async () => {
    await chooseCertScanner(changeProfile({ version: null }));

    expect(text(page().querySelector('.defaults-note'))).toContain(
      'CertScanner has no ServiceNow defaults yet, so the suggested values are filled in',
    );
    expect(page().querySelector('.defaults-note a')?.getAttribute('href')).toBe(
      '/beadle/admin/products/1',
    );
    await next();
    expect(wizard()['step']()).toBe(1);
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });

  it('raises a change from the FixVersion, the chosen epics, stories and services, the edited details and the schedule', async () => {
    await chooseCertScanner();
    expect(text(page().querySelector('dl.rows'))).toContain('Olivia Bennett, James Carter');
    expect(page().querySelector('.defaults-note')).toBeNull();
    await next();
    expect(wizard()['step']()).toBe(1);

    await next();
    expect(wizard()['stepProblem']()).toBe('Enter the FixVersion of the release');
    expect(
      wizard()
        ['versionOptions']()
        .map((version) => version.name),
    ).toEqual(['CERT 4.2', 'CERT 4.3', 'CERT 4.1']);
    wizard()['fixVersion'].setValue('4.3');
    expect(
      wizard()
        ['versionOptions']()
        .map((version) => version.name),
    ).toEqual(['CERT 4.3']);
    expect(wizard()['stepProblem']()).toBe('Find the epics of this FixVersion');

    await scope();
    expect(wizard()['storyKeys']()).toEqual(['CERT-2', 'CERT-3']);
    expect(text(page().querySelector('.story-group'))).toContain('CERT-1 Expiry alerts');
    wizard()['toggleStory']('CERT-3', false);
    wizard()['toggleService'](11, false);
    wizard()['toggleService'](10, false);
    await next();
    expect(wizard()['stepProblem']()).toBe('Choose at least one service');
    wizard()['toggleService'](10, true);
    await next();
    expect(wizard()['step']()).toBe(2);

    const details = wizard()['details']()!;
    expect(details.controls.release.value).toBe('CERT 4.2');
    expect(page().querySelector('dso-change-template-form')).not.toBeNull();
    details.controls.planning.controls.backoutPlan.setValue('');
    await next();
    expect(wizard()['step']()).toBe(2);
    expect(wizard()['stepProblem']()).toBe('Some fields need your attention.');
    details.patchValue({
      planning: { backoutPlan: 'Roll back with the previous image.' },
      riskAssessment: { businessImpact: 'High' },
      downtime: true,
    });
    await next();
    expect(wizard()['step']()).toBe(3);

    expect(wizard()['installationDate'].value).toBe('2030-10-20');
    expect(wizard()['schedule'].getRawValue()).toEqual({
      installationStart: { date: '2030-10-20', time: '18:00' },
      installationEnd: { date: '2030-10-20', time: '20:00' },
      validationStart: { date: '2030-10-20', time: '20:00' },
      validationEnd: { date: '2030-10-20', time: '21:00' },
      firstUsage: { date: '2030-10-20', time: '21:00' },
    });
    expect(text(page().querySelector('.window-text'))).toBe(
      'Installation Sun, 20 Oct 2030, 18:00 to 20:00',
    );
    wizard()['schedule'].controls.firstUsage.setValue({ date: '2030-10-21', time: '08:00' });
    await next();
    expect(wizard()['step']()).toBe(4);

    const preview = http.expectOne('/api/changes/preview');
    expect(preview.request.body).toMatchObject({
      productId: 1,
      serviceIds: [10],
      fixVersion: 'CERT 4.2',
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
      schedule: {
        installationStart: local('2030-10-20T18:00:00'),
        installationEnd: local('2030-10-20T20:00:00'),
        validationStart: local('2030-10-20T20:00:00'),
        validationEnd: local('2030-10-20T21:00:00'),
        firstUsage: local('2030-10-21T08:00:00'),
      },
      template: {
        release: 'CERT 4.2',
        downtime: true,
        planning: { backoutPlan: 'Roll back with the previous image.' },
        riskAssessment: { businessImpact: 'High' },
      },
    });
    preview.flush(productionChange({ id: null, number: null, createdAt: null }));
    await settle();
    expect(wizard()['shortDescription'].value).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(text(page().querySelector('dso-change-summary dt:nth-of-type(3) + dd'))).toBe(
      'CERT 4.2',
    );
    expect(text(page().querySelector('.tasks'))).toContain(
      'Deploy gui of CertScanner to production',
    );
    expect(text(page().querySelector('.scope'))).toBe('1 epic and 1 story of FixVersion CERT 4.2');
    wizard()['shortDescription'].setValue('CertScanner 4.2');

    expect(wizard()['nextLabel']()).toBe('Raise the change in ServiceNow');
    await next();
    const raised = http.expectOne({ method: 'POST', url: '/api/changes' });
    expect(raised.request.body).toMatchObject({
      fixVersion: 'CERT 4.2',
      shortDescription: 'CertScanner 4.2',
      description: 'Production release of CertScanner (CERT).',
    });
    raised.flush(productionChange({ shortDescription: 'CertScanner 4.2' }));
    await settle();

    expect(wizard()['step']()).toBe(5);
    expect(text(page().querySelector('h2'))).toBe('CHG0012345 is raised');
    expect(text(page().querySelector('.next-steps'))).toContain(
      'Olivia Bennett, James Carter approve the change in ServiceNow.',
    );
    expect(wizard().hasUnsavedChanges()).toBe(false);

    wizard()['restart']();
    await settle();
    expect(wizard()['step']()).toBe(0);
    expect(wizard()['productId'].value).toBeNull();
    expect(wizard()['fixVersion'].value).toBe('');
  });

  it('keeps the texts the user changed when the same change is previewed again and shows why ServiceNow refused it', async () => {
    await toSchedule();
    wizard()['installationDate'].setValue('2030-11-02');
    await next();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();
    wizard()['description'].setValue('Mine');
    wizard()['shortDescription'].setValue('é'.repeat(81));
    expect(wizard()['shortDescription'].hasError('columnLength')).toBe(true);
    wizard()['shortDescription'].setValue('é'.repeat(80));
    expect(wizard()['shortDescription'].valid).toBe(true);

    wizard()['back']();
    await next();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();
    expect(wizard()['description'].value).toBe('Mine');

    await next();
    http.expectOne({ method: 'POST', url: '/api/changes' }).flush(
      {
        detail: '3 fields are invalid',
        errors: [
          { field: 'schedule.installationStart', message: 'must be in the future' },
          { field: 'template.planning.backoutPlan', message: 'must not be blank' },
          { field: 'epicKeys', message: 'CERT-1 is not in Jira project CERT' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(wizard()['step']()).toBe(4);
    expect(wizard()['problems']()).toEqual([
      'Installation start: must be in the future',
      'Backout plan: must not be blank',
      'Epics: CERT-1 is not in Jira project CERT',
    ]);
    expect(text(page().querySelector('.save-problem'))).toContain('3 fields are invalid');
    expect(wizard()['details']()!.controls.planning.controls.backoutPlan.errors).toEqual({
      server: 'must not be blank',
    });
  });

  it('asks Jira about the project the user typed', async () => {
    await chooseCertScanner();
    await next();
    const key = wizard()['details']()!.controls.jiraProjectKey;

    key.setValue(' pay ');
    await settle();
    const versions = jira('versions');
    expect(versions.request.params.get('project')).toBe('PAY');
    versions.flush([jiraVersion('PAY 1.0')]);
    await findEpics('PAY 1.0');
    const epics = jira('epics');
    expect(epics.request.params.get('fixVersion')).toBe('PAY 1.0');
    expect(epics.request.params.get('project')).toBe('PAY');
    epics.flush([epic('PAY-1', 'Ledger')]);
    await settle();
    wizard()['toggleEpic']('PAY-1', true);
    await settle();
    const stories = jira('stories');
    expect(stories.request.params.get('epics')).toBe('PAY-1');
    expect(stories.request.params.get('project')).toBe('PAY');
    stories.flush([]);
    await settle();
    expect(text(page().querySelector('.story-group'))).toContain(
      'No story of this epic carries the FixVersion.',
    );

    key.setValue('pay-1');
    key.markAsTouched();
    await settle();
    jira('versions').flush({ detail: 'Jira is down' }, { status: 502, statusText: 'Bad Gateway' });
    jira('stories').flush([]);
    jira('epics').flush([epic('CERT-1', 'Expiry alerts')]);
    await settle();
    expect(wizard()['epicKeys']()).toEqual([]);
    expect(text(page().querySelector('mat-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(page().querySelector('.choice-error'))).toBe(
      'The FixVersions could not be loaded: Jira is down. Type the FixVersion.',
    );
  });

  it('chooses every epic or none and forgets the stories of an epic it drops', async () => {
    await chooseCertScanner();
    await next();
    await scope();

    wizard()['chooseAllEpics'](true);
    await settle();
    jira('stories').flush([
      story('CERT-2', 'E-mail the owner', 'CERT-1'),
      story('CERT-6', 'Record it', 'CERT-5'),
    ]);
    await settle();
    expect(wizard()['epicKeys']()).toEqual(['CERT-1', 'CERT-5']);
    expect(wizard()['storyKeys']()).toEqual(['CERT-2', 'CERT-6']);

    wizard()['toggleEpic']('CERT-5', false);
    expect(wizard()['storyKeys']()).toEqual(['CERT-2']);
    await settle();
    jira('stories').flush([story('CERT-2', 'E-mail the owner', 'CERT-1')]);
    await settle();

    wizard()['chooseAllEpics'](false);
    await settle();
    expect(wizard()['epicKeys']()).toEqual([]);
    expect(wizard()['storyKeys']()).toEqual([]);
    expect(wizard()['issueText'](epic('CERT-1', 'A'))).toBe('Done · updated 2026-09-20');
  });

  it('finds the epics of each FixVersion afresh and holds the step while issues cannot be loaded', async () => {
    await chooseCertScanner();
    await next();
    expect(text(page().querySelector('p.empty'))).toBe('Enter the FixVersion and find its epics.');
    wizard()['fixVersion'].setValue(' ');
    wizard()['findEpics']();
    await settle();
    expect(wizard()['searched']()).toBeNull();

    await scope();
    await findEpics('CERT 4.2');
    jira('epics').flush([epic('CERT-1', 'Expiry alerts')]);
    await settle();
    expect(wizard()['epicKeys']()).toEqual(['CERT-1']);

    await findEpics('CERT 4.3');
    expect(wizard()['epicKeys']()).toEqual([]);
    expect(wizard()['storyKeys']()).toEqual([]);
    jira('epics').flush([epic('CERT-5', 'Audit trail')]);
    await settle();
    wizard()['toggleEpic']('CERT-5', true);
    await settle();
    jira('stories').flush(
      { detail: 'fixVersion must not be blank' },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    const errors = () => [...page().querySelectorAll('.choice-error')].map((error) => text(error));
    expect(errors()).toContain('The stories could not be loaded: fixVersion must not be blank');
    expect(wizard()['stepProblem']()).toBe(
      'The epics and stories of this FixVersion could not be loaded',
    );

    await findEpics('CERT 5.0');
    jira('epics').flush({ detail: 'Jira is down' }, { status: 502, statusText: 'Bad Gateway' });
    await settle();
    expect(errors()).toContain('The epics could not be loaded: Jira is down');

    await findEpics('CERT 5.1');
    jira('epics').flush([]);
    await settle();
    expect(text(page().querySelector('p.empty'))).toBe('No epic carries FixVersion CERT 5.1.');
    expect(wizard()['stepProblem']()).toBe('Choose at least one epic');

    wizard()['goTo'](0);
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });

  it('turns the installation date into the times, follows the timing defaults and refuses times out of order', async () => {
    await toSchedule();
    const schedule = wizard()['schedule'].controls;

    wizard()['installationDate'].setValue('2030-11-02');
    expect(schedule.installationEnd.getRawValue()).toEqual({ date: '2030-11-02', time: '20:00' });
    schedule.installationEnd.setValue({ date: '2030-11-02', time: '17:00' });
    await next();
    expect(wizard()['step']()).toBe(3);
    expect(text(page().querySelector('.choice-error'))).toBe(
      'The installation must end after it starts',
    );
    expect(wizard().hasUnsavedChanges()).toBe(true);

    wizard()['goTo'](2);
    await settle();
    wizard()['details']()!.controls.timing.patchValue({
      installationStart: '22:00',
      installationHours: 4,
    });
    expect(schedule.installationEnd.getRawValue()).toEqual({ date: '2030-11-03', time: '02:00' });
    expect(schedule.firstUsage.getRawValue()).toEqual({ date: '2030-11-03', time: '03:00' });

    await next();
    expect(wizard()['step']()).toBe(3);
    schedule.firstUsage.setValue({ date: '2030-11-03', time: '' });
    await next();
    expect(wizard()['stepProblem']()).toBe('Enter the date and time of the first usage');
  });

  it('names the fields of the request in the problems it shows', () => {
    expect(requestLabel('fixVersion')).toBe('FixVersion');
    expect(requestLabel('schedule.firstUsage')).toBe('First usage');
    expect(requestLabel('template.approvers.l2Manager')).toBe('L2 manager');
    expect(requestLabel('somethingElse')).toBeNull();
  });
});
