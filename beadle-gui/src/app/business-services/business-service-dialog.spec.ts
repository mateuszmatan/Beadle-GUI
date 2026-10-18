import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { BusinessService } from '../core/models';
import { inputOf, text } from '../testing/dom';
import { businessService } from '../testing/fixtures';
import { BusinessServiceDialog } from './business-service-dialog';

describe('BusinessServiceDialog', () => {
  let fixture: ComponentFixture<BusinessServiceDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(data: BusinessService | null) {
    TestBed.configureTestingModule({
      imports: [BusinessServiceDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DIALOG_DATA, useValue: data },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BusinessServiceDialog);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    close.mockReset();
  });

  const page = () => fixture.nativeElement as HTMLElement;

  async function submit(name: string) {
    const input = inputOf(page(), 'Name');
    input.value = name;
    input.dispatchEvent(new Event('input'));
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('adds a business service with the trimmed name', async () => {
    await render(null);
    expect(text(page().querySelector('h2'))).toBe('Add business service');
    expect(text(page().querySelector('button[type=submit]'))).toBe('Add business service');
    expect(text(page().querySelector('beadle-hint'))).toBe(
      'The name of the business service at BBH, for example Fund Services',
    );
    expect(page().querySelector('.intro')).toBeNull();

    await submit('  Treasury ');

    const request = http.expectOne({ method: 'POST', url: '/api/business-services' });
    expect(request.request.body).toEqual({ name: 'Treasury' });
    const added = businessService({ id: 6, name: 'Treasury' });
    request.flush(added);

    expect(close).toHaveBeenCalledWith(added);
  });

  it('asks again for a blank name', async () => {
    await render(null);

    await submit('   ');

    http.expectNone('/api/business-services');
    expect(page().querySelector('beadle-error')?.textContent).toBe('Required');
  });

  it('renames a business service with the version it was read at', async () => {
    await render(businessService({ version: 2 }));
    expect(text(page().querySelector('h2'))).toBe('Rename Corporate Technology');
    expect(text(page().querySelector('.intro'))).toBe(
      'The new name applies throughout Beadle immediately. The products of the business service are not affected.',
    );
    expect(text(page().querySelector('button[type=submit]'))).toBe('Rename business service');
    expect(inputOf(page(), 'Name').value).toBe('Corporate Technology');

    await submit('Corporate Tech');

    const request = http.expectOne({ method: 'PUT', url: '/api/business-services/3' });
    expect(request.request.body).toEqual({ name: 'Corporate Tech', version: 2 });
    request.flush(businessService({ name: 'Corporate Tech', version: 3 }));

    expect(close).toHaveBeenCalledWith(businessService({ name: 'Corporate Tech', version: 3 }));
  });

  it('shows the conflict Beadle reports and stays open', async () => {
    await render(null);

    await submit('custody');
    http
      .expectOne('/api/business-services')
      .flush(
        { detail: 'A business service named Custody already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'A business service named Custody already exists',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
