import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import {
  MY_BUSINESS_SERVICE_KEY,
  MyBusinessService,
} from '../business-services/my-business-service';
import { DRAFT_KEY, DRAFT_KEPT, SessionEnd } from '../core/session-end';
import * as fixtures from '../testing/change-fixtures';
import {
  buttonOf,
  checkboxOf,
  choose,
  enter,
  errorOf,
  fieldOf,
  inputOf,
  optionsOf,
  selectOf,
  text,
} from '../testing/dom';
import { businessService, product } from '../testing/fixtures';
import { isoDate } from './change-model';
import { localInput } from './change-schedule-model';
import { ChangeWizard } from './change-wizard';
import { implementationDateOf } from './secure-coding-model';

const { changeOptions, changeProfile, changeSchedule, changeTask, changeTemplate } = fixtures;
const { epic, jiraVersion, productionChange, story } = fixtures;
const CERT_SCANNER = 'CertScanner (CERT)';
const PAYMENTS = product({
  id: 2,
  name: 'Payments Hub',
  businessServiceId: null,
  businessServiceName: null,
});
const DRAFT = productionChange({ id: null, number: null });

describe('ChangeWizard', () => {
  let fixture: ComponentFixture<ChangeWizard>;
  let http: HttpTestingController;

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const value = (label: string, root: ParentNode = page()) => inputOf(root, label).value;
  const shown = (selector: string) => text(page().querySelector(selector));
  const heading = () => shown('h2');
  const stepCount = () => shown('.step-count');
  const stepProblem = () => shown('.step-problem');
  const nextLabel = () => shown('.step-actions .btn-primary');
  const saveProblem = () => shown('.save-problem strong');
  const problems = () => [...page().querySelectorAll('.save-problem li')].map(text);
  const rows = () => [...page().querySelectorAll<HTMLElement>('.task-row')];
  const chosen = (where: string) =>
    [...page().querySelectorAll(`${where} beadle-checkbox`)]
      .filter((box) => box.querySelector('input')!.checked)
      .map((box) => text(box.querySelector('.key')));
  const epicKeys = () => chosen('.issues');
  const storyKeys = () => chosen('.story-group');
  const fail = (request: TestRequest, detail: string, status = 502) =>
    request.flush({ detail }, { status, statusText: 'Refused' });

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  /** Opens the wizard, which first asks for the business services, the products and the signed-in user. */
  async function open(products: object | null = [product(), PAYMENTS], signedIn = true) {
    fixture = TestBed.createComponent(ChangeWizard);
    fixture.detectChanges();
    http
      .expectOne('/api/business-services')
      .flush([businessService(), businessService({ id: 5, name: 'Funds' })]);
    const catalogue = http.expectOne('/api/products');
    products ? catalogue.flush(products) : fail(catalogue, 'Database unavailable', 500);
    http.expectOne('/api/changes/integrations').flush({ internalReachable: true });
    await settle();
    const me = http.expectOne('/api/me');
    signedIn ? me.flush({ name: 'Mateusz Matan', email: null }) : fail(me, 'Sign in', 401);
    await settle();
  }

  beforeEach(async () => {
    localStorage.removeItem(MY_BUSINESS_SERVICE_KEY);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    await open();
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.removeItem(MY_BUSINESS_SERVICE_KEY);
  });

  async function click(element: HTMLElement) {
    element.click();
    await settle();
  }

  const next = () => click(page().querySelector<HTMLElement>('.step-actions .btn-primary')!);
  const back = () => click(buttonOf(page(), 'Back'));
  const tick = (key: string) => click(checkboxOf(page(), key));
  const goTo = (label: string) =>
    click(
      [...page().querySelectorAll<HTMLElement>('.step-bar button')].find(
        (button) => text(button.querySelector('.step-label')) === label,
      )!,
    );

  async function type(label: string, typed: string, root: ParentNode = page()) {
    enter(root, label, typed);
    await settle();
  }

  async function pick(label: string, option: string) {
    choose(selectOf(page(), label), option);
    await settle();
  }

  const jira = (path: string) =>
    http.expectOne((request) => request.url === `/api/products/1/jira/${path}`);
  const previews = () => http.match('/api/changes/preview').filter((request) => !request.cancelled);

  function refuse(url: string, detail: string, fields: Record<string, string>) {
    const list = Object.entries(fields).map(([field, message]) => ({ field, message }));
    http
      .expectOne({ method: 'POST', url })
      .flush({ detail, problems: list }, { status: 400, statusText: 'No' });
    return settle();
  }

  async function chooseCertScanner(profile = changeProfile()) {
    await pick('Your business service', 'Corporate Technology');
    await pick('Product', CERT_SCANNER);
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
    await type('FixVersion', version);
    await click(buttonOf(page(), 'Find epics'));
  }

  async function texts(draft = DRAFT) {
    await settle();
    previews().forEach((request) => request.flush(draft));
    await settle();
  }

  /** Finds the epics of the FixVersion, chooses the first and answers with its stories. */
  async function scope(version = 'CERT 4.2', stories = ['CERT-2', 'CERT-3']) {
    await findEpics(version);
    jira('epics').flush([epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]);
    await settle();
    await tick('CERT-1');
    jira('stories').flush(stories.map((key) => story(key, `Story ${key}`, 'CERT-1')));
    await texts();
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
    for (let step = 3; step < 7; step++) {
      await next();
    }
    http.expectOne('/api/changes/preview').flush(DRAFT);
    await settle();
  }

  async function toTasks() {
    await toReview();
    await next();
    http.expectOne({ method: 'POST', url: '/api/changes' }).flush(productionChange({ tasks: [] }));
    await settle();
  }

  it('shows the steps of a new change and lists only the products of the chosen business service', async () => {
    expect(shown('beadle-integration-note')).toContain('Demo mode.');
    expect(stepCount()).toBe('Step 1 of 8');
    expect([...page().querySelectorAll('.step-label')].map(text).join(', ')).toBe(
      'Request data, Jira, Approval, Schedule, Planning, Privileged access, Risk assessment, Review',
    );
    expect(nextLabel()).toBe('Next: Jira');
    expect(buttonOf(page(), 'Back')).toBeUndefined();
    expect(shown('.unplaced')).toBe(
      'Products without a business service are not listed; an admin must place them in a business service in Beadle Admin first: Payments Hub.',
    );
    expect(optionsOf(selectOf(page(), 'Your business service'))).toEqual(['Corporate Technology']);
    expect(optionsOf(selectOf(page(), 'Product'))).toEqual([]);

    await pick('Your business service', 'Corporate Technology');
    expect(optionsOf(selectOf(page(), 'Product'))).toEqual([CERT_SCANNER]);
    expect(shown('beadle-form-field:has(select) + beadle-form-field beadle-hint')).toBe(
      '1 product in the business service',
    );
    expect(TestBed.inject(MyBusinessService).businessServiceId()).toBe(3);
    await next();
    expect(stepProblem()).toBe('Choose the product');
  });

  it('waits for the product, says why it could not be loaded and loads it again', async () => {
    await pick('Your business service', 'Corporate Technology');
    await pick('Product', CERT_SCANNER);
    await next();
    expect(stepProblem()).toBe('Wait until the product is loaded');

    fail(http.expectOne('/api/products/1/change-profile'), 'Product 1 is gone', 404);
    await settle();
    expect(stepProblem()).toBe('Choose a product that can be loaded');
    expect(shown('.product-error span')).toBe('The product could not be loaded: Product 1 is gone');

    await click(buttonOf(page(), 'Try again'));
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    jira('versions').flush([]);
    await settle();
    expect(page().querySelector('.product-error')).toBeNull();
    expect(page().querySelector('.step-problem')).toBeNull();
  });

  it('fills in the request data with the signed-in user, the product business service and the template', async () => {
    const template = changeTemplate({ requestedBy: 'Grace Turner' });
    await chooseCertScanner(changeProfile({ version: null, template }));

    expect(shown('.defaults-note')).toContain('CertScanner has no change template yet');
    expect(page().querySelector('.defaults-note a')?.getAttribute('href')).toBe(
      '/admin/products/1',
    );
    expect(inputOf(page(), 'Change number').placeholder).toBe('Given by ProTech when raised');
    const facts = [
      'Approval',
      'Opened by',
      'State',
      'Requested for',
      'Requested by',
      'Business service',
    ];
    expect(facts.map((label) => value(label))).toEqual([
      'Not Approved',
      'Mateusz Matan',
      'Draft',
      'Mateusz Matan',
      'Grace Turner',
      'Corporate Technology',
    ]);
    expect(value('Risk')).toBe('Moderate');

    await type('Assignment group', ' ');
    await next();
    expect(stepCount()).toBe('Step 1 of 8');
    expect(stepProblem()).toBe('Some fields need your attention.');
    expect(errorOf(page(), 'Assignment group')).toBe('Required');
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });

  it('leaves Opened by and the people of the request empty without the signed-in user', async () => {
    fixture.destroy();
    await open([product()], false);
    await chooseCertScanner();
    expect([value('Opened by'), value('Requested for'), value('Business service')]).toEqual([
      '',
      '',
      'Corporate Technology',
    ]);
  });

  it('preselects the business service chosen in Changes and says why products could not be loaded', async () => {
    TestBed.inject(MyBusinessService).choose(3);
    fixture.destroy();
    await open();
    expect(text(selectOf(page(), 'Your business service').selectedOptions[0])).toBe(
      'Corporate Technology',
    );
    fixture.destroy();
    await open(null);
    expect(shown('.choice-error')).toBe('The products could not be loaded: Database unavailable');
  });

  it('shows the Jira project, asks for the epics of the FixVersion and fills the release with it', async () => {
    await chooseCertScanner();
    await next();
    expect(heading()).toBe('Jira');
    expect(inputOf(page(), 'Jira project').readOnly).toBe(true);
    expect(value('Jira project')).toBe('CERT');
    await next();
    expect(stepProblem()).toBe('Enter the FixVersion of the release');
    await type('FixVersion', '4.3');
    expect(stepProblem()).toBe('Find the epics of this FixVersion');

    await scope();
    expect(storyKeys()).toEqual(['CERT-2', 'CERT-3']);
    expect(shown('.story-group')).toContain('CERT-1 Expiry alerts');
    await next();
    expect(heading()).toBe('Approval and notification');
    expect(value('L1 approver')).toBe('Olivia Bennett');
    await goTo('Request data');
    expect(value('Release')).toBe('CERT 4.2');
  });

  it('holds each section step until its values are complete and previews the chosen scope', async () => {
    await toSchedule();
    await type('Installation hours', '0');
    await next();
    expect(heading()).toBe('Schedule');
    expect(shown('beadle-change-schedule [role="alert"]')).toBe(
      'The installation must end after it starts',
    );
    expect(stepProblem()).toBe('Some fields need your attention.');
    await type('Installation hours', '4');
    expect(value('First use')).toBe('2030-10-20T23:00');
    await next();
    await type('Backout plan', '');
    await next();
    expect([heading(), stepProblem()]).toEqual(['Planning', 'Some fields need your attention.']);
    await type('Backout plan', 'Roll back with the previous image.');
    await next();
    await pick('How many privileged accounts', '1');
    await next();
    expect(heading()).toBe('Privileged access');
    await type('Person 1', 'Jane Smith');
    await type('Privileged access 1', 'adm_jsmith');
    await next();
    await next();

    const preview = http.expectOne('/api/changes/preview');
    expect(preview.request.body).toMatchObject({ productId: 1, storyKeys: ['CERT-2', 'CERT-3'] });
    preview.flush(DRAFT);
    await settle();
    expect(heading()).toBe('Check the change and raise it in ProTech');
    expect(shown('.scope')).toBe('1 epic and 1 story of FixVersion CERT 4.2');
    expect(shown('beadle-change-summary')).toContain('Change numberGiven by ProTech when raised');
  });

  it('raises the change with the texts of the review and offers the default change tasks', async () => {
    await toReview();
    expect(nextLabel()).toBe('Raise change in ProTech');
    expect(page().querySelector('beadle-change-tasks-form')).toBeNull();
    await type('Short description', 'CertScanner 4.2');

    await next();
    const raised = http.expectOne({ method: 'POST', url: '/api/changes' });
    expect(raised.request.body).toMatchObject({
      fixVersion: 'CERT 4.2',
      shortDescription: 'CertScanner 4.2',
    });
    expect(raised.request.body).not.toHaveProperty('tasks');
    const template = changeTemplate({ configurationItem: 'Payments Hub' });
    raised.flush(productionChange({ template, tasks: [] }));
    await settle();

    expect(heading()).toBe('Change tasks of CHG0012345');
    expect(shown('.raised-note')).toBe(
      'CHG0012345 has been raised in ProTech. Add its change tasks next.',
    );
    expect(stepCount()).toBe('Step 9 of 10');
    expect(shown('.step-actions .btn-outline-primary')).toBe('Add change tasks later');
    expect(buttonOf(page(), 'Back')).toBeUndefined();
    expect(wizard().hasUnsavedChanges()).toBe(true);
    expect(rows().map((row) => text(row.querySelector('.kind')))).toEqual([
      'Release Management',
      'Change task',
    ]);
    expect(value('Change number', rows()[0])).toBe('CHG0012345');
    expect(value('Affected CI', rows()[0])).toBe('Payments Hub');
    expect(value('Task start', rows()[0])).toBe(localInput(new Date('2026-10-10T06:01:00Z')));
  });

  it('creates the change tasks once they are complete and goes on to the secure coding ticket', async () => {
    await toTasks();
    expect(nextLabel()).toBe('Create change tasks in ProTech');
    await type('Short description', ' ', rows()[1]);
    await next();
    expect(stepProblem()).toBe('Check the change tasks');
    http.expectNone({ method: 'POST', url: '/api/changes/7/tasks' });

    await type('Short description', 'Validate it', rows()[1]);
    await click(buttonOf(page(), 'Add change task'));
    await type('Assignment group', 'Cloud Engineering', rows()[2]);
    await type('Short description', 'Tell the users', rows()[2]);
    await type('Description', 'Send the e-mail.', rows()[2]);
    await next();
    const created = http.expectOne({ method: 'POST', url: '/api/changes/7/tasks' });
    expect(created.request.body).toMatchObject({ version: 4, businessServiceId: 3 });
    expect(
      created.request.body.tasks.map(
        (task: { details: { shortDescription: string } }) => task.details.shortDescription,
      ),
    ).toEqual(['Deploy CertScanner to production', 'Validate it', 'Tell the users']);
    created.flush(productionChange({ tasks: [changeTask()] }));
    await settle();

    expect(heading()).toBe('Secure coding ticket of CHG0012345');
    expect(stepCount()).toBe('Step 10 of 10');
    expect(nextLabel()).toBe('Create secure coding ticket in CyberTrack');
  });

  it('creates the secure coding ticket from the defaults of the change and sums the change up', async () => {
    await toTasks();
    await click(buttonOf(page(), 'Add change tasks later'));
    const date = implementationDateOf(changeSchedule().installationStart);
    expect(shown('.step-actions .btn-outline-primary')).toBe('Create ticket later');
    expect(value('Ticket name in CyberTrack')).toBe(`APO-12345_CertScanner-${date}`);
    await type('APO number', ' ');
    await next();
    expect(stepProblem()).toBe('Some fields need your attention.');
    http.expectNone({ method: 'POST', url: '/api/changes/7/secure-coding' });

    await type('APO number', ' APO-777 ');
    await next();
    const ticket = http.expectOne({ method: 'POST', url: '/api/changes/7/secure-coding' });
    expect(ticket.request.body.apoNumber).toBe('APO-777');
    const template = changeTemplate({ secureCodingTicket: 'SCP-1234' });
    ticket.flush(productionChange({ template, tasks: [changeTask()] }));
    await settle();

    expect(heading()).toBe('CHG0012345 has been raised');
    expect(stepCount()).toBe('Step 11 of 11');
    expect(value('Secure coding ticket number')).toBe('SCP-1234');
    expect(shown('.review-list')).toBe(
      'CTASK0020001 · Deploy CertScanner to production · Technology Architecture',
    );
    expect(shown('.next-steps')).toContain('Olivia Bennett, James Carter, Jane Smith approve');
    expect(page().querySelector('.step-actions a')?.getAttribute('href')).toBe('/changes/7');
    expect(wizard().hasUnsavedChanges()).toBe(false);

    await click(buttonOf(page(), 'Raise another change'));
    expect(stepCount()).toBe('Step 1 of 8');
    expect(selectOf(page(), 'Product').value).toBe('');
  });

  it('stays on the review with the reasons ProTech refused the change, marked on the fields of their steps', async () => {
    await toReview();
    await next();
    await refuse('/api/changes', '3 fields are invalid', {
      'schedule.installationEnd': 'must be after the start',
      'template.planning.backoutPlan': 'must not be blank',
      epicKeys: 'CERT-1 is not in Jira project CERT',
    });

    expect(heading()).toBe('Check the change and raise it in ProTech');
    expect(saveProblem()).toBe('The change could not be raised: 3 fields are invalid');
    expect(problems()).toEqual([
      'Installation end: must be after the start',
      'Backout plan: must not be blank',
      'Epics: CERT-1 is not in Jira project CERT',
    ]);
    expect(page().querySelector('beadle-change-tasks-form')).toBeNull();
    await goTo('Planning');
    expect(errorOf(page(), 'Backout plan')).toBe('must not be blank');
    await goTo('Schedule');
    expect(errorOf(page(), 'Installation hours')).toBe('must be after the start');
  });

  it('shows why ProTech refused the change tasks and lets them be added later', async () => {
    await toTasks();
    await next();
    await refuse('/api/changes/7/tasks', '2 fields are invalid', {
      'tasks[0].start': 'must be inside the installation window',
      'tasks[1].details.assignedTo': 'is not a ProTech user',
    });

    expect(heading()).toBe('Change tasks of CHG0012345');
    expect(saveProblem()).toBe('The change tasks could not be created: 2 fields are invalid');
    expect(problems()).toEqual([
      'Change task 1: task start: must be inside the installation window',
      'Change task 2: assigned to: is not a ProTech user',
    ]);
    expect(errorOf(rows()[0], 'Task start')).toBe('must be inside the installation window');
    expect(errorOf(rows()[1], 'Assigned to')).toBe('is not a ProTech user');

    buttonOf(page(), 'Remove change task 2').click();
    buttonOf(page(), 'Remove change task 1').click();
    await next();
    expect(stepProblem()).toBe('Add at least one change task, or choose Add change tasks later');
    http.expectNone({ method: 'POST', url: '/api/changes/7/tasks' });

    await click(buttonOf(page(), 'Add change tasks later'));
    expect(heading()).toBe('Secure coding ticket of CHG0012345');
    expect(page().querySelector('.save-problem')).toBeNull();
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });

  it('shows why CyberTrack refused the ticket and lets it be created later', async () => {
    await toTasks();
    await click(buttonOf(page(), 'Add change tasks later'));
    await next();
    await refuse('/api/changes/7/secure-coding', '2 fields are invalid', {
      apoNumber: 'is too long: it may take at most 40 bytes',
      artifactLink: 'must be a link starting with https:// or http://',
    });

    expect(heading()).toBe('Secure coding ticket of CHG0012345');
    expect(saveProblem()).toBe(
      'The secure coding ticket could not be created: 2 fields are invalid',
    );
    expect(errorOf(page(), 'APO number')).toBe('is too long: it may take at most 40 bytes');
    expect(errorOf(page(), 'Artifact link')).toBe(
      'must be a link starting with https:// or http://',
    );

    await click(buttonOf(page(), 'Create ticket later'));
    expect(heading()).toBe('CHG0012345 has been raised');
    expect(page().querySelector('.save-problem')).toBeNull();
    expect(shown('.review-list')).toBe('None yet. Add them with Edit change on the change page.');
    expect(inputOf(page(), 'Secure coding ticket number').placeholder).toBe(
      'Not created yet: create it on the change page',
    );
    expect(wizard().hasUnsavedChanges()).toBe(false);
  });

  it('writes the short description and description on the Jira step once the stories are loaded', async () => {
    await chooseCertScanner();
    await next();
    const headings = () => [...page().querySelectorAll('h3')].map(text);
    expect(headings()).not.toContain('Text sent to ProTech');
    await findEpics();
    jira('epics').flush([epic('CERT-1', 'Expiry alerts')]);
    await settle();
    await tick('CERT-1');
    expect(previews()).toEqual([]);
    jira('stories').flush([story('CERT-2', 'Mail', 'CERT-1'), story('CERT-3', 'Chat', 'CERT-1')]);
    await settle();

    const [preview, ...more] = previews();
    expect(more).toEqual([]);
    expect(preview.request.body.storyKeys).toEqual(['CERT-2', 'CERT-3']);
    preview.flush(DRAFT);
    await settle();
    expect(headings()).toContain('Text sent to ProTech');
    expect(value('Short description')).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(value('Description')).toBe('Production release of CertScanner (CERT).');

    await tick('CERT-3');
    const [again, ...others] = previews();
    expect(others).toEqual([]);
    expect(again.request.body.storyKeys).toEqual(['CERT-2']);
    again.flush(productionChange({ shortDescription: 'CertScanner 4.2: Alerts' }));
    await settle();
    expect(value('Short description')).toBe('CertScanner 4.2: Alerts');

    await next();
    expect(headings()).not.toContain('Text sent to ProTech');
  });

  it('keeps a text the user edited when the change is previewed anew and offers the new text', async () => {
    await toReview();
    expect(buttonOf(page(), 'Use generated text')).toBeUndefined();
    await type('Description', 'Mine');

    await back();
    await next();
    const moved = { shortDescription: 'Release on 2 November', description: 'On 2 November.' };
    http.expectOne('/api/changes/preview').flush({ ...DRAFT, ...moved });
    await settle();

    expect([value('Short description'), value('Description')]).toEqual([
      'Release on 2 November',
      'Mine',
    ]);
    expect(buttonOf(page(), 'Use generated text for the short description')).toBeUndefined();
    await click(buttonOf(page(), 'Use generated text for the description'));
    expect(value('Description')).toBe('On 2 November.');

    await back();
    await next();
    http.expectOne('/api/changes/preview').flush(DRAFT);
    await settle();
    expect(value('Description')).toBe('Production release of CertScanner (CERT).');
  });

  it('says why the change could not be previewed and previews it again on request', async () => {
    await toSchedule();
    for (let step = 3; step < 7; step++) {
      await next();
    }
    fail(http.expectOne('/api/changes/preview'), 'Jira is down');
    await settle();
    const raise = () => buttonOf(page(), 'Raise change in ProTech');
    expect(saveProblem()).toBe('The change could not be previewed: Jira is down');
    expect(raise().disabled).toBe(true);

    await click(buttonOf(page(), 'Try again'));
    http.expectOne('/api/changes/preview').flush(DRAFT);
    await settle();
    expect(page().querySelector('.save-problem')).toBeNull();
    expect(value('Short description')).toBe('CertScanner CERT 4.2: Expiry alerts');
    expect(raise().disabled).toBe(false);
  });

  it('asks Jira about the project of the product and keeps the epics found until they are found again', async () => {
    await pick('Your business service', 'Corporate Technology');
    await pick('Product', CERT_SCANNER);
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    const versions = jira('versions');
    expect(versions.request.params.keys()).toEqual([]);
    fail(versions, 'Jira is down');
    await settle();
    await next();
    expect(shown('.choice-error')).toBe(
      'The FixVersions could not be listed, so type the FixVersion yourself. Jira is down',
    );

    await findEpics('CERT 4.2');
    const epics = jira('epics');
    expect(epics.request.params.get('fixVersion')).toBe('CERT 4.2');
    expect(epics.request.params.has('project')).toBe(false);
    epics.flush([epic('CERT-1', 'Expiry alerts')]);
    await settle();
    await tick('CERT-1');
    expect(shown('.story-group')).toContain('Loading its stories');
    const stories = jira('stories');
    expect(stories.request.params.get('epics')).toBe('CERT-1');
    expect(stories.request.params.has('project')).toBe(false);
    stories.flush([]);
    await texts();
    expect(shown('.story-group')).toContain('No story of this epic carries the FixVersion.');

    await next();
    expect(heading()).toBe('Approval and notification');
    await goTo('Jira');
    expect(epicKeys()).toEqual(['CERT-1']);
    await texts();
  });

  it('chooses every epic or none and forgets the stories of an epic it drops', async () => {
    await chooseCertScanner();
    await next();
    await scope();

    await click(buttonOf(page(), 'All'));
    jira('stories').flush([story('CERT-2', 'Mail', 'CERT-1'), story('CERT-6', 'Log', 'CERT-5')]);
    await texts();
    expect(epicKeys()).toEqual(['CERT-1', 'CERT-5']);
    expect(storyKeys()).toEqual(['CERT-2', 'CERT-6']);

    await tick('CERT-5');
    jira('stories').flush([story('CERT-2', 'Mail', 'CERT-1')]);
    await settle();
    const [dropped, ...more] = previews();
    expect([dropped.request.body.storyKeys, more]).toEqual([['CERT-2'], []]);
    dropped.flush(DRAFT);
    await settle();

    await click(buttonOf(page(), 'None'));
    expect(epicKeys()).toEqual([]);
    expect(page().querySelector('.story-group')).toBeNull();
    expect(text(checkboxOf(page(), 'CERT-1').closest('beadle-checkbox'))).toBe(
      'CERT-1 Expiry alerts · Done · updated 2026-09-20',
    );
  });

  it('finds the epics of each FixVersion afresh and holds the step while issues cannot be loaded', async () => {
    await chooseCertScanner();
    await next();
    const empty = () => shown('p.empty');
    const errors = () => [...page().querySelectorAll('.choice-error')].map(text);
    await findEpics(' ');
    expect(empty()).toBe('Enter the FixVersion and find its epics.');
    expect(errorOf(page(), 'FixVersion')).toBe('Required');

    await scope();
    await findEpics('CERT 4.3');
    await next();
    expect(stepProblem()).toBe('Wait until the epics and stories are loaded');
    jira('epics').flush([epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]);
    await settle();
    expect(epicKeys()).toEqual([]);
    expect(stepProblem()).toBe('Choose at least one epic');

    await tick('CERT-5');
    fail(jira('stories'), 'fixVersion must not be blank', 400);
    await settle();
    expect(errors()).toContain(
      'The stories could not be loaded: fixVersion must not be blank Try again',
    );
    expect(stepProblem()).toBe('The epics and stories of this FixVersion could not be loaded');
    await click(buttonOf(page().querySelector('.choice-error')!, 'Try again'));
    jira('stories').flush([story('CERT-6', 'Log', 'CERT-5')]);
    await texts();
    expect([storyKeys(), errors()]).toEqual([['CERT-6'], []]);

    await findEpics('CERT 5.0');
    fail(jira('epics'), 'Jira is down');
    await settle();
    expect(errors()).toContain('The epics could not be loaded: Jira is down Try again');

    await findEpics('CERT 5.1');
    jira('epics').flush([]);
    await settle();
    expect(empty()).toBe('No epic carries FixVersion CERT 5.1.');
  });

  it('lists the matching FixVersions under the field and picks one with the keyboard or the mouse', async () => {
    await chooseCertScanner();
    await next();
    const input = inputOf(page(), 'FixVersion');
    const list = () => page().querySelector('[role="listbox"]');
    const options = () => [...page().querySelectorAll<HTMLElement>('[role="option"]')];
    const active = () => input.getAttribute('aria-activedescendant');
    const press = async (key: string) => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key }));
      await settle();
    };
    expect(input.getAttribute('role')).toBe('combobox');
    input.dispatchEvent(new Event('focus'));
    await settle();
    expect(options().map(text)).toEqual([
      'CERT 4.2 · unreleased · 2030-10-20',
      'CERT 4.3 · unreleased · 2030-12-01',
      'CERT 4.1 · released · 2026-09-01',
    ]);
    expect(input.getAttribute('aria-controls')).toBe(list()!.id);
    await press('ArrowUp');
    expect(active()).toBe(options()[2].id);
    await press('ArrowDown');
    await press('ArrowDown');
    expect(options()[1].getAttribute('aria-selected')).toBe('true');
    await press('Enter');
    expect([input.value, list()]).toEqual(['CERT 4.3', null]);
    expect(jira('epics').request.params.get('fixVersion')).toBe('CERT 4.3');

    await type('FixVersion', '4.1');
    expect(options().map(text)).toEqual(['CERT 4.1 · released · 2026-09-01']);
    await press('Escape');
    expect(list()).toBeNull();
    await press('ArrowDown');
    await click(options()[0]);
    expect([input.value, list()]).toEqual(['CERT 4.1', null]);
    expect(jira('epics').request.params.get('fixVersion')).toBe('CERT 4.1');
  });

  it('plans the installation on the release date of the FixVersion Jira names, or the next day, until it is moved', async () => {
    await toSchedule();
    const start = () => value('Installation start');
    expect(start()).toBe('2030-10-20T18:00');
    expect(text(fieldOf(page(), 'Installation hours')?.querySelector('beadle-hint'))).toBe(
      'Until Sun, 20 Oct 2030, 20:00',
    );

    async function rescope(version: string) {
      await goTo('Jira');
      await scope(version, ['CERT-2']);
      await next();
      await next();
    }

    await rescope(' cert 4.3 ');
    expect(heading()).toBe('Schedule');
    expect(start()).toBe('2030-12-01T18:00');
    await goTo('Jira');
    expect(value('FixVersion')).toBe('CERT 4.3');
    await texts();
    await next();
    await next();

    await rescope('CERT 4.1');
    const now = new Date();
    const tomorrow = isoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1));
    expect(start()).toBe(`${tomorrow}T18:00`);

    await type('Installation start', '2030-11-02T18:00');
    await rescope('CERT 4.2');
    expect(start()).toBe('2030-11-02T18:00');
    await goTo('Request data');
    expect(value('Release')).toBe('CERT 4.2');
  });

  /** The session ends while the wizard is open: the user chooses to sign in again, and the wizard opens anew. */
  async function signInAgain() {
    const dialog = vi.spyOn(TestBed.inject(Dialog), 'open').mockReturnValue({
      closed: of(true),
    } as unknown as DialogRef<unknown>);
    const signIn = vi.fn<() => void>();
    const sessionEnd = TestBed.inject(SessionEnd);
    sessionEnd.shown(wizard());
    sessionEnd.ended(signIn);
    expect(dialog).toHaveBeenCalledWith(expect.anything(), {
      data: expect.objectContaining({ message: DRAFT_KEPT }),
    });
    expect(signIn).toHaveBeenCalledTimes(1);
    dialog.mockRestore();
    fixture.destroy();
    await open();
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.match('/api/changes/options').forEach((request) => request.flush(changeOptions()));
    jira('versions').flush([jiraVersion('CERT 4.2', false, '2030-10-20')]);
    jira('epics').flush([epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]);
    await settle();
    jira('stories').flush([story('CERT-2', 'Mail', 'CERT-1'), story('CERT-3', 'Chat', 'CERT-1')]);
    await settle();
    expect(sessionStorage.getItem(DRAFT_KEY)).toBeNull();
  }

  it('keeps its entries when the session ends and opens on the same step with them after the new sign-in', async () => {
    await chooseCertScanner();
    await next();
    await scope();
    await tick('CERT-3');
    await type('Description', 'Mine');
    await texts();
    await next();
    await type('Business approver', 'Grace Turner');
    await next();
    await type('Installation hours', '3');
    expect(wizard().draft()).toMatchObject({ step: 3, storyKeys: ['CERT-2'], description: 'Mine' });
    const firstUse = value('First use');

    await signInAgain();

    expect([heading(), value('Installation hours')]).toEqual(['Schedule', '3']);
    expect(wizard().hasUnsavedChanges()).toBe(true);
    await back();
    expect(value('Business approver')).toBe('Grace Turner');
    await next();
    expect([value('Installation hours'), value('First use')]).toEqual(['3', firstUse]);
    await goTo('Jira');
    expect([value('FixVersion'), epicKeys(), storyKeys()]).toEqual([
      'CERT 4.2',
      ['CERT-1'],
      ['CERT-2'],
    ]);
    await texts();
    expect(value('Description')).toBe('Mine');
  });

  it('comes back to the change tasks of the change it raised, without raising it again', async () => {
    await toTasks();
    await type('Short description', 'Deploy CertScanner 4.2 with care', rows()[0]);

    await signInAgain();

    expect(heading()).toBe('Change tasks of CHG0012345');
    expect(value('Short description', rows()[0])).toBe('Deploy CertScanner 4.2 with care');
    expect(wizard().hasUnsavedChanges()).toBe(true);
    await click(buttonOf(page(), 'Add change tasks later'));
    expect(heading()).toBe('Secure coding ticket of CHG0012345');
  });
});
