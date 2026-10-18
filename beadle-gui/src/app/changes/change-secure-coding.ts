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
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MyBusinessService } from '../business-services/my-business-service';
import { fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { changePath } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { applyFieldProblems } from '../shared/form-controls';
import { Failure, NEEDS_ATTENTION, SaveBar, failureOf } from '../ui/save-bar';
import { ChangesApi, ProductionChange, isOpen } from './change-api';
import { editRefusal } from './change-model';
import { ChangePage } from './change-page';
import { PublishedChange } from './published-change';
import { SecureCodingFields } from './secure-coding-form';
import { SecureCodingForm, secureCodingForm, secureCodingRequest } from './secure-coding-model';

export function refusalOf(
  change: ProductionChange,
  businessServiceId: number | null,
): string | null {
  const ticket = change.template.secureCodingTicket;
  return isOpen(change) && ticket
    ? `${change.number} already has the secure coding ticket ${ticket}`
    : editRefusal(change, businessServiceId);
}

@Component({
  selector: 'beadle-change-secure-coding',
  imports: [ReactiveFormsModule, RouterLink, ChangePage, SaveBar, SecureCodingFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <beadle-change-page [change]="change" crumb="Secure coding ticket">
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
          <form [formGroup]="f" (ngSubmit)="create()" novalidate>
            <section class="card block">
              <beadle-secure-coding-form [form]="f" [applicationName]="c.productName" />
            </section>
            <beadle-save-bar
              label="Create the secure coding ticket in CyberTrack"
              [cancelLink]="changeLink(id())"
              [failure]="failure()"
              [saving]="saving()"
              [dirty]="f.dirty"
            />
          </form>
        }
      }
    </beadle-change-page>
  `,
})
export class ChangeSecureCoding implements HasUnsavedChanges {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly myBusinessService = inject(MyBusinessService);
  private readonly published = inject(PublishedChange);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly changeLink = changePath;

  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly refusal = computed(() =>
    this.change.hasValue()
      ? refusalOf(this.change.value(), this.myBusinessService.businessServiceId())
      : null,
  );
  protected readonly form = signal<SecureCodingForm | null>(null);
  protected readonly saving = signal(false);
  protected readonly failure = signal<Failure | null>(null);

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
      this.failure.set(NEEDS_ATTENTION);
      return;
    }
    this.saving.set(true);
    this.api
      .createSecureCodingTicket(
        this.id(),
        secureCodingRequest(form, change, this.myBusinessService.businessServiceId()!),
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
          this.router.navigate(changePath(this.id()));
        },
        error: (error) => {
          applyFieldProblems(form, fieldProblems(error));
          this.failure.set(failureOf(error, 'The secure coding ticket could not be created'));
        },
      });
  }
}
