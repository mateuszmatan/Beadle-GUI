import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  ElementRef,
  booleanAttribute,
  computed,
  forwardRef,
  inject,
  input,
  model,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

const ARROWS: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };

@Component({
  selector: 'dso-toggle-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsoToggleGroup), multi: true },
  ],
  host: {
    class: 'btn-group btn-group-sm dso-toggle-group',
    '[attr.role]': "multiple() ? 'group' : 'radiogroup'",
    '(keydown)': 'move($event)',
  },
  template: '<ng-content />',
})
export class DsoToggleGroup implements ControlValueAccessor {
  readonly value = model<unknown>(null);
  readonly multiple = input(false, { transform: booleanAttribute });

  readonly disabled = signal(false);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private changed: (value: unknown) => void = () => undefined;
  private touched: () => void = () => undefined;

  selected(value: unknown): boolean {
    const current = this.value();
    return this.multiple() ? Array.isArray(current) && current.includes(value) : current === value;
  }

  pick(value: unknown): void {
    const current = this.value();
    if (!this.multiple() && value === current) {
      return;
    }
    const next = this.multiple()
      ? this.selected(value)
        ? (current as unknown[]).filter((item) => item !== value)
        : [...(Array.isArray(current) ? current : []), value]
      : value;
    this.value.set(next);
    this.changed(next);
    this.touched();
  }

  protected move(event: KeyboardEvent): void {
    const step = ARROWS[event.key];
    if (this.multiple() || !step) {
      return;
    }
    const buttons = [
      ...this.element.querySelectorAll<HTMLButtonElement>('button[role=radio]:not(:disabled)'),
    ];
    const at = buttons.indexOf(event.target as HTMLButtonElement);
    const next = buttons[(at + step + buttons.length) % buttons.length];
    if (at >= 0 && next) {
      event.preventDefault();
      next.focus();
      next.click();
    }
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
