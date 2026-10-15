import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Product } from '../core/models';
import {
  buttonOf,
  gridCell,
  gridHeaders,
  gridRows,
  settleGrid,
  text,
  toast,
} from '@common/testing/dom';
import { department, product } from '../testing/fixtures';
import { ChangeProfileSummary } from '../changes/change-api';
import { BeadleProducts } from './beadle-products';
import { ProductDialog } from './product-dialog';
import { Department } from '@common/core/models';

describe('BeadleProducts', () => {
  let fixture: ComponentFixture<BeadleProducts>;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const fundServices = department({
    id: 5,
    name: 'Fund Services',
    productCount: 0,
  });
  const payments = product({
    id: 2,
    code: 'PAYHUB',
    name: 'Payments Hub',
    ownerTeam: null,
    departmentId: 5,
    departmentName: 'Fund Services',
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
    gridRows(page()).find((row) => text(row.querySelector('.name')) === name)!;
  const cells = (name: string) => [...rowOf(name).querySelectorAll('.ag-cell')].map(text);
  const snack = () => text(toast());

  async function load(
    products: Product[] = [product(), payments],
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
        { detail: 'The change templates are not available' },
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

  it('lists the products by department with the state of their change template', async () => {
    await load();

    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
    expect(text(card('Corporate Technology').querySelector('.tally'))).toBe('1 product');
    expect(text(page().querySelector('.count'))).toBe('2 products in 2 departments');
    expect(gridHeaders(card('Fund Services'))).toEqual([
      'Product',
      'Owner team',
      'Change template',
    ]);
    expect(rowOf('CertScanner').classList).toContain('clickable');
    expect(cells('CertScanner')).toEqual([
      'CertScannerCERT',
      'Technology Architecture',
      'Filled in · saved 2 days ago',
    ]);
    expect(cells('Payments Hub')).toEqual(['Payments HubPAYHUB', '–', 'Not filled in yet']);
    expect(text(page().querySelector('.tab-help'))).toBe(
      'Open a product to see its details and fill in its change template. Until then, its new changes start with values suggested from its name, code and owner team.',
    );
    expect(text(page())).not.toContain('service');
    expect(rowOf('CertScanner').querySelector('a')?.getAttribute('href')).toBe('/admin/products/1');

    const follow = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    rowOf('CertScanner').querySelector<HTMLAnchorElement>('a.name')!.click();
    await settleGrid();
    expect(String(follow.mock.calls[0][0])).toBe('/admin/products/1');
    expect(navigate).not.toHaveBeenCalled();

    gridCell(rowOf('Payments Hub'), 'ownerTeam').click();
    await settleGrid();
    expect(navigate).toHaveBeenCalledWith(['/admin/products', 2]);
  });

  it('still lists the products when their change templates cannot be loaded', async () => {
    await load(undefined, undefined, null);

    expect(text(page().querySelector('.banner span'))).toBe(
      'The products are listed, but the state of their change templates could not be loaded. The change templates are not available',
    );
    expect(text(gridCell(rowOf('CertScanner'), 'template'))).toBe('Not known');
    expect(cells('Payments Hub')).toEqual(['Payments HubPAYHUB', '–', 'Not known']);

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/change-profiles').flush([saved]);
    await fixture.whenStable();
    expect(page().querySelector('.banner')).toBeNull();
    expect(text(gridCell(rowOf('CertScanner'), 'template'))).toBe('Filled in · saved 2 days ago');
  });

  it('gathers the products without a department in a last card', async () => {
    await load([
      product(),
      product({ id: 7, name: 'Ledger', departmentId: null, departmentName: null }),
    ]);
    const unassigned = cards().at(-1)!;

    expect(text(unassigned.querySelector('h2'))).toBe('Not in a department');
    expect(text(unassigned.querySelector('.tally'))).toBe('1 product');
    expect(text(unassigned.querySelector('.hint'))).toBe(
      'Open each product and choose its department with Edit details.',
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
    expect(text(page().querySelector('.empty-state p'))).toBe(
      'Add the first product, then fill in its change template on its page.',
    );
    expect(text(page().querySelector('.empty-state button'))).toBe('Add product');

    fixture.componentInstance['products'].reload();
    fixture.detectChanges();
    http.expectOne('/api/products').flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();
    expect(text(page().querySelector('.banner span'))).toBe(
      'The products could not be loaded. The portal cannot be reached. Check your network connection and try again.',
    );

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/products').flush([payments]);
    http.expectNone('/api/departments');
    await fixture.whenStable();
    expect(page().querySelector('.banner')).toBeNull();
    expect(cards().map((section) => text(section.querySelector('h2')))).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
  });

  it('adds a product from the toolbar or a department card and opens it', async () => {
    await load();
    const created = product({ id: 7, name: 'Trade Archive' });
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    [undefined, created].forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );

    buttonOf(page().querySelector('.toolbar')!, 'Add product').click();
    expect(open.mock.calls[0][0]).toBe(ProductDialog);
    expect(open.mock.calls[0][1]?.data).toEqual({
      departments: [department(), fundServices],
      departmentId: null,
      product: null,
    });
    expect(navigate).not.toHaveBeenCalled();

    const addToFund = buttonOf(card('Fund Services'), 'Add product');
    expect(addToFund.getAttribute('aria-label')).toBe('Add product to Fund Services');
    addToFund.click();
    expect(open.mock.calls[1][1]?.data).toMatchObject({ departmentId: 5 });
    await fixture.whenStable();
    expect(snack()).toContain('Trade Archive added. Now fill in its change template.');
    expect(navigate).toHaveBeenCalledWith(['/admin/products', 7]);
  });
});
