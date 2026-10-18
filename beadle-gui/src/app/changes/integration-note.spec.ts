import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { text } from '../testing/dom';
import { ChangeIntegrations } from './change-api';
import { IntegrationNote, demoText, unreachableText } from './integration-note';

const answering = (integrations: Partial<ChangeIntegrations>): ChangeIntegrations => ({
  jiraConnected: true,
  proTechConnected: true,
  cyberTrackConnected: true,
  internalReachable: true,
  proTechReachable: true,
  ...integrations,
});

describe('demoText', () => {
  const demoProTech =
    'A demo ProTech gives each change its number, moves it through the workflow automatically and applies an update a few seconds after it is published.';
  const demoCyberTrack =
    'CyberTrack is not connected yet, so a secure coding ticket gets an example number and does not reach the real Jira project SCP.';

  it('says what demo mode means for each missing connection', () => {
    expect(demoText(answering({ jiraConnected: false, proTechConnected: false }))).toBe(
      'Jira and ProTech are not connected yet, so the epics and stories are examples and no change reaches the real ProTech. ' +
        demoProTech,
    );
    expect(demoText(answering({ jiraConnected: false }))).toBe(
      'Jira is not connected yet, so the epics and stories are examples, not your real Jira data.',
    );
    expect(demoText(answering({ proTechConnected: false }))).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech}`,
    );
    expect(demoText(answering({}))).toBeNull();
  });

  it('adds CyberTrack when only the demo creates secure coding tickets', () => {
    expect(demoText(answering({ cyberTrackConnected: false }))).toBe(demoCyberTrack);
    expect(demoText(answering({ proTechConnected: false, cyberTrackConnected: false }))).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech} ${demoCyberTrack}`,
    );
  });

  it('calls nothing a demo whose service does not answer', () => {
    const nothingConnected = {
      jiraConnected: false,
      proTechConnected: false,
      cyberTrackConnected: false,
    };

    expect(demoText(answering({ ...nothingConnected, proTechReachable: false }))).toBe(
      `Jira is not connected yet, so the epics and stories are examples, not your real Jira data. ${demoCyberTrack}`,
    );
    expect(demoText(answering({ ...nothingConnected, internalReachable: false }))).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech}`,
    );
    expect(
      demoText(
        answering({ ...nothingConnected, internalReachable: false, proTechReachable: false }),
      ),
    ).toBeNull();
  });
});

describe('unreachableText', () => {
  const proTech =
    'Beadle-ProTech cannot be reached, so no change can be created, changed or reminded in ProTech, and the changes show what Beadle stored last, until it is reachable again.';
  const internal =
    'Beadle-Internal cannot be reached, so the Jira FixVersions, epics and stories cannot be read and no secure coding ticket can be created, until it is reachable again.';

  it('names each service that does not answer and what cannot be done meanwhile', () => {
    expect(unreachableText(answering({}))).toBeNull();
    expect(unreachableText(answering({ proTechConnected: false, proTechReachable: false }))).toBe(
      proTech,
    );
    expect(unreachableText(answering({ internalReachable: false }))).toBe(internal);
    expect(unreachableText(answering({ internalReachable: false, proTechReachable: false }))).toBe(
      `${proTech} ${internal}`,
    );
  });
});

describe('IntegrationNote', () => {
  async function shown(answer: (http: HttpTestingController) => void) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(IntegrationNote);
    fixture.detectChanges();
    answer(TestBed.inject(HttpTestingController));
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says what demo mode means for the connections it reads', async () => {
    const note = await shown((http) =>
      http.expectOne('/api/changes/integrations').flush(answering({ jiraConnected: false })),
    );
    expect(text(note.querySelector('.banner.info'))).toContain('Demo mode. Jira is not connected');
    expect(note.querySelector('.note')).toBeNull();
  });

  it('says in a muted note when the connections could not be loaded', async () => {
    const note = await shown((http) =>
      http
        .expectOne('/api/changes/integrations')
        .flush({ detail: 'Down' }, { status: 503, statusText: 'Unavailable' }),
    );
    expect(note.querySelector('.banner')).toBeNull();
    expect(text(note.querySelector('.note'))).toBe(
      'The connection status of Jira, ProTech and CyberTrack could not be loaded.',
    );
  });
});
