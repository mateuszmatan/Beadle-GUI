import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of } from 'rxjs';
import { ChoiceTiles } from '../beadle/choice-tiles';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { CHANGES } from '../core/sections';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { NOT_IN_A_DEPARTMENT, byDepartment } from '../products/departments';
import { errorText } from '../shared/form-errors';
import { counted } from '../shared/formatting';
import {
  ChangesApi,
  IMPACTS,
  JiraIssue,
  ProductionChange,
  RISKS,
  TYPES,
  labelOf,
} from './change-api';
import {
  ChangeWindow,
  WINDOWS,
  WindowChoice,
  changeRequest,
  fromLocalInput,
  localInput,
  presetWindow,
  recentDays,
  storiesFollowing,
  storiesText,
  toggled,
  windowProblem,
  windowText,
} from './change-model';
import { IntegrationNote } from './integration-note';

export const STEPS = ['Product', 'Jira scope', 'Window', 'Review', 'Raised'];

@Component({
  selector: 'dso-change-wizard',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    ChoiceTiles,
    IntegrationNote,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-wizard.html',
  styleUrls: ['../beadle/onboarding.scss', './change-wizard.scss'],
})
export class ChangeWizard implements HasUnsavedChanges {
  private readonly changesApi = inject(ChangesApi);
  private readonly productsApi = inject(ProductsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = CHANGES;
  protected readonly steps = STEPS;
  protected readonly windows = WINDOWS;
  protected readonly errorText = errorText;
  protected readonly windowText = windowText;
  protected readonly counted = counted;
  protected readonly storiesText = storiesText;
  protected readonly typeLabel = (value: string) => labelOf(TYPES, value);
  protected readonly riskLabel = (value: string) => labelOf(RISKS, value);
  protected readonly impactLabel = (value: string) => labelOf(IMPACTS, value);
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
  private readonly catalogue = toSignal(
    this.productsApi.list().pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
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
  protected readonly template = computed(() =>
    this.profile.hasValue() && this.profile.value().version !== null
      ? this.profile.value().template
      : null,
  );
  protected readonly serviceIds = signal<number[]>([]);

  protected readonly epicFrom = new FormControl('', { nonNullable: true });
  protected readonly epicTo = new FormControl('', { nonNullable: true });
  protected readonly storyFrom = new FormControl('', { nonNullable: true });
  protected readonly storyTo = new FormControl('', { nonNullable: true });
  private readonly epicDates = this.dates(this.epicFrom, this.epicTo);
  private readonly storyDates = this.dates(this.storyFrom, this.storyTo);

  protected readonly epicKeys = signal<string[]>([]);
  protected readonly storyKeys = signal<string[]>([]);
  private seenStories = new Set<string>();

  protected readonly epics = rxResource({
    params: () => {
      const productId = this.template() ? this.chosenProduct() : null;
      const dates = this.epicDates();
      return productId && dates ? { productId, dates } : undefined;
    },
    stream: ({ params }) => this.changesApi.epics(params.productId, params.dates),
  });
  protected readonly stories = rxResource({
    params: () => {
      const productId = this.template() ? this.chosenProduct() : null;
      const dates = this.storyDates();
      const epics = this.epicKeys();
      return productId && dates && epics.length ? { productId, epics, dates } : undefined;
    },
    stream: ({ params }) => this.changesApi.stories(params.productId, params.epics, params.dates),
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

  protected readonly windowChoice = signal<WindowChoice | null>(null);
  protected readonly customStart = new FormControl('', { nonNullable: true });
  protected readonly customEnd = new FormControl('', { nonNullable: true });
  private readonly customStartValue = toSignal(this.customStart.valueChanges, {
    initialValue: '',
  });
  private readonly customEndValue = toSignal(this.customEnd.valueChanges, { initialValue: '' });
  protected readonly window = computed<ChangeWindow | null>(() => {
    const choice = this.windowChoice();
    if (choice === null) {
      return null;
    }
    if (choice !== 'custom') {
      return presetWindow(choice, new Date());
    }
    const start = fromLocalInput(this.customStartValue());
    const end = fromLocalInput(this.customEndValue());
    return start && end ? { start, end } : null;
  });

  protected readonly shortDescription = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(160)],
  });
  protected readonly description = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(4000)],
  });
  protected readonly preview = signal<ProductionChange | null>(null);
  protected readonly previewing = signal(false);
  protected readonly raising = signal(false);
  protected readonly problem = signal<string | null>(null);
  protected readonly problems = signal<string[]>([]);
  protected readonly raised = signal<ProductionChange | null>(null);
  private previewed = '';

  protected readonly nextLabel = computed(() =>
    this.step() === 3 ? 'Raise the change in ServiceNow' : 'Continue',
  );

  constructor() {
    this.departmentId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.productId.setValue(null));
    this.productId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.epicKeys.set([]);
      this.storyKeys.set([]);
      this.seenStories = new Set();
    });
    effect(() => {
      const product = this.product.hasValue() ? this.product.value() : null;
      this.serviceIds.set(product?.services.map((service) => service.id) ?? []);
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
    return !this.raised() && this.step() > 0;
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.raised() && !this.raising();
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
    if (this.stepProblem()) {
      return;
    }
    if (this.step() === 3) {
      this.raise();
      return;
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
    if (this.step() === 3) {
      this.loadPreview();
    }
  }

  protected stepProblem(): string | null {
    switch (this.step()) {
      case 0:
        if (!this.chosenProduct()) {
          return 'Choose the product';
        }
        if (!this.template()) {
          return 'The product needs its ServiceNow change template first';
        }
        return this.serviceIds().length ? null : 'Choose at least one service';
      case 1:
        return this.epicKeys().length ? null : 'Choose at least one epic';
      case 2:
        return this.windowChoice() === null
          ? 'Choose the change window'
          : windowProblem(this.window(), new Date());
      case 3:
        return !this.preview() || this.shortDescription.invalid || this.description.invalid
          ? 'Check the short description and the description'
          : null;
      default:
        return null;
    }
  }

  protected toggleService(id: number, on: boolean): void {
    this.serviceIds.update((ids) => (on ? [...new Set([...ids, id])] : ids.filter((i) => i !== id)));
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

  protected chooseWindow(choice: WindowChoice | null): void {
    this.windowChoice.set(choice);
    const preset = choice ? presetWindow(choice === 'custom' ? 'weekend' : choice, new Date()) : null;
    if (choice === 'custom' && preset && !this.customStart.value) {
      this.customStart.setValue(localInput(preset.start));
      this.customEnd.setValue(localInput(preset.end));
    }
  }

  protected issueText(issue: JiraIssue): string {
    return [issue.status, `updated ${issue.updated}`].filter(Boolean).join(' · ');
  }

  protected restart(): void {
    this.raised.set(null);
    this.preview.set(null);
    this.previewed = '';
    this.productId.setValue(null);
    this.windowChoice.set(null);
    this.checked.set(false);
    this.step.set(0);
  }

  private dates(from: FormControl<string>, to: FormControl<string>) {
    const range = recentDays(new Date());
    from.setValue(range.from);
    to.setValue(range.to);
    const fromValue = toSignal(from.valueChanges, { initialValue: range.from });
    const toValue = toSignal(to.valueChanges, { initialValue: range.to });
    return computed(() =>
      fromValue() && toValue() ? { from: fromValue(), to: toValue() } : null,
    );
  }

  private loadedStories(): JiraIssue[] {
    return this.stories.hasValue() ? this.stories.value() : [];
  }

  private forget(stories: JiraIssue[]): void {
    const keys = stories.map((story) => story.key);
    keys.forEach((key) => this.seenStories.delete(key));
    this.storyKeys.update((chosen) => chosen.filter((key) => !keys.includes(key)));
  }

  private request() {
    return changeRequest(
      this.chosenProduct()!,
      this.serviceIds(),
      this.epicKeys(),
      this.storyKeys(),
      this.window(),
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
          this.step.set(4);
        },
        error: (error) => this.fail(error),
      });
  }

  private fail(error: unknown): void {
    this.problem.set(errorMessage(error));
    this.problems.set(fieldProblems(error).map((problem) => problem.message));
  }
}
