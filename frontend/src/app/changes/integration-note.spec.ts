import { demoText } from './integration-note';

describe('demoText', () => {
  const demoProTech =
    'A demo ProTech gives each change its number, moves it through the workflow on its own and applies an update a few seconds after it is published.';
  const demoCyberTrack =
    'CyberTrack is not connected yet, so a secure coding ticket gets an example number and does not reach the real Jira project SCP.';

  it('says what demo mode means for each missing connection', () => {
    const connected = { cyberTrackConnected: true };

    expect(demoText({ ...connected, jiraConnected: false, serviceNowConnected: false })).toBe(
      'Jira and ProTech are not connected yet, so the epics and stories are examples and no change reaches the real ProTech. ' +
        demoProTech,
    );
    expect(demoText({ ...connected, jiraConnected: false, serviceNowConnected: true })).toBe(
      'Jira is not connected yet, so the epics and stories are examples, not your real Jira data.',
    );
    expect(demoText({ ...connected, jiraConnected: true, serviceNowConnected: false })).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech}`,
    );
    expect(demoText({ ...connected, jiraConnected: true, serviceNowConnected: true })).toBeNull();
  });

  it('adds CyberTrack when only the demo creates secure coding tickets', () => {
    expect(
      demoText({ jiraConnected: true, serviceNowConnected: true, cyberTrackConnected: false }),
    ).toBe(demoCyberTrack);
    expect(
      demoText({ jiraConnected: true, serviceNowConnected: false, cyberTrackConnected: false }),
    ).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech} ${demoCyberTrack}`,
    );
  });
});
