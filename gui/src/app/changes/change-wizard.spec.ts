import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MY_DEPARTMENT_KEY, MyDepartment } from '../beadle/my-department';
import { LookupItem } from '../core/models';
import {
  changeOptions,
  changeProfile,
  changeTemplate,
  epic,
  jiraVersion,
  productionChange,
  story,
  taskText,
} from '../testing/change-fixtures';
import { buttonOf, fieldOf, inputOf, text } from '../testing/dom';
import { department, productSummary } from '../testing/fixtures';
import { ChangeWizard } from './change-wizard';

const ME = { name: 'Mateusz Matan' };

function configure() {
  TestBed.configureTestingModule({
    imports: [ChangeWizard],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  return TestBed.inject(HttpTestingController);
}

async function settled(fixture: ComponentFixture<ChangeWizard>) {
  TestBed.tick();
  await new Promise((resolve) => setTimeout(resolve));
  TestBed.tick();
  fixture.detectChanges();
}

function flushIntegrations(http: HttpTestingController) {
  http
    .expectOne('/api/changes/integrations')
    .flush({ jiraConnected: false, serviceNowConnected: false });
}

describe('ChangeWizard', () => {
  let fixture: ComponentFixture<ChangeWizard>;
  let http: HttpTestingController;

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const url = (path: string) => `/api/products/1/jira/${path}`;
  const local = (time: string) => new Date(time).toISOString();

  beforeEach(async () => {
    localStorage.removeItem(MY_DEPARTMENT_KEY);
    http = configure();
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
    flushIntegrations(http);
    await settle();
    http.expectOne('/api/me').flush(ME);
    await settle();
  });

  afterEach(() => http.verify());

  const settle = () => settled(fixture);

  async function next() {
    wizard()['next']();
    await settle();
  }

  function jira(path: string): TestRequest {
    return http.expectOne((request) => request.url === url(path));
  }

  async function chooseOption(label: string, option: string) {
    fieldOf(page(), label)!.querySelector<HTMLElement>('mat-select')!.click();
    await settle();
    const panels = document.querySelectorAll('.mat-mdc-select-panel');
    [...panels[panels.length - 1].querySelectorAll<HTMLElement>('mat-option')]
      .find((element) => text(element) === option)!
      .click();
    await settle();
  }

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  function picking(item: LookupItem) {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(item),
    } as unknown as MatDialogRef<unknown>);
  }

  async function chooseCertScanner(profile = changeProfile()) {
    wizard()['departmentId'].setValue(3);
    wizard()['productId'].setValue(1);
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(profile);
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    jira('versions').flush([
      jiraVersion('CERT 4.2', false, '2030-10-20'),
      jiraVersion('CERT 4.3', false, '2030-12-01'),
      jiraVersion('CERT 4.1', true, '2026-09-01'),
    ]);
    await settle();
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

  async function toReview() {
    await toSchedule();
    for (let step = 3; step < 8; step++) {
      await next();
    }
  }

  const schedule = () => wizard()['schedule']()!;
  const details = () => wizard()['details']()!;

  it('says that Jira and ServiceNow are demo ones and lists the products of the chosen department', async () => {
    expect(text(page().querySelector('dso-integration-note'))).toContain(
      'Jira is not connected yet',
    );
    expect([...page().querySelectorAll('.step-label')].map(text)).toEqual([
      'Request data',
      'Jira',
      'Approval',
      'Schedule',
      'Planning',
      'Privileged access',
      'Risk assessment',
      'Secure coding',
      'Review',
      'Raised',
    ]);
    expect(
      wizard()
        ['groups']()
        .map((group) => group.name),
    ).toEqual(['Corporate Technology', 'Not in a department']);

    expect(wizard()['productsInDepartment']()).toEqual([]);
    await chooseOption('Your department', 'Not in a department');
    expect(text(fieldOf(page(), 'Your department')?.querySelector('.mat-mdc-select-value'))).toBe(
      'Not in a department',
    );
    expect(
      wizard()
        ['productsInDepartment']()
        .map((p) => p.name),
    ).toEqual(['Payments Hub']);
    expect(text(fieldOf(page(), 'Product')?.querySelector('mat-hint'))).toBe(
      '1 product in the department',
    );
    wizard()['departmentId'].setValue(3);
    expect(
      wizard()
        ['productsInDepartment']()
        .map((p) => p.name),
    ).toEqual(['CertScanner']);

    await next();
    expect(text(page().querySelector('.step-problem'))).toBe('Choose the product');

    wizard()['productId'].setValue(1);
    await settle();
    expect(wizard()['stepProblem']()).toBe('Wait until the product is loaded');
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
      'CertScanner has no change template yet, so the suggested values are filled in',
    );
    expect(page().querySelector('.defaults-note a')?.getAttribute('href')).toBe(
      '/beadle/admin/products/1',
    );
    await next();
    expect(wizard()['step']()).toBe(1);
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });

  it('shows the request data of a new change with the signed-in user and the product department', async () => {
    await chooseCertScanner(
      changeProfile({ template: changeTemplate({ requestedBy: 'Grace Turner' }) }),
    );

    expect(text(page().querySelector('h2'))).toBe('Generic request data');
    expect(inputOf(page(), 'Change number').value).toBe('');
    expect(inputOf(page(), 'Change number').placeholder).toBe('Given by ProTech when raised');
    expect(inputOf(page(), 'Approval').value).toBe('Not Yet Requested');
    expect(inputOf(page(), 'Opened By').value).toBe('Mateusz Matan');
    expect(inputOf(page(), 'State').value).toBe('Draft');
    expect(inputOf(page(), 'Requested For').value).toBe('Mateusz Matan');
    expect(inputOf(page(), 'Requested By').value).toBe('Grace Turner');
    expect(inputOf(page(), 'Assigned to').value).toBe('Mateusz Matan');
    expect(inputOf(page(), 'Department').value).toBe('Corporate Technology');
    expect(inputOf(page(), 'Risk').value).toBe('Moderate');

    details().controls.riskAssessment.controls.bbhUsers.setValue('All users');
    await settle();
    expect(inputOf(page(), 'Risk').value).toBe('High');

    picking({ value: 'Payments Hub', detail: 'Payments' });
    buttonOf(page(), 'Find Affected CI').click();
    await settle();
    expect(inputOf(page(), 'Affected CI').value).toBe('Payments Hub');
    expect(inputOf(page(), 'Direct business service').value).toBe('Payments');
    await type('Affected CI', 'Payments Hu');
    expect(inputOf(page(), 'Direct business service').value).toBe('');

    await type('Assignment group', ' ');
    await next();
    expect(wizard()['step']()).toBe(0);
    expect(text(page().querySelector('.step-problem'))).toBe('Some fields need your attention.');
    expect(text(fieldOf(page(), 'Assignment group')?.querySelector('mat-error'))).toBe('Required');
  });

  it('raises a change from the request data, the Jira scope, the schedule and the other sections', async () => {
    await chooseCertScanner();
    picking({ value: 'Payments Hub', detail: 'Payments' });
    buttonOf(page(), 'Find Affected CI').click();
    await settle();
    expect(page().querySelector('.defaults-note')).toBeNull();
    await next();
    expect(wizard()['step']()).toBe(1);
    expect(text(page().querySelector('h2'))).toBe('Jira');
    expect(inputOf(page(), 'Jira project').value).toBe('CERT');
    expect(inputOf(page(), 'Jira project').readOnly).toBe(true);

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
    await next();
    expect(wizard()['step']()).toBe(2);
    expect(text(page().querySelector('h2'))).toBe('Approval and Notification');
    expect(details().controls.release.value).toBe('CERT 4.2');
    expect(inputOf(page(), 'L1 approver').value).toBe('Olivia Bennett');
    await type('Business approver', 'Grace Turner');

    await next();
    expect(wizard()['step']()).toBe(3);
    expect(schedule().getRawValue()).toEqual({
      installationStart: '2030-10-20T18:00',
      installationHours: 2,
      validationStart: '2030-10-20T20:00',
      validationHours: 1,
      firstUsage: '2030-10-20T21:00',
      downtimeStart: '2030-10-20T18:00',
      downtimeHours: 2,
    });
    expect(fieldOf(page(), 'Downtime start')).toBeNull();
    await chooseOption('Downtime', 'Yes');
    expect(inputOf(page(), 'Downtime start').value).toBe('2030-10-20T18:00');
    await type('Downtime hours', '1');
    await type('First use', '2030-10-21T08:00');

    await next();
    expect(wizard()['step']()).toBe(4);
    expect(text(page().querySelector('h2'))).toBe('Planning');
    await type('Backout plan', '');
    await next();
    expect(wizard()['step']()).toBe(4);
    expect(wizard()['stepProblem']()).toBe('Some fields need your attention.');
    await type('Backout plan', 'Roll back with the previous image.');

    await next();
    expect(wizard()['step']()).toBe(5);
    await chooseOption('How many privileged accounts', '1');
    await next();
    expect(wizard()['step']()).toBe(5);
    await type('Person', 'Jane Smith');
    await type('Privileged account', 'adm_jsmith');

    await next();
    expect(wizard()['step']()).toBe(6);
    await chooseOption('Business impact', 'High');

    await next();
    expect(wizard()['step']()).toBe(7);
    await type('Secure coding ticket number', 'SEC-12');
    await next();
    expect(wizard()['step']()).toBe(8);

    const preview = http.expectOne('/api/changes/preview');
    expect(preview.request.body.serviceIds).toBeUndefined();
    expect(preview.request.body).toMatchObject({
      productId: 1,
      fixVersion: 'CERT 4.2',
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
      schedule: {
        installationStart: local('2030-10-20T18:00:00'),
        installationEnd: local('2030-10-20T20:00:00'),
        validationStart: local('2030-10-20T20:00:00'),
        validationEnd: local('2030-10-20T21:00:00'),
        firstUsage: local('2030-10-21T08:00:00'),
        downtimeStart: local('2030-10-20T18:00:00'),
        downtimeEnd: local('2030-10-20T19:00:00'),
      },
      template: {
        requestedFor: 'Mateusz Matan',
        requestedBy: 'Mateusz Matan',
        assignedTo: 'Mateusz Matan',
        department: 'Corporate Technology',
        configurationItem: 'Payments Hub',
        directBusinessService: 'Payments',
        release: 'CERT 4.2',
        risk: null,
        downtime: true,
        approvers: { businessApprover: 'Grace Turner', l1Manager: 'Olivia Bennett' },
        planning: { backoutPlan: 'Roll back with the previous image.' },
        privilegedAccess: {
          required: true,
          users: [{ user: 'Jane Smith', account: 'adm_jsmith' }],
        },
        riskAssessment: { businessImpact: 'High', bbhUsers: '5-25' },
        secureCodingTicket: 'SEC-12',
      },
      tasks: [
        taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
        taskText('Validate CertScanner in production', 'Run the smoke tests of CertScanner.'),
      ],
    });
    preview.flush(
      productionChange({ id: null, number: null, createdAt: null, state: 'DRAFT', workflow: [] }),
    );
    await settle();
    expect(wizard()['shortDescription'].value).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(text(page().querySelector('.scope'))).toBe('1 epic and 1 story of FixVersion CERT 4.2');
    expect(text(page().querySelector('dso-change-summary'))).toContain(
      'Change numberGiven by ProTech when raisedApprovalNot Yet Requested',
    );
    wizard()['shortDescription'].setValue('CertScanner 4.2');

    const tasks = wizard()['tasks']()!;
    expect(page().querySelectorAll('dso-change-tasks-form .task-row').length).toBe(2);
    tasks.at(1).controls.shortDescription.setValue(' ');
    await next();
    expect(wizard()['stepProblem']()).toBe('Check the change tasks');
    tasks.at(1).controls.shortDescription.setValue('Validate it');
    buttonOf(page(), 'Add a change task').click();
    await settle();
    tasks.at(2).patchValue({ shortDescription: 'Tell the users', description: 'Send the e-mail.' });

    expect(wizard()['nextLabel']()).toBe('Raise the change in ProTech');
    await next();
    const raised = http.expectOne({ method: 'POST', url: '/api/changes' });
    expect(raised.request.body).toMatchObject({
      fixVersion: 'CERT 4.2',
      shortDescription: 'CertScanner 4.2',
      description: 'Production release of CertScanner (CERT).',
      tasks: [
        taskText('Deploy CertScanner to production', 'Deploy the release of CertScanner.'),
        taskText('Validate it', 'Run the smoke tests of CertScanner.'),
        taskText('Tell the users', 'Send the e-mail.'),
      ],
    });
    raised.flush(productionChange({ shortDescription: 'CertScanner 4.2' }));
    await settle();

    expect(wizard()['step']()).toBe(9);
    expect(text(page().querySelector('h2'))).toBe('CHG0012345 is raised');
    expect(text(page().querySelector('.review-list'))).toBe(
      'CTASK0020001 · Deploy CertScanner to production',
    );
    expect(text(page().querySelector('.next-steps'))).toContain(
      'Olivia Bennett, James Carter approve the change in ProTech.',
    );
    expect(
      [...page().querySelectorAll<HTMLAnchorElement>('.step-actions a')]
        .find((link) => text(link) === 'Open the change')
        ?.getAttribute('href'),
    ).toBe('/beadle/changes/7');
    expect(wizard().hasUnsavedChanges()).toBe(false);

    wizard()['restart']();
    await settle();
    expect(wizard()['step']()).toBe(0);
    expect(wizard()['productId'].value).toBeNull();
    expect(wizard()['fixVersion'].value).toBe('');
  });

  it('keeps the texts the user changed when the same change is previewed again and shows why ProTech refused it', async () => {
    await toReview();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();
    await type('Description', 'Mine');
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
        detail: '5 fields are invalid',
        errors: [
          { field: 'schedule.installationStart', message: 'must be in the future' },
          { field: 'schedule.installationEnd', message: 'must be after the start' },
          { field: 'template.planning.backoutPlan', message: 'must not be blank' },
          { field: 'epicKeys', message: 'CERT-1 is not in Jira project CERT' },
          { field: 'tasks[1].description', message: 'must not be blank' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(wizard()['step']()).toBe(8);
    expect(wizard()['problems']()).toEqual([
      'Installation start: must be in the future',
      'Installation end: must be after the start',
      'Backout plan: must not be blank',
      'Epics: CERT-1 is not in Jira project CERT',
      'Change task 2: description: must not be blank',
    ]);
    expect(text(page().querySelector('.save-problem'))).toContain('5 fields are invalid');
    expect(details().controls.planning.controls.backoutPlan.errors).toEqual({
      server: 'must not be blank',
    });
    expect(schedule().controls.installationStart.errors).toEqual({
      server: 'must be in the future',
    });
    expect(schedule().controls.installationHours.errors).toEqual({
      server: 'must be after the start',
    });
    expect(wizard()['tasks']()!.at(1).controls.description.errors).toEqual({
      server: 'must not be blank',
    });
  });

  it('asks Jira about the project of the product and keeps the epics found until they are found again', async () => {
    wizard()['departmentId'].setValue(3);
    wizard()['productId'].setValue(1);
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    const versions = jira('versions');
    expect(versions.request.params.keys()).toEqual([]);
    versions.flush({ detail: 'Jira is down' }, { status: 502, statusText: 'Bad Gateway' });
    await settle();
    await next();

    expect(text(page().querySelector('.choice-error'))).toBe(
      'The FixVersions could not be loaded: Jira is down. Type the FixVersion.',
    );
    await findEpics('CERT 4.2');
    expect(wizard()['stepProblem']()).toBe('Wait until the epics and stories are loaded');
    const epics = jira('epics');
    expect(epics.request.params.get('fixVersion')).toBe('CERT 4.2');
    expect(epics.request.params.has('project')).toBe(false);
    epics.flush([epic('CERT-1', 'Expiry alerts')]);
    await settle();
    wizard()['toggleEpic']('CERT-1', true);
    await settle();
    expect(text(page().querySelector('.story-group'))).toContain('Loading its stories');
    expect(wizard()['stepProblem']()).toBe('Wait until the epics and stories are loaded');
    const stories = jira('stories');
    expect(stories.request.params.get('epics')).toBe('CERT-1');
    expect(stories.request.params.has('project')).toBe(false);
    stories.flush([]);
    await settle();
    expect(text(page().querySelector('.story-group'))).toContain(
      'No story of this epic carries the FixVersion.',
    );
    expect(wizard()['stepProblem']()).toBeNull();

    await next();
    expect(wizard()['step']()).toBe(2);
    wizard()['goTo'](1);
    await settle();
    expect(wizard()['epicKeys']()).toEqual(['CERT-1']);
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
    expect(wizard()['stepProblem']()).toBe('Wait until the epics and stories are loaded');
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

  it('plans the schedule from the release date, moves the later windows along and refuses times out of order', async () => {
    await toSchedule();
    expect(text(fieldOf(page(), 'Installation hours')?.querySelector('mat-hint'))).toBe(
      'until Sun, 20 Oct 2030, 20:00',
    );

    await type('Installation hours', '0');
    await next();
    expect(wizard()['step']()).toBe(3);
    expect(text(page().querySelector('dso-change-schedule [role="alert"]'))).toBe(
      'The installation must end after it starts',
    );
    expect(text(page().querySelector('.step-problem'))).toBe('Some fields need your attention.');
    expect(wizard().hasUnsavedChanges()).toBe(true);

    await type('Installation hours', '4');
    expect(schedule().getRawValue()).toMatchObject({
      validationStart: '2030-10-20T22:00',
      firstUsage: '2030-10-20T23:00',
      downtimeHours: 4,
    });
    await next();
    expect(wizard()['step']()).toBe(4);

    wizard()['goTo'](3);
    await settle();
    await type('First use', '');
    await next();
    expect(text(page().querySelector('dso-change-schedule [role="alert"]'))).toBe(
      'Enter the date and time of the first use',
    );
  });

  it('moves the installation start it filled in to the release date of another FixVersion', async () => {
    await toSchedule();
    const start = () => schedule().controls.installationStart.value;
    expect(start()).toBe('2030-10-20T18:00');

    async function rescope(version: string) {
      wizard()['goTo'](1);
      await findEpics(version);
      jira('epics').flush([epic('CERT-1', 'Expiry alerts')]);
      await settle();
      wizard()['toggleEpic']('CERT-1', true);
      await settle();
      jira('stories').flush([story('CERT-2', 'E-mail the owner', 'CERT-1')]);
      await settle();
      await next();
      await next();
    }

    await rescope('CERT 4.3');
    expect(wizard()['step']()).toBe(3);
    expect(details().controls.release.value).toBe('CERT 4.3');
    expect(start()).toBe('2030-12-01T18:00');

    await rescope('CERT 4.1');
    expect(start()).toBe('');

    schedule().controls.installationStart.setValue('2030-11-02T18:00');
    await rescope('CERT 4.2');
    expect(start()).toBe('2030-11-02T18:00');
  });

  it('keeps a text the user edited when the change is previewed anew and offers the new text', async () => {
    await toReview();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();
    expect(buttonOf(page(), 'Use the generated text')).toBeUndefined();
    await type('Description', 'Mine');

    wizard()['back']();
    await next();
    http.expectOne('/api/changes/preview').flush(
      productionChange({
        id: null,
        number: null,
        shortDescription: 'CertScanner CERT 4.2: Expiry alerts on 2 November',
        description: 'Production release of CertScanner (CERT) on 2 November.',
      }),
    );
    await settle();

    expect(wizard()['shortDescription'].value).toBe(
      'CertScanner CERT 4.2: Expiry alerts on 2 November',
    );
    expect(wizard()['description'].value).toBe('Mine');
    expect(buttonOf(page(), 'Use the generated text for the short description')).toBeUndefined();
    buttonOf(page(), 'Use the generated text for the description').click();
    await settle();
    expect(wizard()['description'].value).toBe(
      'Production release of CertScanner (CERT) on 2 November.',
    );
    expect(buttonOf(page(), 'Use the generated text')).toBeUndefined();

    wizard()['back']();
    await next();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();
    expect(wizard()['description'].value).toBe('Production release of CertScanner (CERT).');
  });

  it('says why the change could not be previewed and previews it again on request', async () => {
    await toReview();
    http
      .expectOne('/api/changes/preview')
      .flush({ detail: 'Jira is down' }, { status: 502, statusText: 'Bad Gateway' });
    await settle();

    expect(text(page().querySelector('.save-problem strong'))).toBe(
      'The change could not be previewed: Jira is down',
    );
    expect(buttonOf(page(), 'Raise the change in ProTech').disabled).toBe(true);

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/changes/preview').flush(productionChange({ id: null, number: null }));
    await settle();

    expect(page().querySelector('.save-problem')).toBeNull();
    expect(wizard()['shortDescription'].value).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(buttonOf(page(), 'Raise the change in ProTech').disabled).toBe(false);
  });
});

describe('ChangeWizard of a chosen department', () => {
  it('preselects the department chosen in Changes', async () => {
    localStorage.setItem(MY_DEPARTMENT_KEY, '3');
    const http = configure();
    const fixture = TestBed.createComponent(ChangeWizard);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/products').flush([productSummary()]);
    flushIntegrations(http);
    await settled(fixture);
    http.expectOne('/api/me').flush(ME);
    await settled(fixture);

    const page = fixture.nativeElement as HTMLElement;
    expect(text(fieldOf(page, 'Your department')?.querySelector('.mat-mdc-select-value'))).toBe(
      'Corporate Technology',
    );
    expect(text(fieldOf(page, 'Product')?.querySelector('mat-hint'))).toBe(
      '1 product in the department',
    );
    http.verify();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('remembers the department chosen here as the department of the user', async () => {
    localStorage.removeItem(MY_DEPARTMENT_KEY);
    const http = configure();
    const fixture = TestBed.createComponent(ChangeWizard);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/products').flush([productSummary()]);
    flushIntegrations(http);
    await settled(fixture);
    http.expectOne('/api/me').flush(ME);

    fixture.componentInstance['departmentId'].setValue(3);
    await settled(fixture);

    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBe('3');
    expect(TestBed.inject(MyDepartment).departmentId()).toBe(3);
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });
});

describe('ChangeWizard without the signed-in user', () => {
  it('leaves Opened By and the people of the request empty', async () => {
    localStorage.removeItem(MY_DEPARTMENT_KEY);
    const http = configure();
    const fixture = TestBed.createComponent(ChangeWizard);
    const settle = () => settled(fixture);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/products').flush([productSummary()]);
    flushIntegrations(http);
    await settle();
    http.expectOne('/api/me').flush({ detail: 'Sign in' }, { status: 401, statusText: 'No' });
    fixture.componentInstance['departmentId'].setValue(3);
    fixture.componentInstance['productId'].setValue(1);
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    http.expectOne('/api/products/1/jira/versions').flush([]);
    await settle();

    const page = fixture.nativeElement as HTMLElement;
    expect(inputOf(page, 'Opened By').value).toBe('');
    expect(inputOf(page, 'Requested For').value).toBe('');
    expect(inputOf(page, 'Department').value).toBe('Corporate Technology');
    http.verify();
  });
});

describe('ChangeWizard without products', () => {
  it('says why no product can be chosen', async () => {
    const http = configure();
    const fixture = TestBed.createComponent(ChangeWizard);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    http
      .expectOne('/api/products')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    flushIntegrations(http);
    await settled(fixture);
    http.expectOne('/api/me').flush(ME);
    await settled(fixture);

    expect(text((fixture.nativeElement as HTMLElement).querySelector('.choice-error'))).toBe(
      'The products could not be loaded: Database unavailable',
    );
    http.verify();
  });
});
