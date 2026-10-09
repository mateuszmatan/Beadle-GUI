import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChangeScheduleFields } from './change-schedule-fields';
import { ScheduleForm } from './change-schedule-model';
import { Fact, SECTIONS, Section } from './change-sections';
import { ChangeTemplateSection } from './change-template-section';
import { TemplateForm } from './change-template-model';

@Component({
  selector: 'dso-change-template-form',
  imports: [ChangeScheduleFields, ChangeTemplateSection],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @for (section of sections(); track section.key) {
      <section class="card template-card" [attr.aria-label]="section.title">
        <header>
          <h3>{{ section.title }}</h3>
          <p>{{ lead(section) }}</p>
        </header>
        @if (section.key === 'schedule' && schedule(); as changeSchedule) {
          <dso-change-schedule [schedule]="changeSchedule" [downtime]="form().controls.downtime" />
        } @else {
          <dso-template-section
            [form]="form()"
            [section]="section.key"
            [facts]="facts()"
            [admin]="admin()"
          />
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .template-card {
      padding: 8px 14px 8px;

      > header {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: 2px 10px;

        h3 {
          margin: 0;
          font-size: 14px;
          font-weight: 600;
        }

        p {
          margin: 0;
          font-size: 11.5px;
          color: var(--dso-muted);
        }
      }
    }
  `,
})
export class ChangeTemplateForm {
  readonly form = input.required<TemplateForm>();
  readonly admin = input(false);
  readonly facts = input<readonly Fact[]>([]);
  readonly schedule = input<ScheduleForm | null>(null);

  protected readonly sections = computed(() =>
    SECTIONS.filter((section) => this.admin() || section.key !== 'jira'),
  );

  protected lead(section: Section): string {
    return (this.admin() && section.adminLead) || section.lead;
  }
}
