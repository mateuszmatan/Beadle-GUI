import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { byteLength } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { FORM_FIELD } from '../ui/form-field';

/** The two texts ProTech shows for a change, with their length in bytes as ProTech counts it. */
@Component({
  selector: 'beadle-change-texts',
  imports: [ReactiveFormsModule, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (generated(); as text) {
      @if (shortDescription().value !== text.shortDescription) {
        <button
          type="button"
          class="text-link new-text"
          aria-label="Use the generated text for the short description"
          (click)="use(shortDescription(), text.shortDescription)"
        >
          Use the generated text
        </button>
      }
    }
    <beadle-form-field>
      <beadle-label>Short description</beadle-label>
      <input beadleInput [formControl]="shortDescription()" maxlength="160" required />
      <beadle-hint class="length-hint"
        >{{ byteLength(shortDescription().value) }} / 160</beadle-hint
      >
      <beadle-error>{{ errorText(shortDescription()) }}</beadle-error>
    </beadle-form-field>
    @if (generated(); as text) {
      @if (description().value !== text.description) {
        <button
          type="button"
          class="text-link new-text"
          aria-label="Use the generated text for the description"
          (click)="use(description(), text.description)"
        >
          Use the generated text
        </button>
      }
    }
    <beadle-form-field>
      <beadle-label>Description</beadle-label>
      <textarea beadleInput [rows]="rows()" [formControl]="description()" required></textarea>
      <beadle-hint class="length-hint">{{ byteLength(description().value) }} / 4000</beadle-hint>
      <beadle-error>{{ errorText(description()) }}</beadle-error>
    </beadle-form-field>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    textarea {
      font-family: var(--beadle-mono);
      font-size: 12px;
    }

    .new-text {
      align-self: flex-start;
    }

    .length-hint {
      display: block;
      text-align: end;
    }
  `,
})
export class ChangeTexts {
  readonly shortDescription = input.required<FormControl<string>>();
  readonly description = input.required<FormControl<string>>();
  /** The texts Beadle wrote, offered again once the user changed them; none on a change ProTech holds. */
  readonly generated = input<{ shortDescription: string; description: string } | null>(null);
  readonly rows = input(9);

  protected readonly byteLength = byteLength;
  protected readonly errorText = errorText;

  protected use(control: FormControl<string>, generated: string): void {
    control.setValue(generated);
    control.markAsPristine();
  }
}
