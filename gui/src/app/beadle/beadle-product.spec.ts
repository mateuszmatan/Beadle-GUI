import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { changeOptions, changeProfile, changeTemplate, taskText } from '../testing/change-fixtures';
import { buttonOf, fieldOf, text } from '../testing/dom';
import { product } from '../testing/fixtures';
import { BeadleProduct } from './beadle-product';

describe('BeadleProduct', () => {
  let fixture: ComponentFixture<BeadleProduct>;
  let http: HttpTestingController;

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const form = () => editor()['form']()!;
  const snack = () =>
    [...document.querySelectorAll('mat-snack-bar-container')].map((bar) => text(bar)).join(' ');
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
    expect(text(page().querySelector('.banner.info'))).toContain('Not saved yet');
    expect(text(page().querySelector('#defaults-title'))).toBe('Change template');
    expect(page().querySelector('dso-change-template-form')).not.toBeNull();
    expect(text(page().querySelector('.default-tasks h3'))).toBe('Default change tasks');
    expect(page().querySelectorAll('dso-change-tasks-form .task-row')).toHaveLength(2);
    expect([...page().querySelectorAll('.template-card h3')].map(text)).toEqual([
      'Generic request data',
      'Jira',
      'Approval and Notification',
      'Schedule',
      'Planning',
      'Privileged access',
      'Risk assessment',
      'Secure coding',
    ]);
    expect(text(fieldOf(page(), 'Requested For')?.querySelector('mat-hint'))).toBe(
      'left empty: the user who opens the change',
    );
    expect(fieldOf(page(), 'Opened By')).toBeNull();
    const access = template().controls.privilegedAccess.controls;
    access.count.setValue(1);
    access.users.at(0).setValue({ user: ' Jane Smith ', account: 'adm_jsmith' });
    template().patchValue({ riskAssessment: { businessImpact: 'High' } });
    buttonOf(page(), 'Remove change task 2').click();
    tasks().at(0).patchValue({ shortDescription: ' Deploy it ' });
    expect(editor().hasUnsavedChanges()).toBe(true);

    buttonOf(page(), 'Save the template').click();
    const request = saved();
    expect(editor().hasUnsavedChanges()).toBe(true);
    const expected = changeTemplate({
      privilegedAccess: { required: true, users: [{ user: 'Jane Smith', account: 'adm_jsmith' }] },
      riskAssessment: { ...changeTemplate().riskAssessment, businessImpact: 'High' },
    });
    const savedTasks = [taskText('Deploy it', 'Deploy the release of CertScanner.')];
    expect(request.request.body).toEqual({ version: null, template: expected, tasks: savedTasks });
    request.flush(changeProfile({ version: 0, template: expected, tasks: savedTasks }));
    await settle();
    expect(snack()).toContain('The change template of CertScanner is saved');

    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(editor()['version']()).toBe(0);
    expect(page().querySelector('.banner.info')).toBeNull();
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

  it('leaves a deleted product without asking about its unsaved template', async () => {
    await open();
    template().controls.category.setValue('Hardware');
    form().markAsDirty();
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as unknown as MatDialogRef<unknown>);
    const unsaved: boolean[] = [];
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(() => {
      unsaved.push(editor().hasUnsavedChanges());
      return Promise.resolve(true);
    });

    buttonOf(page(), 'Delete product').click();
    http.expectOne({ method: 'DELETE', url: '/api/products/1' }).flush(null);
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/beadle/admin/products']);
    expect(unsaved).toEqual([false]);
  });

  it('does not send a template with missing values or without a change task', async () => {
    await open();
    template().patchValue({ jiraProjectKey: 'cert-1', planning: { backoutPlan: '' } });
    tasks().at(1).controls.description.setValue(' ');

    editor()['save']();
    fixture.detectChanges();

    expect(editor()['saveError']()).toBe('Some fields need your attention.');
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(text(fieldOf(page(), 'Jira project')?.querySelector('mat-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(fieldOf(page(), 'Backout plan')?.querySelector('mat-error'))).toBe('Required');
    const second = page().querySelectorAll('.task-row')[1];
    expect(text(fieldOf(second, 'Description')?.querySelector('mat-error'))).toBe('Required');
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
    expect(text(fieldOf(page(), 'Number of BBH users impacted')?.querySelector('mat-error'))).toBe(
      'is not one of the options',
    );
    expect(
      text(
        fieldOf(page().querySelectorAll('.task-row')[1], 'Short description')?.querySelector(
          'mat-error',
        ),
      ),
    ).toBe('is used twice');

    editor()['save']();
    expect(editor()['saveError']()).toBe('Some fields need your attention.');

    template().controls.riskAssessment.controls.bbhUsers.setValue('26-250');
    tasks().at(1).controls.shortDescription.setValue('Validate it');
    editor()['save']();
    saved().flush(
      { detail: 'Invalid request', errors: [{ field: 'version', message: 'is unknown' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Invalid request');

    editor()['save']();
    saved().flush(
      { detail: 'Someone else changed the template' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Someone else changed the template');
    expect(text(page().querySelector('.save-error'))).toBe('Someone else changed the template');
  });
});
