import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { BusinessServicesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { BusinessService } from '../core/models';
import { filled, max, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { DIALOG } from '../ui/dialog';
import { FORM_FIELD } from '../ui/form-field';

@Component({
  selector: 'beadle-business-service-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 beadleDialogTitle>
        {{ businessService ? 'Rename ' + businessService.name : 'Add business service' }}
      </h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        @if (businessService) {
          <p class="intro">
            The new name applies throughout Beadle immediately. The products of the business service
            are not affected.
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
          <beadle-hint
            >The name of the business service at BBH, for example Fund Services</beadle-hint
          >
          <beadle-error>{{ errorText(name) }}</beadle-error>
        </beadle-form-field>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" beadleDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ businessService ? 'Rename business service' : 'Add business service' }}
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
export class BusinessServiceDialog {
  protected readonly businessService = inject<BusinessService | null>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<BusinessService, BusinessServiceDialog>>(DialogRef);
  private readonly api = inject(BusinessServicesApi);

  protected readonly form = new FormGroup({
    name: text(this.businessService?.name, filled, max(100)),
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
    (this.businessService
      ? this.api.rename(this.businessService.id, { name, version: this.businessService.version })
      : this.api.create(name)
    )
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error) => this.error.set(errorMessage(error)),
      });
  }
}
