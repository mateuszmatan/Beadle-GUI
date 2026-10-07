import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ChangesApi } from './change-api';

@Component({
  selector: 'dso-integration-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (integrations(); as connected) {
      @if (!connected.jiraConnected || !connected.serviceNowConnected) {
        <div class="banner info" role="note">
          <span>
            <strong>Draft.</strong>
            @if (!connected.jiraConnected) {
              Jira is not connected yet, so the epics and stories are demo data.
            }
            @if (!connected.serviceNowConnected) {
              ServiceNow is not connected yet, so a raised change stays in the portal under a demo
              number.
            }
          </span>
        </div>
      }
    }
  `,
})
export class IntegrationNote {
  protected readonly integrations = toSignal(
    inject(ChangesApi)
      .integrations()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
}
