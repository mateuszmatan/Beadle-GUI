import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import {
  changeOptions,
  changeProfile,
  changeTemplate,
  releaseDetails,
} from '../testing/change-fixtures';
import {
  buttonOf,
  choose,
  enter,
  errorOf,
  fieldOf,
  inputOf,
  selectOf,
  text,
  toast,
} from '../testing/dom';
import { product } from '../testing/fixtures';
import { ProductPage } from './product-page';

describe('ProductPage', () => {
  let fixture: ComponentFixture<ProductPage>;
  let http: HttpTestingController;

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const task = (index: number) => page().querySelectorAll('.task-row')[index];
  const chosen = (label: string) => text(selectOf(page(), label).selectedOptions[0]);
  const save = () => buttonOf(page(), 'Save the template').click();

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function open(profile = changeProfile()) {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(profile);
    http.expectOne('/api/products/1').flush(product());
    http.expectOne('/api/business-services').flush([]);
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
  }

  function saved() {
    return http.expectOne({ method: 'PUT', url: '/api/products/1/change-profile' });
  }

  async function refuse(status: number, body: object, request = saved()) {
    request.flush(body, { status, statusText: 'Refused' });
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductPage);
  });

  afterEach(() => http.verify());

  it('shows a suggested change template that is not saved yet, with the admin hints and both forms', async () => {
    await open(changeProfile({ version: null, updatedAt: null }));

    expect(
      [...page().querySelectorAll('.breadcrumb a, .breadcrumb span:not(.sep)')].map(text),
    ).toEqual(['Beadle Admin', 'Products', 'CertScanner']);
    expect(text(page().querySelector('h1'))).toBe('CertScanner');
    expect(text(page().querySelector('.banner.info'))).toBe(
      'Not saved yet. Until you save it, new changes of CertScanner start with these values, suggested from its name, code and owner team.',
    );
    expect(text(page().querySelector('.defaults-header .chip'))).toBe('Not filled in yet');
    expect(page().querySelector('.defaults-header .saved-at')).toBeNull();
    expect(text(page().querySelector('.defaults .section-help'))).toContain(
      'Every new change of CertScanner starts with these answers',
    );
    expect([...page().querySelectorAll('.template-card h3')].map(text)).toEqual([
      'Request details',
      'Jira',
      'Approval and notification',
      'Schedule',
      'Planning',
      'Privileged access',
      'Risk assessment',
      'Secure coding',
    ]);
    expect(text(page().querySelector('.default-tasks h3'))).toBe('Default change tasks');
    expect(page().querySelectorAll('beadle-change-tasks-form .task-row')).toHaveLength(2);
    expect(text(fieldOf(page(), 'Requested for')?.querySelector('beadle-hint'))).toBe(
      'If left empty: the user who opens the change',
    );
    expect(fieldOf(page(), 'Opened by')).toBeNull();
  });

  it('saves what the admin typed, with the privileged users and tasks, trimmed', async () => {
    await open(changeProfile({ version: null, updatedAt: null }));
    choose(selectOf(page(), 'How many privileged accounts'), '1');
    await settle();
    enter(page(), 'Person 1', ' Jane Smith ');
    enter(page(), 'Privileged access 1', 'adm_jsmith');
    choose(selectOf(page(), 'Business impact'), 'High');
    buttonOf(page(), 'Remove change task 2').click();
    await settle();
    enter(task(0), 'Short description', ' Deploy it ');
    expect(editor().hasUnsavedChanges()).toBe(true);

    save();
    const request = saved();
    expect(editor().hasUnsavedChanges()).toBe(true);
    const expected = changeTemplate({
      privilegedAccess: { required: true, users: [{ user: 'Jane Smith', account: 'adm_jsmith' }] },
      riskAssessment: { ...changeTemplate().riskAssessment, businessImpact: 'High' },
    });
    const tasks = [releaseDetails('Deploy it', 'Deploy the release of CertScanner.')];
    expect(request.request.body).toEqual({ version: null, template: expected, tasks });
    request.flush(changeProfile({ version: 0, template: expected, tasks }));
    await settle();

    expect(text(toast())).toContain('The change template of CertScanner is saved');
    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(page().querySelector('.banner.info')).toBeNull();
    expect(text(page().querySelector('.defaults-header .chip'))).toBe('Filled in');
    expect(text(page().querySelector('.defaults-header .saved-at'))).toMatch(
      /^last saved \d{1,2} Oct 2026, \d\d:\d\d$/,
    );
    save();
    expect(saved().request.body.version).toBe(0);
  });

  it('says why the change template could not be loaded and loads it again', async () => {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http
      .expectOne('/api/products/1/change-profile')
      .flush({ detail: 'The database is busy' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/products/1').flush(product());
    http.expectOne('/api/business-services').flush([]);
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change template could not be loaded. The database is busy',
    );
    expect(page().querySelector('beadle-change-template-form')).toBeNull();

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(page().querySelector('beadle-change-template-form')).not.toBeNull();
  });

  it('shows the new name of a renamed product and keeps the unsaved template', async () => {
    await open();
    choose(selectOf(page(), 'Category'), 'Hardware');

    editor().renamed({ name: 'CertWatch' });
    await settle();

    expect(text(page().querySelector('h1'))).toBe('CertWatch');
    expect(text(page().querySelector('.breadcrumb span:last-child'))).toBe('CertWatch');
    expect(chosen('Category')).toBe('Hardware');
    expect(editor().hasUnsavedChanges()).toBe(true);
  });

  it('suggests the template again from the new name while it is not saved yet', async () => {
    await open(changeProfile({ version: null, updatedAt: null }));

    editor().renamed({ name: 'CertWatch' });
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(
      changeProfile({
        productName: 'CertWatch',
        version: null,
        updatedAt: null,
        template: changeTemplate({ configurationItem: 'CertWatch' }),
      }),
    );
    await settle();

    expect(text(page().querySelector('h1'))).toBe('CertWatch');
    expect(inputOf(page(), 'Affected CI').value).toBe('CertWatch');
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('leaves a deleted product without asking about its unsaved template', async () => {
    await open();
    choose(selectOf(page(), 'Category'), 'Hardware');
    vi.spyOn(TestBed.inject(Dialog), 'open').mockReturnValue({
      closed: of(true),
    } as unknown as DialogRef<unknown>);
    const unsaved: boolean[] = [];
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(() => {
      unsaved.push(editor().hasUnsavedChanges());
      return Promise.resolve(true);
    });

    buttonOf(page(), 'Delete product').click();
    http.expectOne({ method: 'DELETE', url: '/api/products/1' }).flush(null);
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/admin/products']);
    expect(unsaved).toEqual([false]);
  });

  it('does not send a template with missing values or without a change task', async () => {
    await open();
    enter(page(), 'Jira project', 'cert-1');
    enter(page(), 'Backout plan', '');
    enter(task(1), 'Description', ' ');

    save();
    fixture.detectChanges();

    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(errorOf(page(), 'Jira project')).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(errorOf(page(), 'Backout plan')).toBe('Required');
    expect(errorOf(task(1), 'Description')).toBe('Required');
    http.expectNone({ method: 'PUT', url: '/api/products/1/change-profile' });
  });

  it('marks the fields Beadle refused, at the template and at the tasks', async () => {
    await open();

    save();
    await refuse(400, {
      detail: 'Invalid request',
      problems: [
        { field: 'template.riskAssessment.bbhUsers', message: 'is not one of the options' },
        { field: 'tasks[1].shortDescription', message: 'is used twice' },
      ],
    });

    expect(text(page().querySelector('.save-error'))).toBe(
      'The change template could not be saved: Invalid request',
    );
    expect(errorOf(page(), 'Number of BBH users impacted')).toBe('is not one of the options');
    expect(errorOf(task(1), 'Short description')).toBe('is used twice');
    save();
    await settle();
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
  });

  it('saves with the version it loaded and reloads the template after a conflict', async () => {
    await open();
    save();
    const first = saved();
    expect(first.request.body.version).toBe(2);
    await refuse(409, { detail: 'Someone else changed the template' }, first);
    expect(text(page().querySelector('.save-error'))).toBe(
      'The change template could not be saved: Someone else changed the template',
    );

    buttonOf(page(), 'Reload').click();
    await settle();
    http
      .expectOne('/api/products/1/change-profile')
      .flush(changeProfile({ version: 5, template: changeTemplate({ category: 'Network' }) }));
    await settle();

    expect(page().querySelector('.save-error')).toBeNull();
    expect(chosen('Category')).toBe('Network');
    expect(editor().hasUnsavedChanges()).toBe(false);
    save();
    expect(saved().request.body.version).toBe(5);
  });
});
