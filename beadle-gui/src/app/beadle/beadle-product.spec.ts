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
import { buttonOf, fieldOf, text, toast } from '@common/testing/dom';
import { product } from '../testing/fixtures';
import { BeadleProduct } from './beadle-product';

describe('BeadleProduct', () => {
  let fixture: ComponentFixture<BeadleProduct>;
  let http: HttpTestingController;

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const form = () => editor()['form']()!;
  const snack = () => text(toast());
  const template = () => form().controls.template;
  const tasks = () => form().controls.tasks;

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
    http.expectOne('/api/departments').flush([]);
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
  }

  function saved() {
    return http.expectOne({ method: 'PUT', url: '/api/products/1/change-profile' });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BeadleProduct],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BeadleProduct);
  });

  afterEach(() => http.verify());

  it('shows a suggested change template that is not saved yet and saves it with the privileged users and tasks', async () => {
    await open(changeProfile({ version: null, updatedAt: null }));

    expect(
      [...page().querySelectorAll('.breadcrumb a, .breadcrumb span:not(.sep)')].map(text),
    ).toEqual(['Beadle Admin', 'Products', 'CertScanner']);
    expect(text(page().querySelector('h1'))).toBe('CertScanner');
    expect(text(page().querySelector('.page-header p'))).toBe(
      "The product's details and the change template its ProTech (ServiceNow) changes start with.",
    );
    expect(text(page().querySelector('.banner.info'))).toBe(
      'Not saved yet. Until you save it, new changes of CertScanner start with these values, suggested from its name, code and owner team.',
    );
    expect(text(page().querySelector('#defaults-title'))).toBe('Change template');
    expect(text(page().querySelector('.defaults-header .chip'))).toBe('Not filled in yet');
    expect(page().querySelector('.defaults-header .saved-at')).toBeNull();
    expect(text(page().querySelector('.defaults .section-help'))).toBe(
      'Every new change of CertScanner starts with these answers; whoever raises a change can still change each of them for that change. A field left empty is filled in when the change is raised, as the note under the field says.',
    );
    expect(page().querySelector('dso-change-template-form')).not.toBeNull();
    expect(text(page().querySelector('.default-tasks h3'))).toBe('Default change tasks');
    expect(page().querySelectorAll('dso-change-tasks-form .task-row')).toHaveLength(2);
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
    expect(text(fieldOf(page(), 'Requested for')?.querySelector('dso-hint'))).toBe(
      'If left empty: the user who opens the change',
    );
    expect(fieldOf(page(), 'Opened by')).toBeNull();
    const access = template().controls.privilegedAccess.controls;
    access.count.setValue(1);
    access.users.at(0).setValue({ user: ' Jane Smith ', account: 'adm_jsmith' });
    template().patchValue({ riskAssessment: { businessImpact: 'High' } });
    buttonOf(page(), 'Remove change task 2').click();
    tasks().at(0).controls.details.patchValue({ shortDescription: ' Deploy it ' });
    expect(editor().hasUnsavedChanges()).toBe(true);

    buttonOf(page(), 'Save the template').click();
    const request = saved();
    expect(editor().hasUnsavedChanges()).toBe(true);
    const expected = changeTemplate({
      privilegedAccess: { required: true, users: [{ user: 'Jane Smith', account: 'adm_jsmith' }] },
      riskAssessment: { ...changeTemplate().riskAssessment, businessImpact: 'High' },
    });
    const savedTasks = [releaseDetails('Deploy it', 'Deploy the release of CertScanner.')];
    expect(request.request.body).toEqual({ version: null, template: expected, tasks: savedTasks });
    request.flush(changeProfile({ version: 0, template: expected, tasks: savedTasks }));
    await settle();
    expect(snack()).toContain('The change template of CertScanner is saved');

    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(editor()['version']()).toBe(0);
    expect(page().querySelector('.banner.info')).toBeNull();
    expect(text(page().querySelector('.defaults-header .chip'))).toBe('Filled in');
    expect(text(page().querySelector('.defaults-header .saved-at'))).toMatch(
      /^last saved \d{1,2} Oct 2026, \d\d:\d\d$/,
    );
  });

  it('says why the change template could not be loaded and loads it again', async () => {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http
      .expectOne('/api/products/1/change-profile')
      .flush({ detail: 'The database is busy' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/products/1').flush(product());
    http.expectOne('/api/departments').flush([]);
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change template could not be loaded. The database is busy',
    );
    expect(page().querySelector('dso-change-template-form')).toBeNull();

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    await settle();
    http.expectOne('/api/products/1/change-profile').flush(changeProfile());
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(page().querySelector('dso-change-template-form')).not.toBeNull();
  });

  it('shows the new name of a renamed product and keeps the unsaved template', async () => {
    await open();
    template().controls.category.setValue('Hardware');
    form().markAsDirty();

    editor().renamed({ name: 'CertWatch' });
    await settle();

    expect(text(page().querySelector('h1'))).toBe('CertWatch');
    expect(text(page().querySelector('.breadcrumb span:last-child'))).toBe('CertWatch');
    expect(template().controls.category.value).toBe('Hardware');
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
    expect(template().controls.configurationItem.value).toBe('CertWatch');
    expect(editor().hasUnsavedChanges()).toBe(false);
  });

  it('leaves a deleted product without asking about its unsaved template', async () => {
    await open();
    template().controls.category.setValue('Hardware');
    form().markAsDirty();
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
    template().patchValue({ jiraProjectKey: 'cert-1', planning: { backoutPlan: '' } });
    tasks().at(1).controls.details.controls.description.setValue(' ');

    editor()['save']();
    fixture.detectChanges();

    expect(editor()['saveError']()).toBe('Some fields need your attention.');
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(text(fieldOf(page(), 'Jira project')?.querySelector('dso-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(fieldOf(page(), 'Backout plan')?.querySelector('dso-error'))).toBe('Required');
    const second = page().querySelectorAll('.task-row')[1];
    expect(text(fieldOf(second, 'Description')?.querySelector('dso-error'))).toBe('Required');
    http.expectNone({ method: 'PUT', url: '/api/products/1/change-profile' });
  });

  it('marks the fields the portal refused and reports a conflict as it is', async () => {
    await open();

    editor()['save']();
    const first = saved();
    expect(first.request.body.version).toBe(2);
    first.flush(changeProfile({ version: 3 }));
    await settle();
    editor()['save']();
    const second = saved();
    expect(second.request.body.version).toBe(3);
    second.flush(
      {
        detail: 'Invalid request',
        errors: [
          { field: 'template.riskAssessment.bbhUsers', message: 'is not one of the options' },
          { field: 'tasks[1].shortDescription', message: 'is used twice' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe(
      'The portal did not accept some values. They are marked below.',
    );
    expect(text(fieldOf(page(), 'Number of BBH users impacted')?.querySelector('dso-error'))).toBe(
      'is not one of the options',
    );
    expect(
      text(
        fieldOf(page().querySelectorAll('.task-row')[1], 'Short description')?.querySelector(
          'dso-error',
        ),
      ),
    ).toBe('is used twice');

    editor()['save']();
    expect(editor()['saveError']()).toBe('Some fields need your attention.');

    template().controls.riskAssessment.controls.bbhUsers.setValue('26-250');
    tasks().at(1).controls.details.controls.shortDescription.setValue('Validate it');
    editor()['save']();
    saved().flush(
      { detail: 'Invalid request', errors: [{ field: 'version', message: 'is unknown' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Invalid request');
    expect(buttonOf(page(), 'Reload')).toBeUndefined();

    editor()['save']();
    saved().flush(
      { detail: 'Someone else changed the template' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Someone else changed the template');
    expect(text(page().querySelector('.save-error'))).toBe('Someone else changed the template');

    buttonOf(page(), 'Reload').click();
    await settle();
    http
      .expectOne('/api/products/1/change-profile')
      .flush(changeProfile({ version: 5, template: changeTemplate({ category: 'Network' }) }));
    await settle();
    expect(page().querySelector('.save-error')).toBeNull();
    expect(buttonOf(page(), 'Reload')).toBeUndefined();
    expect(template().controls.category.value).toBe('Network');
    expect(editor().hasUnsavedChanges()).toBe(false);
    editor()['save']();
    expect(saved().request.body.version).toBe(5);
  });
});
