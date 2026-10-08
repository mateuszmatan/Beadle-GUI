import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Product } from '../core/models';
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

  it('shows the facts of the product and leaves its services to DevSecOps Management', async () => {
    await load();

    expect(facts()).toEqual([
      'CERT',
      'Corporate Technology',
      'Technology Architecture',
      'arch@bbh.com',
    ]);
    expect(page().querySelector('.facts a')?.getAttribute('href')).toBe('mailto:arch@bbh.com');
    expect(page().querySelector('table')).toBeNull();
    expect(text(page())).not.toContain('api');
    expect([...page().querySelectorAll('button')].map((button) => text(button))).toEqual([
      'Change',
      'Delete product',
    ]);
  });

  it('shows a product without team, e-mail or department', async () => {
    await load(product({ services: [], ownerTeam: null, contactEmail: null, departmentId: null }));

    expect(facts()).toEqual(['CERT', 'Not in a department', '–', '–']);
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

  it('deletes the product once confirmed and tells the page', async () => {
    await load();
    const open = dialogClosing(true, true);

    buttonOf(page(), 'Delete product').click();
    expect(open.mock.calls[0][1]?.data).toEqual({
      title: 'Delete CertScanner?',
      message:
        'CertScanner is deleted with its change template and its DevSecOps pipelines and keys. ' +
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
