import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CodeDialog, CodeDialogData } from './code-dialog';
import { ConfirmDialog, ConfirmDialogData } from './confirm-dialog';

describe('ConfirmDialog', () => {
  async function render(data: ConfirmDialogData) {
    TestBed.configureTestingModule({
      imports: [ConfirmDialog],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }],
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
    expect(confirm.classList).toContain('danger');
  });
});

describe('CodeDialog', () => {
  let fixture: ComponentFixture<CodeDialog>;

  async function render(data: CodeDialogData) {
    TestBed.configureTestingModule({
      imports: [CodeDialog],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }],
    });
    fixture = TestBed.createComponent(CodeDialog);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const button = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('button')].find(
      (element) => element.textContent?.trim() === label,
    );

  it('shows the code and downloads it under its file name', async () => {
    await render({
      title: 'config.yaml',
      subtitle: 'Every service',
      code: 'projects: {}',
      fileName: 'cert.yaml',
    });
    const createObjectURL = vi.fn(() => 'blob:config');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    expect(page().querySelector('.subtitle')?.textContent).toBe('Every service');
    expect(page().querySelector('pre')?.textContent).toBe('projects: {}');
    button('Download')!.click();

    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:config');
    vi.unstubAllGlobals();
    click.mockRestore();
  });

  it('copies the code and says so, without a download for code without a file name', async () => {
    await render({ title: 'Jenkinsfile', code: "@Library('dso') _" });

    expect(page().querySelector('.subtitle')).toBeNull();
    expect(button('Download')).toBeUndefined();
    button('Copy')!.click();
    await fixture.whenStable();

    expect(document.querySelector('mat-snack-bar-container')?.textContent).toContain(
      'Copied to the clipboard',
    );
  });
});
