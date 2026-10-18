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
import { RouterLink } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  finalize,
  of,
  switchMap,
} from 'rxjs';
import { MyBusinessService } from '../business-services/my-business-service';
import { BusinessServicesApi, ProductsApi, UserApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { CHANGES, NEW_CHANGE, changePath, productPath } from '../core/sections';
import { KeepsDraft, SessionEnd } from '../core/session-end';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { byBusinessService } from '../business-services/business-services';
import { applyFieldProblems, applyProblemsAt, filled } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import { BeadleCheckbox } from '../ui/checkbox';
import { FORM_FIELD } from '../ui/form-field';
import { BeadleLoading } from '../ui/loading';
import {
  ChangeRequest,
  ChangeTemplate,
  ChangesApi,
  JiraIssue,
  JiraVersion,
  ProductionChange,
  TaskRequest,
} from './change-api';
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
import { ATTENTION, problemText } from './change-problems';
import { ChangeScheduleFields } from './change-schedule-fields';
import {
  SCHEDULE_PREFIX,
  ScheduleForm,
  ScheduleInput,
  onHours,
  plannedDay,
  plannedInput,
  scheduleForm,
  scheduleValue,
} from './change-schedule-model';
import { SectionKey, WIZARD_SECTIONS, changeFacts, sectionControls } from './change-sections';
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
import { ChangeTexts } from './change-texts';
import {
  TemplateForm,
  applyTemplateProblems,
  templateForm,
  toTemplate,
  withDefaults,
} from './change-template-model';
import { IntegrationNote } from './integration-note';
import { SecureCodingFields } from './secure-coding-form';
import { SecureCodingForm, secureCodingForm, secureCodingRequest } from './secure-coding-model';

type StepKey = SectionKey | 'review' | 'tasks' | 'ticket' | 'created';

const STEP_KEYS: readonly StepKey[] = [
  ...WIZARD_SECTIONS.map((section) => section.key),
  'review',
  'tasks',
  'ticket',
  'created',
];

export const STEPS = [
  ...WIZARD_SECTIONS.map((section) => section.step),
  'Review',
  'Change tasks',
  'Secure coding',
  'Summary',
];

const REVIEW = STEP_KEYS.indexOf('review');
const TASKS = STEP_KEYS.indexOf('tasks');
const TICKET = STEP_KEYS.indexOf('ticket');
const CREATED = STEP_KEYS.indexOf('created');

const NEXT_LABELS: Partial<Record<StepKey, string>> = {
  review: 'Create change in ProTech',
  tasks: 'Create change tasks in ProTech',
  ticket: 'Create secure coding ticket in CyberTrack',
};

const LATER_LABELS: Partial<Record<StepKey, string>> = {
  tasks: 'Add change tasks later',
  ticket: 'Create ticket later',
};

/** The name the wizard keeps its draft under while the browser signs in again, see {@link SessionEnd}. */
export const WIZARD_DRAFT = 'new-change';

/**
 * What the wizard keeps in the tab when the session ends before the change is created, or before its change tasks and
 * its secure coding ticket are created: enough to open it again on the same step with the same entries.
 */
export interface WizardDraft {
  step: number;
  businessServiceId: number | null;
  productId: number | null;
  template: ChangeTemplate | null;
  schedule: ScheduleInput | null;
  fixVersion: string;
  searched: string | null;
  epicKeys: string[];
  storyKeys: string[];
  seenStories: string[];
  filledRelease: string | null;
  filledStart: string | null;
  /** The texts the user wrote over the generated ones; null keeps the generated text. */
  shortDescription: string | null;
  description: string | null;
  created: ProductionChange | null;
  tasks: TaskRequest[] | null;
  secureCoding: ReturnType<SecureCodingForm['getRawValue']> | null;
}

@Component({
  selector: 'beadle-change-wizard',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    BeadleCheckbox,
    BeadleLoading,
    FORM_FIELD,
    ChangeScheduleFields,
    ChangeSummary,
    ChangeTasksForm,
    ChangeTemplateSection,
    ChangeTexts,
    IntegrationNote,
    SecureCodingFields,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-wizard.html',
  styleUrl: './change-wizard.scss',
})
export class ChangeWizard implements HasUnsavedChanges, KeepsDraft {
  readonly draftName = WIZARD_DRAFT;

  private readonly changesApi = inject(ChangesApi);
  private readonly productsApi = inject(ProductsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = NEW_CHANGE;
  protected readonly changes = CHANGES;
  protected readonly laterLabels = LATER_LABELS;
  protected readonly errorText = errorText;
  protected readonly errorMessage = errorMessage;
  protected readonly windowText = windowText;
  protected readonly versionText = versionText;
  protected readonly approverNames = approverNames;
  protected readonly counted = counted;
  protected readonly templateLink = productPath;
  protected readonly changeLink = changePath;

  protected readonly step = signal(0);
  protected readonly stepKey = computed(() => STEP_KEYS[this.step()]);
  protected readonly current = computed(() =>
    WIZARD_SECTIONS.find((section) => section.key === this.stepKey()),
  );
  protected readonly checked = signal(false);

  private readonly myBusinessService = inject(MyBusinessService);
  protected readonly businessServiceId = new FormControl(
    this.myBusinessService.businessServiceId(),
  );
  protected readonly productId = new FormControl<number | null>(null);
  protected readonly chosenBusinessService = toSignal(this.businessServiceId.valueChanges, {
    initialValue: this.businessServiceId.value,
  });
  private readonly chosenProduct = toSignal(this.productId.valueChanges, { initialValue: null });

  protected readonly businessServicesError = signal<string | null>(null);
  protected readonly businessServices = toSignal(
    inject(BusinessServicesApi)
      .list()
      .pipe(
        catchError((error) => {
          this.businessServicesError.set(errorMessage(error));
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
  private readonly grouped = computed(() =>
    byBusinessService(this.businessServices(), this.catalogue()),
  );
  protected readonly groups = computed(() =>
    this.grouped().flatMap(({ businessService, products }) =>
      businessService && products.length ? [{ businessService, products }] : [],
    ),
  );
  protected readonly unplaced = computed(() =>
    (this.grouped().find((group) => !group.businessService)?.products ?? [])
      .map((product) => product.name)
      .join(', '),
  );
  protected readonly productsOfBusinessService = computed(() => {
    const id = this.chosenBusinessService();
    return this.groups().find((group) => group.businessService.id === id)?.products ?? [];
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
  protected readonly secureCoding = signal<SecureCodingForm | null>(null);

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
  /** Why the texts of the chosen epics and stories could not be written on the Jira step. */
  protected readonly textsProblem = signal<string | null>(null);
  private readonly reviews = new Subject<void>();
  private readonly jiraScope = computed(() =>
    this.stepKey() === 'jira' && this.epicKeys().length && this.stories.status() === 'resolved'
      ? [...this.epicKeys(), ...this.storyKeys()].join()
      : null,
  );
  protected readonly creating = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly problems = signal<string[]>([]);
  protected readonly created = signal<ProductionChange | null>(null);
  protected readonly steps = computed(() =>
    STEPS.slice(0, Math.max(this.step(), this.created() ? TICKET : REVIEW) + 1),
  );
  private filledRelease: string | null = null;
  private filledStart: string | null = null;
  /** The draft kept before the last sign-in, until the profile of its product is loaded to take it. */
  private resuming = inject(SessionEnd).takeDraft<WizardDraft>(WIZARD_DRAFT);

  protected readonly nextLabel = computed(
    () => NEXT_LABELS[this.stepKey()] ?? `Next: ${STEPS[this.step() + 1]}`,
  );

  constructor() {
    toObservable(this.jiraScope)
      .pipe(
        debounceTime(500),
        switchMap((scope) => {
          this.textsProblem.set(null);
          return scope === null
            ? EMPTY
            : this.changesApi.preview(this.request()).pipe(
                catchError((error) => {
                  this.textsProblem.set(errorMessage(error));
                  return EMPTY;
                }),
              );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((draft) => this.showTexts(draft));
    this.reviews
      .pipe(
        switchMap(() => this.previewed()),
        takeUntilDestroyed(),
      )
      .subscribe((draft) => this.showTexts(draft));
    this.businessServiceId.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => {
      this.myBusinessService.choose(id);
      this.productId.setValue(null);
    });
    this.productId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.forgetScope());
    effect(() => {
      const profile = this.profile.hasValue() ? this.profile.value() : null;
      const openedBy = this.openedBy();
      untracked(() => {
        const ready = profile !== null && openedBy !== undefined;
        const draft = ready ? this.takeResumed() : null;
        const details = ready
          ? templateForm(
              draft?.template ??
                withDefaults(profile.template, openedBy, this.productBusinessService()),
            )
          : null;
        this.details.set(details);
        this.schedule.set(details ? scheduleForm(details.controls.downtime) : null);
        this.filledStart = null;
        if (details && draft) {
          this.resume(draft, details);
        }
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
    if (this.resuming) {
      this.reopen(this.resuming);
    }
  }

  /** The entries of the wizard as they are, for {@link SessionEnd} to keep while the browser signs in again. */
  draft(): WizardDraft {
    const details = this.details();
    const tasks = this.tasks();
    return {
      step: this.step(),
      businessServiceId: this.businessServiceId.value,
      productId: this.productId.value,
      template: details ? toTemplate(details) : null,
      schedule: this.schedule()?.getRawValue() ?? null,
      fixVersion: this.fixVersion.value,
      searched: this.searched(),
      epicKeys: this.epicKeys(),
      storyKeys: this.storyKeys(),
      seenStories: [...this.seenStories],
      filledRelease: this.filledRelease,
      filledStart: this.filledStart,
      shortDescription: this.shortDescription.dirty ? this.shortDescription.value : null,
      description: this.description.dirty ? this.description.value : null,
      created: this.created(),
      tasks: tasks ? toTaskRequests(tasks) : null,
      secureCoding: this.secureCoding()?.getRawValue() ?? null,
    };
  }

  hasUnsavedChanges(): boolean {
    return (
      this.step() === TASKS ||
      this.step() === TICKET ||
      (!this.created() && (this.step() > 0 || this.searched() !== null || !!this.details()?.dirty))
    );
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.created() && !this.creating() && !this.previewing();
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
      this.create();
      return;
    }
    if (leaving === 'tasks') {
      this.createTasks();
      return;
    }
    if (leaving === 'ticket') {
      this.createTicket();
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
      case 'ticket':
        return this.secureCoding()!.invalid ? ATTENTION : null;
      case 'created':
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
    const typed = this.fixVersion.value.trim();
    const fixVersion = this.knownVersion(typed)?.name ?? typed;
    if (fixVersion !== this.fixVersion.value) {
      this.fixVersion.setValue(fixVersion);
    }
    if (fixVersion === this.searched()) {
      this.epics.reload();
      if (this.stories.error()) {
        this.stories.reload();
      }
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

  protected later(): void {
    this.checked.set(false);
    this.problem.set(null);
    this.problems.set([]);
    this.step.update((step) => step + 1);
  }

  protected restart(): void {
    this.created.set(null);
    this.tasks.set(null);
    this.secureCoding.set(null);
    this.productId.setValue(null);
    this.checked.set(false);
    this.step.set(0);
  }

  /** Chooses the business service, the product and the Jira scope of the draft; its profile then gives the rest. */
  private reopen(draft: WizardDraft): void {
    if (draft.businessServiceId !== null) {
      this.businessServiceId.setValue(draft.businessServiceId);
    }
    this.productId.setValue(draft.productId);
    this.fixVersion.setValue(draft.fixVersion);
    this.searched.set(draft.searched);
    this.epicKeys.set(draft.epicKeys);
    this.storyKeys.set(draft.storyKeys);
    this.seenStories = new Set(draft.seenStories);
    this.filledRelease = draft.filledRelease;
    this.keepText(this.shortDescription, draft.shortDescription);
    this.keepText(this.description, draft.description);
  }

  /** The draft once the profile of its product is loaded, unless another product was chosen meanwhile. */
  private takeResumed(): WizardDraft | null {
    const draft = this.resuming;
    this.resuming = null;
    return draft && draft.productId === this.chosenProduct() ? draft : null;
  }

  /** Puts the schedule, the change tasks and the ticket of the draft back and goes to the step it was on. */
  private resume(draft: WizardDraft, details: TemplateForm): void {
    details.markAsDirty();
    if (draft.schedule) {
      this.schedule()!.patchValue(draft.schedule);
    }
    this.filledStart = draft.filledStart;
    const created = draft.created;
    if (created) {
      const ticket = secureCodingForm(created);
      if (draft.secureCoding) {
        ticket.patchValue(draft.secureCoding);
      }
      this.created.set(created);
      this.tasks.set(tasksForm(draft.tasks ?? [], taskWindow(created.number, created.schedule)));
      this.secureCoding.set(ticket);
      this.step.set(Math.min(Math.max(draft.step, TASKS), TICKET));
      return;
    }
    this.step.set(Math.min(Math.max(draft.step, 0), REVIEW));
    if (this.stepKey() === 'review') {
      this.loadPreview();
    }
  }

  private keepText(control: FormControl<string>, text: string | null): void {
    if (text !== null) {
      control.setValue(text);
      control.markAsDirty();
    }
  }

  private productBusinessService(): string | null {
    const id = this.chosenProduct();
    return this.catalogue().find((product) => product.id === id)?.businessServiceName ?? null;
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
    } else if (key === 'ticket') {
      this.secureCoding()!.markAllAsTouched();
    } else if (key !== 'review' && key !== 'created' && this.details()) {
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

  private knownVersion(name: string | null): JiraVersion | undefined {
    const typed = name?.toLowerCase();
    return this.versions.hasValue()
      ? this.versions.value().find((version) => version.name.toLowerCase() === typed)
      : undefined;
  }

  private suggestSchedule(): void {
    const timing = this.details()!.controls.timing.getRawValue();
    const releaseDate = this.knownVersion(this.searched())?.releaseDate ?? null;
    const planned = plannedInput(plannedDay(releaseDate, timing.installationStart ?? ''), timing);
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

  private create(): void {
    this.creating.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .create({
        ...this.request(),
        shortDescription: this.shortDescription.value,
        description: this.description.value,
      })
      .pipe(
        finalize(() => this.creating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (change) => {
          this.created.set(change);
          this.tasks.set(this.draftTasks(change));
          this.secureCoding.set(secureCodingForm(change));
          this.checked.set(false);
          this.step.set(TASKS);
        },
        error: (error) => this.fail(error, 'The change could not be created: '),
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
    const change = this.created()!;
    this.creating.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .createTasks(change.id!, {
        version: change.version!,
        businessServiceId: this.businessServiceId.value ?? change.businessServiceId!,
        tasks: toTaskRequests(this.tasks()!),
      })
      .pipe(
        finalize(() => this.creating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (tasked) => {
          this.created.set(tasked);
          this.tasks()!.markAsPristine();
          this.checked.set(false);
          this.step.set(TICKET);
        },
        error: (error) => this.fail(error, 'The change tasks could not be created: '),
      });
  }

  private createTicket(): void {
    const change = this.created()!;
    this.creating.set(true);
    this.problem.set(null);
    this.problems.set([]);
    this.changesApi
      .createSecureCodingTicket(
        change.id!,
        secureCodingRequest(
          this.secureCoding()!,
          change,
          this.businessServiceId.value ?? change.businessServiceId!,
        ),
      )
      .pipe(
        finalize(() => this.creating.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (ticketed) => {
          this.created.set(ticketed);
          this.secureCoding()!.markAsPristine();
          this.checked.set(false);
          this.step.set(CREATED);
        },
        error: (error) => this.fail(error, 'The secure coding ticket could not be created: '),
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
    const ticket = this.secureCoding();
    const unmatched = applyTemplateProblems(this.details()!, problems);
    const rest = applyProblemsAt(
      this.schedule()!,
      SCHEDULE_PREFIX,
      (tasks ? applyTaskProblems(tasks, unmatched) : unmatched).map(onHours),
    );
    if (ticket && this.stepKey() === 'ticket') {
      applyFieldProblems(ticket, rest);
    }
    this.problem.set(lead + errorMessage(error));
    this.problems.set(problems.map(problemText));
  }
}
