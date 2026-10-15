import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Field, Fields, count, formRevision, line } from '@common/shared/fields';
import { errorText } from '@common/shared/form-errors';
import { momentText } from './change-model';
import { MAX_HOURS, ScheduleForm, windowsOf } from './change-schedule-model';
import { DOWNTIME_FIELDS } from './change-sections';
import { TIME_ZONE_NOTE } from '@common/shared/formatting';

const start = (key: string, label: string): Field =>
  line(key, label, '', 6, { type: 'datetime-local' });

const hours = (key: string, label: string, end: Date | null): Field =>
  count(key, label, '', 6, {
    min: 0,
    max: MAX_HOURS,
    step: 0.5,
    hint: end ? `until ${momentText(end)}` : '',
  });

@Component({
  selector: 'dso-change-schedule',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let s = schedule();
    <div class="form-fields">
      <dso-fields [group]="s" [fields]="fields().windows" />
      <dso-fields [group]="downtime().parent!" [fields]="downtimeChoice" />
      @if (downtime().value) {
        <dso-fields [group]="s" [fields]="fields().downtime" />
      }
    </div>
    @if (s.errors && s.touched) {
      <p class="choice-error" role="alert">{{ errorText(s) }}</p>
    }
    <p class="note">{{ timeZoneNote }}</p>
  `,
  styles: `
    :host {
      display: block;
    }

    .note {
      margin: 4px 0 0;
      font-size: 12px;
      color: var(--dso-muted);
    }
  `,
})
export class ChangeScheduleFields {
  readonly schedule = input.required<ScheduleForm>();
  readonly downtime = input.required<FormControl<boolean>>();

  private readonly scheduleRevision = formRevision(() => this.schedule());
  private readonly downtimeRevision = formRevision(() => this.downtime());

  protected readonly downtimeChoice = DOWNTIME_FIELDS;
  protected readonly timeZoneNote = TIME_ZONE_NOTE;
  protected readonly errorText = errorText;

  protected readonly fields = computed(() => {
    this.scheduleRevision();
    this.downtimeRevision();
    const windows = windowsOf(this.schedule().getRawValue(), this.downtime().value);
    return {
      windows: [
        start('installationStart', 'Installation start'),
        hours('installationHours', 'Installation hours', windows.installationEnd),
        start('validationStart', 'Post-install validation start'),
        hours('validationHours', 'Validation hours', windows.validationEnd),
        start('firstUsage', 'First use'),
      ],
      downtime: [
        start('downtimeStart', 'Downtime start'),
        hours('downtimeHours', 'Downtime hours', windows.downtimeEnd),
      ],
    };
  });
}
