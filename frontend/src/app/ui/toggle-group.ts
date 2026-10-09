import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  booleanAttribute,
  computed,
  forwardRef,
  inject,
  input,
  model,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'dso-toggle-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsoToggleGroup), multi: true },
  ],
  host: {
    class: 'btn-group btn-group-sm dso-toggle-group',
    '[attr.role]': "multiple() ? 'group' : 'radiogroup'",
  },
  template: '<ng-content />',
})
export class DsoToggleGroup implements ControlValueAccessor {
  readonly value = model<unknown>(null);
  readonly multiple = input(false, { transform: booleanAttribute });

  readonly disabled = signal(false);
  private changed: (value: unknown) => void = () => undefined;
  private touched: () => void = () => undefined;

  selected(value: unknown): boolean {
    const current = this.value();
    return this.multiple() ? Array.isArray(current) && current.includes(value) : current === value;
  }

  pick(value: unknown): void {
    const current = this.value();
    const next = this.multiple()
      ? this.selected(value)
        ? (current as unknown[]).filter((item) => item !== value)
        : [...(Array.isArray(current) ? current : []), value]
      : value;
    this.value.set(next);
    this.changed(next);
    this.touched();
  }

  writeValue(value: unknown): void {
    this.value.set(value);
  }

  registerOnChange(changed: (value: unknown) => void): void {
    this.changed = changed;
  }

  registerOnTouched(touched: () => void): void {
    this.touched = touched;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }
}

@Directive({
  selector: 'button[dsoToggle]',
  host: {
    type: 'button',
    class: 'btn btn-outline-primary',
    '[class.active]': 'selected()',
    '[attr.role]': "group.multiple() ? null : 'radio'",
    '[attr.aria-checked]': 'group.multiple() ? null : selected()',
    '[attr.aria-pressed]': 'group.multiple() ? selected() : null',
    '[disabled]': 'group.disabled()',
    '(click)': 'group.pick(value())',
  },
})
export class DsoToggle {
  readonly value = input.required<unknown>({ alias: 'dsoToggle' });

  protected readonly group = inject(DsoToggleGroup);
  protected readonly selected = computed(() => this.group.selected(this.value()));
}

export const TOGGLES = [DsoToggleGroup, DsoToggle] as const;
