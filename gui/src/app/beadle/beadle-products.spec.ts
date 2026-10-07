import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Department, ProductSummary } from '../core/models';
import { buttonOf, text } from '../testing/dom';
import { department, product, productSummary } from '../testing/fixtures';
import { ChangeProfileSummary } from '../changes/change-api';
import { BeadleProducts } from './beadle-products';
import { ProductDialog } from './product-dialog';

describe('BeadleProducts', () => {
  let fixture: ComponentFixture<BeadleProducts>;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const fundServices = department({
    id: 5,
    name: 'Fund Services',
    productCount: 0,
    serviceCount: 0,
  });
  const payments = productSummary({
    id: 2,
    code: 'PAYHUB',
    name: 'Payments Hub',
    ownerTeam: null,
    departmentId: 5,
    departmentName: 'Fund Services',
    serviceCount: 4,
  });
  const saved: ChangeProfileSummary = {
    productId: 1,
    productName: 'CertScanner',
    version: 2,
    updatedAt: new Date(Date.now() - 2 * 86400000).toISOString(),
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BeadleProducts],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(BeadleProducts);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const cards = () => [...page().querySelectorAll<HTMLElement>('section.department')];
  const card = (name: string) =>
    cards().find((section) => text(section.querySelector('h2')) === name)!;
  const rowOf = (name: string) =>
    [...page().querySelectorAll<HTMLElement>('tr.mat-mdc-row')].find(
      (row) => text(row.querySelector('.name')) === name,
    )!;
  const cells = (name: string) => [...rowOf(name).querySelectorAll('td')].map((cell) => text(cell));
  const snack = () =>
    [...document.querySelectorAll('mat-snack-bar-container')].map((bar) => text(bar)).join(' ');

  async function load(
    products: ProductSummary[] = [productSummary(), payments],
    departments: Department[] = [department(), fundServices],
    defaults: ChangeProfileSummary[] | null = [saved],
  ) {
    fixture.detectChanges();
    http.expectOne('/api/products').flush(products);
    http.expectOne('/api/departments').flush(departments);
    const profiles = http.expectOne('/api/change-profiles');
    if (defaults) {
      profiles.flush(defaults);
    } else {
      profiles.flush(
        { detail: 'ServiceNow defaults are not available' },
        { status: 500, statusText: 'Error' },
      );
    }
    await fixture.whenStable();
  }

  async function search(value: string) {
    const input = page().querySelector<HTMLInputElement>('input[aria-label="Search products"]')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 300));
    fixture.detectChanges();
  }

  it('lists the products by department with their services and ServiceNow defaults', async () => {
    await load();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
    expect(text(card('Corporate Technology').querySelector('.tally'))).toBe(
      '1 product · 2 services',
    );
    expect(text(page().querySelector('.count'))).toBe('2 products in 2 departments');
    expect([...page().querySelectorAll('th')].map((th) => text(th)).slice(0, 4)).toEqual([
      'Product',
      'Owner team',
      'Services',
      'ServiceNow defaults',
    ]);
    expect(cells('CertScanner')).toEqual([
      'CertScannerCERT',
      'Technology Architecture',
      '2',
      'Saved · 2 days ago',
    ]);
    expect(cells('Payments Hub')).toEqual(['Payments HubPAYHUB', '–', '4', 'Suggested values']);
    expect(rowOf('CertScanner').querySelector('a')?.getAttribute('href')).toBe(
      '/beadle/admin/products/1',
    );

    rowOf('Payments Hub').click();
    expect(navigate).toHaveBeenCalledWith(['/beadle/admin/products', 2]);
  });

  it('still lists the products when their ServiceNow defaults cannot be loaded', async () => {
    await load(undefined, undefined, null);

    expect(page().querySelector('.banner')).toBeNull();
    const state = rowOf('CertScanner').querySelector('.mat-column-defaults span')!;
    expect(text(state)).toBe('Unknown');
    expect(state.getAttribute('title')).toBe('ServiceNow defaults are not available');
  });

  it('gathers the products without a department in a last card', async () => {
    await load([
      productSummary(),
      productSummary({ id: 7, name: 'Ledger', departmentId: null, serviceCount: 1 }),
    ]);
    const unassigned = cards().at(-1)!;

    expect(text(unassigned.querySelector('h2'))).toBe('Not in a department');
    expect(text(unassigned.querySelector('.tally'))).toBe('1 product · 1 service');
    expect(text(unassigned.querySelector('.hint'))).toBe(
      'Change these products to choose their department.',
    );
    expect(text(card('Fund Services').querySelector('.no-products'))).toBe(
      'No products in Fund Services yet.',
    );
    expect(text(page().querySelector('.count'))).toBe(
      '1 product in 2 departments · 1 not in a department',
    );
  });

  it('searches as the user types and says when nothing matches', async () => {
    await load();

    await search(' payments ');
    http.expectOne('/api/products?search=payments').flush([payments]);
    await fixture.whenStable();
    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual(['Fund Services']);
    expect(text(page().querySelector('.count'))).toBe('1 product in 1 department');

    await search('archive');
    http.expectOne('/api/products?search=archive').flush([]);
    await fixture.whenStable();
    expect(text(page().querySelector('.empty-state h3'))).toBe('No product matches "archive"');
    expect(page().querySelector('.empty-state button')).toBeNull();
  });

  it('invites to add the first product and shows why the products could not be loaded', async () => {
    await load([]);
    expect(text(page().querySelector('.empty-state h3'))).toBe('No products yet');
    expect(text(page().querySelector('.empty-state button'))).toBe('Add product');

    fixture.componentInstance['products'].reload();
    fixture.detectChanges();
    http.expectOne('/api/products').flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();
    expect(text(page().querySelector('.banner'))).toContain('cannot be reached');
  });

  it('adds a product from the toolbar or a department card and opens it', async () => {
    await load();
    const created = product({ id: 7, name: 'Trade Archive' });
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');
    [undefined, created].forEach((result) =>
      open.mockReturnValueOnce({
        afterClosed: () => of(result),
      } as unknown as MatDialogRef<unknown>),
    );

    buttonOf(page().querySelector('.toolbar')!, 'Add product').click();
    expect(open.mock.calls[0][0]).toBe(ProductDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      departments: [department(), fundServices],
      departmentId: null,
      product: null,
    });
    expect(navigate).not.toHaveBeenCalled();

    buttonOf(card('Fund Services'), 'Add product').click();
    expect(open.mock.calls[1][1]?.data).toMatchObject({ departmentId: 5 });
    expect(snack()).toContain('Trade Archive added');
    expect(navigate).toHaveBeenCalledWith(['/beadle/admin/products', 7]);
  });
});
