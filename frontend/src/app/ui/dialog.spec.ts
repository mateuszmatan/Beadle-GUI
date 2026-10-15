import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DIALOG, provideDialogs } from './dialog';

@Component({
  imports: [DIALOG],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h2 dsoDialogTitle>Delete the pipeline?</h2>
    <button [dsoDialogClose]="true">Delete pipeline</button>`,
})
class Question {}

describe('provideDialogs', () => {
  let ref: DialogRef<boolean, Question>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideDialogs()] });
    ref = TestBed.inject(Dialog).open<boolean, unknown, Question>(Question);
    TestBed.tick();
    await Promise.resolve();
  });

  afterEach(() => TestBed.inject(Dialog).closeAll());

  const pane = () => ref.overlayRef.overlayElement;

  it('opens modal dialogs over a backdrop that close when the reader navigates away', () => {
    expect(ref.config.hasBackdrop).toBe(true);
    expect(ref.config.closeOnNavigation).toBe(true);
    expect(ref.config.panelClass).toBe('dso-dialog');
    expect(ref.overlayRef.backdropElement).not.toBeNull();
    expect(pane().querySelector('[role="dialog"]')).not.toBeNull();
  });

  it('closes with the result of the close button', async () => {
    const closed = new Promise((resolve) => ref.closed.subscribe(resolve));

    pane().querySelector('button')?.click();

    expect(await closed).toBe(true);
  });
});
