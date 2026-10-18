import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  numberAttribute,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, of } from 'rxjs';
import { MyBusinessService } from '../business-services/my-business-service';
import { RETRY } from '../core/errors';
import { changePath, productPath } from '../core/sections';
import { RelativeTimePipe, formatRelative } from '../shared/formatting';
import { PANEL } from '../ui/panel';
import {
  ChangeUpdate,
  ChangesApi,
  ChangeState,
  STATES,
  UpdateStatus,
  isOpen,
  labelOf,
} from './change-api';
import { ChangeApprovals } from './change-approvals';
import { ChangePage } from './change-page';
import { editHint, momentText } from './change-model';
import { fieldLabels } from './change-problems';
import { ChangeSummary } from './change-summary';
import { ChangeTasksForm } from './change-tasks-form';
import { taskWindow, tasksForm } from './change-tasks-model';
import { PublishedChange } from './published-change';
import { WorkflowProgress, stateNow } from './workflow-progress';

export const POLL_INTERVAL = 3000;
export const MAX_POLLS = 25;

const TONES: Record<UpdateStatus, string> = {
  PENDING: 'info',
  APPLIED: 'success',
  NOT_APPLIED: 'danger',
};

export function updateText(update: ChangeUpdate, now = Date.now()): string {
  const at = momentText(update.requestedAt);
  switch (update.status) {
    case 'PENDING':
      return update.fields.length
        ? `Your update of ${at} is waiting for ProTech: ${fieldLabels(update.fields)}.`
        : `Your update of ${at} is waiting for ProTech.`;
    case 'APPLIED':
      return [
        `ProTech applied the update of ${at}`,
        update.businessServiceName ? ` by ${update.businessServiceName}` : '',
        '.',
        update.checkedAt ? ` Checked ${formatRelative(update.checkedAt, now)}.` : '',
      ].join('');
    case 'NOT_APPLIED':
      return [
        `ProTech did not apply the update of ${at}: ${fieldLabels(update.fields)}.`,
        update.message,
      ]
        .filter(Boolean)
        .join(' ');
  }
}

