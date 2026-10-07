import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Field, Fields, area, check, choice, count, line, mono } from '../shared/fields';
import { removeItem } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { RiskAssessment, SUGGESTIONS, TYPES } from './change-api';
import {
  JIRA_KEY_ERROR,
  MAX_PRIVILEGED_USERS,
  TIME_ERROR,
  TemplateForm,
  addPrivilegedUser,
} from './change-template-model';

type Suggested = keyof typeof SUGGESTIONS;

interface Suggestion {
  key: Suggested;
  label: string;
}

const CHANGE: Field[] = [
  mono('jiraProjectKey', 'Jira project', '', 2, { error: JIRA_KEY_ERROR }),
  line('assignmentGroup', 'Assignment group', '', 4),
  line('category', 'Category', '', 3),
  choice('type', 'Type', TYPES, '', 3),
  line('configurationItem', 'Affected CI', '', 4),
  line('release', 'Release', '', 4, { hint: 'left empty: the FixVersion' }),
  mono('incident', 'Incident', '', 2, { placeholder: 'INC0012345' }),
  mono('problem', 'Problem', '', 2, { placeholder: 'PRB0001234' }),
  area('affectedClients', 'Affected clients', '', 6),
  area('description', 'Description', '', 6, { hint: 'About the product' }),
];

const APPROVERS: Field[] = [
  line('l1Manager', 'L1 manager', '', 12),
  line('l2Manager', 'L2 manager', '', 12),
  line('businessApprover', 'Business approver', '', 12),
];

const DOWNTIME: Field[] = [check('downtime', 'Downtime during the installation')];

const INSTALLATION_START: Field = line('installationStart', 'Installation start', '', 4);

const TIMING: Field[] = [
  count('installationHours', 'Installation hours', '', 4, { min: 1, max: 72 }),
  count('validationHours', 'Validation hours', '', 4, { min: 0, max: 72 }),
];

const PLANNING: Field[] = [
  area('testSummary', 'Test summary', '', 6),
  area('implementationPlan', 'Implementation plan', '', 6),
  area('validationPlan', 'Validation plan', '', 4),
  area('backoutPlan', 'Backout plan', '', 4),
  area('firstUsePlan', 'First use plan', '', 4),
];

const USER: Field[] = [line('user', 'User', '', 6), mono('account', 'Privileged account', '', 6)];

const RISK_COUNTS: Field[] = [
  count('bbhWorkgroups', 'BBH workgroups', '', 2, { min: 0 }),
  count('bbhUsers', 'BBH users', '', 2, { min: 0 }),
  count('bbhApplications', 'BBH applications', '', 2, { min: 0 }),
  count('clients', 'Impacted clients', '', 3, { min: 0 }),
  count('clientsOutsideBbh', 'Clients outside BBH', '', 3, { min: 0 }),
];

const RISK_CHOICES: Suggestion[] = [
  { key: 'businessImpact', label: 'Business impact' },
  { key: 'changeComplexity', label: 'Change complexity' },
  { key: 'validationComplexity', label: 'Validation complexity' },
  { key: 'platformStatus', label: 'Platform status' },
];

const BACKOUT_TESTING: Field[] = [area('backoutTesting', 'Backout testing & duration', '', 12)];

const LABELS: [string, readonly Pick<Field, 'key' | 'label'>[]][] = [
  ['', [...CHANGE, ...DOWNTIME]],
  ['approvers.', APPROVERS],
  ['timing.', [INSTALLATION_START, ...TIMING]],
  ['planning.', PLANNING],
  ['privilegedAccess.', [{ key: 'users', label: 'Privileged users' }]],
  ['privilegedAccess.users.', USER],
  ['riskAssessment.', [...RISK_COUNTS, ...RISK_CHOICES, ...BACKOUT_TESTING]],
];

export function templateLabel(path: string): string | null {
  const plain = path.replace(/\[\d+]/g, '');
  for (const [prefix, fields] of LABELS) {
    const found = fields.find((field) => prefix + field.key === plain);
    if (found) {
      return found.label;
    }
  }
  return null;
}

