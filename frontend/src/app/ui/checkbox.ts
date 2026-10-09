import { ChangeDetectionStrategy, Component, forwardRef, model, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { uniqueId } from './form-field';

@Component({
  selector: 'dso-checkbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsoCheckbox), multi: true },
  ],
  host: { class: 'form-check dso-checkbox' },
  template: `
    <input
      class="form-check-input"
      type="checkbox"
      [id]="id"
      [checked]="checked()"
      [disabled]="disabled()"
      (change)="toggle($event)"
      (blur)="touched()"
    />
    <label class="form-check-label" [for]="id"><ng-content /></label>
  `,
})
export class DsoCheckbox implements ControlValueAccessor {
  readonly checked = model(false);

  protected readonly id = uniqueId('dso-checkbox');
  protected readonly disabled = signal(false);
  private changed: (value: boolean) => void = () => undefined;
  protected touched: () => void = () => undefined;

  protected toggle(event: Event): void {
    event.stopPropagation();
    const checked = (event.target as HTMLInputElement).checked;
    this.checked.set(checked);
    this.changed(checked);
  }

  writeValue(value: unknown): void {
    this.checked.set(!!value);
  }

  registerOnChange(changed: (value: boolean) => void): void {
    this.changed = changed;
  }

  registerOnTouched(touched: () => void): void {
    this.touched = touched;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }
}
