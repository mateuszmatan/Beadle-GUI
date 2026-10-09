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
  selector: 'input[dsoInput], textarea[dsoInput], select[dsoInput]',
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
export class DsoInput implements DoCheck {
  readonly id = uniqueId('dso-input');
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

  private readonly field = inject(DsoFormField, { optional: true });

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
  selector: 'dso-label',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoLabel {}

@Component({
  selector: 'dso-hint',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoHint {}

@Component({
  selector: 'dso-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
})
export class DsoError {}

@Directive({
  selector: '[dsoSuffix]',
  host: { class: 'dso-suffix' },
})
export class DsoSuffix {}

@Component({
  selector: 'dso-form-field',
  changeDetection: ChangeDetectionStrategy.Eager,
  host: {
    class: 'dso-form-field',
    '[class.has-error]': 'errorState()',
    '[class.has-suffix]': '!!suffix()',
  },
  template: `
    <label class="form-label" [attr.for]="input()?.id">
      <ng-content select="dso-label" />
      @if (input()?.required()) {
        <span class="required-marker" aria-hidden="true"></span>
      }
    </label>
    <div class="control">
      <ng-content />
      <ng-content select="[dsoSuffix]" />
    </div>
    @if (errorState()) {
      <div class="invalid-feedback d-block" [id]="errorId">
        <ng-content select="dso-error" />
      </div>
    } @else {
      <div class="form-text" [id]="hintId">
        <ng-content select="dso-hint" />
      </div>
    }
  `,
})
export class DsoFormField {
  protected readonly input = contentChild(DsoInput);
  protected readonly suffix = contentChild(DsoSuffix);
  readonly hintId = uniqueId('dso-hint');
  readonly errorId = uniqueId('dso-error');
  protected readonly errorState = computed(() => this.input()?.errorState() ?? false);
}

export const FORM_FIELD = [DsoFormField, DsoLabel, DsoHint, DsoError, DsoInput, DsoSuffix] as const;
