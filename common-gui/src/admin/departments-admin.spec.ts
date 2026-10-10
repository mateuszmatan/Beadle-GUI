import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Department } from '../core/models';
import { DepartmentDialog } from '../departments/department-dialog';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { buttonOf, gridCell, gridHeaders, gridRows, text, toast } from '../testing/dom';
import { department } from '../testing/fixtures';
import { DepartmentUsage, DepartmentsAdmin } from './departments-admin';

interface Busy extends Department {
  taskCount: number;
}

describe('DepartmentsAdmin', () => {
  let fixture: ComponentFixture<DepartmentsAdmin<Busy>>;
  let http: HttpTestingController;
  let shown: readonly Busy[][];

  const busy = (overrides: Partial<Busy> = {}): Busy => ({
    ...department(),
    taskCount: 2,
    ...overrides,
  });
  const fundServices = busy({ id: 5, name: 'Fund Services', productCount: 0, taskCount: 0 });
  const plain: DepartmentUsage<Busy> = { subject: 'its changes' };
  const tasks: DepartmentUsage<Busy> = {
    subject: 'its products and tasks',
    counts: [{ noun: 'task', count: (department) => department.taskCount }],
    columns: [
      {
        key: 'tasks',
        header: 'Tasks',
        value: (department) => department.taskCount,
        numeric: true,
      },
    ],
    blocker: (department) =>
      department.taskCount ? `${department.name} still has open tasks.` : null,
  };

  function create(usage: DepartmentUsage<Busy>) {
    TestBed.configureTestingModule({
      imports: [DepartmentsAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent<DepartmentsAdmin<Busy>>(DepartmentsAdmin);
    fixture.componentRef.setInput('usage', usage);
    shown = [];
    fixture.componentInstance.loaded.subscribe(
      (departments) => (shown = [...shown, [...departments]]),
    );
  }

  afterEach(() => http.verify());

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => gridRows(page());
  const row = (name: string) => rows().find((tr) => text(tr.querySelector('.name')) === name)!;
  const headers = () => gridHeaders(page());
  const snack = () => text(toast());

  async function load(departments: Busy[] = [busy(), fundServices]) {
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(departments);
    await fixture.whenStable();
  }

  const dialogClosing = (...results: unknown[]) => {
    const open = vi.spyOn(TestBed.inject(Dialog), 'open');
    results.forEach((result) =>
      open.mockReturnValueOnce({ closed: of(result) } as unknown as DialogRef<unknown>),
    );
    return open;
  };

  it('lists the departments with the columns and counts the app adds', async () => {
    create(tasks);
    await load();

    expect(text(page().querySelector('.list-header h2'))).toBe('Departments');
    expect(text(page().querySelector('.summary'))).toBe('2 departments with 1 product and 2 tasks');
    expect(text(page().querySelector('.departments .section-help'))).toBe(
      'Every product belongs to one department, and people choose their department to see its products and tasks. Only an empty department can be deleted.',
    );
    expect(headers()).toEqual(['Department', 'Products', 'Tasks', '']);
    expect(text(gridCell(row('Corporate Technology'), 'tasks'))).toBe('2');
    expect(shown.at(-1)?.map((department) => department.name)).toEqual([
      'Corporate Technology',
      'Fund Services',
    ]);
  });

  it('shows only the products when the app adds nothing, as a routed page without cells', async () => {
    create(plain);
    fixture.componentRef.setInput('cells', undefined);
    await load();

    expect(headers()).toEqual(['Department', 'Products', '']);
    expect(text(page().querySelector('.summary'))).toBe('2 departments with 1 product');
    expect(text(page().querySelector('.departments .section-help'))).toBe(
      'Every product belongs to one department, and people choose their department to see its changes. Only an empty department can be deleted.',
    );
  });

  it('invites to add the first department', async () => {
    create(plain);
    await load([]);

    expect(text(page().querySelector('.empty-state h3'))).toBe('No departments yet');
    expect(text(page().querySelector('.summary'))).toBe('0 departments with 0 products');
    expect(
      [...page().querySelectorAll('button')].filter((button) => text(button) === 'Add department'),
    ).toEqual([buttonOf(page().querySelector('.empty-state')!, 'Add department')]);
  });

  it('shows why the departments could not be loaded', async () => {
    create(tasks);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush(null, { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    expect(text(page().querySelector('.banner'))).toContain(
      'The departments could not be loaded: The portal cannot be reached.',
    );

    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([busy()]);
    await fixture.whenStable();

    expect(page().querySelector('.banner')).toBeNull();
    expect(rows().length).toBe(1);
  });

  it('deletes only a department without products or anything the app counts', async () => {
    create(tasks);
    await load([
      busy(),
      fundServices,
      busy({ id: 4, name: 'Custody', productCount: 0, taskCount: 2 }),
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
      'Only an empty department can be deleted. Corporate Technology still has 1 product: move it to another department first.';
    expect(row('Corporate Technology').querySelector('.delete')?.getAttribute('title')).toBe(
      productsLeft,
    );
    expect(reason('Corporate Technology')).toBe(productsLeft);
    expect(buttonOf(row('Fund Services'), 'Delete').disabled).toBe(false);
    expect(row('Fund Services').querySelector('.delete')?.hasAttribute('title')).toBe(false);
    expect(reason('Fund Services')).toBeNull();
    expect(buttonOf(row('Custody'), 'Delete').disabled).toBe(true);
    expect(row('Custody').querySelector('.delete')?.getAttribute('title')).toBe(
      'Custody still has open tasks.',
    );
    expect(reason('Custody')).toBe('Custody still has open tasks.');
  });

  it('adds and renames a department and lists the departments again', async () => {
    create(tasks);
    await load();
    const added = busy({ id: 6, name: 'Treasury', productCount: 0 });
    const open = dialogClosing(added, { ...fundServices, name: 'Fund Administration' }, undefined);

    buttonOf(page(), 'Add department').click();
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([busy(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[0][0]).toBe(DepartmentDialog);
    expect(open.mock.calls[0][1]?.data).toBeNull();
    expect(snack()).toContain('Treasury added. Add its products on the Products tab.');
    expect(text(rows().at(-1)!.querySelector('.name'))).toBe('Treasury');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([busy(), fundServices, added]);
    await fixture.whenStable();

    expect(open.mock.calls[1][1]?.data).toEqual(fundServices);
    expect(snack()).toContain('Fund Services renamed to Fund Administration');

    buttonOf(row('Fund Services'), 'Rename').click();
    fixture.detectChanges();
    http.expectNone('/api/departments');
  });

  it('deletes a department once confirmed and says why one could not be deleted', async () => {
    create(tasks);
    await load();
    const open = dialogClosing(true, false, true);

    buttonOf(row('Fund Services'), 'Delete').click();
    expect(open.mock.calls[0][0]).toBe(ConfirmDialog);
    expect(open.mock.calls[0][1]?.data).toMatchObject({
      title: 'Delete the department Fund Services?',
      message:
        'Fund Services has no products, so nothing else is deleted with it. It disappears from every list of departments in the portal. This cannot be undone.',
      confirmLabel: 'Delete department',
      danger: true,
    });
    http.expectOne({ method: 'DELETE', url: '/api/departments/5' }).flush(null);
    fixture.detectChanges();
    http.expectOne('/api/departments').flush([busy()]);
    await fixture.whenStable();

    expect(snack()).toContain('Fund Services deleted');
    expect(rows().length).toBe(1);

    fixture.componentInstance['delete'](busy());
    http.expectNone('/api/departments/3');

    fixture.componentInstance['delete'](busy());
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
