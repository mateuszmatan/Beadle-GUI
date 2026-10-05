import { FormArray, FormGroup, Validators } from '@angular/forms';
import {
  applyFieldProblems,
  controlAt,
  eachItem,
  fitsColumn,
  flag,
  integer,
  joinLines,
  joinWords,
  lines,
  maxLines,
  maxWords,
  optional,
  requireWhile,
  requiredRule,
  requiredWhen,
  revalidateAll,
  setEnabled,
  text,
  words,
} from './form-controls';

describe('text helpers', () => {
  it('trims optional values and drops blank ones', () => {
    expect(optional('  value ')).toBe('value');
    expect(optional('   ')).toBeNull();
    expect(optional(null)).toBeNull();
  });

  it('splits lines and words, dropping repeats unless asked to keep them', () => {
    expect(lines(' a \n\nb\na ')).toEqual(['a', 'b']);
    expect(lines('a\na', false)).toEqual(['a', 'a']);
    expect(words('clean, build  build')).toEqual(['clean', 'build']);
    expect(words('clean build build', false)).toEqual(['clean', 'build', 'build']);
    expect(joinLines(['a', 'b'])).toBe('a\nb');
    expect(joinWords(['DV', 'RD'], ', ')).toBe('DV, RD');
    expect(joinWords(null)).toBe('');
  });
});

describe('validators', () => {
  it('limits the number of lines and words', () => {
    expect(text('a\nb\nc', maxLines(2)).errors).toEqual({ maxLines: { max: 2 } });
    expect(text('a\na\na', maxLines(2)).valid).toBe(true);
    expect(text('a b c', maxWords(2)).errors).toEqual({ maxItems: { max: 2 } });
  });

  it('names the first item that does not match', () => {
    const control = text('A=1\nnope\nalso no', eachItem(lines, /=/, 'Write NAME=value'));
    expect(control.errors).toEqual({ item: { value: 'nope', message: 'Write NAME=value' } });
  });

  it('requires a value with a message of its own, for text and lists alike', () => {
    expect(text('', requiredRule('Name the module')).errors).toEqual({ rule: 'Name the module' });
    expect(text('core', requiredRule('Name the module')).valid).toBe(true);
    expect(new FormArray([], requiredRule('Add a component')).errors).toEqual({
      rule: 'Add a component',
    });
  });

  it('checks that the joined items fit their column, counting UTF-8 bytes', () => {
    const control = text('', fitsColumn(lines, '\n', 10));
    control.setValue('abcd\nefgh');
    expect(control.valid).toBe(true);
    control.setValue('abcd\nefghij');
    expect(control.errors).toEqual({ columnLength: { max: 10 } });
    control.setValue('ąbcd\nefghi');
    expect(control.hasError('columnLength')).toBe(true);
    expect(text('a, b, a', fitsColumn(words, ',', 3)).valid).toBe(true);
  });

  it('accepts only whole numbers within the range', () => {
    expect(integer(1.5, 0, 10).hasError('integer')).toBe(true);
    expect(integer(11, 0, 10).hasError('max')).toBe(true);
    expect(integer(null, 0, 10).valid).toBe(true);
    expect(integer(null, 0, 10, Validators.required).hasError('required')).toBe(true);
  });

  it('requires a value while the siblings meet a condition, with a message of its own', () => {
    const group = new FormGroup({
      remote: flag(false),
      host: text(
        '',
        requiredWhen((siblings) => siblings['remote'] === true, 'Name the host'),
      ),
    });
    expect(group.controls.host.valid).toBe(true);

    group.controls.remote.setValue(true);
    group.controls.host.updateValueAndValidity();

    expect(group.controls.host.errors).toEqual({ rule: 'Name the host' });
  });

  it('adds and removes the required validator as the condition changes', () => {
    const group = new FormGroup({ host: text(''), port: integer(null, 1, 65535) });
    const { host, port } = group.controls;
    requireWhile(port, () => !!optional(host.value), host);
    expect(port.hasValidator(Validators.required)).toBe(false);

    host.setValue('proxy.bbh.com');
    expect(port.hasValidator(Validators.required)).toBe(true);
    expect(port.hasError('required')).toBe(true);

    host.setValue('');
    expect(port.valid).toBe(true);
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
      platform: new FormGroup({ proxyHost: text('proxy'), proxyPort: integer(null, 1, 65535) }),
      limits: new FormGroup({ SAST: new FormGroup({ maxHigh: integer(0, 0, 10) }) }),
      jobs: new FormArray([new FormGroup({ job: text('a') })]),
    });
  }

  it('finds controls by list index and by map key', () => {
    const f = form();
    expect(controlAt(f, 'limits[SAST].maxHigh')).toBe(
      f.controls.limits.controls.SAST.controls.maxHigh,
    );
    expect(controlAt(f, 'jobs[0].job')).toBe(f.controls.jobs.at(0).controls.job);
    expect(controlAt(f, 'jobs[3].job')).toBe(f.controls.jobs);
    expect(controlAt(f, 'other')).toBeNull();
  });

  it('keeps the error of a field when its field is shown again', () => {
    const f = form();
    applyFieldProblems(f, [
      { field: 'platform.proxyPort', message: 'is required with a proxy host' },
    ]);
    const port = f.controls.platform.controls.proxyPort;

    port.updateValueAndValidity();

    expect(port.errors).toEqual({ server: 'is required with a proxy host' });
    expect(port.touched).toBe(true);
    expect(f.invalid).toBe(true);
  });

  it('drops the error once its value changes', () => {
    const f = form();
    applyFieldProblems(f, [{ field: 'platform.proxyPort', message: 'is required' }]);
    const port = f.controls.platform.controls.proxyPort;

    port.setValue(8080);

    expect(port.valid).toBe(true);
    expect(f.valid).toBe(true);
  });

  it('drops the error when a neighbouring value changed by the next check', () => {
    const f = form();
    applyFieldProblems(f, [{ field: 'platform.proxyPort', message: 'is required' }]);
    const { proxyHost, proxyPort } = f.controls.platform.controls;

    proxyHost.setValue('');
    expect(proxyPort.hasError('server')).toBe(true);

    revalidateAll(f);
    expect(proxyPort.valid).toBe(true);
    expect(f.valid).toBe(true);
  });

  it('joins several problems of one field and replaces those of an earlier answer', () => {
    const f = form();
    const maxHigh = f.controls.limits.controls.SAST.controls.maxHigh;
    applyFieldProblems(f, [
      { field: 'limits[SAST].maxHigh', message: 'is too high' },
      { field: 'limits[SAST].maxHigh', message: 'is not allowed' },
    ]);
    expect(maxHigh.errors).toEqual({ server: 'is too high; is not allowed' });

    applyFieldProblems(f, [{ field: 'limits[SAST].maxHigh', message: 'is stale' }]);
    expect(maxHigh.errors).toEqual({ server: 'is stale' });
  });
});