@Component({
  selector: 'dso-change-template-form',
  imports: [
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    Fields,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let f = form();
    @let timing = f.controls.timing;
    @let access = f.controls.privilegedAccess.controls;
    @let users = access.users;
    <div class="cards">
      <section class="card template-card wide">
        <header>
          <h3>Change</h3>
          <p>Where the change is filed and what it touches.</p>
        </header>
        <div class="form-fields">
          <dso-fields [group]="f" [fields]="change" />
        </div>
      </section>
      <section class="card template-card">
        <header>
          <h3>Approvers</h3>
          <p>Who approves the change.</p>
        </header>
        <div class="form-fields">
          <dso-fields [group]="f.controls.approvers" [fields]="approvers" />
        </div>
      </section>
      <section class="card template-card">
        <header>
          <h3>Schedule defaults</h3>
          <p>The change starts on the installation date at this time, in local time.</p>
        </header>
        <div class="form-fields">
          <dso-fields [group]="f" [fields]="downtime" />
          <mat-form-field class="span-4">
            <mat-label>{{ installationStart.label }}</mat-label>
            <input
              matInput
              type="time"
              required
              [formControl]="timing.controls.installationStart"
            />
            <mat-error>{{ errorText(timing.controls.installationStart, timeError) }}</mat-error>
          </mat-form-field>
          <dso-fields [group]="timing" [fields]="timingFields" />
        </div>
      </section>
      <section class="card template-card wide">
        <header>
          <h3>Planning</h3>
          <p>How the change is tested, done, validated, backed out and first used.</p>
        </header>
        <div class="form-fields">
          <dso-fields [group]="f.controls.planning" [fields]="planning" />
        </div>
      </section>
      <section class="card template-card wide">
        <header>
          <h3>Privileged access</h3>
          <p>Users who need a privileged account for the change, up to {{ maxUsers }}.</p>
        </header>
        <div class="form-fields">
          <mat-checkbox class="span-12" [formControl]="access.required">
            Privileged access needed
          </mat-checkbox>
        </div>
        @if (access.required.value) {
          @for (user of users.controls; track user; let i = $index) {
            <div class="user-row">
              <span class="index">{{ i + 1 }}</span>
              <div class="form-fields">
                <dso-fields [group]="user" [fields]="userFields" />
              </div>
              <button
                mat-button
                type="button"
                class="danger"
                [attr.aria-label]="'Remove user ' + (i + 1)"
                (click)="removeUser(i)"
              >
                Remove
              </button>
            </div>
          }
          @if (users.errors && (users.touched || users.dirty)) {
            <p class="choice-error" role="alert">{{ errorText(users) }}</p>
          }
          <div class="user-actions">
            <button
              mat-stroked-button
              type="button"
              [disabled]="users.length >= maxUsers"
              (click)="addUser()"
            >
              Add user
            </button>
            <span class="muted">{{ users.length }} of {{ maxUsers }}</span>
          </div>
        }
      </section>
      <section class="card template-card wide">
        <header>
          <h3>Risk assessment</h3>
          <p>The numbers impacted by the change and how hard it is.</p>
        </header>
        <div class="form-fields">
          <dso-fields [group]="f.controls.riskAssessment" [fields]="riskCounts" />
          @for (choice of riskChoices; track choice.key) {
            @let control = risk(choice.key);
            <mat-form-field class="span-3">
              <mat-label>{{ choice.label }}</mat-label>
              <input matInput autocomplete="off" [formControl]="control" [matAutocomplete]="auto" />
              <mat-autocomplete #auto="matAutocomplete">
                @for (option of suggestions(choice.key, control.value); track option) {
                  <mat-option [value]="option">{{ option }}</mat-option>
                }
              </mat-autocomplete>
              <mat-error>{{ errorText(control) }}</mat-error>
            </mat-form-field>
          }
          <dso-fields [group]="f.controls.riskAssessment" [fields]="backoutTesting" />
        </div>
      </section>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .cards {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }

    .wide {
      grid-column: 1 / -1;
    }

    .template-card {
      padding: 8px 14px 6px;

      > header {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 2px 10px;

        h3 {
          margin: 0;
          font-size: 14px;
          font-weight: 600;
        }

        p {
          margin: 0;
          font-size: 11.5px;
          color: var(--dso-muted);
        }
      }
    }

    .user-row {
      display: flex;
      align-items: flex-start;
      gap: 8px;

      .index {
        margin-top: 12px;
      }

      .form-fields {
        flex: 1;
      }

      button {
        margin-top: 8px;
      }
    }

    .user-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 4px 0 6px;
      font-size: 12px;
    }

    @media (max-width: 900px) {
      .cards {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ChangeTemplateForm {
  readonly form = input.required<TemplateForm>();

  protected readonly change = CHANGE;
  protected readonly approvers = APPROVERS;
  protected readonly downtime = DOWNTIME;
  protected readonly installationStart = INSTALLATION_START;
  protected readonly timingFields = TIMING;
  protected readonly planning = PLANNING;
  protected readonly userFields = USER;
  protected readonly riskCounts = RISK_COUNTS;
  protected readonly riskChoices = RISK_CHOICES;
  protected readonly backoutTesting = BACKOUT_TESTING;
  protected readonly maxUsers = MAX_PRIVILEGED_USERS;
  protected readonly timeError = TIME_ERROR;
  protected readonly errorText = errorText;

  protected risk(key: Suggested): FormControl<string> {
    return this.form().controls.riskAssessment.controls[key satisfies keyof RiskAssessment];
  }

  protected suggestions(key: Suggested, value: string): readonly string[] {
    const options = SUGGESTIONS[key];
    const typed = value.trim().toLowerCase();
    return options.some((option) => option.toLowerCase() === typed)
      ? options
      : options.filter((option) => option.toLowerCase().includes(typed));
  }

  protected addUser(): void {
    addPrivilegedUser(this.form());
  }

  protected removeUser(index: number): void {
    removeItem(this.form().controls.privilegedAccess.controls.users, index);
  }
}
