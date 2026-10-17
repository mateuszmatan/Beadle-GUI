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
import { MyDepartment } from '../departments/my-department';
import { fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { beadleChange } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { applyFieldProblems, filled, text } from '../shared/form-controls';
import { Failure, NEEDS_ATTENTION, SaveBar, failureOf } from '../ui/save-bar';
import { ChangesApi, ProductionChange } from './change-api';
import { activeTasks, editRefusal, fits } from './change-model';
import { ChangePage } from './change-page';
import { onHours, scheduleForm, scheduleValue } from './change-schedule-model';
import { changeFacts } from './change-sections';
import { ChangeTasksForm } from './change-tasks-form';
import { setTaskWindow, taskWindow, tasksForm, toTaskRequests } from './change-tasks-model';
import { ChangeTemplateForm } from './change-template-form';
import { templateForm, toTemplate } from './change-template-model';
import { ChangeTexts } from './change-texts';
import { PublishedChange } from './published-change';

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
  selector: 'beadle-change-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ChangePage,
    ChangeTasksForm,
    ChangeTemplateForm,
    ChangeTexts,
    SaveBar,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <beadle-change-page [change]="change" crumb="Edit">
      @if (change.hasValue()) {
        @let c = change.value();
        <header class="page-header">
          <div>
            <h1>Edit {{ c.number }}</h1>
            <p>
              Change any value and publish it: the update goes to ProTech at once, and the change
              page then shows whether ProTech applied it. Fields marked * are required.
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
          <form [formGroup]="f" (ngSubmit)="publish()" novalidate>
            <section class="fields" aria-labelledby="protech-fields">
              <h2 id="protech-fields">ProTech fields</h2>
              <p class="section-help">
                The values ProTech holds for the change, in the sections of New Change.
              </p>
              <beadle-change-template-form
                [form]="f.controls.template"
                [facts]="facts()"
                [schedule]="f.controls.schedule"
              />
            </section>
            <section class="card block">
              <h2>Change tasks</h2>
              <p class="section-help">
                A change task (CTASK) is a piece of work inside the change, done by one team. When
                you publish, a task you add is created in ProTech and a task you remove is canceled
                there.
              </p>
              <beadle-change-tasks-form [tasks]="f.controls.tasks" />
            </section>
            <section class="card block">
              <h2>Text sent to ProTech</h2>
              <p class="section-help">
                The short description and description ProTech shows for the change. They are not
                rewritten when you change the fields above, so bring them in line yourself.
              </p>
              <beadle-change-texts
                [shortDescription]="f.controls.shortDescription"
                [description]="f.controls.description"
              />
            </section>
            <beadle-save-bar
              label="Publish the update to ProTech"
              [cancelLink]="changeLink(id())"
              [failure]="failure()"
              [saving]="saving()"
              [dirty]="f.dirty"
              (reload)="reload()"
            />
          </form>
        }
      }
    </beadle-change-page>
  `,
  styles: `
    .fields {
      margin-bottom: 12px;

      h2 {
        margin: 0 0 6px;
        font-size: 15px;
      }
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

  protected readonly changeLink = beadleChange;

  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly refusal = computed(() =>
    this.change.hasValue()
      ? editRefusal(this.change.value(), this.myDepartment.departmentId())
      : null,
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
      this.failure.set(NEEDS_ATTENTION);
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
          this.notifier.success(
            `Your update of ${saved.number} is published to ProTech. This page shows when ProTech has applied it.`,
          );
          this.router.navigate(beadleChange(this.id()));
        },
        error: (error) => {
          applyFieldProblems(form, fieldProblems(error).map(onHours));
          this.failure.set(failureOf(error, 'The update could not be published'));
        },
      });
  }
}
