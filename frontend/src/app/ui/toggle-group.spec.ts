import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { buttonOf } from '../testing/dom';
import { TOGGLES } from './toggle-group';

@Component({
  imports: [ReactiveFormsModule, TOGGLES],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dso-toggle-group [formControl]="target" aria-label="Target">
      <button [dsoToggle]="'VM'">Virtual machine</button>
      <button [dsoToggle]="'OPENSHIFT'">OpenShift</button>
      <button [dsoToggle]="'NONE'">None</button>
    </dso-toggle-group>
  `,
})
class Host {
  readonly target = new FormControl('VM');
}

describe('DsoToggleGroup', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const target = () => fixture.componentInstance.target;

  function press(label: string, key: string) {
    buttonOf(page(), label).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
  }

  it('leaves the form untouched when the chosen option is clicked again', async () => {
    buttonOf(page(), 'Virtual machine').click();
    await fixture.whenStable();

    expect(target().value).toBe('VM');
    expect(target().dirty).toBe(false);

    buttonOf(page(), 'OpenShift').click();
    await fixture.whenStable();

    expect(target().value).toBe('OPENSHIFT');
    expect(target().dirty).toBe(true);
    expect(buttonOf(page(), 'OpenShift').getAttribute('aria-checked')).toBe('true');
  });

  it('moves the choice and the focus with the arrow keys and wraps around', async () => {
    press('Virtual machine', 'ArrowRight');
    await fixture.whenStable();

    expect(target().value).toBe('OPENSHIFT');
    expect(document.activeElement).toBe(buttonOf(page(), 'OpenShift'));

    press('OpenShift', 'ArrowDown');
    press('None', 'ArrowRight');
    await fixture.whenStable();

    expect(target().value).toBe('VM');

    press('Virtual machine', 'ArrowLeft');
    await fixture.whenStable();

    expect(target().value).toBe('NONE');

    press('None', 'Enter');
    await fixture.whenStable();

    expect(target().value).toBe('NONE');
  });
});
