import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { of } from 'rxjs';
import { LookupItem } from '../core/models';
import { buttonOf, fieldOf, text } from '../testing/dom';
import { Field, Fields, line } from './fields';
import { text as control } from './form-controls';
import { LOOKUP_DELAY, LookupDialog, appended, pickInto } from './lookup-dialog';

const item = (value: string, detail: string | null = null): LookupItem => ({ value, detail });

describe('LookupDialog', () => {
  let fixture: ComponentFixture<LookupDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  const page = () => fixture.nativeElement as HTMLElement;
  const search = () => page().querySelector<HTMLInputElement>('.search input')!;

  beforeEach(async () => {
    close.mockReset();
    TestBed.configureTestingModule({
      imports: [LookupDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DIALOG_DATA,
          useValue: { kind: 'configuration-items', label: 'Affected CI' },
        },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LookupDialog);
    await settle();
  });

  afterEach(() => http.verify());

  async function settle(wait = 0) {
    await new Promise((resolve) => setTimeout(resolve, wait));
    TestBed.tick();
    fixture.detectChanges();
  }

  function found(query: string | null): TestRequest {
    return http.expectOne(
      (request) =>
        request.url === '/api/lookups/configuration-items' && request.params.get('q') === query,
    );
  }

  async function type(value: string) {
    search().value = value;
    search().dispatchEvent(new Event('input'));
    await settle(LOOKUP_DELAY + 10);
  }

  it('lists the first entries, searches for the typed text and fills in the picked one', async () => {
    found(null).flush([item('CertScanner', 'Certificate Management'), item('Payments Hub')]);
    await settle();

    expect(text(page().querySelector('h2'))).toBe('Find Affected CI');
    expect(page().querySelector('.results')?.getAttribute('aria-label')).toBe('Found Affected CI');
    expect([...page().querySelectorAll('.results .value')].map(text)).toEqual([
      'CertScanner',
      'Payments Hub',
    ]);
    expect(text(page().querySelector('.results .detail'))).toBe('Certificate Management');

    await type(' pay ');
    found('pay').flush([item('Payments Hub', 'Payments')]);
    await settle();
    page().querySelector<HTMLButtonElement>('.results button')!.click();

    expect(close).toHaveBeenCalledWith(item('Payments Hub', 'Payments'));
  });

  it('picks the first entry on Enter, says when nothing matches and why the search failed', async () => {
    found(null).flush([]);
    await settle();
    expect(text(page().querySelector('.results .empty'))).toBe('Nothing to choose from.');
    search().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(close).not.toHaveBeenCalled();

    await type('zz');
    found('zz').flush([]);
    await settle();
    expect(text(page().querySelector('.results .empty'))).toBe('Nothing matches "zz".');

    await type('cert');
    found('cert').flush({ detail: 'ProTech is down' }, { status: 502, statusText: 'Bad Gateway' });
    await settle();
    expect(text(page().querySelector('.choice-error'))).toBe('ProTech is down');

    await type('certs');
    found('certs').flush([item('CertScanner'), item('CertVault')]);
    await settle();
    search().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    expect(close).toHaveBeenCalledWith(item('CertScanner'));
  });

  it('picks the first entry of the text typed when Enter comes before its search', async () => {
    found(null).flush([item('CertScanner'), item('Payments Hub')]);
    await settle();

    search().value = 'pay';
    search().dispatchEvent(new Event('input'));
    search().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();
    expect(close).not.toHaveBeenCalled();
    await settle(LOOKUP_DELAY + 10);
    const payments = found('pay');
    await settle();
    expect(close).not.toHaveBeenCalled();
    payments.flush([item('Payments Hub', 'Payments')]);
    await settle();
    expect(close).toHaveBeenCalledWith(item('Payments Hub', 'Payments'));
  });

  it('forgets an Enter when the text changes before its search', async () => {
    found(null).flush([item('CertScanner'), item('Payments Hub')]);
    await settle();

    search().value = 'pay';
    search().dispatchEvent(new Event('input'));
    search().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await type('cert');
    found('cert').flush([item('CertScanner')]);
    await settle();
    await type('pay');
    found('pay').flush([item('Payments Hub', 'Payments')]);
    await settle();

    expect(close).not.toHaveBeenCalled();
  });
});

@Component({
  imports: [Fields],
  template: `<div class="form-fields"><dso-fields [group]="group" [fields]="fields" /></div>`,
})
class LookupHost {
  readonly group = new FormGroup({
    configurationItem: control('CertScanner'),
    directBusinessService: control('Certificate Management'),
    affectedClients: control('Funds'),
  });
  readonly fields: Field[] = [
    line('configurationItem', 'Affected CI', '', 6, {
      lookup: { kind: 'configuration-items', detail: 'directBusinessService' },
    }),
    line('directBusinessService', 'Direct business service', '', 6, { readonly: true }),
    line('affectedClients', 'Affected clients', '', 6, {
      lookup: { kind: 'clients', append: true },
    }),
  ];
}

describe('Fields with a lookup', () => {
  let fixture: ComponentFixture<LookupHost>;

  const page = () => fixture.nativeElement as HTMLElement;
  const group = () => fixture.componentInstance.group;

  beforeEach(() => {
    fixture = TestBed.createComponent(LookupHost);
    fixture.detectChanges();
  });

  function picking(result: LookupItem | undefined) {
    return vi.spyOn(TestBed.inject(Dialog), 'open').mockReturnValue({
      closed: of(result),
    } as unknown as DialogRef<unknown>);
  }

  it('puts a magnifier on the fields with a lookup only', () => {
    const magnifiers = [...page().querySelectorAll('button.lookup')];

    expect(magnifiers.map((button) => button.getAttribute('aria-label'))).toEqual([
      'Find Affected CI',
      'Find Affected clients',
    ]);
    expect(magnifiers[0].querySelector('svg-icon')?.getAttribute('name')).toBe('search');
    expect(fieldOf(page(), 'Direct business service')?.querySelector('input')?.readOnly).toBe(true);
    expect(fieldOf(page(), 'Direct business service')?.classList).toContain('read-only');
  });

  it('fills the field and its detail with the picked entry and appends a picked client', () => {
    const open = picking(item('Payments Hub', 'Payments'));
    buttonOf(page(), 'Find Affected CI').click();

    expect(open.mock.calls[0][1]?.data).toEqual({
      kind: 'configuration-items',
      label: 'Affected CI',
    });
    expect(group().getRawValue()).toMatchObject({
      configurationItem: 'Payments Hub',
      directBusinessService: 'Payments',
    });
    expect(group().controls.configurationItem.dirty).toBe(true);

    open.mockReturnValue({
      closed: of(item('Asset Servicing')),
    } as unknown as DialogRef<unknown>);
    buttonOf(page(), 'Find Affected clients').click();
    expect(group().controls.affectedClients.value).toBe('Funds, Asset Servicing');
  });

  it('leaves the field as it is when nothing is picked', () => {
    picking(undefined);
    buttonOf(page(), 'Find Affected CI').click();

    expect(group().controls.configurationItem.value).toBe('CertScanner');
    expect(group().controls.configurationItem.dirty).toBe(false);
  });
});

describe('lookup helpers', () => {
  it('appends a client to the list once', () => {
    expect(appended('', 'Funds')).toBe('Funds');
    expect(appended(null, 'Funds')).toBe('Funds');
    expect(appended('Funds,Asset Servicing', 'Funds')).toBe('Funds, Asset Servicing');
    expect(appended('Funds', 'Asset Servicing')).toBe('Funds, Asset Servicing');
  });

  it('clears the detail of an entry without one', () => {
    const form = new FormGroup({ ci: control('A'), service: control('Old') });

    pickInto(form, 'ci', { kind: 'configuration-items', detail: 'service' }, item('B'));

    expect(form.getRawValue()).toEqual({ ci: 'B', service: '' });
    expect(form.controls.ci.touched).toBe(true);
  });
});
