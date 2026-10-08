import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { changeOptions, changeTemplate } from '../testing/change-fixtures';
import { fieldOf, inputOf, text } from '../testing/dom';
import { SectionKey } from './change-sections';
import { ChangeTemplateSection } from './change-template-section';
import { TemplateForm, templateForm } from './change-template-model';

describe('ChangeTemplateSection', () => {
  let fixture: ComponentFixture<ChangeTemplateSection>;
  let http: HttpTestingController;
  let form: TemplateForm;

  const page = () => fixture.nativeElement as HTMLElement;
  const labels = () => [...page().querySelectorAll('mat-form-field mat-label')].map(text);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChangeTemplateSection],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function render(section: SectionKey, template = changeTemplate(), more = {}) {
    form = templateForm(template);
    fixture = TestBed.createComponent(ChangeTemplateSection);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('section', section);
    Object.entries(more).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
    await settle();
  }

  async function chooseOption(label: string, option: string) {
    fieldOf(page(), label)!.querySelector<HTMLElement>('mat-select')!.click();
    await settle();
    const panels = document.querySelectorAll('.mat-mdc-select-panel');
    const options = [...panels[panels.length - 1].querySelectorAll<HTMLElement>('mat-option')];
    const choices = options.map(text);
    options.find((element) => text(element) === option)!.click();
    await settle();
    return choices;
  }

  it('shows the read-only facts first and then the request fields in the order of ProTech', async () => {
    await render(
      'request',
      changeTemplate({ requestedFor: 'Grace Turner', directBusinessService: 'Certificates' }),
      {
        facts: [
          { label: 'Change number', value: null, placeholder: 'Given by ProTech when raised' },
          { label: 'Approval', value: 'Not Yet Requested' },
        ],
      },
    );

    expect(labels()).toEqual([
      'Change number',
      'Approval',
      'Requested For',
      'Requested By',
      'Department',
      'Assignment group',
      'Category',
      'Assigned to',
      'Type',
      'Release',
      'Affected CI',
      'Incident',
      'Direct business service',
      'Problem',
      'Risk',
      'Affected clients',
      'Users affected',
      'Product description',
    ]);
    expect(inputOf(page(), 'Change number').placeholder).toBe('Given by ProTech when raised');
    expect(inputOf(page(), 'Approval').readOnly).toBe(true);
    expect(inputOf(page(), 'Approval').value).toBe('Not Yet Requested');
    expect(inputOf(page(), 'Requested For').value).toBe('Grace Turner');
    expect(inputOf(page(), 'Direct business service').readOnly).toBe(true);
    expect(text(fieldOf(page(), 'Category')?.querySelector('.mat-mdc-select-value'))).toBe(
      'Application',
    );
    expect(text(fieldOf(page(), 'Type')?.querySelector('.mat-mdc-select-value'))).toBe('Standard');
    expect(inputOf(page(), 'Risk').value).toBe('Moderate');
    expect(fieldOf(page(), 'Users affected')?.classList).toContain('span-12');
    expect(
      [...page().querySelectorAll('button.lookup')].map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual([
      'Find Requested For',
      'Find Requested By',
      'Find Department',
      'Find Assignment group',
      'Find Assigned to',
      'Find Release',
      'Find Affected CI',
      'Find Incident',
      'Find Problem',
      'Find Affected clients',
    ]);
    expect(text(fieldOf(page(), 'Requested For')?.querySelector('mat-hint'))).toBe('');

    expect(await chooseOption('Type', 'Business Critical')).toEqual([
      'Standard',
      'Emergency',
      'Business Critical',
      'Model',
    ]);
    expect(form.controls.type.value).toBe('BUSINESS_CRITICAL');
    await chooseOption('Category', 'Database');
    expect(form.controls.category.value).toBe('Database');
  });

  it('tells the admin who fills the empty request fields', async () => {
    await render('request', changeTemplate(), { admin: true });

    expect(text(fieldOf(page(), 'Requested For')?.querySelector('mat-hint'))).toBe(
      'left empty: the user who opens the change',
    );
    expect(text(fieldOf(page(), 'Assigned to')?.querySelector('mat-hint'))).toBe(
      'left empty: the user who opens the change',
    );
    expect(text(fieldOf(page(), 'Department')?.querySelector('mat-hint'))).toBe(
      'left empty: the department of the product',
    );
    expect(page().querySelectorAll('.read-only input[readonly]')).toHaveLength(2);
  });

  it('offers the risk answers and computes the risk from them', async () => {
    await render('risk');

    expect(labels()).toEqual([
      'Number of BBH workgroups impacted',
      'Complexity of the change',
      'Number of BBH users impacted',
      'Complexity of validation',
      'Number of applications impacted',
      'Backout testing & duration',
      'Number of impacted clients outside BBH',
      'Platform status',
      'Business impact',
    ]);
    expect(await chooseOption('Number of BBH users impacted', 'All users')).toEqual([
      'Not assessed',
      'Less than 5',
      '5-25',
      '26-250',
      'All users',
    ]);
    expect(form.controls.riskAssessment.controls.bbhUsers.value).toBe('All users');
    await chooseOption('Platform status', 'Not assessed');
    expect(form.controls.riskAssessment.controls.platformStatus.value).toBeNull();

    fixture.componentRef.setInput('section', 'request');
    await settle();
    expect(inputOf(page(), 'Risk').value).toBe('High');
    form.controls.riskAssessment.reset();
    await settle();
    expect(inputOf(page(), 'Risk').value).toBe('');
  });

  it('asks for the person and the account of as many privileged accounts as chosen', async () => {
    await render('privileged');

    expect(labels()).toEqual(['How many privileged accounts']);
    expect(await chooseOption('How many privileged accounts', '2')).toEqual([
      'None',
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
    ]);
    expect([...page().querySelectorAll('.account legend')].map(text)).toEqual([
      'Privileged account 1',
      'Privileged account 2',
    ]);
    const account = page().querySelector('.account')!;
    expect(labels().slice(1, 3)).toEqual(['Person', 'Privileged account']);
    expect(account.querySelector('button.lookup')?.getAttribute('aria-label')).toBe('Find Person');

    await chooseOption('How many privileged accounts', 'None');
    expect(page().querySelector('.account')).toBeNull();
    expect(form.controls.privilegedAccess.controls.users.length).toBe(0);
  });

  it('stacks the approvers, the Jira project and the secure coding ticket', async () => {
    await render('approvals');
    expect(labels()).toEqual(['Business approver', 'L1 approver', 'L2 approver']);
    expect(page().querySelector('.form-fields')?.classList).toContain('stacked');
    expect(inputOf(page(), 'L1 approver').value).toBe('Olivia Bennett');

    fixture.componentRef.setInput('section', 'jira');
    await settle();
    expect(inputOf(page(), 'Jira project').value).toBe('CERT');

    fixture.componentRef.setInput('section', 'secure');
    await settle();
    expect(labels()).toEqual(['Secure coding ticket number']);
    expect(page().querySelector('.form-fields')?.classList).toContain('stacked');
  });

  it('shows the planning texts and the schedule defaults of a template', async () => {
    await render('planning');
    expect(labels()).toEqual([
      'Test summary',
      'Implementation plan',
      'Validation plan',
      'Backout plan',
      'First use plan',
    ]);

    fixture.componentRef.setInput('section', 'schedule');
    await settle();
    expect(labels()).toEqual([
      'Installation start',
      'Installation hours',
      'Validation hours',
      'Downtime',
    ]);
    expect(inputOf(page(), 'Installation start').type).toBe('time');
    expect(text(fieldOf(page(), 'Downtime')?.querySelector('.mat-mdc-select-value'))).toBe('No');
    await chooseOption('Downtime', 'Yes');
    expect(form.controls.downtime.value).toBe(true);
  });

  it('says why the lists of the ProTech fields could not be loaded', async () => {
    form = templateForm(changeTemplate());
    fixture = TestBed.createComponent(ChangeTemplateSection);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('section', 'risk');
    fixture.detectChanges();
    await settle();
    http
      .expectOne('/api/changes/options')
      .flush({ detail: 'Database unavailable' }, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(text(page().querySelector('.choice-error'))).toBe(
      'The lists of the ProTech fields could not be loaded: Database unavailable',
    );
    fixture.componentRef.setInput('section', 'planning');
    await settle();
    expect(page().querySelector('.choice-error')).toBeNull();
  });
});
