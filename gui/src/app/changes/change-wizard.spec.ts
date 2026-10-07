import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { changeProfile, epic, productionChange, story } from '../testing/change-fixtures';
import { text } from '../testing/dom';
import { department, product, productSummary, service } from '../testing/fixtures';
import { ChangeWizard } from './change-wizard';

describe('ChangeWizard', () => {
  let fixture: ComponentFixture<ChangeWizard>;
  let http: HttpTestingController;

  const wizard = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const epicsUrl = '/api/products/1/jira/epics';
  const storiesUrl = '/api/products/1/jira/stories';

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
  }

  async function scope() {
    http
      .expectOne((request) => request.url === epicsUrl)
      .flush([epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail')]);
    await settle();
    wizard()['toggleEpic']('CERT-1', true);
    await settle();
    http
      .expectOne((request) => request.url === storiesUrl)
      .flush([
        story('CERT-2', 'E-mail the owner', 'CERT-1'),
        story('CERT-3', 'Teams alert', 'CERT-1'),
      ]);
    await settle();
  }

  it('says that Jira and ServiceNow are demo ones and lists the products of the chosen department', async () => {
    expect(text(page().querySelector('dso-integration-note'))).toContain(
      'Jira is not connected yet',
    );
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
  });

  it('asks to fill in the template of a product that has none', async () => {
    await chooseCertScanner(changeProfile({ version: null }));

    expect(text(page().querySelector('.banner[role="alert"]'))).toContain(
      'CertScanner has no ServiceNow change template yet',
    );
    await next();
    expect(wizard()['step']()).toBe(0);
    expect(wizard()['stepProblem']()).toBe(
      'The product needs its ServiceNow change template first',
    );
    expect(wizard().hasUnsavedChanges()).toBe(false);
  });

  it('raises a change from the chosen services, Jira epics and stories and window with the texts the user changed', async () => {
    await chooseCertScanner();
    expect(text(page().querySelector('dl.rows'))).toContain('Moderate risk · Low impact');
    expect(wizard()['serviceIds']()).toEqual([10, 11]);
    wizard()['toggleService'](11, false);
    wizard()['toggleService'](10, false);
    await next();
    expect(wizard()['stepProblem']()).toBe('Choose at least one service');
    wizard()['toggleService'](10, true);
    await next();
    expect(wizard()['step']()).toBe(1);

    await scope();
    expect(wizard()['storyKeys']()).toEqual(['CERT-2', 'CERT-3']);
    expect(text(page().querySelector('.story-group'))).toContain('CERT-1 Expiry alerts');
    wizard()['toggleStory']('CERT-3', false);
    await next();
    expect(wizard()['step']()).toBe(2);

    await next();
    expect(wizard()['stepProblem']()).toBe('Choose the change window');
    wizard()['chooseWindow']('weekend');
    await next();
    expect(wizard()['step']()).toBe(3);

    const preview = http.expectOne('/api/changes/preview');
    expect(preview.request.body).toMatchObject({
      productId: 1,
      serviceIds: [10],
      epicKeys: ['CERT-1'],
      storyKeys: ['CERT-2'],
    });
    preview.flush(productionChange({ id: null, number: null, createdAt: null }));
    await settle();
    expect(wizard()['shortDescription'].value).toBe('CertScanner release: Expiry alerts');
    expect(text(page().querySelector('.tasks'))).toContain(
      'Deploy gui of CertScanner to production',
    );
    wizard()['shortDescription'].setValue('CertScanner 2.4');

    expect(wizard()['nextLabel']()).toBe('Raise the change in ServiceNow');
    await next();
    const raised = http.expectOne({ method: 'POST', url: '/api/changes' });
    expect(raised.request.body).toMatchObject({
      shortDescription: 'CertScanner 2.4',
      description: 'Production release of CertScanner (CERT).',
    });
    raised.flush(productionChange({ shortDescription: 'CertScanner 2.4' }));
    await settle();

    expect(wizard()['step']()).toBe(4);
    expect(text(page().querySelector('h2'))).toBe('CHG0012345 is raised');
    expect(wizard().hasUnsavedChanges()).toBe(false);

    wizard()['restart']();
    await settle();
    expect(wizard()['step']()).toBe(0);
    expect(wizard()['productId'].value).toBeNull();
  });

  it('keeps the texts the user changed when the same change is previewed again and shows why ServiceNow refused it', async () => {
    await chooseCertScanner();
    await next();
    await scope();
    await next();
    wizard()['chooseWindow']('tonight');
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
        detail: '2 fields are invalid',
        errors: [
          { field: 'start', message: 'must be in the future' },
          { field: 'epicKeys', message: 'CERT-1 is not in Jira project CERT' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(wizard()['step']()).toBe(3);
    expect(wizard()['problems']()).toEqual([
      'must be in the future',
      'CERT-1 is not in Jira project CERT',
    ]);
    expect(text(page().querySelector('.save-problem'))).toContain('must be in the future');
  });

  it('chooses every epic or none and forgets the stories of an epic it drops', async () => {
    await chooseCertScanner();
    await next();
    await scope();

    wizard()['chooseAllEpics'](true);
    await settle();
    http
      .expectOne((request) => request.url === storiesUrl)
      .flush([
        story('CERT-2', 'E-mail the owner', 'CERT-1'),
        story('CERT-6', 'Record it', 'CERT-5'),
      ]);
    await settle();
    expect(wizard()['epicKeys']()).toEqual(['CERT-1', 'CERT-5']);
    expect(wizard()['storyKeys']()).toEqual(['CERT-2', 'CERT-6']);

    wizard()['toggleEpic']('CERT-5', false);
    expect(wizard()['storyKeys']()).toEqual(['CERT-2']);
    await settle();
    http
      .expectOne((request) => request.url === storiesUrl)
      .flush([story('CERT-2', 'E-mail the owner', 'CERT-1')]);
    await settle();

    wizard()['chooseAllEpics'](false);
    await settle();
    expect(wizard()['epicKeys']()).toEqual([]);
    expect(wizard()['storyKeys']()).toEqual([]);
    expect(wizard()['issueText'](epic('CERT-1', 'A'))).toBe('Done · updated 2026-09-20');
  });

  it('drops the epics a narrower range leaves out and holds the step while issues cannot be loaded', async () => {
    await chooseCertScanner();
    await next();
    await scope();

    wizard()['epicFrom'].setValue('2026-10-01');
    await settle();
    http.expectOne((request) => request.url === epicsUrl).flush([epic('CERT-5', 'Audit trail')]);
    await settle();
    expect(wizard()['epicKeys']()).toEqual([]);
    expect(wizard()['storyKeys']()).toEqual([]);

    wizard()['toggleEpic']('CERT-5', true);
    await settle();
    http
      .expectOne((request) => request.url === storiesUrl)
      .flush({ detail: 'from must not be after to' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect([...page().querySelectorAll('.choice-error')].map((error) => text(error))).toContain(
      'The stories could not be loaded: from must not be after to',
    );
    expect(wizard()['stepProblem']()).toBe('Choose dates the epics and stories can be loaded for');
  });

  it('takes a window the user types and refuses one that ends before it starts', async () => {
    await chooseCertScanner();
    await next();
    await scope();
    await next();

    wizard()['chooseWindow']('custom');
    await settle();
    expect(wizard()['customStart'].value).toMatch(/T06:00$/);
    wizard()['customEnd'].setValue(wizard()['customStart'].value.replace('T06:00', 'T05:00'));
    await next();

    expect(wizard()['step']()).toBe(2);
    expect(wizard()['stepProblem']()).toBe('The change must end after it starts');
    expect(wizard().hasUnsavedChanges()).toBe(true);
  });
});
