import { ComponentFixture, TestBed } from '@angular/core/testing';
import { changeTask, taskText } from '../testing/change-fixtures';
import { buttonOf, text } from '../testing/dom';
import { ChangeTasksForm } from './change-tasks-form';
import { TasksForm, tasksForm } from './change-tasks-model';

describe('ChangeTasksForm', () => {
  let fixture: ComponentFixture<ChangeTasksForm>;
  let tasks: TasksForm;

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () => [...page().querySelectorAll('.task-row')];

  async function render(form: TasksForm, numbers = false) {
    tasks = form;
    fixture = TestBed.createComponent(ChangeTasksForm);
    fixture.componentRef.setInput('tasks', tasks);
    fixture.componentRef.setInput('numbers', numbers);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('edits the texts of the tasks, adds one and removes one', async () => {
    await render(tasksForm([taskText('Deploy it', 'Deploy the release.')]));

    expect(rows()).toHaveLength(1);
    expect(page().querySelector('.number')).toBeNull();
    expect(page().querySelector<HTMLInputElement>('.task-row input')!.value).toBe('Deploy it');
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(true);
    expect(text(page().querySelector('.task-actions .muted'))).toBe('1 change task');

    buttonOf(page(), 'Add a change task').click();
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
    expect(text(page().querySelector('.task-actions .muted'))).toBe('2 change tasks');
    const input = rows()[1].querySelector<HTMLInputElement>('input')!;
    input.value = 'Validate it';
    input.dispatchEvent(new Event('input'));
    expect(tasks.at(1).controls.shortDescription.value).toBe('Validate it');

    buttonOf(page(), 'Remove change task 1').click();
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(tasks.at(0).controls.shortDescription.value).toBe('Validate it');
  });

  it('shows the CTASK numbers and states and keeps a closed task as it is', async () => {
    await render(
      tasksForm([
        changeTask({ state: 'CLOSED' }),
        changeTask({ number: 'CTASK0020002', state: 'WORK_IN_PROGRESS' }),
        changeTask({ number: null }),
      ]),
      true,
    );

    expect(rows().map((row) => text(row.querySelector('.task-head')))).toEqual([
      '1CTASK0020001ClosedClosed in ProTech, so it stays as it is Remove',
      '2CTASK0020002Work in progress Remove',
      '3NewOpen Remove',
    ]);
    expect(rows()[0].classList).toContain('closed');
    expect(buttonOf(page(), 'Remove change task 1').disabled).toBe(true);
    expect(buttonOf(page(), 'Remove change task 2').disabled).toBe(false);
    expect(rows()[0].querySelector<HTMLInputElement>('input')!.disabled).toBe(true);
  });

  it('asks for at least one task', async () => {
    const empty = tasksForm([]);
    empty.markAsTouched();
    await render(empty);

    expect(text(page().querySelector('.choice-error'))).toBe('Add at least one change task');
  });
});
