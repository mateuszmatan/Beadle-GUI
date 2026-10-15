import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { errorMessage } from '../core/errors';
import { Field, FieldOption, Fields, formRevision } from '../shared/fields';
import { FORM_FIELD } from '../ui/form-field';
import { ChangeOptions, RiskQuestion } from './change-api';
import { ChangeOptionLists } from './change-options';
import {
  ADMIN_HINTS,
  APPROVAL_FIELDS,
  CLOSING_FIELDS,
  COUNT_FIELDS,
  DOWNTIME_FIELDS,
  Fact,
  JIRA_FIELDS,
  PLANNING_FIELDS,
  REQUEST_FIELDS,
  RISK,
  RISK_FIELDS,
  SECURE_DEFAULT_FIELDS,
  SECURE_FIELDS,
  SectionKey,
  TIMING_FIELDS,
  USER_FIELDS,
} from './change-sections';
import { MAX_PRIVILEGED_USERS, TemplateForm, riskOf } from './change-template-model';

const LISTED: readonly SectionKey[] = ['request', 'risk'];

const optionsOf = (values: readonly string[]): FieldOption[] =>
  values.map((value) => ({ value, label: value }));

function listed(field: Field, options: ChangeOptions | null): Field {
  switch (field.key) {
    case 'category':
      return { ...field, options: optionsOf(options?.categories ?? []) };
    case 'type':
      return { ...field, options: options?.types ?? [] };
    default:
      return field;
  }
}

@Component({
  selector: 'dso-template-section',
  imports: [FORM_FIELD, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let f = form();
    @switch (section()) {
      @case ('request') {
        <div class="form-fields">
          @for (fact of facts(); track fact.label) {
            <dso-form-field class="span-6 read-only">
              <dso-label>{{ fact.label }}</dso-label>
              <input
                dsoInput
                readonly
                [value]="fact.value ?? ''"
                [placeholder]="fact.placeholder ?? ''"
              />
            </dso-form-field>
          }
          <dso-fields [group]="f" [fields]="requestFields()" />
          <dso-form-field class="span-6 read-only">
            <dso-label>{{ riskField.label }}</dso-label>
            <input dsoInput readonly [value]="risk() ?? ''" />
            <dso-hint>{{ riskField.hint }}</dso-hint>
          </dso-form-field>
          <dso-fields [group]="f" [fields]="closingFields" />
        </div>
      }
      @case ('jira') {
        <div class="form-fields stacked">
          <dso-fields [group]="f" [fields]="jiraFields" />
        </div>
      }
      @case ('approvals') {
        <div class="form-fields stacked">
          <dso-fields [group]="f.controls.approvers" [fields]="approvalFields" />
        </div>
      }
      @case ('schedule') {
        <div class="form-fields">
          <dso-fields [group]="f.controls.timing" [fields]="timingFields" />
          <dso-fields [group]="f" [fields]="downtimeFields" />
        </div>
      }
      @case ('planning') {
        <div class="form-fields">
          <dso-fields [group]="f.controls.planning" [fields]="planningFields" />
        </div>
      }
      @case ('privileged') {
        <div class="form-fields">
          <dso-fields [group]="f.controls.privilegedAccess" [fields]="countFields" />
        </div>
        @if (accounts().length) {
          <div class="form-fields accounts">
            @for (user of accounts(); track user; let i = $index) {
              <dso-fields [group]="user" [fields]="userRows[i]" />
            }
          </div>
        }
      }
      @case ('risk') {
        <div class="form-fields">
          <dso-fields [group]="f.controls.riskAssessment" [fields]="riskFields()" />
        </div>
      }
      @case ('secure') {
        <div class="form-fields">
          @if (!admin()) {
            <dso-fields [group]="f" [fields]="secureFields" />
          }
          <dso-fields [group]="f.controls.secureCoding" [fields]="secureCodingFields()" />
        </div>
      }
    }
    @if (listsError(); as error) {
      <div class="lists-error" role="alert">
        <p class="choice-error">
          The lists of the ProTech fields could not be loaded: {{ errorMessage(error) }}
        </p>
        <button type="button" class="btn btn-link" (click)="reloadLists()">Try again</button>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .accounts {
      padding-top: 2px;
    }

    .lists-error {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0 8px;
    }
  `,
})
export class ChangeTemplateSection {
  readonly form = input.required<TemplateForm>();
  readonly section = input.required<SectionKey>();
  readonly facts = input<readonly Fact[]>([]);
  readonly admin = input(false);

  private readonly lists = inject(ChangeOptionLists);

  protected readonly errorMessage = errorMessage;
  protected readonly closingFields = CLOSING_FIELDS;
  protected readonly jiraFields = JIRA_FIELDS;
  protected readonly approvalFields = APPROVAL_FIELDS;
  protected readonly timingFields = TIMING_FIELDS;
  protected readonly downtimeFields = DOWNTIME_FIELDS;
  protected readonly planningFields = PLANNING_FIELDS;
  protected readonly countFields = COUNT_FIELDS;
  protected readonly userRows = Array.from({ length: MAX_PRIVILEGED_USERS }, (_, index) =>
    USER_FIELDS.map((field) => ({ ...field, label: `${field.label} ${index + 1}` })),
  );
  protected readonly secureFields = SECURE_FIELDS;
  protected readonly secureCodingFields = computed(() =>
    this.admin()
      ? SECURE_DEFAULT_FIELDS
      : SECURE_DEFAULT_FIELDS.map((field) => ({ ...field, readonly: true })),
  );
  protected readonly riskField = RISK;

  protected readonly requestFields = computed(() => {
    const options = this.lists.options();
    const hints = this.admin() ? ADMIN_HINTS : {};
    return REQUEST_FIELDS.map((field) => {
      const hint = hints[field.key];
      return listed(hint ? { ...field, hint } : field, options);
    });
  });
  protected readonly riskFields = computed(() => {
    const lists = this.lists.options()?.risk;
    return RISK_FIELDS.map((field) => ({
      ...field,
      options: optionsOf(lists?.[field.key as RiskQuestion] ?? []),
    }));
  });
  protected readonly listsError = computed(() =>
    LISTED.includes(this.section()) ? this.lists.loaded.error() : undefined,
  );

  private readonly riskRevision = formRevision(() => this.form().controls.riskAssessment);
  private readonly accessRevision = formRevision(() => this.form().controls.privilegedAccess);

  protected readonly risk = computed(() => {
    this.riskRevision();
    const lists = this.lists.options()?.risk;
    return lists ? riskOf(this.form().controls.riskAssessment.getRawValue(), lists) : null;
  });
  protected readonly accounts = computed(() => {
    this.accessRevision();
    return [...this.form().controls.privilegedAccess.controls.users.controls];
  });

  protected reloadLists(): void {
    this.lists.loaded.reload();
  }
}
