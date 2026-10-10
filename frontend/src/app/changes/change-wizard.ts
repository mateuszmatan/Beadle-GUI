import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  linkedSignal,
  signal,
  untracked,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, Subject, catchError, finalize, of, switchMap } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { DepartmentsApi, ProductsApi, UserApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { CHANGES, NEW_CHANGE, beadleChange, beadleProduct } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { byDepartment } from '../products/departments';
import { applyProblemsAt, byteLength, filled } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import { DsoCheckbox } from '../ui/checkbox';
import { FORM_FIELD } from '../ui/form-field';
import { DsoLoading } from '../ui/loading';
import { ChangeRequest, ChangesApi, JiraIssue, ProductionChange } from './change-api';
import {
  approverNames,
  changeRequest,
  fits,
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
  plannedDay,
  plannedInput,
  scheduleForm,
  scheduleValue,
} from './change-schedule-model';
import { SECTIONS, SectionKey, changeFacts, sectionControls } from './change-sections';
import { ChangeSummary } from './change-summary';
import { ChangeTasksForm } from './change-tasks-form';
import {
  TasksForm,
  applyTaskProblems,
  taskWindow,
  tasksForm,
  toTaskRequests,
} from './change-tasks-model';
import { ChangeTemplateSection } from './change-template-section';
import {
  TemplateForm,
  applyTemplateProblems,
  templateForm,
  toTemplate,
  withDefaults,
} from './change-template-model';
import { IntegrationNote } from './integration-note';

type StepKey = SectionKey | 'review' | 'tasks' | 'raised';

const STEP_KEYS: readonly StepKey[] = [
  ...SECTIONS.map((section) => section.key),
  'review',
  'tasks',
  'raised',
];

export const STEPS = [
  ...SECTIONS.map((section) => section.step),
  'Review',
  'Change tasks',
  'Raised',
];

const TASKS = STEP_KEYS.indexOf('tasks');
const RAISED = STEP_KEYS.indexOf('raised');

const NEXT_LABELS: Partial<Record<StepKey, string>> = {
  review: 'Raise the change in ProTech',
  tasks: 'Create the change tasks in ProTech',
};

const ATTENTION = 'Some fields need your attention.';

@Component({
  selector: 'dso-change-wizard',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    RouterLink,
    DsoCheckbox,
    DsoLoading,
    FORM_FIELD,
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
  protected readonly changes = CHANGES;
  protected readonly steps = STEPS;
  protected readonly errorText = errorText;
  protected readonly errorMessage = errorMessage;
  protected readonly windowText = windowText;
  protected readonly versionText = versionText;
  protected readonly approverNames = approverNames;
  protected readonly counted = counted;
  protected readonly byteLength = byteLength;
  protected readonly templateLink = beadleProduct;
  protected readonly changeLink = beadleChange;

  protected readonly step = signal(0);
  protected readonly stepKey = computed(() => STEP_KEYS[this.step()]);
  protected readonly current = computed(() =>
    SECTIONS.find((section) => section.key === this.stepKey()),
  );
  protected readonly checked = signal(false);

  private readonly myDepartment = inject(MyDepartment);
  protected readonly departmentId = new FormControl(this.myDepartment.departmentId());
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
  private readonly grouped = computed(() => byDepartment(this.departments(), this.catalogue()));
  protected readonly groups = computed(() =>
    this.grouped().flatMap(({ department, products }) =>
      department && products.length ? [{ department, products }] : [],
    ),
  );
  protected readonly unplaced = computed(() =>
    (this.grouped().find((group) => !group.department)?.products ?? [])
      .map((product) => product.name)
      .join(', '),
  );
  protected readonly productsInDepartment = computed(() => {
    const id = this.chosenDepartment();
    return this.groups().find((group) => group.department.id === id)?.products ?? [];
  });

  private readonly userApi = inject(UserApi);
  private readonly me = rxResource({ stream: () => this.userApi.me() });
  private readonly openedBy = computed(() => {
    if (this.me.hasValue()) {
      return this.me.value().name;
    }
    return this.me.error() ? null : undefined;
  });
  protected readonly facts = computed(() =>
    changeFacts({ number: null, state: 'DRAFT', openedBy: this.openedBy() ?? null }),
  );

  protected readonly profile = rxResource({
    params: () => this.chosenProduct() ?? undefined,
    stream: ({ params }) => this.changesApi.profile(params),
  });
  protected readonly details = signal<TemplateForm | null>(null);
  protected readonly schedule = signal<ScheduleForm | null>(null);
  protected readonly tasks = signal<TasksForm | null>(null);

  protected readonly fixVersion = new FormControl('', {
    nonNullable: true,
    validators: [filled, fits(100)],
  });
  private readonly fixVersionText = toSignal(this.fixVersion.valueChanges, { initialValue: '' });
  protected readonly searched = signal<string | null>(null);
  protected readonly versionsOpen = signal(false);
  protected readonly versions = rxResource({
    params: () => (this.details() ? (this.chosenProduct() ?? undefined) : undefined),
    stream: ({ params }) => this.changesApi.versions(params),
  });
  protected readonly versionOptions = computed(() =>
    matchingVersions(this.versions.hasValue() ? this.versions.value() : [], this.fixVersionText()),
  );
  protected readonly versionsShown = computed(
    () => this.versionsOpen() && this.versionOptions().length > 0,
  );
  protected readonly activeVersion = linkedSignal({
    source: () => this.versionsShown() && this.versionOptions(),
    computation: (): number | null => null,
  });

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
  private readonly reviews = new Subject<void>();
  private readonly jiraScope = computed(() =>
    this.stepKey() === 'jira' && this.epicKeys().length && this.stories.status() === 'resolved'
      ? [...this.epicKeys(), ...this.storyKeys()].join()
      : null,
  );
  protected readonly raising = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly problems = signal<string[]>([]);
  protected readonly raised = signal<ProductionChange | null>(null);
  private filledRelease: string | null = null;
  private filledStart: string | null = null;

  protected readonly nextLabel = computed(
    () => NEXT_LABELS[this.stepKey()] ?? `Next: ${STEPS[this.step() + 1]}`,
  );

  constructor() {
    toObservable(this.jiraScope)
      .pipe(
        switchMap((scope) =>
          scope === null
            ? EMPTY
            : this.changesApi.preview(this.request()).pipe(catchError(() => EMPTY)),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((draft) => this.showTexts(draft));
    this.reviews
      .pipe(
        switchMap(() => this.previewed()),
        takeUntilDestroyed(),
      )
      .subscribe((draft) => this.showTexts(draft));
    this.departmentId.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => {
      this.myDepartment.choose(id);
      this.productId.setValue(null);
    });
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
      this.stepKey() === 'tasks' ||
      (!this.raised() && (this.step() > 0 || this.searched() !== null || !!this.details()?.dirty))
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
    if (leaving === 'tasks') {
      this.createTasks();
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
        if (this.previewing()) {
          return 'Wait until the change is previewed';
        }
        return !this.preview() || this.shortDescription.invalid || this.description.invalid
          ? 'Check the short description and the description'
          : null;
      case 'tasks':
        if (!this.tasks()!.length) {
          return 'Add at least one change task, or choose Add change tasks later';
        }
        return this.tasks()!.invalid ? 'Check the change tasks' : null;
      case 'raised':
        return null;
      default:
        return this.sectionProblem(key);
    }
  }

  protected findEpics(): void {
    this.versionsOpen.set(false);
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

  protected versionKey(event: KeyboardEvent): void {
    const options = this.versionOptions();
    const active = this.activeVersion();
    switch (event.key) {
      case 'Enter':
        event.preventDefault();
        if (this.versionsShown() && active !== null) {
          this.pickVersion(options[active].name);
        } else {
          this.findEpics();
        }
        break;
      case 'Escape':
        this.versionsOpen.set(false);
        break;
      case 'ArrowDown':
      case 'ArrowUp':
        event.preventDefault();
        if (!this.versionsShown()) {
          this.versionsOpen.set(true);
        } else if (event.key === 'ArrowDown') {
          this.activeVersion.set(((active ?? -1) + 1) % options.length);
        } else {
          this.activeVersion.set(((active ?? 0) + options.length - 1) % options.length);
        }
        break;
    }
  }

  protected pickVersion(name: string): void {
    this.fixVersion.setValue(name);
    this.findEpics();
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

  protected later(): void {
    this.checked.set(false);
    this.problem.set(null);
    this.problems.set([]);
    this.step.set(RAISED);
  }

  protected restart(): void {
    this.raised.set(null);
    this.tasks.set(null);
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
    } else if (key === 'tasks') {
      this.tasks()!.markAllAsTouched();
    } else if (key !== 'review' && key !== 'raised' && this.details()) {
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
    const planned = plannedInput(
      plannedDay(version?.releaseDate ?? null),
      this.details()!.controls.timing.getRawValue(),
    );
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
    );
  }

  protected loadPreview(): void {
    this.reviews.next();
  }

  private previewed(): Observable<ProductionChange> {
    this.previewing.set(true);
    this.problem.set(null);
    this.problems.set([]);
    return this.changesApi.preview(this.request()).pipe(
      catchError((error) => {
        this.preview.set(null);
        this.fail(error, 'The change could not be previewed: ');
        return EMPTY;
      }),
      finalize(() => this.previewing.set(false)),
    );
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
          this.tasks.set(this.draftTasks(change));
          this.checked.set(false);
          this.step.set(TASKS);
        },
        error: (error) => this.fail(error, 'The change could not be raised: '),
      });
  }

  private draftTasks(change: ProductionChange): TasksForm {
    const affected = change.template.configurationItem;
    const defaults = this.profile.hasValue() ? this.profile.value().tasks : [];
    return tasksForm(
      defaults.map((details) => ({
        details: { ...details, configurationItem: details.configurationItem ?? affected },
      })),
      taskWindow(change.number, change.schedule),
    );
  }

  private createTasks(): void {
    const change = this.raised()!;
    this.raising.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .createTasks(change.id!, {
        version: change.version!,
        departmentId: this.departmentId.value ?? change.departmentId!,
        tasks: toTaskRequests(this.tasks()!),
      })
      .pipe(
        finalize(() => this.raising.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (tasked) => {
          this.raised.set(tasked);
          this.tasks()!.markAsPristine();
          this.checked.set(false);
          this.step.set(RAISED);
        },
        error: (error) => this.fail(error, 'The change tasks could not be created: '),
      });
  }

  private showTexts(draft: ProductionChange): void {
    this.fillText(this.shortDescription, draft.shortDescription);
    this.fillText(this.description, draft.description);
    this.preview.set(draft);
  }

  private fillText(control: FormControl<string>, generated: string): void {
    if (control.pristine) {
      control.setValue(generated);
    }
  }

  private fail(error: unknown, lead = ''): void {
    const problems = fieldProblems(error);
    const tasks = this.tasks();
    const unmatched = applyTemplateProblems(this.details()!, problems);
    applyProblemsAt(
      this.schedule()!,
      SCHEDULE_PREFIX,
      (tasks ? applyTaskProblems(tasks, unmatched) : unmatched).map(onHours),
    );
    this.problem.set(lead + errorMessage(error));
    this.problems.set(problems.map(problemText));
  }
}
