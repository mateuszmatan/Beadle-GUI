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
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { errorMessage, fieldProblems } from '../core/errors';
import { Notifier } from '../core/notifier';
import { HasUnsavedChanges } from '../core/unsaved-changes';
import { Fields, area, choice, line, mono } from '../shared/fields';
import { applyFieldProblems, joinLines, lines, maxLines, text } from '../shared/form-controls';
import {
  ChangeImpact,
  ChangeRisk,
  ChangeTemplate,
  ChangeType,
  ChangesApi,
  IMPACTS,
  RISKS,
  TYPES,
} from './change-api';

const JIRA_KEY = /^[A-Z][A-Z0-9_]{1,9}$/;

function templateForm(template: ChangeTemplate) {
  return new FormGroup({
    jiraProjectKey: text(
      template.jiraProjectKey,
      Validators.required,
      Validators.pattern(JIRA_KEY),
    ),
    configurationItem: text(
      template.configurationItem,
      Validators.required,
      Validators.maxLength(200),
    ),
    assignmentGroup: text(template.assignmentGroup, Validators.required, Validators.maxLength(200)),
    type: new FormControl<ChangeType>(template.type, { nonNullable: true }),
    category: text(template.category, Validators.required, Validators.maxLength(100)),
    risk: new FormControl<ChangeRisk>(template.risk, { nonNullable: true }),
    impact: new FormControl<ChangeImpact>(template.impact, { nonNullable: true }),
    riskAssessment: text(template.riskAssessment, Validators.required, Validators.maxLength(2000)),
    approvers: text(joinLines(template.approvers), Validators.required, maxLines(10)),
    description: text(template.description, Validators.maxLength(2000)),
    implementationPlan: text(
      template.implementationPlan,
      Validators.required,
      Validators.maxLength(2000),
    ),
    backoutPlan: text(template.backoutPlan, Validators.required, Validators.maxLength(2000)),
    testPlan: text(template.testPlan, Validators.required, Validators.maxLength(2000)),
  });
}

type TemplateForm = ReturnType<typeof templateForm>;

export const BLOCKS = [
  {
    title: 'ServiceNow',
    text: 'Where the change is filed and who implements it.',
    fields: [
      line('configurationItem', 'Configuration item', '', 6, {
        hint: 'The CMDB CI of the product',
      }),
      line('assignmentGroup', 'Assignment group', '', 6),
      choice('type', 'Change type', TYPES, '', 4),
      line('category', 'Category', '', 4),
      mono('jiraProjectKey', 'Jira project key', '', 4, {
        hint: 'Epics and stories come from this project',
        error: '2 to 10 upper case letters, digits or _',
      }),
    ],
  },
  {
    title: 'Risk and approvals',
    text: 'The risk assessment and the managers who approve every change of the product.',
    fields: [
      choice('risk', 'Risk', RISKS, '', 3),
      choice('impact', 'Impact', IMPACTS, '', 3),
      area('approvers', 'Approvers', '', 6, { hint: 'One manager per line' }),
      area('riskAssessment', 'Risk assessment', '', 12),
    ],
  },
  {
    title: 'Description and plans',
    text: 'The fixed part of every change. The portal adds the Jira scope and the window to the description.',
    fields: [
      area('description', 'About the product', '', 12),
      area('implementationPlan', 'Implementation plan', '', 12),
      area('backoutPlan', 'Backout plan', '', 6),
      area('testPlan', 'Test plan', '', 6),
    ],
  },
];

@Component({
  selector: 'dso-change-profile-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    Fields,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page editor">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a routerLink="/products">DevSecOps Product Management</a>
        <span class="sep" aria-hidden="true">/</span>
        <a [routerLink]="['/products', id()]">{{ productName() }}</a>
        <span class="sep" aria-hidden="true">/</span>
        <span>ServiceNow change</span>
      </nav>
      <header class="page-header">
        <div>
          <h1>ServiceNow change template of {{ productName() }}</h1>
          <p>
            Beadle uses it for every production change of the product, together with the Jira scope
            and the window chosen for that change.
          </p>
        </div>
      </header>
      @if (profile.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (profile.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (form(); as group) {
        @if (profile.hasValue() && profile.value().version === null) {
          <div class="banner info" role="status">
            Not saved yet. The values below are suggestions from the product.
          </div>
        }
        <form [formGroup]="group" (ngSubmit)="save()" novalidate>
          <section class="card product-fields">
            @for (block of blocks; track block.title) {
              <div class="form-block">
                <header>
                  <h3>{{ block.title }}</h3>
                  <p>{{ block.text }}</p>
                </header>
                <div class="form-fields">
                  <dso-fields [group]="group" [fields]="block.fields" />
                </div>
              </div>
            }
          </section>
          <div class="save-bar">
            @if (saveError(); as error) {
              <span class="save-error" role="alert">{{ error }}</span>
            } @else if (group.dirty) {
              <span class="muted">Unsaved changes</span>
            }
            <span class="spacer"></span>
            <a mat-button [routerLink]="['/products', id()]">Cancel</a>
            <button mat-flat-button type="submit" [disabled]="saving()">
              @if (saving()) {
                <mat-spinner diameter="18" />
              }
              Save template
            </button>
          </div>
        </form>
      }
    </div>
  `,
  styles: `
    .product-fields {
      padding: 4px 16px;
    }
  `,
})
export class ChangeProfileEditor implements HasUnsavedChanges {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly blocks = BLOCKS;
  protected readonly errorMessage = errorMessage;
  protected readonly profile = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.profile(params),
  });
  protected readonly productName = computed(() =>
    this.profile.hasValue() ? this.profile.value().productName : 'Product',
  );
  protected readonly form = signal<TemplateForm | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  private version: number | null = null;

  constructor() {
    effect(() => {
      if (this.profile.hasValue()) {
        this.version = this.profile.value().version;
        this.form.set(templateForm(this.profile.value().template));
      }
    });
  }

  hasUnsavedChanges(): boolean {
    return !!this.form()?.dirty && !this.saving();
  }

  protected save(): void {
    const form = this.form()!;
    this.saveError.set(null);
    form.markAllAsTouched();
    if (form.invalid) {
      this.saveError.set('Some fields need your attention.');
      return;
    }
    const value = form.getRawValue();
    this.saving.set(true);
    this.api
      .saveProfile(this.id(), this.version, { ...value, approvers: lines(value.approvers) })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.version = saved.version;
          form.markAsPristine();
          this.notifier.success(`The ServiceNow change template of ${saved.productName} is saved`);
        },
        error: (error) => {
          const unmatched = applyFieldProblems(
            form,
            fieldProblems(error).map((problem) => ({
              ...problem,
              field: problem.field.replace(/^template\./, ''),
            })),
          );
          this.saveError.set(
            unmatched.length || !fieldProblems(error).length
              ? errorMessage(error)
              : 'The portal did not accept some values. They are marked below.',
          );
        },
      });
  }
}
