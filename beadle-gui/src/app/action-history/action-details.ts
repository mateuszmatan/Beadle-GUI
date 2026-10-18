import { DIALOG_DATA, Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DIALOG } from '../ui/dialog';
import { PANEL } from '../ui/panel';
import {
  ActionRecord,
  actionText,
  atText,
  detailsText,
  durationText,
  objectText,
  outcomeText,
} from './action-history-model';

@Component({
  selector: 'beadle-action-details',
  imports: [DIALOG, PANEL],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 beadleDialogTitle>{{ title }}</h2>
    </div>
    <div class="modal-body">
      <dl class="rows">
        <dt>When</dt>
        <dd>
          {{ at }} <span class="muted">in your time zone, {{ zone }}</span>
        </dd>
        <dt>User</dt>
        <dd>{{ record.user }}</dd>
        <dt>Object</dt>
        <dd>{{ object }}</dd>
        <dt>Outcome</dt>
        <dd>
          <span class="chip" [class.success]="success" [class.danger]="!success">{{
            outcome
          }}</span>
        </dd>
        <dt>Duration</dt>
        <dd>{{ duration }}</dd>
        <dt>Correlation ID</dt>
        <dd class="mono">{{ record.correlationId ?? '–' }}</dd>
      </dl>
      @if (record.message; as message) {
        <section class="message">
          <h3>Message</h3>
          <div class="banner" [class.danger]="!success" [class.info]="success" role="note">
            {{ message }}
          </div>
        </section>
      }
      <beadle-panel class="request" [expanded]="true" #request="beadlePanel">
        <beadle-panel-header>
          <span class="panel-title">Request</span>
          <span class="panel-description">What the action was given, as JSON without secrets</span>
          <span class="panel-toggle" aria-hidden="true">{{
            request.expanded() ? 'Hide' : 'Show'
          }}</span>
        </beadle-panel-header>
        @if (details) {
          <pre class="code-block">{{ details }}</pre>
        } @else {
          <p class="muted none">Nothing was recorded for the request.</p>
        }
      </beadle-panel>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-primary" beadleDialogClose>Close</button>
    </div>
  `,
  styles: `
    .modal-body {
      width: min(720px, 86vw);
    }

    .message {
      margin-top: 12px;

      h3 {
        margin: 0 0 4px;
        font-size: 14px;
      }

      .banner {
        margin: 0;
        overflow-wrap: anywhere;
        white-space: pre-wrap;
      }
    }

    .request {
      margin-top: 12px;
    }

    .code-block {
      max-height: 50vh;
      margin: 0;
      padding: 10px 12px;
      overflow: auto;
      background: var(--beadle-neutral-bg);
      color: var(--beadle-text);
      font-family: var(--beadle-mono);
      font-size: 12px;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    .none {
      margin: 0;
    }
  `,
})
export class ActionDetails {
  protected readonly record = inject<ActionRecord>(DIALOG_DATA);

  protected readonly object = objectText(this.record);
  protected readonly title = `${actionText(this.record.action)}: ${this.object}`;
  protected readonly at = atText(this.record.at);
  protected readonly outcome = outcomeText(this.record.outcome);
  protected readonly success = this.record.outcome === 'SUCCESS';
  protected readonly duration = durationText(this.record.durationMs);
  protected readonly details = detailsText(this.record.details);
  protected readonly zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function openDetails(dialog: Dialog, record: ActionRecord) {
  return dialog.open<void, ActionRecord, ActionDetails>(ActionDetails, { data: record });
}
