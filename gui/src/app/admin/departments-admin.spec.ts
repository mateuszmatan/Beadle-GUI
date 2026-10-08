import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { Department } from '../core/models';
import { DepartmentDialog } from '../products/department-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { buttonOf, text } from '../testing/dom';
import { department } from '../testing/fixtures';
import { DepartmentsAdmin } from './departments-admin';

describe('DepartmentsAdmin', () => {
  let fixture: ComponentFixture<DepartmentsAdmin>;
  let http: HttpTestingController;

  const fundServices = department({
    id: 5,
    name: 'Fund Services',
    productCount: 0,
    serviceCount: 0,
    pipelineCount: 0,
    activePipelineCount: 0,
  });

  function create(pipelines: boolean) {
    TestBed.configureTestingModule({
      imports: [DepartmentsAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DepartmentsAdmin);
    fixture.componentRef.setInput('pipelines', pipelines);
  }

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => [...page().querySelectorAll<HTMLElement>('tr.mat-mdc-row')];
  const row = (name: string) => rows().find((tr) => text(tr.querySelector('.name')) === name)!;
  const headers = () =>
    [...page().querySelectorAll('th.mat-mdc-header-cell')].map((th) => text(th));
  const snack = () =>
    [...document.querySelectorAll('mat-snack-bar-container')].map((bar) => text(bar)).join(' ');

  async function load(departments: Department[] = [department(), fundServices]) {
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  const dialogClosing = (...results: unknown[]) => {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({
        afterClosed: () => of(result),
      } as unknown as MatDialogRef<unknown>),
    );
    return open;
  };

  it('lists the departments with their pipelines and charts them for DevSecOps', async () => {
    create(true);
    await load();
    const chart = page().querySelector('section.chart')!;

    expect(text(page().querySelector('.count'))).toBe('2 departments · 1 product · 2 services');
    expect(headers()).toEqual(['Department', 'Products', 'Services', 'DevSecOps pipelines', '']);
    expect(text(row('Corporate Technology').querySelector('.mat-column-pipelines'))).toBe(
      '3 · 2 active · 1 invalidated',
    );
    expect(text(row('Fund Services').querySelector('.mat-column-pipelines'))).toBe('None yet');
    expect(
      [...chart.querySelectorAll('.track')].map((track) => track.getAttribute('aria-label')),
    ).toEqual(['Corporate Technology: 2 active, 1 invalidated', 'Fund Services: none']);
    expect([...chart.querySelectorAll('.note')].map((note) => text(note))).toEqual([
      '3 pipelines · 1 product',
      '0 pipelines · 0 products',
    ]);
  });

  it('leaves the pipelines out for Beadle', async () => {
    create(false);
    await load();

    expect(headers()).toEqual(['Department', 'Products', '']);
    expect(text(page().querySelector('.count'))).toBe('2 departments · 1 product');
    expect(page().querySelector('section.chart')).toBeNull();
  });

  it('invites to add the first department', async () => {
    create(false);
    await load([]);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No departments yet');
    expect(text(page().querySelector('.count'))).toBe('0 departments · 0 products');
  });

  it('shows why the departments could not be loaded', async () => {
    create(true);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toContain('cannot be reached');
  });

  it('deletes only a department without products or changes', async () => {
    create(true);
    await load([
      department(),
      fundServices,
      department({ id: 4, name: 'Custody', productCount: 0, changeCount: 2 }),
    ]);

    expect(buttonOf(row('Corporate Technology'), 'Delete').disabled).toBe(true);
    expect(
      [...row('Fund Services').querySelectorAll('button')].map((button) =>
        button.getAttribute('aria-label'),
      ),
    ).toEqual(['Rename Fund Services', 'Delete Fund Services']);
    expect(row('Corporate Technology').querySelector('.delete')?.getAttribute('title')).toBe(
      'Corporate Technology still has 1 product. Move them to another department first.',
    );
    expect(buttonOf(row('Fund Services'), 'Delete').disabled).toBe(false);
    expect(row('Fund Services').querySelector('.delete')?.hasAttribute('title')).toBe(false);
    expect(buttonOf(row('Custody'), 'Delete').disabled).toBe(true);
    expect(row('Custody').querySelector('.delete')?.getAttribute('title')).toBe(
      'Custody still owns 2 changes raised in Beadle, so it cannot be deleted.',
    );
  });

  it('adds and renames a department and lists the departments again', async () => {
    create(true);
    await load();
    const added = department({ id: 6, name: 'Treasury', productCount: 0 });
    const open = dialogClosing(added, { ...fundServices, name: 'Fund Administration' }, undefined);

    buttonOf(page(), 'Add department').click();
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(DepartmentDialog);
    expect(open.mock.calls[0][1]?.data).toBeNull();
    expect(snack()).toContain('Treasury added');
    expect(text(rows().at(-1)!.querySelector('.name'))).toBe('Treasury');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[1][1]?.data).toEqual(fundServices);
    expect(snack()).toContain('Fund Services renamed to Fund Administration');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectNone('/api/departments');
  });

  it('deletes a department once confirmed and says why one could not be deleted', async () => {
    create(true);
    await load();
    const open = dialogClosing(true, false, true);

    buttonOf(row('Fund Services'), 'Delete').click();
    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toMatchObject({ title: 'Delete Fund Services?' });
    http.expectOne({ method: 'DELETE', url: '/api/departments/5' }).flush(null);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([department()]);
    await fixture.whenStable();

    expect(snack()).toContain('Fund Services deleted');
    expect(rows().length).toBe(1);

    fixture.componentInstance['delete'](department());
    http.expectNone('/api/departments/3');

    fixture.componentInstance['delete'](department());
    http
      .expectOne({ method: 'DELETE', url: '/api/departments/3' })
      .flush(
        { detail: 'Corporate Technology still has 1 product(s).' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(snack()).toContain('Corporate Technology still has 1 product(s).');
  });
});
