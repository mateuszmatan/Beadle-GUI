import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { ChangeIntegrations, ChangesApi } from './change-api';

const DEMO_PROTECH =
  'A demo ProTech gives each change its number, moves it through the workflow on its own and applies an update a few seconds after it is published.';

const DEMO_CYBERTRACK =
  'CyberTrack is not connected yet, so a secure coding ticket gets an example number and does not reach the real Jira project SCP.';

const UNREACHABLE_PROTECH =
  'Beadle-ProTech cannot be reached, so no change can be raised, changed or reminded in ProTech, and the changes show what Beadle stored last, until it answers again.';

const UNREACHABLE_INTERNAL =
  'Beadle-Internal cannot be reached, so the Jira FixVersions, epics and stories cannot be read and no secure coding ticket can be created, until it answers again.';

function changeText(jiraDemo: boolean, proTechDemo: boolean): string | null {
  if (jiraDemo && proTechDemo) {
    return `Jira and ProTech are not connected yet, so the epics and stories are examples and no change reaches the real ProTech. ${DEMO_PROTECH}`;
  }
  if (jiraDemo) {
    return 'Jira is not connected yet, so the epics and stories are examples, not your real Jira data.';
  }
  return proTechDemo
    ? `ProTech is not connected yet, so no change reaches the real ProTech. ${DEMO_PROTECH}`
    : null;
}

/**
 * What demo mode means for the systems that are not connected while their service answers: a service that does not
 * answer runs no demo, see {@link unreachableText}.
 */
export function demoText({
  jiraConnected,
  proTechConnected,
  cyberTrackConnected,
  internalReachable,
  proTechReachable,
}: ChangeIntegrations): string | null {
  const internalAnswers = internalReachable !== false;
  const texts = [
    changeText(!jiraConnected && internalAnswers, !proTechConnected && proTechReachable !== false),
    cyberTrackConnected || !internalAnswers ? null : DEMO_CYBERTRACK,
  ].filter(Boolean);
  return texts.length ? texts.join(' ') : null;
}

/** What cannot be done while Beadle-ProTech or Beadle-Internal does not answer. */
export function unreachableText({
  internalReachable,
  proTechReachable,
}: ChangeIntegrations): string | null {
  const texts = [
    proTechReachable === false ? UNREACHABLE_PROTECH : null,
    internalReachable === false ? UNREACHABLE_INTERNAL : null,
  ].filter(Boolean);
  return texts.length ? texts.join(' ') : null;
}

@Component({
  selector: 'beadle-integration-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (unreachable(); as unreachable) {
      <div class="banner" role="alert">
        <span><strong>Not reachable.</strong> {{ unreachable }}</span>
      </div>
    }
    @if (demo(); as demo) {
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
  protected readonly demo = computed(() => {
    const integrations = this.integrations();
    return integrations && demoText(integrations);
  });
  protected readonly unreachable = computed(() => {
    const integrations = this.integrations();
    return integrations && unreachableText(integrations);
  });
}
