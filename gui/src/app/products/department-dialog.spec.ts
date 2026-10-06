import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Department } from '../core/models';
import { inputOf } from '../testing/dom';
import { department } from '../testing/fixtures';
import { DepartmentDialog } from './department-dialog';

describe('DepartmentDialog', () => {
  let fixture: ComponentFixture<DepartmentDialog>;
  let http: HttpTestingController;
  const close = vi.fn();

  async function render(data: Department | null) {
    TestBed.configureTestingModule({
      imports: [DepartmentDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DepartmentDialog);
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

  it('adds a department with the trimmed name', async () => {
    await render(null);
    expect(page().querySelector('h2')?.textContent).toBe('Add department');

    await submit('  Treasury ');

    const request = http.expectOne({ method: 'POST', url: '/api/departments' });
    expect(request.request.body).toEqual({ name: 'Treasury' });
    const added = department({ id: 6, name: 'Treasury' });
    request.flush(added);

    expect(close).toHaveBeenCalledWith(added);
  });

  it('asks again for a blank name', async () => {
    await render(null);

    await submit('   ');

    http.expectNone('/api/departments');
    expect(page().querySelector('mat-error')?.textContent).toBe('Required');
  });

  it('renames a department with the version it was read at', async () => {
    await render(department({ version: 2 }));
    expect(page().querySelector('h2')?.textContent).toBe('Rename Corporate Technology');
    expect(inputOf(page(), 'Name').value).toBe('Corporate Technology');

    await submit('Corporate Tech');

    const request = http.expectOne({ method: 'PUT', url: '/api/departments/3' });
    expect(request.request.body).toEqual({ name: 'Corporate Tech', version: 2 });
    request.flush(department({ name: 'Corporate Tech', version: 3 }));

    expect(close).toHaveBeenCalledWith(department({ name: 'Corporate Tech', version: 3 }));
  });

  it('shows the conflict the portal reports and stays open', async () => {
    await render(null);

    await submit('custody');
    http
      .expectOne('/api/departments')
      .flush(
        { detail: 'A department named Custody already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(page().querySelector('[role=alert]')?.textContent).toBe(
      'A department named Custody already exists',
    );
    expect(close).not.toHaveBeenCalled();
  });
});
