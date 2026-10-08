import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { changeOptions, changeSchedule, changeTemplate } from '../testing/change-fixtures';
import { fieldOf, inputOf, text } from '../testing/dom';
import { scheduleForm } from './change-schedule-model';
import { ChangeTemplateForm } from './change-template-form';
import { TemplateForm, templateForm } from './change-template-model';

describe('ChangeTemplateForm', () => {
  let fixture: ComponentFixture<ChangeTemplateForm>;
  let http: HttpTestingController;
  let form: TemplateForm;

  const page = () => fixture.nativeElement as HTMLElement;
  const titles = () => [...page().querySelectorAll('.template-card h3')].map(text);
  const leadOf = (title: string) =>
    text(page().querySelector(`.template-card[aria-label="${title}"] header p`));

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChangeTemplateForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function render(
    inputs: (form: TemplateForm) => Record<string, unknown>,
    template = changeTemplate(),
  ) {
    form = templateForm(template);
    fixture = TestBed.createComponent(ChangeTemplateForm);
    fixture.componentRef.setInput('form', form);
    Object.entries(inputs(form)).forEach(([name, value]) =>
      fixture.componentRef.setInput(name, value),
    );
    fixture.detectChanges();
    await settle();
    http.expectOne('/api/changes/options').flush(changeOptions());
    await settle();
    await settle();
  }

  it('shows the admin every section of the wizard with the Jira project and the schedule defaults', async () => {
    await render(() => ({ admin: true }));

    expect(titles()).toEqual([
      'Generic request data',
      'Jira',
      'Approval and Notification',
      'Schedule',
      'Planning',
      'Privileged access',
      'Risk assessment',
      'Secure coding',
    ]);
    expect(leadOf('Jira')).toBe('The Jira project the epics and stories of a release come from.');
    expect(leadOf('Schedule')).toBe(
      'A new change starts on the release date of its FixVersion at this time, in local time.',
    );
    expect(inputOf(page(), 'Jira project').value).toBe('CERT');
    expect(inputOf(page(), 'Installation start').value).toBe('18:00');
    expect(fieldOf(page(), 'Change number')).toBeNull();
    expect(fieldOf(page(), 'Installation hours')).not.toBeNull();
  });

  it('shows the edit page the facts of the change and its schedule instead of the defaults', async () => {
    await render((template) => ({
      facts: [{ label: 'Change number', value: 'CHG0012345' }],
      schedule: scheduleForm(template.controls.downtime, changeSchedule()),
    }));

    expect(titles()).not.toContain('Jira');
    expect(titles()).toHaveLength(7);
    expect(leadOf('Schedule')).toBe(
      'When the change is installed, validated and first used, and whether it brings downtime.',
    );
    expect(inputOf(page(), 'Change number').value).toBe('CHG0012345');
    expect(fieldOf(page(), 'Jira project')).toBeNull();
    expect(inputOf(page(), 'Installation start').type).toBe('datetime-local');
    expect(fieldOf(page(), 'Post-install validation start')).not.toBeNull();
  });

  it('shows why a value is refused', async () => {
    await render(() => ({ admin: true }), changeTemplate({ jiraProjectKey: 'CERT-1' }));
    form.controls.timing.controls.installationStart.setValue('');
    form.markAllAsTouched();
    await settle();

    expect(text(fieldOf(page(), 'Jira project')?.querySelector('mat-error'))).toBe(
      '1 to 10 letters, digits or _, starting with a letter',
    );
    expect(text(fieldOf(page(), 'Installation start')?.querySelector('mat-error'))).toBe(
      'Required',
    );
  });
});
