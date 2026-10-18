import { FormArray, FormGroup, Validators } from '@angular/forms';
import { fits } from '../changes/change-model';
import {
  addItem,
  applyFieldProblems,
  applyProblemsAt,
  commaItems,
  controlAt,
  filled,
  integer,
  max,
  optional,
  removeItem,
  setEnabled,
  text,
} from './form-controls';

describe('text helpers', () => {
  it('trims optional values and drops blank ones', () => {
    expect(optional('  value ')).toBe('value');
    expect(optional('   ')).toBeNull();
    expect(optional(null)).toBeNull();
  });

  it('splits items at commas and new lines and drops blank and repeated ones', () => {
    expect(commaItems(' DV, RD\n\nDV ,')).toEqual(['DV', 'RD']);
    expect(commaItems(null)).toEqual([]);
  });
});

describe('validators', () => {
  it('limits text in characters and in the UTF-8 bytes the Oracle column holds', () => {
    const control = text('', max(5));
    control.setValue('abcde');
    expect(control.valid).toBe(true);
    control.setValue('abcdef');
    expect(control.errors).toEqual({ maxlength: { requiredLength: 5, actualLength: 6 } });
    control.setValue('łódź');
    expect(control.errors).toEqual({ bytes: { max: 5 } });
    control.setValue(null as unknown as string);
    expect(control.valid).toBe(true);
  });

  it('counts the UTF-8 bytes of the trimmed text against the ProTech field length', () => {
    expect(text(' abcd ', fits(4)).valid).toBe(true);
    expect(text('abcde', fits(4)).errors).toEqual({ bytes: { max: 4 } });
    expect(text('ąbcd', fits(4)).hasError('bytes')).toBe(true);
  });

  it('accepts only whole numbers within the range', () => {
    expect(integer(1.5, 0, 10).hasError('integer')).toBe(true);
    expect(integer(11, 0, 10).hasError('max')).toBe(true);
    expect(integer(null, 0, 10).valid).toBe(true);
    expect(integer(null, 0, 10, Validators.required).hasError('required')).toBe(true);
  });

  it('rejects a value of spaces only where a value is required', () => {
    expect(text('   ', filled).hasError('required')).toBe(true);
    expect(text(' x ', filled).valid).toBe(true);
    expect(integer(0, 0, 10, filled).valid).toBe(true);
  });

  it('enables and disables a control only when it changes', () => {
    const control = text('x');
    setEnabled(control, false);
    expect(control.disabled).toBe(true);
    setEnabled(control, false);
    expect(control.disabled).toBe(true);
    setEnabled(control, true);
    expect(control.enabled).toBe(true);
  });
});

describe('field problems', () => {
  function form() {
    return new FormGroup({
      timing: new FormGroup({
        installationStart: text('18:00'),
        installationHours: integer(null, 1, 72),
      }),
      approvals: new FormGroup({ L1: new FormGroup({ reminders: integer(0, 0, 10) }) }),
      tasks: new FormArray([new FormGroup({ shortDescription: text('a') })]),
    });
  }

  it('keeps the error of a field when its field is shown again', () => {
    const f = form();
    applyFieldProblems(f, [
      { field: 'timing.installationHours', message: 'is required with an installation start' },
    ]);
    const hours = f.controls.timing.controls.installationHours;

    hours.updateValueAndValidity();

    expect(hours.errors).toEqual({ server: 'is required with an installation start' });
    expect(hours.touched).toBe(true);
    expect(f.invalid).toBe(true);
  });

  it('drops the error once its value changes', () => {
    const f = form();
    applyFieldProblems(f, [{ field: 'timing.installationHours', message: 'is required' }]);
    const hours = f.controls.timing.controls.installationHours;

    hours.setValue(2);

    expect(hours.valid).toBe(true);
    expect(f.valid).toBe(true);
  });

  it('joins several problems of one field and replaces those of an earlier answer', () => {
    const f = form();
    const reminders = f.controls.approvals.controls.L1.controls.reminders;
    applyFieldProblems(f, [
      { field: 'approvals[L1].reminders', message: 'is too high' },
      { field: 'approvals[L1].reminders', message: 'is not allowed' },
    ]);
    expect(reminders.errors).toEqual({ server: 'is too high; is not allowed' });

    applyFieldProblems(f, [{ field: 'approvals[L1].reminders', message: 'is stale' }]);
    expect(reminders.errors).toEqual({ server: 'is stale' });
  });

  it('marks the problems under a prefix on a part of the form and keeps the others', () => {
    const f = form();

    expect(
      applyProblemsAt(f.controls.tasks, 'tasks', [
        { field: 'tasks[0].shortDescription', message: 'is used twice' },
        { field: 'tasks', message: 'is too long' },
        { field: 'timing.installationHours', message: 'is required' },
      ]),
    ).toEqual([
      { field: 'timing.installationHours', message: 'is required' },
      { field: 'tasks', message: 'is too long' },
    ]);
    expect(f.controls.tasks.at(0).controls.shortDescription.errors).toEqual({
      server: 'is used twice',
    });
    expect(controlAt(f.controls.tasks, '[0].shortDescription')).toBe(
      f.controls.tasks.at(0).controls.shortDescription,
    );
  });
});

describe('lists', () => {
  it('adds and removes items and marks the list as changed', () => {
    const list = new FormArray([text('a'), text('b')]);
    addItem(list, text('c'), 1);
    expect(list.value).toEqual(['a', 'c', 'b']);
    expect(list.dirty).toBe(true);

    list.markAsPristine();
    removeItem(list, 1);
    expect(list.value).toEqual(['a', 'b']);
    expect(list.dirty).toBe(true);
  });
});
