import { TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  async function render(data: ConfirmDialogData) {
    TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [{ provide: DIALOG_DATA, useValue: data }],
    });
    const fixture = TestBed.createComponent(ConfirmDialog);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('asks the question with a danger button for destructive actions', async () => {
    const page = await render({
      title: 'Delete the pipeline?',
      message: 'Its key history is deleted.',
      confirmLabel: 'Delete pipeline',
      danger: true,
    });

    expect(page.querySelector('h2')?.textContent).toBe('Delete the pipeline?');
    expect(page.querySelector('.message')?.textContent).toBe('Its key history is deleted.');
    const confirm = page.querySelectorAll('button')[1];
    expect(confirm.textContent?.trim()).toBe('Delete pipeline');
    expect(confirm.classList).toContain('btn-danger');
  });
});
