import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Directive,
  DoCheck,
  ElementRef,
  computed,
  contentChild,
  inject,
  signal,
} from '@angular/core';
import { AbstractControl, FormGroupDirective, NgControl, NgForm, Validators } from '@angular/forms';
import { Subscription, merge } from 'rxjs';

let nextId = 0;

export const uniqueId = (prefix: string) => `${prefix}-${nextId++}`;

@Directive({
  selector: 'input[beadleInput], textarea[beadleInput], select[beadleInput]',
  host: {
    '[id]': 'id',
    '[class.form-control]': '!select',
    '[class.form-control-sm]': '!select',
    '[class.form-select]': 'select',
    '[class.form-select-sm]': 'select',
    '[class.is-invalid]': 'errorState()',
    '[attr.aria-invalid]': 'errorState() || null',
    '[attr.aria-describedby]': 'describedBy()',
  },
})
export class BeadleInput implements DoCheck {
  readonly id = uniqueId('beadle-input');
  readonly element =
    inject<ElementRef<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>>(ElementRef)
      .nativeElement;
  readonly ngControl = inject(NgControl, { optional: true, self: true });

  protected readonly select = this.element.tagName === 'SELECT';

  private readonly form =
    inject(FormGroupDirective, { optional: true }) ?? inject(NgForm, { optional: true });
  private readonly revision = signal(0);
  private watched: AbstractControl | null = null;
  private subscription?: Subscription;
  private snapshot = '';

  private readonly field = inject(BeadleFormField, { optional: true });

  readonly errorState = computed(() => {
    this.revision();
    const control = this.ngControl?.control;
    return !!control && control.invalid && (control.touched || !!this.form?.submitted);
  });

  protected readonly describedBy = computed(() =>
    this.field ? (this.errorState() ? this.field.errorId : this.field.hintId) : null,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.subscription?.unsubscribe());
  }

  ngDoCheck(): void {
    const control = this.ngControl?.control ?? null;
    if (control !== this.watched) {
      this.watched = control;
      this.subscription?.unsubscribe();
      this.subscription = control
        ? merge(control.events, this.form?.ngSubmit ?? []).subscribe(() => this.changed())
        : undefined;
    }
    const snapshot = `${control?.status}|${control?.touched}`;
    if (snapshot !== this.snapshot) {
      this.snapshot = snapshot;
      this.changed();
    }
  }

  required(): boolean {
    this.revision();
    return this.element.required || !!this.ngControl?.control?.hasValidator(Validators.required);
  }

  private changed(): void {
    this.revision.update((value) => value + 1);
  }
}

@Component({
  selector: 'beadle-label',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class BeadleLabel {}

@Component({
  selector: 'beadle-hint',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class BeadleHint {}

@Component({
  selector: 'beadle-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class BeadleError {}

@Directive({
  selector: '[beadleSuffix]',
  host: { class: 'beadle-suffix' },
})
export class BeadleSuffix {}

@Component({
  selector: 'beadle-form-field',
  changeDetection: ChangeDetectionStrategy.Eager,
  host: {
    class: 'beadle-form-field',
    '[class.has-error]': 'errorState()',
    '[class.has-suffix]': '!!suffix()',
  },
  template: `
    <label class="form-label" [attr.for]="input()?.id">
      <ng-content select="beadle-label" />
      @if (input()?.required()) {
        <span class="required-marker" aria-hidden="true"></span>
      }
    </label>
    <div class="control">
      <ng-content />
      <ng-content select="[beadleSuffix]" />
    </div>
    @if (errorState()) {
      <div class="invalid-feedback d-block" [id]="errorId">
        <ng-content select="beadle-error" />
      </div>
    } @else {
      <div class="form-text" [id]="hintId">
        <ng-content select="beadle-hint" />
      </div>
    }
  `,
})
export class BeadleFormField {
  protected readonly input = contentChild(BeadleInput);
  protected readonly suffix = contentChild(BeadleSuffix);
  readonly hintId = uniqueId('beadle-hint');
  readonly errorId = uniqueId('beadle-error');
  protected readonly errorState = computed(() => this.input()?.errorState() ?? false);
}

export const FORM_FIELD = [
  BeadleFormField,
  BeadleLabel,
  BeadleHint,
  BeadleError,
  BeadleInput,
  BeadleSuffix,
] as const;
