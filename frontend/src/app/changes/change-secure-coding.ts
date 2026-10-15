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
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MyDepartment } from '@common/departments/my-department';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { Notifier } from '@common/core/notifier';
import { CHANGES, beadleChange } from '../core/sections';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { applyFieldProblems } from '@common/shared/form-controls';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';
import { ChangesApi, ProductionChange, isOpen } from './change-api';
import { editHint } from './change-model';
import { problemText } from './change-problems';
import { PublishedChange } from './published-change';
import { SecureCodingFields } from './secure-coding-form';
import { SecureCodingForm, secureCodingForm, secureCodingRequest } from './secure-coding-model';

export function refusalOf(change: ProductionChange, departmentId: number | null): string | null {
  if (!isOpen(change)) {
    return `${change.number} is closed in ProTech and can no longer be changed`;
  }
  const ticket = change.template.secureCodingTicket;
  return ticket
    ? `${change.number} already has the secure coding ticket ${ticket}`
    : editHint(change, departmentId);
}

@Component({
  selector: 'dso-change-secure-coding',
  imports: [RouterLink, DsoLoading, DsoSpinner, SecureCodingFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a [routerLink]="section.path">{{ section.label }}</a>
        <span class="sep" aria-hidden="true">/</span>
        @if (change.hasValue()) {
          <a [routerLink]="changeLink(id())">{{ change.value().number }}</a>
          <span class="sep" aria-hidden="true">/</span>
        }
        <span>Secure coding ticket</span>
      </nav>
      @if (change.isLoading()) {
        <dso-loading />
      }
      @if (change.error(); as error) {
        <div class="banner" role="alert">
          <span>The change could not be loaded: {{ errorMessage(error) }}</span>
          <span class="spacer"></span>
          <button type="button" class="btn btn-link" (click)="change.reload()">Try again</button>
        </div>
      }
      @if (change.hasValue()) {
        @let c = change.value();
        <header class="page-header">
          <div>
            <h1>Secure coding ticket of {{ c.number }}</h1>
            <p>
              CyberTrack, the Jira project SCP, holds the secure coding tickets. The ticket is named
              after the APO number, the application and the implementation date, lists every value
              below in its description, and its number becomes the secure coding ticket of
              {{ c.number }} in ProTech. Fields marked * are required.
            </p>
          </div>
        </header>
        @if (refusal(); as message) {
          <div class="banner refused" role="alert">
            <span>{{ message }}</span>
            <span class="spacer"></span>
            <a class="btn btn-link" [routerLink]="changeLink(id())">Back to the change</a>
          </div>
        } @else if (form(); as f) {
          <section class="card block">
            <dso-secure-coding-form [form]="f" [applicationName]="c.productName" />
          </section>
          @if (failure(); as failed) {
            <div class="banner danger problems-banner" role="alert">
              <div>
                <strong>{{ failed.message }}</strong>
                @if (failed.problems.length) {
                  <ul class="problems">
                    @for (problem of failed.problems; track problem) {
                      <li>{{ problem }}</li>
                    }
                  </ul>
                }
              </div>
            </div>
          }
          <div class="save-bar">
            <span class="spacer"></span>
            <a class="btn btn-link" [routerLink]="changeLink(id())">Cancel</a>
            <button type="button" class="btn btn-primary" [disabled]="saving()" (click)="create()">
              @if (saving()) {
                <dso-spinner />
              }
              Create the secure coding ticket in CyberTrack
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .block {
      margin-bottom: 12px;
      padding: 10px 14px;
    }

    .problems-banner {
      margin: 0 0 12px;
    }

    .refused {
      flex-wrap: wrap;
    }
  `,
})
export class ChangeSecureCoding implements HasUnsavedChanges {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly myDepartment = inject(MyDepartment);
  private readonly published = inject(PublishedChange);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = CHANGES;
  protected readonly changeLink = beadleChange;
  protected readonly errorMessage = errorMessage;

  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly refusal = computed(() =>
    this.change.hasValue()
      ? refusalOf(this.change.value(), this.myDepartment.departmentId())
      : null,
  );
  protected readonly form = signal<SecureCodingForm | null>(null);
  protected readonly saving = signal(false);
  protected readonly failure = signal<{ message: string; problems: string[] } | null>(null);

  constructor() {
    effect(() => {
      if (this.change.hasValue()) {
        this.form.set(secureCodingForm(this.change.value()));
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return !!this.form()?.dirty;
  }

  protected create(): void {
    const form = this.form()!;
    const change = this.change.value()!;
    this.failure.set(null);
    form.markAllAsTouched();
    if (form.invalid) {
      this.failure.set({ message: 'Some fields need your attention.', problems: [] });
      return;
    }
    this.saving.set(true);
    this.api
      .createSecureCodingTicket(
        this.id(),
        secureCodingRequest(form, change, this.myDepartment.departmentId()!),
      )
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          form.markAsPristine();
          this.published.hand(saved);
          this.notifier.success(
            `${saved.template.secureCodingTicket} is created in CyberTrack and sent to ProTech as the secure coding ticket of ${saved.number}.`,
          );
          this.router.navigate(beadleChange(this.id()));
        },
        error: (error) => {
          const problems = fieldProblems(error);
          applyFieldProblems(form, problems);
          this.failure.set({
            message: `The secure coding ticket could not be created: ${errorMessage(error)}`,
            problems: problems.map(problemText),
          });
        },
      });
  }
}
