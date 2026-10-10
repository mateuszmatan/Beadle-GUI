import { demoText } from './integration-note';

describe('demoText', () => {
  it('says what demo mode means for each missing connection', () => {
    const demoProTech =
      'A demo ProTech gives each change its number, moves it through the workflow on its own and applies an update a few seconds after it is published.';

    expect(demoText({ jiraConnected: false, serviceNowConnected: false })).toBe(
      'Jira and ProTech are not connected yet, so the epics and stories are examples and no change reaches the real ProTech. ' +
        demoProTech,
    );
    expect(demoText({ jiraConnected: false, serviceNowConnected: true })).toBe(
      'Jira is not connected yet, so the epics and stories are examples, not your real Jira data.',
    );
    expect(demoText({ jiraConnected: true, serviceNowConnected: false })).toBe(
      `ProTech is not connected yet, so no change reaches the real ProTech. ${demoProTech}`,
    );
    expect(demoText({ jiraConnected: true, serviceNowConnected: true })).toBeNull();
  });
});
