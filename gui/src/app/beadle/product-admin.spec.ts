import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Product } from '../core/models';
import { ServiceDialog } from '../self-service/service-dialog';
import { WizardService, fromService, serviceRequest } from '../self-service/self-service-model';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { buttonOf, text } from '../testing/dom';
import { anotherService, department, product, service } from '../testing/fixtures';
import { ProductAdmin } from './product-admin';
import { ProductDialog } from './product-dialog';

describe('ProductAdmin', () => {
  let fixture: ComponentFixture<ProductAdmin>;
  let http: HttpTestingController;
  let emitted: Product[];
  let deleted: Product[];

  const gui = service();
  const api = anotherService({
    description: 'REST API',
    build: { ...service().build, tool: 'MAVEN' },
    deployment: { ...service().deployment, target: 'OPENSHIFT' },
  });
  const stored = product({ services: [gui, api] });
  const added: WizardService = {
    id: null,
    name: 'worker',
    description: 'Scans in the background',
    appScanId: '209f44ac-dd06-4ca0-884e-d944904f8022',
    tool: 'GRADLE',
    target: 'VM',
    openShiftProject: '',
    nexusIqApplication: '',
    repositoryUrl: '',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductAdmin);
    emitted = [];
    deleted = [];
    fixture.componentInstance.saved.subscribe((saved) => emitted.push(saved));
    fixture.componentInstance.deleted.subscribe((product) => deleted.push(product));
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const facts = () => [...page().querySelectorAll('.facts dd')].map((dd) => text(dd));
  const rows = () => [...page().querySelectorAll<HTMLElement>('tr.mat-mdc-row')];
  const row = (name: string) => rows().find((tr) => text(tr.querySelector('.name')) === name)!;
  const snack = () =>
    [...document.querySelectorAll('mat-snack-bar-container')].map((bar) => text(bar)).join(' ');

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function load(loaded: Product = stored) {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http.expectOne('/api/products/1').flush(loaded);
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    await settle();
  }

  function dialogClosing(...results: unknown[]) {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({
        afterClosed: () => of(result),
      } as unknown as MatDialogRef<unknown>),
    );
    return open;
  }

  function expectSave(version: number) {
    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.body.version).toBe(version);
    return request;
  }

  it('shows the facts of the product and its services', async () => {
    await load();

    expect(facts()).toEqual([
      'CERT',
      'Corporate Technology',
      'Technology Architecture',
      'arch@bbh.com',
    ]);
    expect(page().querySelector('.facts a')?.getAttribute('href')).toBe('mailto:arch@bbh.com');
    expect([...page().querySelectorAll('th')].map((th) => text(th))).toEqual([
      'Service',
      'What it does',
      'Build tool',
      'Runs on',
      '',
    ]);
    expect([...row('api').querySelectorAll('td')].map((td) => text(td))).toEqual([
      'api',
      'REST API',
      'Maven',
      'OpenShift',
      'Change Remove',
    ]);
    expect(text(row('gui').querySelector('.mat-column-target'))).toBe('Virtual machine');
    expect(
      [...page().querySelectorAll('td.actions button')].map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual(['Change gui', 'Remove gui', 'Change api', 'Remove api']);
  });

  it('shows a product without services, team, e-mail or department', async () => {
    await load(product({ services: [], ownerTeam: null, contactEmail: null, departmentId: null }));

    expect(facts()).toEqual(['CERT', 'Not in a department', '–', '–']);
    expect(text(page().querySelector('.none'))).toBe('CertScanner has no services yet.');
    expect(rows().length).toBe(0);
  });

  it('says why the product could not be loaded', async () => {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http
      .expectOne('/api/products/1')
      .flush({ detail: 'Product 1 was not found' }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/departments').flush([]);
    await settle();

    expect(text(page().querySelector('.banner'))).toBe('Product 1 was not found');
  });

  it('changes the facts in the product dialog and tells the page', async () => {
    await load();
    const renamed = { ...stored, name: 'Cert Scanner', departmentId: 5, version: 4 };
    const open = dialogClosing(renamed, undefined);

    buttonOf(page(), 'Change').click();
    fixture.detectChanges();

    expect(open.mock.calls[0][0]).toBe(ProductDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      departments: [department(), department({ id: 5, name: 'Fund Services' })],
      departmentId: null,
      product: stored,
    });
    expect(facts()[1]).toBe('Fund Services');
    expect(snack()).toContain('Cert Scanner saved');
    expect(emitted).toEqual([renamed]);

    buttonOf(page(), 'Change').click();
    expect(open.mock.calls[1][1]?.data).toMatchObject({ product: renamed });
    expect(emitted.length).toBe(1);
  });

  it('loads the product again when someone else changed it meanwhile', async () => {
    await load();
    const conflict = new HttpErrorResponse({
      status: 409,
      statusText: 'Conflict',
      error: { detail: 'A product named Payments Hub already exists' },
    });
    dialogClosing(conflict, conflict);

    buttonOf(page(), 'Change').click();
    http.expectOne('/api/products/1').flush({ ...stored, ownerTeam: 'Security', version: 5 });
    fixture.detectChanges();

    expect(snack()).toContain(
      'CertScanner was changed by someone else. Its latest version is shown now; make your change again.',
    );
    expect(facts()[2]).toBe('Security');

    buttonOf(page(), 'Change').click();
    http.expectOne('/api/products/1').flush({ ...stored, version: 5 });
    fixture.detectChanges();

    expect(snack()).toContain('A product named Payments Hub already exists');
    expect(emitted).toEqual([]);
  });

  it('adds a service with the next version and keeps the stored services as they are', async () => {
    await load();
    const changed = { ...fromService(api), description: 'Public REST API' };
    const open = dialogClosing(added, changed);

    buttonOf(page(), 'Add service').click();
    expect(open.mock.calls[0][0]).toBe(ServiceDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      pipeline: 'FULL',
      service: null,
      takenNames: ['gui', 'api'],
    });
    const request = expectSave(3);
    expect(request.request.body).toEqual({
      code: 'CERT',
      name: 'CertScanner',
      description: 'TLS certificate scanner',
      ownerTeam: 'Technology Architecture',
      contactEmail: 'arch@bbh.com',
      departmentId: 3,
      appScan: stored.appScan,
      version: 3,
      services: [gui, api, serviceRequest(added, 'FULL')],
    });
    expect(request.request.body.services[2]).toMatchObject({ id: null, name: 'worker' });
    const saved = {
      ...stored,
      version: 4,
      services: [gui, api, service({ id: 12, name: 'worker' })],
    };
    request.flush(saved);
    fixture.detectChanges();

    expect(snack()).toContain('worker added to CertScanner');
    expect(rows().map((tr) => text(tr.querySelector('.name')))).toEqual(['gui', 'api', 'worker']);
    expect(emitted).toEqual([saved]);

    buttonOf(row('api'), 'Change').click();

    expect(open.mock.calls[1][1]?.data).toEqual({
      pipeline: 'FULL',
      service: fromService(api),
      takenNames: ['gui', 'worker'],
    });
    const second = expectSave(4);
    expect(second.request.body.services).toEqual([
      gui,
      serviceRequest(changed, 'FULL', api),
      saved.services[2],
    ]);
    expect(second.request.body.services[1]).toMatchObject({
      id: 11,
      description: 'Public REST API',
    });
    second.flush({ ...saved, version: 5 });
    fixture.detectChanges();

    expect(snack()).toContain('api saved');
    expect(emitted.length).toBe(2);
  });

  it('removes a service once confirmed', async () => {
    await load();
    const open = dialogClosing(false, true);

    buttonOf(row('gui'), 'Remove').click();
    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      title: 'Remove gui?',
      message:
        'gui is removed from CertScanner with its pipelines and their keys. ' +
        'Jenkins jobs using those keys stop working.',
      confirmLabel: 'Remove service',
      danger: true,
    });
    http.expectNone({ method: 'PUT', url: '/api/products/1' });

    buttonOf(row('gui'), 'Remove').click();
    const request = expectSave(3);
    expect(request.request.body.services).toEqual([api]);
    request.flush({ ...stored, version: 4, services: [api] });
    fixture.detectChanges();

    expect(rows().map((tr) => text(tr.querySelector('.name')))).toEqual(['api']);
    expect(snack()).toContain('gui removed from CertScanner');
  });

  it('lists the values the portal refused and reloads after a conflict', async () => {
    await load();
    dialogClosing(added, added);

    buttonOf(page(), 'Add service').click();
    expectSave(3).flush(
      {
        detail: 'The portal did not accept some values.',
        errors: [
          { field: 'services[2].appScan.applicationId', message: 'is used by Payments Hub' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(text(page().querySelector('[role=alert] strong'))).toBe(
      'The portal did not accept some values.',
    );
    expect([...page().querySelectorAll('[role=alert] li')].map((li) => text(li))).toEqual([
      'worker, AppScan application ID: is used by Payments Hub',
    ]);

    buttonOf(page(), 'Add service').click();
    expectSave(3).flush(
      { detail: 'The product was changed by someone else' },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne('/api/products/1').flush({ ...stored, version: 6 });
    fixture.detectChanges();

    expect(page().querySelector('[role=alert]')).toBeNull();
    expect(snack()).toContain('CertScanner was changed by someone else.');
    expect(fixture.componentInstance['product'].value()?.version).toBe(6);
    expect(emitted).toEqual([]);
  });

  it('deletes the product once confirmed and tells the page', async () => {
    await load();
    const open = dialogClosing(true, true);

    buttonOf(page(), 'Delete product').click();
    expect(open.mock.calls[0][1]?.data).toEqual({
      title: 'Delete CertScanner?',
      message:
        'CertScanner is deleted with its services, their pipelines and keys, and its ServiceNow defaults. ' +
        'Jenkins jobs using those keys stop working. This cannot be undone.',
      confirmLabel: 'Delete product',
      danger: true,
    });
    http
      .expectOne({ method: 'DELETE', url: '/api/products/1' })
      .flush({ detail: 'The portal cannot be reached.' }, { status: 503, statusText: 'Down' });
    expect(snack()).toContain('The portal cannot be reached.');
    expect(deleted).toEqual([]);

    buttonOf(page(), 'Delete product').click();
    http.expectOne({ method: 'DELETE', url: '/api/products/1' }).flush(null);
    await fixture.whenStable();

    expect(snack()).toContain('CertScanner deleted');
    expect(deleted).toEqual([stored]);
  });
});
