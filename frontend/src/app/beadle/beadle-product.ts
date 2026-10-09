import { HttpErrorResponse } from '@angular/common/http';
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
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ChangeProfile, ChangesApi } from '../changes/change-api';
import { ChangeTasksForm } from '../changes/change-tasks-form';
import { tasksForm, toTaskTexts } from '../changes/change-tasks-model';
import { ChangeTemplateForm } from '../changes/change-template-form';
import { templateForm, toTemplate } from '../changes/change-template-model';
import { errorMessage, fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { BEADLE_ADMIN, BEADLE_PRODUCTS } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { applyFieldProblems } from '../shared/form-controls';
import { ProductAdmin } from './product-admin';

export function profileForm(profile: ChangeProfile) {
  return new FormGroup({
    template: templateForm(profile.template),
    tasks: tasksForm(profile.tasks),
  });
}

export type ProfileForm = ReturnType<typeof profileForm>;

@Component({
  selector: 'dso-beadle-product',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    ChangeTasksForm,
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
            The product and the change template of its ProTech changes. Whoever raises a change sees
            the template filled in and can change any of it.
          </p>
        </div>
      </header>
      <dso-product-admin [id]="id()" (saved)="renamed($event)" (deleted)="leave()" />
      @if (profile.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (profile.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (form(); as group) {
        <section class="defaults" aria-labelledby="defaults-title">
          <h2 id="defaults-title">Change template</h2>
          @if (version() === null) {
            <div class="banner info" role="status">
              Not saved yet. The values below are suggestions from the product.
            </div>
          }
          <form [formGroup]="group" (ngSubmit)="save()" novalidate>
            <dso-change-template-form [form]="group.controls.template" [admin]="true" />
            <section class="card default-tasks" aria-labelledby="tasks-title">
              <header>
                <h3 id="tasks-title">Default change tasks</h3>
                <p>Every new change of the product starts with these change tasks.</p>
              </header>
              <dso-change-tasks-form [tasks]="group.controls.tasks" />
            </section>
            <div class="save-bar">
              @if (saveError(); as error) {
                <span class="save-error" role="alert">{{ error }}</span>
                @if (conflict()) {
                  <button mat-button type="button" (click)="reload()">Reload</button>
                }
              } @else if (group.dirty) {
                <span class="muted">Unsaved changes</span>
              }
              <span class="spacer"></span>
              <a mat-button [routerLink]="products.path">Cancel</a>
              <button mat-flat-button type="submit" [disabled]="saving()">
                @if (saving()) {
                  <mat-spinner diameter="18" />
                }
                Save the template
              </button>
            </div>
          </form>
        </section>
      }
    </div>
  `,
  styles: `
    .default-tasks {
      margin-top: 10px;
      padding: 8px 14px 10px;

      > header {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 2px 10px;
        margin-bottom: 6px;

        h3 {
          margin: 0;
          font-size: 14px;
          font-weight: 600;
        }

        p {
          margin: 0;
          font-size: 11.5px;
          color: var(--dso-muted);
        }
      }
    }
  `,
})
export class BeadleProduct implements HasUnsavedChanges {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
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
  protected readonly form = signal<ProfileForm | null>(null);
  protected readonly version = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly conflict = signal(false);

  constructor() {
    effect(() => {
      if (this.profile.hasValue()) {
        this.version.set(this.profile.value().version);
        this.form.set(profileForm(this.profile.value()));
      }
    });
  }

  renamed(product: { name: string }): void {
    this.newName.set(product.name);
  }

  hasUnsavedChanges(): boolean {
    return !!this.form()?.dirty;
  }

  protected leave(): void {
    this.form()?.markAsPristine();
    this.router.navigate([this.products.path]);
  }

  protected reload(): void {
    this.saveError.set(null);
    this.conflict.set(false);
    this.profile.reload();
  }

  protected save(): void {
    const form = this.form()!;
    this.saveError.set(null);
    this.conflict.set(false);
    form.markAllAsTouched();
    if (form.invalid) {
      this.saveError.set('Some fields need your attention.');
      return;
    }
    this.saving.set(true);
    this.api
      .saveProfile(
        this.id(),
        this.version(),
        toTemplate(form.controls.template),
        toTaskTexts(form.controls.tasks),
      )
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.version.set(saved.version);
          form.markAsPristine();
          this.notifier.success(`The change template of ${saved.productName} is saved`);
        },
        error: (error) => {
          const problems = fieldProblems(error);
          const unmatched = applyFieldProblems(form, problems);
          this.saveError.set(
            unmatched.length || !problems.length
              ? errorMessage(error)
              : 'The portal did not accept some values. They are marked below.',
          );
          this.conflict.set(error instanceof HttpErrorResponse && error.status === 409);
        },
      });
  }
}
