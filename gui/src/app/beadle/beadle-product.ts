import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ChangesApi } from '../changes/change-api';
import { ChangeTemplateForm } from '../changes/change-template-form';
import {
  TemplateForm,
  applyTemplateProblems,
  templateForm,
  toTemplate,
} from '../changes/change-template-model';
import { errorMessage, fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { BEADLE_ADMIN, BEADLE_PRODUCTS } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { ProductAdmin } from './product-admin';

@Component({
  selector: 'dso-beadle-product',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    ChangeTemplateForm,
    ProductAdmin,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page editor">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a [routerLink]="admin.path">{{ admin.heading }}</a>
        <span class="sep" aria-hidden="true">/</span>
        <a [routerLink]="products.path">{{ products.label }}</a>
        <span class="sep" aria-hidden="true">/</span>
        <span>{{ productName() }}</span>
      </nav>
      <header class="page-header">
        <div>
          <h1>{{ productName() }}</h1>
          <p>
            The product, its services and the ServiceNow defaults of its production changes. The
            app owner who raises a change sees the defaults filled in and can change any of them.
          </p>
        </div>
      </header>
      <dso-product-admin [id]="id()" (saved)="renamed($event)" />
      @if (profile.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (profile.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (form(); as group) {
        <section class="defaults" aria-labelledby="defaults-title">
          <h2 id="defaults-title">ServiceNow defaults</h2>
          @if (version() === null) {
            <div class="banner info" role="status">
              Not saved yet. The values below are suggestions from the product.
            </div>
          }
          <form [formGroup]="group" (ngSubmit)="save()" novalidate>
            <dso-change-template-form [form]="group" />
            <div class="save-bar">
              @if (saveError(); as error) {
                <span class="save-error" role="alert">{{ error }}</span>
              } @else if (group.dirty) {
                <span class="muted">Unsaved changes</span>
              }
              <span class="spacer"></span>
              <a mat-button [routerLink]="products.path">Cancel</a>
              <button mat-flat-button type="submit" [disabled]="saving()">
                @if (saving()) {
                  <mat-spinner diameter="18" />
                }
                Save defaults
              </button>
            </div>
          </form>
        </section>
      }
    </div>
  `,
})
export class BeadleProduct implements HasUnsavedChanges {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly admin = BEADLE_ADMIN;
  protected readonly products = BEADLE_PRODUCTS;
  protected readonly errorMessage = errorMessage;
  protected readonly profile = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.profile(params),
  });
  private readonly newName = signal<string | null>(null);
  protected readonly productName = computed(
    () =>
      this.newName() ?? (this.profile.hasValue() ? this.profile.value().productName : 'Product'),
  );
  protected readonly form = signal<TemplateForm | null>(null);
  protected readonly version = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  constructor() {
    effect(() => {
      if (this.profile.hasValue()) {
        this.version.set(this.profile.value().version);
        this.form.set(templateForm(this.profile.value().template));
      }
    });
  }

  renamed(product: { name: string }): void {
    this.newName.set(product.name);
  }

  hasUnsavedChanges(): boolean {
    return !!this.form()?.dirty && !this.saving();
  }

  protected save(): void {
    const form = this.form()!;
    this.saveError.set(null);
    form.markAllAsTouched();
    if (form.invalid) {
      this.saveError.set('Some fields need your attention.');
      return;
    }
    this.saving.set(true);
    this.api
      .saveProfile(this.id(), this.version(), toTemplate(form))
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.version.set(saved.version);
          form.markAsPristine();
          this.notifier.success(`The ServiceNow defaults of ${saved.productName} are saved`);
        },
        error: (error) => {
          const problems = fieldProblems(error);
          const unmatched = applyTemplateProblems(form, problems);
          this.saveError.set(
            unmatched.length || !problems.length
              ? errorMessage(error)
              : 'The portal did not accept some values. They are marked below.',
          );
        },
      });
  }
}
