import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { fieldOf, inputOf, text } from '../testing/dom';
import { ChangeScheduleFields } from './change-schedule-fields';
import { TIME_ZONE_NOTE } from './change-model';
import { ScheduleForm, scheduleForm } from './change-schedule-model';

describe('ChangeScheduleFields', () => {
  let fixture: ComponentFixture<ChangeScheduleFields>;
  let schedule: ScheduleForm;
  let downtime: FormControl<boolean>;

  const page = () => fixture.nativeElement as HTMLElement;
  const labels = () => [...page().querySelectorAll('dso-form-field dso-label')].map(text);
  const hint = (label: string) => text(fieldOf(page(), label)?.querySelector('dso-hint'));

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    downtime = new FormGroup({ downtime: new FormControl(false, { nonNullable: true }) }).controls
      .downtime;
    schedule = scheduleForm(downtime);
    schedule.setValue({
      installationStart: '2099-10-20T18:00',
      installationHours: 2,
      validationStart: '2099-10-20T20:00',
      validationHours: 1,
      firstUsage: '2099-10-21T08:00',
      downtimeStart: '2099-10-20T18:00',
      downtimeHours: 2,
    });
    fixture = TestBed.createComponent(ChangeScheduleFields);
    fixture.componentRef.setInput('schedule', schedule);
    fixture.componentRef.setInput('downtime', downtime);
    await settle();
  });

  it('shows each window as a date and time with its hours and when it ends', async () => {
    expect(labels()).toEqual([
      'Installation start',
      'Installation hours',
      'Post-install validation start',
      'Validation hours',
      'First use',
      'Downtime',
    ]);
    expect(inputOf(page(), 'Installation start').type).toBe('datetime-local');
    expect(inputOf(page(), 'Installation start').value).toBe('2099-10-20T18:00');
    expect(inputOf(page(), 'Installation hours').step).toBe('0.5');
    expect(hint('Installation hours')).toBe('until Tue, 20 Oct 2099, 20:00');
    expect(hint('Validation hours')).toBe('until Tue, 20 Oct 2099, 21:00');
    expect(text(page().querySelector('.note'))).toBe(TIME_ZONE_NOTE);

    const hours = inputOf(page(), 'Installation hours');
    hours.value = '2.5';
    hours.dispatchEvent(new Event('input'));
    await settle();
    expect(hint('Installation hours')).toBe('until Tue, 20 Oct 2099, 20:30');
    expect(inputOf(page(), 'Post-install validation start').value).toBe('2099-10-20T20:30');
  });

  it('asks for the downtime window when there is a downtime', async () => {
    downtime.setValue(true);
    await settle();

    expect(labels().slice(-3)).toEqual(['Downtime', 'Downtime start', 'Downtime hours']);
    expect(inputOf(page(), 'Downtime start').value).toBe('2099-10-20T18:00');
    expect(hint('Downtime hours')).toBe('until Tue, 20 Oct 2099, 20:00');

    downtime.setValue(false);
    await settle();
    expect(fieldOf(page(), 'Downtime start')).toBeNull();
  });

  it('says what is wrong with the times once they are touched', async () => {
    schedule.controls.firstUsage.setValue('2099-10-20T20:30');
    await settle();
    expect(page().querySelector('[role="alert"]')).toBeNull();

    schedule.markAllAsTouched();
    await settle();
    expect(text(page().querySelector('[role="alert"]'))).toBe(
      'The first use cannot be before the validation ends',
    );
  });
});
