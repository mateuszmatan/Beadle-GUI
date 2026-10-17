import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { buttonOf, enter, errorOf, fieldOf, text } from '../testing/dom';
import { fits } from './change-model';
import { ChangeTexts } from './change-texts';

const GENERATED = { shortDescription: 'CertScanner 4.2', description: 'Release of CertScanner.' };

@Component({
  imports: [ChangeTexts],
  template: `
    <beadle-change-texts
      [shortDescription]="shortDescription"
      [description]="description"
      [generated]="generated()"
    />
  `,
})
class Host {
  readonly shortDescription = new FormControl(GENERATED.shortDescription, {
    nonNullable: true,
    validators: [Validators.required, fits(160)],
  });
  readonly description = new FormControl(GENERATED.description, { nonNullable: true });
  readonly generated = signal<typeof GENERATED | null>(null);
}

describe('ChangeTexts', () => {
  let fixture: ComponentFixture<Host>;

  const page = () => fixture.nativeElement as HTMLElement;
  const counter = (label: string) => text(fieldOf(page(), label)?.querySelector('.length-hint'));

  function type(label: string, value: string) {
    enter(page(), label, value);
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it('counts the length of the texts in bytes, as ProTech and the checks do', () => {
    expect(
      [...page().querySelectorAll('beadle-form-field')].map(
        (field) => !!field.querySelector('.required-marker'),
      ),
    ).toEqual([true, true]);
    type('Short description', 'Überweisung – 4.2');
    type('Description', 'Ü');
    expect(counter('Short description')).toBe('20 / 160');
    expect(counter('Description')).toBe('2 / 4000');

    type('Short description', 'é'.repeat(80) + 'x');
    fieldOf(page(), 'Short description')!.querySelector('input')!.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(counter('Short description')).toBe('');
    expect(errorOf(page(), 'Short description')).not.toBe('');
  });

  it('offers the generated text again for a text the user changed', () => {
    fixture.componentInstance.generated.set(GENERATED);
    fixture.detectChanges();
    expect(buttonOf(page(), 'Use the generated text')).toBeUndefined();

    type('Description', 'Mine');
    expect(buttonOf(page(), 'Use the generated text for the short description')).toBeUndefined();
    buttonOf(page(), 'Use the generated text for the description').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.description.value).toBe(GENERATED.description);
    expect(fixture.componentInstance.description.pristine).toBe(true);
    expect(buttonOf(page(), 'Use the generated text')).toBeUndefined();
  });
});
