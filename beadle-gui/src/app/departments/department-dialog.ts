import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { DIALOG } from '../ui/dialog';
import { FORM_FIELD } from '../ui/form-field';

@Component({
  selector: 'beadle-department-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 beadleDialogTitle>{{ department ? 'Rename ' + department.name : 'Add a department' }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        @if (department) {
          <p class="intro">
            The new name shows everywhere in Beadle at once. The products of the department stay
            as they are.
          </p>
        }
        <beadle-form-field class="full-width">
          <beadle-label>Name</beadle-label>
          <input
            beadleInput
            formControlName="name"
            placeholder="Fund Services"
            autocomplete="off"
            required
          />
          <beadle-hint>The name people know the department by, for example Fund Services</beadle-hint>
          <beadle-error>{{ errorText(name) }}</beadle-error>
        </beadle-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" beadleDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ department ? 'Rename department' : 'Add department' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(420px, 80vw);
    }
    .intro {
      margin: 0 0 10px;
      color: var(--beadle-muted);
    }
    .banner {
      margin: 8px 0 0;
    }
  `,
})
export class DepartmentDialog {
  protected readonly department = inject<Department | null>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<Department, DepartmentDialog>>(DialogRef);
  private readonly api = inject(DepartmentsApi);

  protected readonly form = new FormGroup({
    name: text(this.department?.name, filled, max(100)),
  });
  protected readonly name = this.form.controls.name;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorText = errorText;

  protected save(): void {
    this.name.markAsTouched();
    if (this.name.invalid || this.saving()) {
      return;
    }
    const name = this.name.value.trim();
    this.saving.set(true);
    this.error.set(null);
    (this.department
      ? this.api.rename(this.department.id, { name, version: this.department.version })
      : this.api.create(name)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) => this.error.set(errorMessage(error)),
      });
  }
}
