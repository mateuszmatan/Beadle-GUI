import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { BusinessService } from '../core/models';
import { BusinessServiceDialog } from './business-service-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { buttonOf, gridHeaders, gridRows, text, toast } from '../testing/dom';
import { businessService } from '../testing/fixtures';
import { BusinessServicesAdmin } from './business-services-admin';

describe('BusinessServicesAdmin', () => {
  let fixture: ComponentFixture<BusinessServicesAdmin>;
  let http: HttpTestingController;

  const fundServices = businessService({ id: 5, name: 'Fund Services', productCount: 0 });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BusinessServicesAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BusinessServicesAdmin);
  });

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => gridRows(page());
  const row = (name: string) => rows().find((tr) => text(tr.querySelector('.name')) === name)!;
  const headers = () => gridHeaders(page());
  const snack = () => text(toast());

  async function load(businessServices: BusinessService[] = [businessService(), fundServices]) {
    fixture.detectChanges();
    http.expectOne('/api/business-services').flush(businessServices);
    await fixture.whenStable();
  }

  const dialogClosing = (...results: unknown[]) => {
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );
    return open;
  };

  it('lists the business services with their products and says which can be deleted', async () => {
    await load();

    expect(text(page().querySelector('.list-header h2'))).toBe('Business services');
    expect(text(page().querySelector('.summary'))).toBe('2 business services with 1 product');
    expect(text(page().querySelector('.business-services .section-help'))).toBe(
      'Every product belongs to one business service, and people choose their business service to see its changes. Only an empty business service can be deleted.',
    );
    expect(headers()).toEqual(['Business service', 'Products', '']);
  });

  it('invites to add the first business service', async () => {
    await load([]);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No business services yet');
    expect(text(page().querySelector('.summary'))).toBe('0 business services with 0 products');
    expect(
      [...page().querySelectorAll('button')].filter(
        (button) => text(button) === 'Add business service',
      ),
    ).toEqual([buttonOf(page().querySelector('.empty-state')!, 'Add business service')]);
  });

  it('shows why the business services could not be loaded', async () => {
    fixture.detectChanges();
    http
      .expectOne('/api/business-services')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toContain(
      'The business services could not be loaded: Beadle cannot be reached.',
    );

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/business-services').flush([businessService()]);
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
    expect(rows().length).toBe(1);
  });

  it('deletes only a business service without products or changes', async () => {
    await load([
      businessService(),
      fundServices,
      businessService({ id: 4, name: 'Custody', productCount: 0, changeCount: 2 }),
    ]);

    expect(buttonOf(row('Corporate Technology'), 'Delete').disabled).toBe(true);
    expect(
      [...row('Fund Services').querySelectorAll('button')].map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual(['Rename Fund Services', 'Delete Fund Services']);
    const reason = (name: string) => {
      const id = buttonOf(row(name), 'Delete').getAttribute('aria-describedby');
      return id ? text(page().querySelector(`#${id}`)) : null;
    };
    const productsLeft =
      'Only an empty business service can be deleted. Corporate Technology still has 1 product: move it to another business service first.';
    expect(row('Corporate Technology').querySelector('.delete')?.getAttribute('title')).toBe(
      productsLeft,
    );
    expect(reason('Corporate Technology')).toBe(productsLeft);
    expect(buttonOf(row('Fund Services'), 'Delete').disabled).toBe(false);
    expect(row('Fund Services').querySelector('.delete')?.hasAttribute('title')).toBe(false);
    expect(reason('Fund Services')).toBeNull();
    expect(buttonOf(row('Custody'), 'Delete').disabled).toBe(true);
    expect(reason('Custody')).toBe('Custody cannot be deleted: it still has 2 changes.');
  });

  it('adds and renames a business service and lists the business services again', async () => {
    await load();
    const added = businessService({ id: 6, name: 'Treasury', productCount: 0 });
    const open = dialogClosing(added, { ...fundServices, name: 'Fund Administration' }, undefined);

    buttonOf(page(), 'Add business service').click();
    fixture.detectChanges();
    http.expectOne('/api/business-services').flush([businessService(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(BusinessServiceDialog);
    expect(open.mock.calls[0][1]?.data).toBeNull();
    expect(snack()).toContain('Treasury added. Add its products on the Products tab.');
    expect(text(rows().at(-1)!.querySelector('.name'))).toBe('Treasury');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectOne('/api/business-services').flush([businessService(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[1][1]?.data).toEqual(fundServices);
    expect(snack()).toContain('Fund Services renamed to Fund Administration');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectNone('/api/business-services');
  });

  it('deletes a business service once confirmed and says why one could not be deleted', async () => {
    await load([businessService({ productCount: 0 }), fundServices]);
    const open = dialogClosing(true, false, true);

    buttonOf(row('Fund Services'), 'Delete').click();
    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toMatchObject({
      title: 'Delete the business service Fund Services?',
      message:
        'Fund Services has no products, so nothing else is deleted with it. It disappears from every list of business services in Beadle. This cannot be undone.',
      confirmLabel: 'Delete business service',
      danger: true,
    });
    http.expectOne({ method: 'DELETE', url: '/api/business-services/5' }).flush(null);
    fixture.detectChanges();
    http.expectOne('/api/business-services').flush([businessService({ productCount: 0 })]);
    await fixture.whenStable();

    expect(snack()).toContain('Fund Services deleted');
    expect(rows().length).toBe(1);

    buttonOf(row('Corporate Technology'), 'Delete').click();
    http.expectNone('/api/business-services/3');

    buttonOf(row('Corporate Technology'), 'Delete').click();
    http
      .expectOne({ method: 'DELETE', url: '/api/business-services/3' })
      .flush(
        { detail: 'Corporate Technology still has 1 product(s).' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(snack()).toContain('Corporate Technology still has 1 product(s).');
  });
});
