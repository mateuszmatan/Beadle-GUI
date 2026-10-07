import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { changeProfile, changeTemplate } from '../testing/change-fixtures';
import { buttonOf, fieldOf, text } from '../testing/dom';
import { product } from '../testing/fixtures';
import { BeadleProduct } from './beadle-product';

describe('BeadleProduct', () => {
  let fixture: ComponentFixture<BeadleProduct>;
  let http: HttpTestingController;

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;
  const form = () => editor()['form']()!;

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

  it('shows suggested defaults that are not saved yet and saves them with the privileged users', async () => {
    await open(changeProfile({ version: null, updatedAt: null }));

    expect(
      [...page().querySelectorAll('.breadcrumb a, .breadcrumb span:not(.sep)')].map(text),
    ).toEqual(['Beadle Admin', 'Products', 'CertScanner']);
    expect(text(page().querySelector('h1'))).toBe('CertScanner');
    expect(text(page().querySelector('.banner.info'))).toContain('Not saved yet');
    expect(page().querySelector('dso-change-template-form')).not.toBeNull();
    const access = form().controls.privilegedAccess.controls;
    access.required.setValue(true);
    access.users.at(0).setValue({ user: ' Jane Smith ', account: 'adm_jsmith' });
    form().patchValue({ riskAssessment: { businessImpact: 'High' } });
    form().markAsDirty();
    expect(editor().hasUnsavedChanges()).toBe(true);

    editor()['save']();
    const request = saved();
    const template = changeTemplate({
      privilegedAccess: { required: true, users: [{ user: 'Jane Smith', account: 'adm_jsmith' }] },
      riskAssessment: { ...changeTemplate().riskAssessment, businessImpact: 'High' },
    });
    expect(request.request.body).toEqual({ version: null, template });
    request.flush(changeProfile({ version: 0, template }));
    await settle();

    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(editor()['version']()).toBe(0);
    expect(page().querySelector('.banner.info')).toBeNull();
  });

  it('shows the new name of a renamed product and keeps the unsaved defaults', async () => {
    await open();
    form().controls.category.setValue('Hardware');
    form().markAsDirty();

    editor().renamed({ name: 'CertWatch' });
    await settle();

    expect(text(page().querySelector('h1'))).toBe('CertWatch');
    expect(text(page().querySelector('.breadcrumb span:last-child'))).toBe('CertWatch');
    expect(form().controls.category.value).toBe('Hardware');
    expect(editor().hasUnsavedChanges()).toBe(true);
  });

  it('leaves a deleted product without asking about its unsaved defaults', async () => {
    await open();
    form().controls.category.setValue('Hardware');
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

  it('does not send defaults with missing values', async () => {
    await open();
    form().patchValue({ jiraProjectKey: 'cert-1', planning: { backoutPlan: '' } });

    editor()['save']();
    fixture.detectChanges();

    expect(editor()['saveError']()).toBe('Some fields need your attention.');
    expect(text(page().querySelector('.save-error'))).toBe('Some fields need your attention.');
    expect(text(fieldOf(page(), 'Jira project')?.querySelector('mat-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(fieldOf(page(), 'Backout plan')?.querySelector('mat-error'))).toBe('Required');
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
        errors: [{ field: 'template.riskAssessment.bbhUsers', message: 'must be at most 5000' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe(
      'The portal did not accept some values. They are marked below.',
    );
    expect(text(fieldOf(page(), 'BBH users')?.querySelector('mat-error'))).toBe(
      'must be at most 5000',
    );

    editor()['save']();
    expect(editor()['saveError']()).toBe('Some fields need your attention.');

    form().controls.riskAssessment.controls.bbhUsers.setValue(50);
    editor()['save']();
    saved().flush(
      { detail: 'Invalid request', errors: [{ field: 'version', message: 'is unknown' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Invalid request');

    editor()['save']();
    saved().flush(
      { detail: 'Someone else changed the defaults' },
      { status: 409, statusText: 'Conflict' },
    );
    await settle();
    expect(editor()['saveError']()).toBe('Someone else changed the defaults');
    expect(text(page().querySelector('.save-error'))).toBe('Someone else changed the defaults');
  });
});