@Component({
  selector: 'beadle-change-detail',
  imports: [
    RouterLink,
    ChangePage,
    PANEL,
    RelativeTimePipe,
    ChangeApprovals,
    ChangeSummary,
    ChangeTasksForm,
    WorkflowProgress,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <beadle-change-page [change]="change">
      @if (change.hasValue()) {
        @let c = change.value();
        <header class="page-header">
          <div>
            <h1>{{ c.number }}</h1>
            <p>{{ c.shortDescription }}</p>
          </div>
          <div class="actions">
            @if (open()) {
              @if (hint(); as message) {
                <span class="muted edit-hint" id="edit-hint">{{ message }}</span>
                <button
                  type="button"
                  class="btn btn-primary"
                  disabled
                  aria-describedby="edit-hint"
                  [attr.title]="message"
                >
                  Edit change
                </button>
              } @else {
                <a class="btn btn-primary" [routerLink]="changeLink(c.id!, 'edit')">Edit change</a>
              }
            }
            @if (c.url) {
              <a class="btn btn-outline-primary" [href]="c.url" target="_blank" rel="noopener"
                >Open in ProTech</a
              >
            }
            @if (c.productId) {
              <a class="btn btn-outline-primary" [routerLink]="productLink(c.productId)"
                >Open product</a
              >
            }
          </div>
        </header>
        @if (c.syncProblem) {
          <div class="banner sync-problem" role="status">
            <span>
              <strong>{{ c.syncProblem }}</strong>
              @if (c.syncedAt) {
                Beadle shows what it last read from ProTech {{ c.syncedAt | relative }}.
              } @else {
                Beadle shows what it last read from ProTech.
              }
              {{ retry }}
            </span>
            <span class="spacer"></span>
            <button type="button" class="btn btn-link" (click)="change.reload()">Try again</button>
          </div>
        } @else if (c.syncedAt) {
          <p class="note sync">Read from ProTech {{ c.syncedAt | relative }}</p>
        }
        @if (c.update; as update) {
          <div class="banner update" [class]="tones[update.status]" role="status">
            <span>
              {{ updateText(update) }}
              @if (update.status === 'PENDING') {
                {{
                  polling()
                    ? 'This page checks again every few seconds.'
                    : 'ProTech is taking longer than usual, so this page stopped checking.'
                }}
              } @else if (update.status === 'NOT_APPLIED' && open() && !hint()) {
                Edit the change to send these values again.
              }
            </span>
            @if (update.status === 'PENDING' && !polling()) {
              <span class="spacer"></span>
              <button type="button" class="btn btn-link" (click)="checkAgain()">Check again</button>
            }
          </div>
        }
        @if (open() && !hint() && !c.template.secureCodingTicket) {
          <div class="banner info secure-coding-missing" role="note">
            <span
              >{{ c.number }} has no secure coding ticket yet. Create it in CyberTrack, the Jira
              project SCP.</span
            >
            <span class="spacer"></span>
            <a class="btn btn-link" [routerLink]="changeLink(c.id!, 'secure-coding')"
              >Create secure coding ticket</a
            >
          </div>
        }
        <section class="card block">
          <h2>Where the change is</h2>
          <p class="now">
            <strong>{{ stateLabel(c.state) }}.</strong>
            {{ stateNow(c) }}
          </p>
          <beadle-workflow-progress [state]="c.state" [workflow]="c.workflow" />
        </section>
        <section class="card block">
          <beadle-change-approvals [change]="c" (changed)="change.set($event)" />
        </section>
        <section class="card block">
          <h2>The change at a glance</h2>
          <beadle-change-summary [change]="c" layout="key" />
        </section>
        <section class="card block">
          <h2>Change tasks</h2>
          <p class="section-help">
            A change task (CTASK) is a piece of work inside the change, done by one team. ProTech
            asks the approvers of its assignment group for their approval in the CTask Approval
            stage.
          </p>
          @if (tasks(); as list) {
            <beadle-change-tasks-form [tasks]="list" [readonly]="true" />
          } @else {
            <p class="none muted">
              {{ open() ? 'No change tasks yet. Add them with Edit change.' : 'None' }}
            </p>
          }
        </section>
        <beadle-panel class="more" #fields="beadlePanel">
          <beadle-panel-header>
            <span class="panel-title">All ProTech fields</span>
            <span class="panel-description"
              >Request data, risk assessment, privileged access, secure coding and planning</span
            >
            <span class="panel-toggle" aria-hidden="true">{{
              fields.expanded() ? 'Hide' : 'Show'
            }}</span>
          </beadle-panel-header>
          <beadle-change-summary [change]="c" layout="details" />
        </beadle-panel>
        <beadle-panel class="more" #description="beadlePanel">
          <beadle-panel-header>
            <span class="panel-title">Text sent to ProTech</span>
            <span class="panel-description"
              >The description ProTech holds, written from the fields of the change</span
            >
            <span class="panel-toggle" aria-hidden="true">{{
              description.expanded() ? 'Hide' : 'Show'
            }}</span>
          </beadle-panel-header>
          <pre class="text">{{ c.description }}</pre>
        </beadle-panel>
      }
    </beadle-change-page>
  `,
  styles: `
    .now {
      margin: -2px 0 10px;
    }

    .edit-hint {
      font-size: 12px;
    }

    .sync {
      margin: -6px 0 10px;
    }

    .banner .btn-link {
      flex-shrink: 0;
    }

    .more .panel-title {
      font-family: var(--beadle-serif);
      font-size: 14px;
      color: var(--beadle-navy);
    }

    .text {
      margin: 0;
      font: inherit;
      font-size: 13px;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    .none {
      margin: 0;
      font-size: 13px;
    }

    @media (max-width: 760px) {
      .more .panel-title {
        min-width: 0;
      }

      .more .panel-description {
        display: none;
      }
    }
  `,
})
export class ChangeDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly published = inject(PublishedChange);
  private readonly myBusinessService = inject(MyBusinessService);

  protected readonly productLink = productPath;
  protected readonly changeLink = changePath;
  protected readonly updateText = updateText;
  protected readonly stateNow = stateNow;
  protected readonly retry = RETRY;
  protected readonly tones = TONES;
  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => {
      const published = this.published.take(params);
      return published ? of(published) : this.api.get(params);
    },
  });
  protected readonly open = computed(() => this.change.hasValue() && isOpen(this.change.value()));
  protected readonly tasks = computed(() => {
    const change = this.change.hasValue() ? this.change.value() : null;
    if (!change?.tasks.length) {
      return null;
    }
    const form = tasksForm(change.tasks, taskWindow(change.number, change.schedule));
    form.disable();
    return form;
  });
  protected readonly hint = computed(() =>
    this.change.hasValue()
      ? editHint(this.change.value(), this.myBusinessService.businessServiceId())
      : null,
  );
  private readonly polls = linkedSignal({ source: this.id, computation: () => 0 });
  protected readonly polling = computed(() => this.polls() < MAX_POLLS);

  constructor() {
    effect((onCleanup) => {
      const pending =
        this.change.hasValue() &&
        !this.change.isLoading() &&
        this.change.value().update?.status === 'PENDING';
      if (!pending || this.polls() >= MAX_POLLS) {
        return;
      }
      let request: Subscription | undefined;
      const counted = () => this.polls.update((count) => count + 1);
      const timer = setTimeout(() => {
        request = this.api.get(this.id()).subscribe({
          next: (read) => {
            this.change.set(read);
            counted();
          },
          error: counted,
        });
      }, POLL_INTERVAL);
      onCleanup(() => {
        clearTimeout(timer);
        request?.unsubscribe();
      });
    });
  }

  protected checkAgain(): void {
    this.polls.set(0);
  }

  protected stateLabel(state: ChangeState): string {
    return labelOf(STATES, state);
  }
}
