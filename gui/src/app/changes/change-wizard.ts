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
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { FieldProblem } from '../core/models';
import { CHANGES, beadleProduct } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { NOT_IN_A_DEPARTMENT, byDepartment } from '../products/departments';
import { filled, fitsColumn, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import { ChangeRequest, ChangesApi, JiraIssue, ProductionChange } from './change-api';
import {
  MOMENTS,
  Moments,
  approverNames,
  changeRequest,
  eachMoment,
  isoDate,
  matchingVersions,
  momentInputs,
  plannedSchedule,
  readMoments,
  scheduleProblem,
  storiesFollowing,
  toggled,
  versionText,
  windowText,
} from './change-model';
import { ChangeSummary } from './change-summary';
import { ChangeTemplateForm, templateLabel } from './change-template-form';
import {
  JIRA_KEY,
  JIRA_KEY_ERROR,
  TEMPLATE_PREFIX,
  TemplateForm,
  applyTemplateProblems,
  templateForm,
  toTemplate,
} from './change-template-model';
import { IntegrationNote } from './integration-note';

export const STEPS = ['Product', 'Jira scope', 'Details', 'Schedule', 'Review', 'Raised'];

const SCOPE = 1;
const DETAILS = 2;
const SCHEDULE = 3;
const REVIEW = 4;
const RAISED = 5;

const REQUEST_LABELS: Record<string, string> = {
  productId: 'Product',
  serviceIds: 'Services',
  fixVersion: 'FixVersion',
  epicKeys: 'Epics',
  storyKeys: 'Stories',
  shortDescription: 'Short description',
  description: 'Description',
};

export function requestLabel(field: string): string | null {
  if (field.startsWith(TEMPLATE_PREFIX)) {
    return templateLabel(field.slice(TEMPLATE_PREFIX.length));
  }
  const moment = MOMENTS.find(({ key }) => field === `schedule.${key}`);
  return moment?.label ?? REQUEST_LABELS[field] ?? null;
}

function problemText(problem: FieldProblem): string {
  const label = requestLabel(problem.field);
  return label ? `${label}: ${problem.message}` : problem.message;
}

const momentForm = () => new FormGroup({ date: text(''), time: text('') });

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
    ChangeSummary,
    ChangeTemplateForm,
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

  protected readonly section = CHANGES;
  protected readonly steps = STEPS;
  protected readonly moments = MOMENTS;
  protected readonly errorText = errorText;
  protected readonly errorMessage = errorMessage;
  protected readonly windowText = windowText;
  protected readonly versionText = versionText;
  protected readonly approverNames = approverNames;
  protected readonly counted = counted;
  protected readonly defaultsLink = beadleProduct;
  protected readonly jiraKeyError = JIRA_KEY_ERROR;
  protected readonly timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  protected readonly step = signal(0);
  protected readonly checked = signal(false);

  protected readonly departmentId = new FormControl<number | null>(null);
  protected readonly productId = new FormControl<number | null>(null);
  private readonly chosenDepartment = toSignal(this.departmentId.valueChanges, {
    initialValue: null,
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
  private readonly catalogue = toSignal(this.productsApi.list().pipe(catchError(() => of([]))), {
    initialValue: [],
  });
  protected readonly groups = computed(() =>
    byDepartment(this.departments(), this.catalogue()).filter((group) => group.products.length),
  );
  protected readonly productsInDepartment = computed(() => {
    const id = this.chosenDepartment();
    return (
      this.groups().find((group) =>
        id === null ? group.name === NOT_IN_A_DEPARTMENT : group.department?.id === id,
      )?.products ?? []
    );
  });

  protected readonly product = rxResource({
    params: () => this.chosenProduct() ?? undefined,
    stream: ({ params }) => this.productsApi.get(params),
  });
  protected readonly profile = rxResource({
    params: () => this.chosenProduct() ?? undefined,
    stream: ({ params }) => this.changesApi.profile(params),
  });
  protected readonly details = signal<TemplateForm | null>(null);
  protected readonly serviceIds = signal<number[]>([]);

  private readonly projectKey = signal('');
  private readonly project = computed(() => {
    const key = this.projectKey().trim().toUpperCase();
    const stored = this.profile.hasValue() ? this.profile.value().template.jiraProjectKey : key;
    return key !== stored && JIRA_KEY.test(key) ? key : undefined;
  });

  protected readonly fixVersion = new FormControl('', {
    nonNullable: true,
    validators: [filled, max(100)],
  });
  private readonly fixVersionText = toSignal(this.fixVersion.valueChanges, { initialValue: '' });
  protected readonly searched = signal<string | null>(null);
  private readonly versionTrigger = viewChild(MatAutocompleteTrigger);
  protected readonly versions = rxResource({
    params: () => {
      const productId = this.details() ? this.chosenProduct() : null;
      return productId ? { productId, project: this.project() } : undefined;
    },
    stream: ({ params }) => this.changesApi.versions(params.productId, params.project),
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
      return productId && fixVersion
        ? { productId, fixVersion, project: this.project() }
        : undefined;
    },
    stream: ({ params }) =>
      this.changesApi.epics(params.productId, params.fixVersion, params.project),
  });
  protected readonly stories = rxResource({
    params: () => {
      const productId = this.chosenProduct();
      const fixVersion = this.searched();
      const epics = this.epicKeys();
      return productId && fixVersion && epics.length
        ? { productId, fixVersion, epics, project: this.project() }
        : undefined;
    },
    stream: ({ params }) =>
      this.changesApi.stories(params.productId, params.fixVersion, params.epics, params.project),
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

  protected readonly installationDate = new FormControl('', { nonNullable: true });
  protected readonly schedule = new FormGroup(eachMoment(momentForm));
  private readonly scheduleValue = toSignal(this.schedule.valueChanges, {
    initialValue: this.schedule.getRawValue(),
  });
  protected readonly planned = computed(() => readMoments(this.scheduleValue()));

  protected readonly shortDescription = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, fitsColumn((value) => [value], '', 160)],
  });
  protected readonly description = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, fitsColumn((value) => [value], '', 4000)],
  });
  protected readonly preview = signal<ProductionChange | null>(null);
  protected readonly previewing = signal(false);
  protected readonly raising = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly problems = signal<string[]>([]);
  protected readonly raised = signal<ProductionChange | null>(null);
  private previewed = '';
  private filledRelease: string | null = null;

  protected readonly nextLabel = computed(() =>
    this.step() === REVIEW ? 'Raise the change in ServiceNow' : 'Continue',
  );

  constructor() {
    this.departmentId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.productId.setValue(null));
    this.productId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.forgetScope());
    this.installationDate.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.fillSchedule());
    effect(() => {
      const product = this.product.hasValue() ? this.product.value() : null;
      this.serviceIds.set(product?.services.map((service) => service.id) ?? []);
    });
    effect(() => {
      const profile = this.profile.hasValue() ? this.profile.value() : null;
      untracked(() => this.details.set(profile ? templateForm(profile.template) : null));
    });
    effect((onCleanup) => {
      const form = this.details();
      if (!form) {
        return;
      }
      const key = form.controls.jiraProjectKey;
      untracked(() => this.projectKey.set(key.value));
      const subscriptions = [
        key.valueChanges.subscribe((value) => this.projectKey.set(value)),
        form.controls.timing.valueChanges.subscribe(() => this.fillSchedule()),
      ];
      onCleanup(() => subscriptions.forEach((subscription) => subscription.unsubscribe()));
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
    return !this.raised() && (this.step() > 0 || this.searched() !== null);
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
    if (this.step() === DETAILS) {
      this.details()?.markAllAsTouched();
    }
    if (this.step() === SCHEDULE) {
      this.schedule.markAllAsTouched();
    }
    if (this.stepProblem()) {
      return;
    }
    if (this.step() === REVIEW) {
      this.raise();
      return;
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
    if (this.step() === DETAILS) {
      this.fillRelease();
    } else if (this.step() === SCHEDULE) {
      this.suggestDate();
    } else if (this.step() === REVIEW) {
      this.loadPreview();
    }
  }

  protected stepProblem(): string | null {
    switch (this.step()) {
      case 0:
        if (!this.chosenProduct()) {
          return 'Choose the product';
        }
        if (!this.details() || !this.product.hasValue()) {
          return this.product.isLoading() || this.profile.isLoading()
            ? 'Wait until the product is loaded'
            : 'Choose a product that can be loaded';
        }
        return null;
      case SCOPE:
        return this.scopeProblem();
      case DETAILS:
        return this.details()?.invalid ? 'Some fields need your attention.' : null;
      case SCHEDULE:
        return scheduleProblem(this.planned(), new Date());
      case REVIEW:
        return !this.preview() || this.shortDescription.invalid || this.description.invalid
          ? 'Check the short description and the description'
          : null;
      default:
        return null;
    }
  }

  protected findEpics(): void {
    this.versionTrigger()?.closePanel();
    this.fixVersion.markAsTouched();
    const version = this.fixVersion.value.trim();
    if (this.fixVersion.invalid) {
      return;
    }
    if (version === this.searched()) {
      this.epics.reload();
    } else {
      this.forgetIssues();
      this.searched.set(version);
    }
  }

  protected toggleService(id: number, on: boolean): void {
    this.serviceIds.update((ids) =>
      on ? [...new Set([...ids, id])] : ids.filter((i) => i !== id),
    );
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

  protected restart(): void {
    this.raised.set(null);
    this.productId.setValue(null);
    this.checked.set(false);
    this.step.set(0);
  }

  private scopeProblem(): string | null {
    const version = this.fixVersion.value.trim();
    if (!version || this.fixVersion.invalid) {
      return 'Enter the FixVersion of the release';
    }
    if (version !== this.searched()) {
      return 'Find the epics of this FixVersion';
    }
    if (this.epics.error() || this.stories.error()) {
      return 'The epics and stories of this FixVersion could not be loaded';
    }
    if (!this.epicKeys().length) {
      return 'Choose at least one epic';
    }
    return this.serviceIds().length ? null : 'Choose at least one service';
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
    this.installationDate.reset();
    this.schedule.reset();
    this.filledRelease = null;
    this.preview.set(null);
    this.previewed = '';
  }

  private fillRelease(): void {
    const release = this.details()!.controls.release;
    const version = this.searched()!;
    if (!release.value.trim() || release.value === this.filledRelease) {
      release.setValue(version);
      this.filledRelease = version;
    }
  }

  private suggestDate(): void {
    const version = this.versions.hasValue()
      ? this.versions.value().find((candidate) => candidate.name === this.searched())
      : null;
    const date = version?.releaseDate;
    if (!this.installationDate.value && date && date >= isoDate(new Date())) {
      this.installationDate.setValue(date);
    }
  }

  private fillSchedule(): void {
    const timing = this.details()?.controls.timing.getRawValue();
    const planned = timing ? plannedSchedule(this.installationDate.value, timing) : null;
    if (planned) {
      this.schedule.setValue(momentInputs(planned));
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
    return changeRequest(
      {
        productId: this.chosenProduct()!,
        serviceIds: this.serviceIds(),
        fixVersion: this.searched()!,
        epicKeys: this.epicKeys(),
        storyKeys: this.storyKeys(),
      },
      this.planned() as Moments,
      toTemplate(this.details()!),
    );
  }

  private loadPreview(): void {
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
          const key = JSON.stringify(request);
          if (key !== this.previewed) {
            this.shortDescription.setValue(preview.shortDescription);
            this.description.setValue(preview.description);
            this.previewed = key;
          }
          this.preview.set(preview);
        },
        error: (error) => {
          this.preview.set(null);
          this.fail(error);
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

  private fail(error: unknown): void {
    const problems = fieldProblems(error);
    const form = this.details();
    if (form) {
      applyTemplateProblems(form, problems);
    }
    this.problem.set(errorMessage(error));
    this.problems.set(problems.map(problemText));
  }
}
