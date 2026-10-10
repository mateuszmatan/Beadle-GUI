import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { buttonOf, text, toast } from '../testing/dom';
import { department, productDetails } from '../testing/fixtures';
import { ProductAdmin } from './product-admin';
import { ProductDetails } from './product-details-api';
import { ProductDialog } from './product-dialog';

describe('ProductAdmin', () => {
  let fixture: ComponentFixture<ProductAdmin>;
  let http: HttpTestingController;
  let emitted: ProductDetails[];
  let deleted: ProductDetails[];

  const stored = productDetails();

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
  const snack = () => text(toast());

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function load(loaded: ProductDetails = stored) {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http.expectOne('/api/products/1/details').flush(loaded);
    http
      .expectOne('/api/departments')
      .flush([department(), department({ id: 5, name: 'Fund Services' })]);
    await settle();
  }

  function dialogClosing(...results: unknown[]) {
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );
    return open;
  }

  it('shows the details of the product without reading its services', async () => {
    await load();

    expect(facts()).toEqual([
      'CERT',
      'Corporate Technology',
      'Technology Architecture',
      'arch@bbh.com',
    ]);
    expect(page().querySelector('.facts a')?.getAttribute('href')).toBe('mailto:arch@bbh.com');
    expect(page().querySelector('table')).toBeNull();
    http.expectNone('/api/products/1');
    expect(text(page().querySelector('.facts h2'))).toBe('Product details');
    expect([...page().querySelectorAll('.facts dt')].map((dt) => text(dt))).toEqual([
      'Product code',
      'Department',
      'Owner team',
      'Contact e-mail',
    ]);
    expect([...page().querySelectorAll('button')].map((button) => text(button))).toEqual([
      'Edit details',
      'Delete product',
    ]);
  });

  it('shows a product without team, e-mail or department', async () => {
    await load(productDetails({ ownerTeam: null, contactEmail: null, departmentId: null }));

    expect(facts()).toEqual(['CERT', 'Not in a department', '–', '–']);
  });

  it('says why the product could not be loaded', async () => {
    fixture.componentRef.setInput('id', 1);
    await settle();
    http
      .expectOne('/api/products/1/details')
      .flush({ detail: 'Product 1 was not found' }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/departments').flush([]);
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The product could not be loaded. Product 1 was not found',
    );

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    await settle();
    http.expectOne('/api/products/1/details').flush(stored);
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(facts()[0]).toBe('CERT');
  });

  it('changes the facts in the product dialog and tells the page', async () => {
    await load();
    const renamed = { ...stored, name: 'Cert Scanner', departmentId: 5, version: 4 };
    const open = dialogClosing(renamed, undefined);

    buttonOf(page(), 'Edit details').click();
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

    buttonOf(page(), 'Edit details').click();
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

    buttonOf(page(), 'Edit details').click();
    http
      .expectOne('/api/products/1/details')
      .flush({ ...stored, ownerTeam: 'Security', version: 5 });
    fixture.detectChanges();

    expect(snack()).toContain(
      'CertScanner was changed by someone else. Its latest version is shown now; make your change again.',
    );
    expect(facts()[2]).toBe('Security');

    buttonOf(page(), 'Edit details').click();
    http.expectOne('/api/products/1/details').flush({ ...stored, version: 5 });
    fixture.detectChanges();

    expect(snack()).toContain(
      'The details of CertScanner could not be saved. A product named Payments Hub already exists',
    );
    expect(emitted).toEqual([]);
  });

  it('deletes the product once confirmed and tells the page', async () => {
    await load();
    const open = dialogClosing(true, true);

    buttonOf(page(), 'Delete product').click();
    expect(open.mock.calls[0][1]?.data).toEqual({
      title: 'Delete the product CertScanner?',
      message:
        'CertScanner and its change template are deleted. This cannot be undone.\n' +
        'If CertScanner still has services in DevSecOps Management, it is not deleted; remove them there first.',
      confirmLabel: 'Delete product',
      danger: true,
    });
    http
      .expectOne({ method: 'DELETE', url: '/api/products/1/details' })
      .flush({ detail: 'The portal cannot be reached.' }, { status: 503, statusText: 'Down' });
    await fixture.whenStable();
    expect(snack()).toContain('CertScanner could not be deleted. The portal cannot be reached.');
    expect(deleted).toEqual([]);

    buttonOf(page(), 'Delete product').click();
    http.expectOne({ method: 'DELETE', url: '/api/products/1/details' }).flush(null);
    await fixture.whenStable();

    expect(snack()).toContain('CertScanner deleted');
    expect(deleted).toEqual([stored]);
  });

  it('says why a product with services in DevSecOps Management is not deleted', async () => {
    await load();
    dialogClosing(true);

    buttonOf(page(), 'Delete product').click();
    http.expectOne({ method: 'DELETE', url: '/api/products/1/details' }).flush(
      {
        detail:
          'CertScanner still has 2 service(s) in DevSecOps Management. Remove them there first.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(snack()).toContain(
      'CertScanner could not be deleted. CertScanner still has 2 service(s) in DevSecOps Management. Remove them there first.',
    );
    expect(deleted).toEqual([]);
  });
});
