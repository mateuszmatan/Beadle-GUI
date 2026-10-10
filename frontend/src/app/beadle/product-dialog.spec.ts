import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { fieldOf, inputOf, text } from '../testing/dom';
import { department, product, productDetails } from '../testing/fixtures';
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
          provide: DIALOG_DATA,
          useValue: { departments, departmentId: null, product: null, ...data },
        },
        { provide: DialogRef, useValue: { close } },
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
  const labels = () => [...page().querySelectorAll('dso-label')].map((label) => text(label));
  const errorOf = (label: string) => text(fieldOf(page(), label)?.querySelector('dso-error'));

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

  it('adds a product whose code follows its name, without AppScan account or services', async () => {
    await render({ departmentId: 5 });

    expect(text(page().querySelector('h2'))).toBe('Add product');
    expect(text(page().querySelector('.intro'))).toBe(
      'Its page opens next, where you fill in its change template. Fields marked * are required.',
    );
    expect(labels()).toEqual([
      'Product name',
      'Product code',
      'Department',
      'Owner team',
      'Contact e-mail',
    ]);
    expect(form().controls.departmentId.value).toBe(5);
    expect(text(fieldOf(page(), 'Product code')?.querySelector('dso-hint'))).toBe(
      'Short unique name used in reports, for example PAYHUB. Made from the name.',
    );
    expect(text(page().querySelector('button[type=submit]'))).toBe('Add product');

    await typeName('Trade Archive');
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive').flush({ code: 'TA' });
    expect(inputOf(page(), 'Product code').value).toBe('TA');

    await typeName('Trade Archive 2');
    http.expectOne('/api/products/code-suggestion?name=Trade%20Archive%202').flush({ code: 'TA2' });
    expect(inputOf(page(), 'Product code').value).toBe('TA2');

    type('Product code', 'tarc');
    expect(inputOf(page(), 'Product code').value).toBe('TARC');
    await typeName('Trade Archive 3');
    http.expectNone((request) => request.url === '/api/products/code-suggestion');

    type('Owner team', ' Custody Technology ');
    type('Contact e-mail', '');
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
      appScan: null,
      version: null,
      services: [],
    });
    const created = product({ id: 7, name: 'Trade Archive 3', appScan: null, services: [] });
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
    expect(errorOf('Product code')).toBe('Required');
    expect(errorOf('Department')).toBe('Required');
    expect(errorOf('Contact e-mail')).toBe('Enter an e-mail address');

    type('Product code', '1ARCHIVE');
    fixture.detectChanges();
    expect(errorOf('Product code')).toBe(
      "2 to 50 characters: a letter first, then A-Z, 0-9, '-' or '_'",
    );
  });

  it('shows the problems of the portal on their fields and the rest above the buttons', async () => {
    await render({ departmentId: 3 });
    form().patchValue({ name: 'Archive', code: 'ARCHIVE' });

    await submit();
    http.expectOne({ method: 'POST', url: '/api/products' }).flush(
      {
        detail: 'The portal did not accept some values.',
        errors: [
          { field: 'ownerTeam', message: 'is not a BBH team' },
          { field: 'code', message: 'is reserved' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(errorOf('Owner team')).toBe('is not a BBH team');
    expect(errorOf('Product code')).toBe('is reserved');
    expect(page().querySelector('[role=alert]')).toBeNull();

    await submit();
    http.expectNone('/api/products');

    form().patchValue({ code: 'ARCHIVE2', ownerTeam: 'Custody Technology' });
    await submit();
    http
      .expectOne({ method: 'POST', url: '/api/products' })
      .flush(
        { detail: 'A product named Archive already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(text(page().querySelector('[role=alert]'))).toBe(
      'The product could not be added. A product named Archive already exists',
    );
    expect(close).not.toHaveBeenCalled();
  });

  it('changes the details of a product and sends nothing of its services or AppScan account', async () => {
    const stored = productDetails({ contactEmail: null });
    await render({ product: stored });

    expect(text(page().querySelector('h2'))).toBe('Edit the details of CertScanner');
    expect(text(page().querySelector('.intro'))).toBe(
      'Its product code, CERT, stays as it is. Fields marked * are required.',
    );
    expect(labels()).toEqual(['Product name', 'Department', 'Owner team', 'Contact e-mail']);
    expect(text(page().querySelector('button[type=submit]'))).toBe('Save details');
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

    const request = http.expectOne({ method: 'PUT', url: '/api/products/1/details' });
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.body).toEqual({
      name: 'Cert Scanner',
      departmentId: 5,
      ownerTeam: null,
      contactEmail: 'certs@bbh.com',
      version: 3,
    });
    const saved = productDetails({ name: 'Cert Scanner', departmentId: 5, version: 4 });
    request.flush(saved);

    expect(close).toHaveBeenCalledWith(saved);
  });

  it('hands a conflict back so the product can be loaded again', async () => {
    await render({ product: productDetails() });

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1/details' })
      .flush({ detail: 'The database is busy' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(text(page().querySelector('[role=alert]'))).toBe(
      'The details could not be saved. The database is busy',
    );
    expect(close).not.toHaveBeenCalled();

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1/details' })
      .flush({ detail: 'Changed by someone else' }, { status: 409, statusText: 'Conflict' });
    http
      .expectOne({ method: 'GET', url: '/api/products/1/details' })
      .flush(productDetails({ version: 4 }));

    expect(close).toHaveBeenCalledWith(expect.any(HttpErrorResponse));
    expect(close.mock.calls[0][0].status).toBe(409);
  });

  it('keeps the edits when the new name is taken and nobody else changed the product', async () => {
    await render({ product: productDetails({ version: 3 }) });
    type('Owner team', 'Technology Architecture');

    await submit();
    http
      .expectOne({ method: 'PUT', url: '/api/products/1/details' })
      .flush(
        { detail: 'A product named Payments Hub already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    http
      .expectOne({ method: 'GET', url: '/api/products/1/details' })
      .flush(productDetails({ version: 3 }));
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(text(page().querySelector('[role=alert]'))).toBe(
      'The details could not be saved. A product named Payments Hub already exists',
    );
    expect(form().controls.ownerTeam.value).toBe('Technology Architecture');
  });
});
