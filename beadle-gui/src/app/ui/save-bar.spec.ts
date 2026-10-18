import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { buttonOf, text } from '../testing/dom';
import { Failure, SaveBar } from './save-bar';

const ATTENTION = 'Some fields need your attention.';

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
    fixture.componentRef.setInput('label', 'Save template');
    fixture.componentRef.setInput('cancelLink', ['/changes', 7]);
    reloads = 0;
    fixture.componentInstance.reload.subscribe(() => reloads++);
  });

  it('says there are unsaved changes, links Cancel and shows a spinner on the submit while saving', () => {
    show({});
    expect(text(page().querySelector('.save-bar'))).toBe('Cancel Save template');
    expect(page().querySelector('a.btn-link')?.getAttribute('href')).toBe('/changes/7');

    show({ dirty: true, saving: true });
    expect(text(page().querySelector('.save-bar .muted'))).toBe('Unsaved changes');
    const submit = page().querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(submit.disabled).toBe(true);
    expect(submit.querySelector('beadle-spinner')).not.toBeNull();
  });

  it('shows why the save failed instead, lists the field problems and offers Reload after a conflict', () => {
    show({ dirty: true, failure: { message: ATTENTION, problems: [], reload: false } });
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
