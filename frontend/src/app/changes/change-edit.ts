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
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { errorMessage, fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { CHANGES, beadleChange } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { applyFieldProblems, filled, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { FORM_FIELD } from '../ui/form-field';
import { DsoLoading, DsoSpinner } from '../ui/loading';
import { ChangesApi, ProductionChange, isOpen } from './change-api';
import { activeTasks, editHint, fits } from './change-model';
import { problemText } from './change-problems';
import { onHours, scheduleForm, scheduleValue } from './change-schedule-model';
import { changeFacts } from './change-sections';
import { ChangeTasksForm } from './change-tasks-form';
import { setTaskWindow, taskWindow, tasksForm, toTaskRequests } from './change-tasks-model';
import { ChangeTemplateForm } from './change-template-form';
import { templateForm, toTemplate } from './change-template-model';
import { PublishedChange } from './published-change';

export const STALE =
  'The change was changed in ProTech or by someone else meanwhile. Reload it and apply your change again.';

interface Failure {
  message: string;
  problems: string[];
  reload: boolean;
}

export function editForm(change: ProductionChange) {
  const template = templateForm(change.template);
  template.controls.type.disable();
  const schedule = scheduleForm(template.controls.downtime, change.schedule);
  const tasks = tasksForm(activeTasks(change.tasks), taskWindow(change.number, change.schedule));
  schedule.valueChanges.subscribe(() =>
    setTaskWindow(tasks, taskWindow(change.number, scheduleValue(schedule, false))),
  );
  return new FormGroup({
    shortDescription: text(change.shortDescription, filled, fits(160)),
    description: text(change.description, filled, fits(4000)),
    schedule,
    template,
    tasks,
  });
}

export type EditForm = ReturnType<typeof editForm>;

@Component({
  selector: 'dso-change-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DsoLoading,
    DsoSpinner,
    FORM_FIELD,
    ChangeTasksForm,
    ChangeTemplateForm,
  ],
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
        <span>Edit</span>
      </nav>
      @if (change.isLoading()) {
        <dso-loading />
      }
      @if (change.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (change.hasValue()) {
        @let c = change.value();
        <header class="page-header">
          <div>
            <h1>Edit {{ c.number }}</h1>
            <p>
              What you publish goes to ProTech at once. The change page then shows whether ProTech
              applied it.
            </p>
          </div>
        </header>
        @if (!open()) {
          <div class="banner refused" role="alert">
            <span>{{ c.number }} is closed in ProTech and can no longer be changed</span>
            <span class="spacer"></span>
            <a class="btn btn-link" [routerLink]="changeLink(id())">Back to the change</a>
          </div>
        } @else if (hint(); as message) {
          <div class="banner refused" role="alert">
            <span>{{ message }}</span>
            <span class="spacer"></span>
            <a class="btn btn-link" [routerLink]="changeLink(id())">Back to the change</a>
          </div>
        } @else if (form(); as f) {
          <form [formGroup]="f" (ngSubmit)="publish()" novalidate>
            <section class="card block texts">
              <h2>Texts</h2>
              <dso-form-field>
                <dso-label>Short description</dso-label>
                <input dsoInput formControlName="shortDescription" maxlength="160" />
                <dso-hint class="count"
                  >{{ f.controls.shortDescription.value.length }} / 160</dso-hint
                >
                <dso-error>{{ errorText(f.controls.shortDescription) }}</dso-error>
              </dso-form-field>
              <dso-form-field>
                <dso-label>Description</dso-label>
                <textarea dsoInput rows="9" formControlName="description"></textarea>
                <dso-hint class="count"
                  >{{ f.controls.description.value.length }} / 4000</dso-hint
                >
                <dso-error>{{ errorText(f.controls.description) }}</dso-error>
              </dso-form-field>
            </section>
            <section class="fields" aria-labelledby="protech-fields">
              <h2 id="protech-fields">ProTech fields</h2>
              <dso-change-template-form
                [form]="f.controls.template"
                [facts]="facts()"
                [schedule]="f.controls.schedule"
              />
            </section>
            <section class="card block">
              <h2>Change tasks</h2>
              <p class="note">
                A new change task is created in ProTech and a removed one is canceled there.
              </p>
              <dso-change-tasks-form [tasks]="f.controls.tasks" />
            </section>
            @if (failure()?.problems?.length) {
              <div class="banner danger problems-banner" role="alert">
                <ul class="problems">
                  @for (problem of failure()!.problems; track problem) {
                    <li>{{ problem }}</li>
                  }
                </ul>
              </div>
            }
            <div class="save-bar">
              @if (failure(); as failed) {
                <span class="save-error" role="alert">{{ failed.message }}</span>
                @if (failed.reload) {
                  <button type="button" class="btn btn-link" (click)="reload()">Reload</button>
                }
              } @else if (f.dirty) {
                <span class="muted">Unsaved changes</span>
              }
              <span class="spacer"></span>
              <a class="btn btn-link" [routerLink]="changeLink(id())">Cancel</a>
              <button type="submit" class="btn btn-primary" [disabled]="saving()">
                @if (saving()) {
                  <dso-spinner />
                }
                Publish to ProTech
              </button>
            </div>
          </form>
        }
      }
    </div>
  `,
  styles: `
    .block {
      margin-bottom: 12px;
      padding: 10px 14px;

      h2 {
        margin: 0 0 6px;
        font-size: 15px;
      }
    }

    .texts {
      display: flex;
      flex-direction: column;
      gap: 8px;

      textarea {
        font-family: var(--dso-mono);
        font-size: 12px;
      }

      .count {
        display: block;
        text-align: end;
      }
    }

    .fields {
      margin-bottom: 12px;

      h2 {
        margin: 0 0 6px;
        font-size: 15px;
      }
    }

    .problems-banner {
      margin: 12px 0 0;
    }

    .refused {
      flex-wrap: wrap;
    }
  `,
})
export class ChangeEdit implements HasUnsavedChanges {
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
  protected readonly errorText = errorText;

  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly open = computed(() => this.change.hasValue() && isOpen(this.change.value()));
  protected readonly hint = computed(() =>
    this.change.hasValue() ? editHint(this.change.value(), this.myDepartment.departmentId()) : null,
  );
  protected readonly facts = computed(() =>
    this.change.hasValue() ? changeFacts(this.change.value()) : [],
  );
  protected readonly form = signal<EditForm | null>(null);
  protected readonly saving = signal(false);
  protected readonly failure = signal<Failure | null>(null);

  constructor() {
    effect(() => {
      if (this.change.hasValue()) {
        this.form.set(editForm(this.change.value()));
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return !!this.form()?.dirty;
  }

  protected reload(): void {
    this.failure.set(null);
    this.change.reload();
  }

  protected publish(): void {
    const form = this.form()!;
    const change = this.change.value()!;
    this.failure.set(null);
    form.markAllAsTouched();
    if (form.invalid) {
      this.failure.set({
        message: 'Some fields need your attention.',
        problems: [],
        reload: false,
      });
      return;
    }
    const value = form.getRawValue();
    this.saving.set(true);
    this.api
      .update(this.id(), {
        version: change.version!,
        departmentId: this.myDepartment.departmentId()!,
        shortDescription: value.shortDescription.trim(),
        description: value.description,
        schedule: scheduleValue(form.controls.schedule, value.template.downtime),
        template: toTemplate(form.controls.template),
        tasks: toTaskRequests(form.controls.tasks),
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          form.markAsPristine();
          this.published.hand(saved);
          this.notifier.success(`Your update of ${saved.number} is published to ProTech`);
          this.router.navigate(beadleChange(this.id()));
        },
        error: (error) => this.failed(form, error),
      });
  }

  private failed(form: EditForm, error: unknown): void {
    const problems = fieldProblems(error);
    applyFieldProblems(form, problems.map(onHours));
    const conflict = error instanceof HttpErrorResponse && error.status === 409;
    const message = errorMessage(error);
    this.failure.set({
      message: conflict && message.includes('changed by someone else') ? STALE : message,
      problems: problems.map(problemText),
      reload: conflict,
    });
  }
}
