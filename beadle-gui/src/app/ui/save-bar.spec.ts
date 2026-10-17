import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { buttonOf, text } from '../testing/dom';
import { ATTENTION, Failure, NEEDS_ATTENTION, SaveBar, failureOf } from './save-bar';

const refused = (status: number, body: object) =>
  new HttpErrorResponse({ status, statusText: 'Refused', error: body });

describe('failureOf', () => {
  it('leads with what could not be done, lists the field problems by their labels and offers no reload', () => {
    expect(
      failureOf(
        refused(400, {
          detail: '2 fields are invalid',
          problems: [
            { field: 'schedule.validationEnd', message: 'must be after the start' },
            { field: 'tasks[0].details.shortDescription', message: 'is used twice' },
          ],
        }),
        'The update could not be published',
      ),
    ).toEqual({
      message: 'The update could not be published: 2 fields are invalid',
      problems: [
        'Validation end: must be after the start',
        'Change task 1: short description: is used twice',
      ],
      reload: false,
    });
  });

  it('offers to reload when someone else saved first, and only then', () => {
    expect(failureOf(refused(409, { detail: 'Changed meanwhile' }), 'Not saved')).toEqual({
      message: 'Not saved: Changed meanwhile',
      problems: [],
      reload: true,
    });
    expect(failureOf(refused(403, { detail: 'Only Custody' }), 'Not saved').reload).toBe(false);
    expect(NEEDS_ATTENTION).toEqual({ message: ATTENTION, problems: [], reload: false });
    expect(ATTENTION).toBe('Some fields need your attention.');
  });
});

describe('SaveBar', () => {
  let fixture: ComponentFixture<SaveBar>;
  let reloads: number;

  const page = () => fixture.nativeElement as HTMLElement;

  function show(inputs: { failure?: Failure | null; saving?: boolean; dirty?: boolean }) {
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(SaveBar);
    fixture.componentRef.setInput('label', 'Save the template');
    fixture.componentRef.setInput('cancelLink', ['/changes', 7]);
    reloads = 0;
    fixture.componentInstance.reload.subscribe(() => reloads++);
  });

  it('says there are unsaved changes, links Cancel and shows a spinner on the submit while saving', () => {
    show({});
    expect(text(page().querySelector('.save-bar'))).toBe('Cancel Save the template');
    expect(page().querySelector('a.btn-link')?.getAttribute('href')).toBe('/changes/7');

    show({ dirty: true, saving: true });
    expect(text(page().querySelector('.save-bar .muted'))).toBe('Unsaved changes');
    const submit = page().querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(submit.disabled).toBe(true);
    expect(submit.querySelector('beadle-spinner')).not.toBeNull();
  });

  it('shows why the save failed instead, lists the field problems and offers Reload after a conflict', () => {
    show({ dirty: true, failure: NEEDS_ATTENTION });
    expect(text(page().querySelector('.save-error'))).toBe(ATTENTION);
    expect(page().querySelector('.problems-banner')).toBeNull();
    expect(buttonOf(page(), 'Reload')).toBeUndefined();

    show({
      failure: { message: 'Not saved: Changed meanwhile', problems: ['Epics: gone'], reload: true },
    });
    expect(text(page().querySelector('.save-error'))).toBe('Not saved: Changed meanwhile');
    expect([...page().querySelectorAll('.problems-banner li')].map(text)).toEqual(['Epics: gone']);
    expect(page().querySelector('.save-bar .muted')).toBeNull();
    buttonOf(page(), 'Reload').click();
    expect(reloads).toBe(1);
  });
});
