import { ComponentFixture, TestBed } from '@angular/core/testing';
import { changeTemplate } from '../testing/change-fixtures';
import { buttonOf, checkboxOf, fieldOf, inputOf, text } from '../testing/dom';
import { ChangeTemplateForm, templateLabel } from './change-template-form';
import { TemplateForm, templateForm } from './change-template-model';

describe('ChangeTemplateForm', () => {
  let fixture: ComponentFixture<ChangeTemplateForm>;
  let form: TemplateForm;

  const page = () => fixture.nativeElement as HTMLElement;
  const component = () => fixture.componentInstance;

  async function render(template = changeTemplate(), jiraProject = true) {
    form = templateForm(template);
    fixture = TestBed.createComponent(ChangeTemplateForm);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('jiraProject', jiraProject);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('shows every ServiceNow field in its card', async () => {
    await render();

    expect([...page().querySelectorAll('.template-card h3')].map(text)).toEqual([
      'Change',
      'Approvers',
      'Schedule defaults',
      'Planning',
      'Privileged access',
      'Risk assessment',
    ]);
    expect(inputOf(page(), 'Jira project').value).toBe('CERT');
    expect(inputOf(page(), 'Affected CI').value).toBe('CertScanner');
    expect(inputOf(page(), 'L1 manager').value).toBe('Olivia Bennett');
    expect(inputOf(page(), 'Installation start').value).toBe('18:00');
    expect(inputOf(page(), 'Installation hours').value).toBe('2');
    expect(inputOf(page(), 'First use plan').value).toBe(
      'The business owner confirms the first use.',
    );
    expect(inputOf(page(), 'BBH users').value).toBe('10');
    expect(inputOf(page(), 'Platform status').value).toBe('Existing platform');
    expect(inputOf(page(), 'Backout testing & duration').value).toBe(
      'Tested on QC, about 15 minutes',
    );
    expect(fieldOf(page(), 'User')).toBeNull();
  });

  it('leaves out the Jira project when the page asks for it elsewhere', async () => {
    await render(changeTemplate(), false);

    expect(fieldOf(page(), 'Jira project')).toBeNull();
    expect(inputOf(page(), 'Assignment group').value).toBe('Technology Architecture');
  });

  it('adds up to seven privileged users and removes them again', async () => {
    await render();

    checkboxOf(page(), 'Privileged access needed').click();
    fixture.detectChanges();
    expect(page().querySelectorAll('.user-row')).toHaveLength(1);
    inputOf(page(), 'User').value = 'Jane Smith';
    inputOf(page(), 'User').dispatchEvent(new Event('input'));
    expect(form.controls.privilegedAccess.value.users).toEqual([
      { user: 'Jane Smith', account: '' },
    ]);

    for (let i = 0; i < 6; i++) {
      buttonOf(page(), 'Add user').click();
    }
    fixture.detectChanges();
    expect(page().querySelectorAll('.user-row')).toHaveLength(7);
    expect(buttonOf(page(), 'Add user').disabled).toBe(true);
    expect(text(page().querySelector('.user-actions'))).toContain('7 of 7');

    for (let i = 7; i > 0; i--) {
      buttonOf(page(), `Remove user ${i}`).click();
    }
    fixture.detectChanges();
    expect(page().querySelectorAll('.user-row')).toHaveLength(0);
    expect(text(page().querySelector('.template-card [role="alert"]'))).toBe(
      'Add at least one user',
    );
  });

  it('suggests the usual risk values and allows any other text', async () => {
    await render();
    const suggestions = (key: 'businessImpact' | 'platformStatus', value: string) =>
      component()['suggestions'](key, value);

    expect(suggestions('businessImpact', '')).toEqual(['Low', 'Medium', 'High']);
    expect(suggestions('businessImpact', 'hi')).toEqual(['High']);
    expect(suggestions('businessImpact', 'low')).toEqual(['Low', 'Medium', 'High']);
    expect(suggestions('platformStatus', 'up')).toEqual(['Platform upgrade']);
    expect(suggestions('platformStatus', 'Decommissioned')).toEqual([]);

    const input = inputOf(page(), 'Change complexity');
    input.value = 'Very high';
    input.dispatchEvent(new Event('input'));
    expect(form.controls.riskAssessment.controls.changeComplexity.value).toBe('Very high');
  });

  it('shows why a value is refused', async () => {
    await render(changeTemplate({ jiraProjectKey: 'CERT-1' }));
    form.controls.timing.controls.installationStart.setValue('');
    form.markAllAsTouched();
    fixture.detectChanges();

    expect(text(fieldOf(page(), 'Jira project')?.querySelector('mat-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(fieldOf(page(), 'Installation start')?.querySelector('mat-error'))).toBe(
      'Required',
    );
  });

  it('names the fields by their path', () => {
    expect(templateLabel('configurationItem')).toBe('Affected CI');
    expect(templateLabel('approvers.businessApprover')).toBe('Business approver');
    expect(templateLabel('timing.installationStart')).toBe('Installation start');
    expect(templateLabel('privilegedAccess.users')).toBe('Privileged users');
    expect(templateLabel('privilegedAccess.users[2].account')).toBe('Privileged account');
    expect(templateLabel('riskAssessment.platformStatus')).toBe('Platform status');
    expect(templateLabel('unknown')).toBeNull();
  });
});
