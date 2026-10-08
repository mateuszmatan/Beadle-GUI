import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { errorMessage } from '../core/errors';
import { Field, FieldOption, Fields, formRevision } from '../shared/fields';
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
  NOT_ASSESSED,
  PLANNING_FIELDS,
  REQUEST_FIELDS,
  RISK_FIELDS,
  SECURE_FIELDS,
  SectionKey,
  TIMING_FIELDS,
  USER_FIELDS,
} from './change-sections';
import { TemplateForm, riskOf } from './change-template-model';

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
  imports: [MatFormFieldModule, MatInputModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let f = form();
    @switch (section()) {
      @case ('request') {
        <div class="form-fields">
          @for (fact of facts(); track fact.label) {
            <mat-form-field class="span-6 read-only" floatLabel="always">
              <mat-label>{{ fact.label }}</mat-label>
              <input
                matInput
                readonly
                [value]="fact.value ?? ''"
                [placeholder]="fact.placeholder ?? ''"
              />
            </mat-form-field>
          }
          <dso-fields [group]="f" [fields]="requestFields()" />
          <mat-form-field class="span-6 read-only">
            <mat-label>Risk</mat-label>
            <input matInput readonly [value]="risk() ?? ''" />
            <mat-hint>from the risk assessment</mat-hint>
          </mat-form-field>
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
              <fieldset class="account span-6">
                <legend>Privileged account {{ i + 1 }}</legend>
                <div class="form-fields">
                  <dso-fields [group]="user" [fields]="userFields" />
                </div>
              </fieldset>
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
        <div class="form-fields stacked">
          <dso-fields [group]="f" [fields]="secureFields" />
        </div>
      }
    }
    @if (listsError(); as error) {
      <p class="choice-error" role="alert">
        The lists of the ProTech fields could not be loaded: {{ errorMessage(error) }}
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .accounts {
      padding-top: 2px;
    }

    .account {
      min-width: 0;
      margin: 0;
      padding: 0 10px 6px;
      border: 1px solid var(--dso-border);

      legend {
        padding: 0 4px;
        font-size: 11.5px;
        font-weight: 600;
        color: var(--dso-navy);
      }

      .form-fields {
        padding-top: 2px;
      }
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
  protected readonly userFields = USER_FIELDS;
  protected readonly secureFields = SECURE_FIELDS;

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
      options: [
        { value: null, label: NOT_ASSESSED },
        ...optionsOf(lists?.[field.key as RiskQuestion] ?? []),
      ],
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
}
