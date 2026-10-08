import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { DepartmentsApi, ProductsApi, UserApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { NEW_CHANGE, beadleChange, beadleProduct } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { DepartmentGroup, byDepartment } from '../products/departments';
import { applyProblemsAt, filled, max } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import {
  ChangeRequest,
  ChangesApi,
  JiraIssue,
  ProductionChange,
  STATES,
  approvalOf,
  labelOf,
} from './change-api';
import {
  approverNames,
  changeRequest,
  fits,
  isoDate,
  matchingVersions,
  storiesFollowing,
  toggled,
  versionText,
  windowText,
} from './change-model';
import { problemText } from './change-problems';
import { ChangeScheduleFields } from './change-schedule-fields';
import {
  SCHEDULE_PREFIX,
  ScheduleForm,
  onHours,
  plannedInput,
  scheduleForm,
  scheduleValue,
} from './change-schedule-model';
import { Fact, NUMBER_PENDING, SECTIONS, SectionKey, sectionControls } from './change-sections';
import { ChangeSummary } from './change-summary';
import { ChangeTasksForm } from './change-tasks-form';
import { TasksForm, applyTaskProblems, tasksForm, toTaskTexts } from './change-tasks-model';
import { ChangeTemplateSection } from './change-template-section';
import {
  TemplateForm,
  applyTemplateProblems,
  templateForm,
  toTemplate,
  withDefaults,
} from './change-template-model';
import { IntegrationNote } from './integration-note';

type StepKey = SectionKey | 'review' | 'raised';

const STEP_KEYS: readonly StepKey[] = [
  ...SECTIONS.map((section) => section.key),
  'review',
  'raised',
];

export const STEPS = [...SECTIONS.map((section) => section.step), 'Review', 'Raised'];

const RAISED = STEP_KEYS.indexOf('raised');

const NO_DEPARTMENT = -1;

const ATTENTION = 'Some fields need your attention.';

const departmentKey = (group: DepartmentGroup) => group.department?.id ?? NO_DEPARTMENT;

@Component({
  selector: 'dso-change-wizard',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatAutocompleteModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    ChangeScheduleFields,
    ChangeSummary,
    ChangeTasksForm,
    ChangeTemplateSection,
    IntegrationNote,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-wizard.html',
  styleUrls: ['../shared/wizard.scss', './change-wizard.scss'],
})
export class ChangeWizard implements HasUnsavedChanges {
  private readonly changesApi = inject(ChangesApi);
  private readonly productsApi = inject(ProductsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = NEW_CHANGE;
  protected readonly steps = STEPS;
  protected readonly errorText = errorText;
  protected readonly errorMessage = errorMessage;
  protected readonly windowText = windowText;
  protected readonly versionText = versionText;
  protected readonly approverNames = approverNames;
  protected readonly counted = counted;
  protected readonly templateLink = beadleProduct;
  protected readonly changeLink = beadleChange;
  protected readonly departmentKey = departmentKey;

  protected readonly step = signal(0);
  protected readonly stepKey = computed(() => STEP_KEYS[this.step()]);
  protected readonly current = computed(() =>
    SECTIONS.find((section) => section.key === this.stepKey()),
  );
  protected readonly checked = signal(false);

  protected readonly departmentId = new FormControl(inject(MyDepartment).departmentId());
  protected readonly productId = new FormControl<number | null>(null);
  protected readonly chosenDepartment = toSignal(this.departmentId.valueChanges, {
    initialValue: this.departmentId.value,
  });
  private readonly chosenProduct = toSignal(this.productId.valueChanges, { initialValue: null });

  protected readonly departmentsError = signal<string | null>(null);
  protected readonly departments = toSignal(
    inject(DepartmentsApi)
      .list()
      .pipe(
        catchError((error) => {
          this.departmentsError.set(errorMessage(error));
          return of([]);
        }),
      ),
    { initialValue: [] },
  );
  protected readonly productsError = signal<string | null>(null);
  private readonly catalogue = toSignal(
    this.productsApi.list().pipe(
      catchError((error) => {
        this.productsError.set(errorMessage(error));
        return of([]);
      }),
    ),
    { initialValue: [] },
  );
  protected readonly groups = computed(() =>
    byDepartment(this.departments(), this.catalogue()).filter((group) => group.products.length),
  );
  protected readonly productsInDepartment = computed(() => {
    const id = this.chosenDepartment();
    return this.groups().find((group) => departmentKey(group) === id)?.products ?? [];
  });

  private readonly userApi = inject(UserApi);
  private readonly me = rxResource({ stream: () => this.userApi.me() });
  private readonly openedBy = computed(() => {
    if (this.me.hasValue()) {
      return this.me.value().name;
    }
    return this.me.error() ? null : undefined;
  });
  protected readonly facts = computed<Fact[]>(() => [
    { label: 'Change number', value: null, placeholder: NUMBER_PENDING },
    { label: 'Approval', value: approvalOf('DRAFT') },
    { label: 'Opened By', value: this.openedBy() ?? null },
    { label: 'State', value: labelOf(STATES, 'DRAFT') },
  ]);

  protected readonly profile = rxResource({
    params: () => this.chosenProduct() ?? undefined,
    stream: ({ params }) => this.changesApi.profile(params),
  });
  protected readonly details = signal<TemplateForm | null>(null);
  protected readonly schedule = signal<ScheduleForm | null>(null);
  protected readonly tasks = signal<TasksForm | null>(null);

  protected readonly fixVersion = new FormControl('', {
    nonNullable: true,
    validators: [filled, max(100)],
  });
  private readonly fixVersionText = toSignal(this.fixVersion.valueChanges, { initialValue: '' });
  protected readonly searched = signal<string | null>(null);
  private readonly versionTrigger = viewChild(MatAutocompleteTrigger);
  protected readonly versions = rxResource({
    params: () => (this.details() ? (this.chosenProduct() ?? undefined) : undefined),
    stream: ({ params }) => this.changesApi.versions(params),
  });
  protected readonly versionOptions = computed(() =>
    matchingVersions(this.versions.hasValue() ? this.versions.value() : [], this.fixVersionText()),
  );

  protected readonly epicKeys = signal<string[]>([]);
  protected readonly storyKeys = signal<string[]>([]);
  private seenStories = new Set<string>();

  protected readonly epics = rxResource({
    params: () => {
      const productId = this.chosenProduct();
      const fixVersion = this.searched();
      return productId && fixVersion ? { productId, fixVersion } : undefined;
    },
    stream: ({ params }) => this.changesApi.epics(params.productId, params.fixVersion),
  });
  protected readonly stories = rxResource({
    params: () => {
      const productId = this.chosenProduct();
      const fixVersion = this.searched();
      const epics = this.epicKeys();
      return productId && fixVersion && epics.length ? { productId, fixVersion, epics } : undefined;
    },
    stream: ({ params }) =>
      this.changesApi.stories(params.productId, params.fixVersion, params.epics),
  });
  protected readonly storyGroups = computed(() => {
    const stories = this.stories.hasValue() && this.epicKeys().length ? this.stories.value() : [];
    const epics = this.epics.hasValue() ? this.epics.value() : [];
    return this.epicKeys().map((key) => ({
      epic: epics.find((epic) => epic.key === key) ?? null,
      key,
      stories: stories.filter((story) => story.epicKey === key),
    }));
  });

  protected readonly shortDescription = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, fits(160)],
  });
  protected readonly description = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, fits(4000)],
  });
  protected readonly preview = signal<ProductionChange | null>(null);
  protected readonly previewing = signal(false);
  protected readonly raising = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly problems = signal<string[]>([]);
  protected readonly raised = signal<ProductionChange | null>(null);
  private filledRelease: string | null = null;
  private filledStart: string | null = null;

  protected readonly nextLabel = computed(() =>
    this.stepKey() === 'review' ? 'Raise the change in ProTech' : 'Continue',
  );

  constructor() {
    this.departmentId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.productId.setValue(null));
    this.productId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.forgetScope());
    effect(() => {
      const profile = this.profile.hasValue() ? this.profile.value() : null;
      const openedBy = this.openedBy();
      untracked(() => {
        const ready = profile !== null && openedBy !== undefined;
        const details = ready
          ? templateForm(withDefaults(profile.template, openedBy, this.productDepartment()))
          : null;
        this.details.set(details);
        this.schedule.set(details ? scheduleForm(details.controls.downtime) : null);
        this.tasks.set(ready ? tasksForm(profile.tasks) : null);
        this.filledStart = null;
      });
    });
    effect(() => {
      const loaded = this.epics.hasValue() ? this.epics.value() : null;
      untracked(() => {
        const gone = loaded
          ? this.epicKeys().filter((key) => !loaded.some((e) => e.key === key))
          : [];
        gone.forEach((key) => this.toggleEpic(key, false));
      });
    });
    effect(() => {
      const loaded = this.stories.hasValue() ? this.stories.value() : null;
      untracked(() => {
        if (loaded && this.epicKeys().length) {
          this.storyKeys.set(storiesFollowing(loaded, this.storyKeys(), this.seenStories));
          loaded.forEach((story) => this.seenStories.add(story.key));
        }
      });
    });
  }

  hasUnsavedChanges(): boolean {
    return (
      !this.raised() && (this.step() > 0 || this.searched() !== null || !!this.details()?.dirty)
    );
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.raised() && !this.raising() && !this.previewing();
  }

  protected goTo(index: number): void {
    if (this.canRevisit(index)) {
      this.checked.set(false);
      this.problem.set(null);
      this.problems.set([]);
      this.step.set(index);
    }
  }

  protected back(): void {
    this.goTo(this.step() - 1);
  }

  protected next(): void {
    this.checked.set(true);
    this.touchStep();
    if (this.stepProblem()) {
      return;
    }
    const leaving = this.stepKey();
    if (leaving === 'review') {
      this.raise();
      return;
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
    if (leaving === 'jira') {
      this.fillRelease();
    }
    if (this.stepKey() === 'schedule') {
      this.suggestSchedule();
    } else if (this.stepKey() === 'review') {
      this.loadPreview();
    }
  }

  protected stepProblem(): string | null {
    const key = this.stepKey();
    switch (key) {
      case 'request':
        return this.productProblem() ?? this.sectionProblem(key);
      case 'jira':
        return this.scopeProblem();
      case 'schedule':
        return this.schedule()!.invalid ? ATTENTION : null;
      case 'review':
        if (!this.preview() || this.shortDescription.invalid || this.description.invalid) {
          return 'Check the short description and the description';
        }
        return this.tasks()!.invalid ? 'Check the change tasks' : null;
      case 'raised':
        return null;
      default:
        return this.sectionProblem(key);
    }
  }

  protected findEpics(): void {
    this.versionTrigger()?.closePanel();
    this.fixVersion.markAsTouched();
    if (this.fixVersion.invalid) {
      return;
    }
    const fixVersion = this.fixVersion.value.trim();
    if (fixVersion === this.searched()) {
      this.epics.reload();
    } else {
      this.forgetIssues();
      this.searched.set(fixVersion);
    }
  }

  protected toggleEpic(key: string, on: boolean): void {
    const dropped = on ? [] : this.loadedStories().filter((story) => story.epicKey === key);
    this.epicKeys.update((keys) => toggled(keys, key, on));
    this.forget(dropped);
  }

  protected toggleStory(key: string, on: boolean): void {
    this.storyKeys.update((keys) => toggled(keys, key, on));
  }

  protected chooseAllEpics(on: boolean): void {
    if (on) {
      this.epicKeys.set(this.epics.hasValue() ? this.epics.value().map((epic) => epic.key) : []);
    } else {
      const dropped = this.loadedStories();
      this.epicKeys.set([]);
      this.forget(dropped);
    }
  }

  protected issueText(issue: JiraIssue): string {
    return [issue.status, `updated ${issue.updated}`].filter(Boolean).join(' · ');
  }

  protected useGeneratedText(control: FormControl<string>, generated: string): void {
    control.setValue(generated);
    control.markAsPristine();
  }

  protected restart(): void {
    this.raised.set(null);
    this.productId.setValue(null);
    this.checked.set(false);
    this.step.set(0);
  }

  private productDepartment(): string | null {
    const id = this.chosenProduct();
    return this.catalogue().find((product) => product.id === id)?.departmentName ?? null;
  }

  private productProblem(): string | null {
    if (!this.chosenProduct()) {
      return 'Choose the product';
    }
    if (!this.details()) {
      return this.profile.error()
        ? 'Choose a product that can be loaded'
        : 'Wait until the product is loaded';
    }
    return null;
  }

  private sectionProblem(key: SectionKey): string | null {
    return sectionControls(this.details()!, key).some((control) => control.invalid)
      ? ATTENTION
      : null;
  }

  private touchStep(): void {
    const key = this.stepKey();
    if (key === 'schedule') {
      this.schedule()!.markAllAsTouched();
    } else if (key === 'review') {
      this.tasks()?.markAllAsTouched();
    } else if (key !== 'raised' && this.details()) {
      sectionControls(this.details()!, key).forEach((control) => control.markAllAsTouched());
    }
  }

  private scopeProblem(): string | null {
    if (!this.fixVersion.value.trim() || this.fixVersion.invalid) {
      return 'Enter the FixVersion of the release';
    }
    if (this.fixVersion.value.trim() !== this.searched()) {
      return 'Find the epics of this FixVersion';
    }
    if (this.epics.isLoading() || this.stories.isLoading()) {
      return 'Wait until the epics and stories are loaded';
    }
    if (this.epics.error() || this.stories.error()) {
      return 'The epics and stories of this FixVersion could not be loaded';
    }
    return this.epicKeys().length ? null : 'Choose at least one epic';
  }

  private forgetIssues(): void {
    this.epicKeys.set([]);
    this.storyKeys.set([]);
    this.seenStories = new Set();
  }

  private forgetScope(): void {
    this.forgetIssues();
    this.searched.set(null);
    this.fixVersion.reset();
    this.filledRelease = null;
    this.shortDescription.reset();
    this.description.reset();
    this.preview.set(null);
  }

  private fillRelease(): void {
    const release = this.details()!.controls.release;
    const version = this.searched()!;
    if (!release.value.trim() || release.value === this.filledRelease) {
      release.setValue(version);
      this.filledRelease = version;
    }
  }

  private suggestSchedule(): void {
    const version = this.versions.hasValue()
      ? this.versions.value().find((candidate) => candidate.name === this.searched())
      : null;
    const date = version?.releaseDate ?? '';
    const day = date >= isoDate(new Date()) ? date : '';
    const planned = plannedInput(day, this.details()!.controls.timing.getRawValue());
    const schedule = this.schedule()!;
    const start = schedule.controls.installationStart.value;
    const kept =
      this.filledStart !== null &&
      (start === planned.installationStart || (!!start && start !== this.filledStart));
    if (!kept) {
      schedule.patchValue(planned);
      this.filledStart = planned.installationStart;
    }
  }

  private loadedStories(): JiraIssue[] {
    return this.stories.hasValue() ? this.stories.value() : [];
  }

  private forget(stories: JiraIssue[]): void {
    const keys = stories.map((story) => story.key);
    keys.forEach((key) => this.seenStories.delete(key));
    this.storyKeys.update((chosen) => chosen.filter((key) => !keys.includes(key)));
  }

  private request(): ChangeRequest {
    const details = this.details()!;
    return changeRequest(
      {
        productId: this.chosenProduct()!,
        fixVersion: this.searched()!,
        epicKeys: this.epicKeys(),
        storyKeys: this.storyKeys(),
      },
      scheduleValue(this.schedule()!, details.controls.downtime.value),
      toTemplate(details),
      toTaskTexts(this.tasks()!),
    );
  }

  protected loadPreview(): void {
    const request = this.request();
    this.previewing.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .preview(request)
      .pipe(
        finalize(() => this.previewing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preview) => {
          this.fillText(this.shortDescription, preview.shortDescription);
          this.fillText(this.description, preview.description);
          this.preview.set(preview);
        },
        error: (error) => {
          this.preview.set(null);
          this.fail(error, 'The change could not be previewed: ');
        },
      });
  }

  private raise(): void {
    this.raising.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .raise({
        ...this.request(),
        shortDescription: this.shortDescription.value,
        description: this.description.value,
      })
      .pipe(
        finalize(() => this.raising.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (change) => {
          this.raised.set(change);
          this.checked.set(false);
          this.step.set(RAISED);
        },
        error: (error) => this.fail(error),
      });
  }

  private fillText(control: FormControl<string>, generated: string): void {
    if (control.pristine) {
      control.setValue(generated);
    }
  }

  private fail(error: unknown, lead = ''): void {
    const problems = fieldProblems(error);
    const unmatched = applyTemplateProblems(this.details()!, problems);
    applyProblemsAt(
      this.schedule()!,
      SCHEDULE_PREFIX,
      applyTaskProblems(this.tasks()!, unmatched).map(onHours),
    );
    this.problem.set(lead + errorMessage(error));
    this.problems.set(problems.map(problemText));
  }
}
