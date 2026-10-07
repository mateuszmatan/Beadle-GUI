import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { changeProfile, changeTemplate } from '../testing/change-fixtures';
import { fieldOf, text } from '../testing/dom';
import { product } from '../testing/fixtures';
import { BeadleProduct } from './beadle-product';

describe('BeadleProduct', () => {
  let fixture: ComponentFixture<BeadleProduct>;
  let http: HttpTestingController;

  const editor = () => fixture.componentInstance;
  const page = () => fixture.nativeElement as HTMLElement;

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BeadleProduct],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BeadleProduct);
  });

  afterEach(() => http.verify());

  it('shows a suggested template that is not saved yet and saves it with one approver per line', async () => {
    await open(
      changeProfile({
        version: null,
        updatedAt: null,
        template: changeTemplate({ approvers: [] }),
      }),
    );

    expect(text(page().querySelector('h1'))).toBe('ServiceNow change template of CertScanner');
    expect(text(page().querySelector('.banner.info'))).toContain('Not saved yet');
    const form = editor()['form']()!;
    form.patchValue({ approvers: ' Emma Brooks \n\nHenry Collins ', risk: 'HIGH' });
    form.markAsDirty();
    expect(editor().hasUnsavedChanges()).toBe(true);

    editor()['save']();
    const saved = http.expectOne({ method: 'PUT', url: '/api/products/1/change-profile' });
    expect(saved.request.body).toEqual({
      version: null,
      template: changeTemplate({ approvers: ['Emma Brooks', 'Henry Collins'], risk: 'HIGH' }),
    });
    saved.flush(changeProfile({ version: 0, template: changeTemplate({ risk: 'HIGH' }) }));
    await settle();

    expect(editor().hasUnsavedChanges()).toBe(false);
    expect(editor()['version']).toBe(0);
  });

  it('does not send a template with missing values', async () => {
    await open();
    editor()['form']()!.patchValue({ jiraProjectKey: 'cert-1', riskAssessment: '' });

    editor()['save']();
    fixture.detectChanges();

    expect(editor()['saveError']()).toBe('Some fields need your attention.');
    expect(text(fieldOf(page(), 'Jira project key')?.querySelector('mat-error'))).toBe(
      '2 to 10 upper case letters, digits or _',
    );
  });

  it('marks the fields the portal refused and reports a conflict as it is', async () => {
    await open();

    editor()['save']();
    http.expectOne({ method: 'PUT', url: '/api/products/1/change-profile' }).flush(
      {
        detail: 'Invalid request',
        errors: [{ field: 'template.approvers', message: 'add at least one manager' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(editor()['saveError']()).toBe(
      'The portal did not accept some values. They are marked below.',
    );
    expect(editor()['form']()!.controls.approvers.errors).toEqual({
      server: 'add at least one manager',
    });

    editor()['form']()!.controls.approvers.setValue('Emma Brooks');
    editor()['save']();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1/change-profile' })
      .flush({ detail: 'Someone else changed it' }, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(editor()['saveError']()).toBe('Someone else changed it');
  });
});
