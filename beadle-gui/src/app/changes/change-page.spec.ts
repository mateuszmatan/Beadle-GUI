import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { productionChange } from '../testing/change-fixtures';
import { buttonOf, text } from '../testing/dom';
import { ChangesApi } from './change-api';
import { ChangePage } from './change-page';

@Component({
  imports: [ChangePage],
  template: `
    <beadle-change-page [change]="change" [crumb]="crumb()">
      <p class="content">The page</p>
    </beadle-change-page>
  `,
})
class Host {
  private readonly api = inject(ChangesApi);
  readonly crumb = signal<string | undefined>('Edit');
  readonly change = rxResource({ stream: () => this.api.get(7) });
}

describe('ChangePage', () => {
  let fixture: ComponentFixture<Host>;
  let http: HttpTestingController;

  const page = () => fixture.nativeElement as HTMLElement;
  const crumbs = () => text(page().querySelector('.breadcrumb'));

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function show(crumb?: string) {
    fixture = TestBed.createComponent(Host);
    fixture.componentInstance.crumb.set(crumb);
    fixture.detectChanges();
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('leads from Changes over the change to the page below it while the change loads', async () => {
    await show('Edit');
    expect(crumbs()).toBe('Changes/Edit');
    expect(page().querySelector('beadle-loading')).not.toBeNull();
    expect(text(page().querySelector('.content'))).toBe('The page');

    http.expectOne('/api/changes/7').flush(productionChange());
    await settle();

    expect(crumbs()).toBe('Changes/CHG0012345/Edit');
    expect(
      [...page().querySelectorAll('.breadcrumb a')].map((link) => link.getAttribute('href')),
    ).toEqual(['/changes', '/changes/7']);
    expect(page().querySelector('beadle-loading')).toBeNull();
  });

  it('ends with the change number on the change page itself', async () => {
    await show();
    expect(crumbs()).toBe('Changes/Change');

    http.expectOne('/api/changes/7').flush(productionChange());
    await settle();
    expect(crumbs()).toBe('Changes/CHG0012345');
  });

  it('says why the change could not be loaded and loads it again', async () => {
    await show('Secure coding ticket');
    http
      .expectOne('/api/changes/7')
      .flush({ detail: 'Change 7 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change could not be loaded: Change 7 does not exist',
    );
    expect(crumbs()).toBe('Changes/Secure coding ticket');

    buttonOf(page(), 'Try again').click();
    await settle();
    http.expectOne('/api/changes/7').flush(productionChange());
    await settle();
    expect(page().querySelector('.banner')).toBeNull();
    expect(crumbs()).toBe('Changes/CHG0012345/Secure coding ticket');
  });
});
