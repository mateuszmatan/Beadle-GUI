import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Product } from '../core/models';
import { fieldOf, inputOf, text } from '../testing/dom';
import { anotherService, department, product } from '../testing/fixtures';
import { ProductDialog, ProductDialogData } from './product-dialog';

describe('ProductDialog', () => {
  let fixture: ComponentFixture<ProductDialog>;
  let http: HttpTestingController;
  const close = vi.fn();
  const departments = [department(), department({ id: 5, name: 'Fund Services' })];

  async function render(data: Partial<ProductDialogData> = {}) {
    TestBed.configureTestingModule({
      imports: [ProductDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { departments, departmentId: null, product: null, ...data },
        },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductDialog);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const form = () => fixture.componentInstance['form'];
  const labels = () => [...page().querySelectorAll('mat-label')].map((label) => text(label));
  const errorOf = (label: string) => text(fieldOf(page(), label)?.querySelector('mat-error'));

  function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function typeName(name: string) {
    type('Product name', name);
    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('adds a product whose code follows its name until the code is changed', async () => {
    await render({ departmentId: 5 });

    expect(text(page().querySelector('h2'))).toBe('Add product');
    expect(labels()).toEqual([
      'Product name',
      'Code',
      'Department',
      'Owner team',
      'Contact e-mail',
      'AppScan API key ID',
    ]);
    expect(form().controls.departmentId.value).toBe(5);

    await typeName('Trade Archive');
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive').flush({ code: 'TA' });
    expect(inputOf(page(), 'Code').value).toBe('TA');

    await typeName('Trade Archive 2');
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive%202').flush({ code: 'TA2' });
    expect(inputOf(page(), 'Code').value).toBe('TA2');

    type('Code', 'tarc');
    expect(inputOf(page(), 'Code').value).toBe('TARC');
    await typeName('Trade Archive 3');
    http.expectNone((request) => request.url === '/api/products/code-suggestion');

    type('Owner team', ' Custody Technology ');
    type('Contact e-mail', '');
    type('AppScan API key ID', ' bbh_key ');
    await submit();

    const request = http.expectOne({ method: 'POST', url: '/api/products' });
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.body).toEqual({
      code: 'TARC',
      name: 'Trade Archive 3',
      description: null,
      ownerTeam: 'Custody Technology',
      contactEmail: null,
      departmentId: 5,
      appScan: { keyId: 'bbh_key', secretCredentialsId: null },
      version: null,
      services: [],
    });
    const created = product({ id: 7, name: 'Trade Archive 3', services: [] });
    request.flush(created);

    expect(close).toHaveBeenCalledWith(created);
  });

  it('keeps an empty code when no code can be suggested and asks for the missing values', async () => {
    await render({ departmentId: 99 });
    expect(form().controls.departmentId.value).toBeNull();

    await typeName('Archive');
    http
      .expectOne('/api/products/code-suggestion?name=Archive')
      .flush(null, { status: 500, statusText: 'Server Error' });
    type('Contact e-mail', 'not an address');
    await submit();
    fixture.detectChanges();

    http.expectNone('/api/products');
    expect(errorOf('Code')).toBe('Required');
    expect(errorOf('Department')).toBe('Required');
    expect(errorOf('Contact e-mail')).toBe('Enter an e-mail address');
    expect(errorOf('AppScan API key ID')).toBe('Required');

    type('Code', '1ARCHIVE');
    fixture.detectChanges();
    expect(errorOf('Code')).toBe("Start with a letter; use A-Z, 0-9, '-' or '_'");
  });

  it('shows the problems of the portal on their fields and the rest above the buttons', async () => {
    await render({ departmentId: 3 });
    form().patchValue({ name: 'Archive', code: 'ARCHIVE', appScanKeyId: 'bbh_key' });

    await submit();
    http.expectOne({ method: 'POST', url: '/api/products' }).flush(
      {
        detail: 'The portal did not accept some values.',
        errors: [
          { field: 'appScan.keyId', message: 'is not an AppScan key ID' },
          { field: 'code', message: 'is reserved' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(errorOf('AppScan API key ID')).toBe('is not an AppScan key ID');
    expect(errorOf('Code')).toBe('is reserved');
    expect(page().querySelector('[role=alert]')).toBeNull();

    await submit();
    http.expectNone('/api/products');

    form().patchValue({ code: 'ARCHIVE2', appScanKeyId: 'bbh_key2' });
    await submit();
    http
      .expectOne({ method: 'POST', url: '/api/products' })
      .flush(
        { detail: 'A product named Archive already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(text(page().querySelector('[role=alert]'))).toBe(
      'A product named Archive already exists',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('changes the facts of a product and keeps everything else as the portal returned it', async () => {
    const stored = product({
      departmentId: 3,
      contactEmail: null,
      services: [product().services[0], anotherService()],
    });
    await render({ product: stored });

    expect(text(page().querySelector('h2'))).toBe('Change CertScanner');
    expect(labels()).toEqual(['Product name', 'Department', 'Owner team', 'Contact e-mail']);
    expect(inputOf(page(), 'Product name').value).toBe('CertScanner');
    expect(inputOf(page(), 'Owner team').value).toBe('Technology Architecture');
    expect(form().controls.departmentId.value).toBe(3);

    form().patchValue({
      name: ' Cert Scanner ',
      departmentId: 5,
      ownerTeam: ' ',
      contactEmail: 'certs@bbh.com',
    });
    await submit();
    http.expectNone((request) => request.url === '/api/products/code-suggestion');

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1' });
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.body).toEqual({
      code: 'CERT',
      name: 'Cert Scanner',
      description: 'TLS certificate scanner',
      ownerTeam: null,
      contactEmail: 'certs@bbh.com',
      departmentId: 5,
      appScan: stored.appScan,
      version: 3,
      services: stored.services,
    });
    const saved: Product = { ...stored, name: 'Cert Scanner', version: 4 };
    request.flush(saved);

    expect(close).toHaveBeenCalledWith(saved);
  });

  it('hands a conflict back so the product can be loaded again', async () => {
    await render({ product: product() });

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1' })
      .flush({ detail: 'Changed by someone else' }, { status: 409, statusText: 'Conflict' });

    expect(close).toHaveBeenCalledWith(expect.any(HttpErrorResponse));
    expect(close.mock.calls[0][0].status).toBe(409);
  });
});
