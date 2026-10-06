import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { errorText } from '../shared/form-errors';

@Component({
  selector: 'dso-department-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ department ? 'Rename ' + department.name : 'Add department' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <mat-form-field class="full-width">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" placeholder="Fund Services" autocomplete="off" />
          <mat-error>{{ errorText(name) }}</mat-error>
        </mat-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">Save</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-dialog-content {
      width: min(420px, 80vw);
    }
    .banner {
      margin: 8px 0 0;
    }
  `,
})
export class DepartmentDialog {
  protected readonly department = inject<Department | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<DepartmentDialog, Department>>(MatDialogRef);
  private readonly api = inject(DepartmentsApi);

  protected readonly form = new FormGroup({
    name: new FormControl(this.department?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)],
    }),
  });
  protected readonly name = this.form.controls.name;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorText = errorText;

  protected save(): void {
    this.name.markAsTouched();
    const name = this.name.value.trim();
    if (!name) {
      this.name.setErrors({ required: true });
    }
    if (this.name.invalid || this.saving()) {
      return;
    }
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
