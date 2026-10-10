import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ChangeIntegrations, ChangesApi } from './change-api';

const DEMO_PROTECH =
  'A demo ProTech gives each change its number, moves it through the workflow on its own and applies an update a few seconds after it is published.';

export function demoText({
  jiraConnected,
  serviceNowConnected,
}: ChangeIntegrations): string | null {
  if (!jiraConnected && !serviceNowConnected) {
    return `Jira and ProTech are not connected yet, so the epics and stories are examples and no change reaches the real ProTech. ${DEMO_PROTECH}`;
  }
  if (!jiraConnected) {
    return 'Jira is not connected yet, so the epics and stories are examples, not your real Jira data.';
  }
  return serviceNowConnected
    ? null
    : `ProTech is not connected yet, so no change reaches the real ProTech. ${DEMO_PROTECH}`;
}

@Component({
  selector: 'dso-integration-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (text(); as demo) {
      <div class="banner info" role="note">
        <span><strong>Demo mode.</strong> {{ demo }}</span>
      </div>
    }
  `,
})
export class IntegrationNote {
  private readonly integrations = toSignal(
    inject(ChangesApi)
      .integrations()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  protected readonly text = computed(() => {
    const connected = this.integrations();
    return connected && demoText(connected);
  });
}
