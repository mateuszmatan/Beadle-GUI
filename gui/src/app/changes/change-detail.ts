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
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { Subscription, of } from 'rxjs';
import { MyDepartment } from '../beadle/my-department';
import { errorMessage } from '../core/errors';
import { CHANGES, beadleChange, beadleProduct } from '../core/sections';
import { RelativeTimePipe, formatRelative } from '../shared/formatting';
import {
  ChangeUpdate,
  ChangesApi,
  TASK_STATES,
  TaskState,
  UpdateStatus,
  isOpen,
  labelOf,
} from './change-api';
import { editHint, momentText } from './change-model';
import { fieldLabels } from './change-problems';
import { ChangeSummary } from './change-summary';
import { PublishedChange } from './published-change';
import { WorkflowProgress } from './workflow-progress';

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
        update.departmentName ? ` by ${update.departmentName}` : '',
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
  selector: 'dso-change-detail',
  imports: [
    RouterLink,
    MatButtonModule,
    MatProgressBarModule,
    RelativeTimePipe,
    ChangeSummary,
    WorkflowProgress,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a [routerLink]="section.path">{{ section.label }}</a>
        <span class="sep" aria-hidden="true">/</span>
        <span>{{ change.hasValue() ? change.value().number : 'Change' }}</span>
      </nav>
      @if (change.isLoading() && !change.hasValue()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (change.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
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
                <span class="muted edit-hint">{{ message }}</span>
                <button mat-flat-button type="button" disabled [attr.title]="message">Edit</button>
              } @else {
                <a mat-flat-button [routerLink]="changeLink(c.id!, 'edit')">Edit</a>
              }
            }
            @if (c.url) {
              <a mat-stroked-button [href]="c.url" target="_blank" rel="noopener">ProTech</a>
            }
            @if (c.productId) {
              <a mat-stroked-button [routerLink]="productLink(c.productId)">Product</a>
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
            </span>
          </div>
        } @else if (c.syncedAt) {
          <p class="note sync">Read from ProTech {{ c.syncedAt | relative }}</p>
        }
        @if (c.update; as update) {
          <div class="banner update" [class]="tones[update.status]" role="status">
            {{ updateText(update) }}
          </div>
        }
        <section class="card block">
          <h2>Workflow progress</h2>
          <dso-workflow-progress [state]="c.state" [workflow]="c.workflow" />
        </section>
        <section class="card block">
          <h2>Summary</h2>
          <dso-change-summary [change]="c" />
        </section>
        <div class="columns">
          <section class="card block">
            <h2>Change tasks</h2>
            <ol class="tasks">
              @for (task of c.tasks; track $index) {
                <li [class.canceled]="task.state === 'CANCELED'">
                  <span class="task-head">
                    @if (task.number) {
                      <span class="mono">{{ task.number }}</span>
                    } @else {
                      <span class="muted">not in ProTech yet</span>
                    }
                    <span class="chip neutral">{{ taskState(task.state) }}</span>
                  </span>
                  <strong>{{ task.shortDescription }}</strong>
                  <span class="muted">{{ task.description }}</span>
                </li>
              }
            </ol>
          </section>
          <section class="card block">
            <h2>Description</h2>
            <pre class="text">{{ c.description }}</pre>
          </section>
        </div>
      }
    </div>
  `,
  styles: `
    .columns {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 12px;
      margin-top: 12px;
    }

    .block {
      padding: 12px 16px;

      h2 {
        margin: 0 0 8px;
        font-size: 15px;
      }
    }

    .page > .block + .block {
      margin-top: 12px;
    }

    .edit-hint {
      font-size: 12px;
    }

    .sync {
      margin: -6px 0 10px;
    }

    .text {
      margin: 0;
      font: inherit;
      font-size: 12.5px;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    .tasks {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0;
      padding-left: 20px;
      font-size: 12.5px;

      li {
        display: flex;
        flex-direction: column;
        min-width: 0;
        overflow-wrap: anywhere;
      }

      .canceled {
        color: var(--dso-muted);

        strong {
          font-weight: 500;
          text-decoration: line-through;
        }
      }
    }

    .task-head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px;
    }

    @media (max-width: 900px) {
      .columns {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ChangeDetail {
  readonly id = input.required({ transform: numberAttribute });

  private readonly api = inject(ChangesApi);
  private readonly published = inject(PublishedChange);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = CHANGES;
  protected readonly errorMessage = errorMessage;
  protected readonly productLink = beadleProduct;
  protected readonly changeLink = beadleChange;
  protected readonly updateText = updateText;
  protected readonly tones = TONES;
  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => {
      const published = this.published.take(params);
      return published ? of(published) : this.api.get(params);
    },
  });
  protected readonly open = computed(() => this.change.hasValue() && isOpen(this.change.value()));
  protected readonly hint = computed(() =>
    this.change.hasValue() ? editHint(this.change.value(), this.myDepartment.departmentId()) : null,
  );
  private readonly polls = linkedSignal({ source: this.id, computation: () => 0 });

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

  protected taskState(state: TaskState): string {
    return labelOf(TASK_STATES, state);
  }
}
