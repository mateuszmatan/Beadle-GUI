import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  map,
  of,
  switchMap,
} from 'rxjs';
import { ProductsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { Department, PRODUCT_CODE } from '../core/models';
import { applyFieldProblems, filled, max, optional, text } from '../shared/form-controls';
import { errorText } from '../shared/form-errors';
import { DIALOG } from '../ui/dialog';
import { FORM_FIELD } from '../ui/form-field';
import { ProductDetails, ProductDetailsApi } from './product-details-api';

export interface ProductDialogData {
  departments: readonly Department[];
  departmentId: number | null;
  product: ProductDetails | null;
}

export type ProductDialogResult = ProductDetails | HttpErrorResponse;

const CODE_HELP = "Start with a letter; use A-Z, 0-9, '-' or '_'";

@Component({
  selector: 'dso-product-dialog',
  imports: [ReactiveFormsModule, DIALOG, FORM_FIELD],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>{{ product ? 'Change ' + product.name : 'Add product' }}</h2>
    </div>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div class="modal-body">
        @if (!product) {
          <p class="intro">Add its change template on the product's page.</p>
        }
        <div class="fields">
          <dso-form-field>
            <dso-label>Product name</dso-label>
            <input
              dsoInput
              formControlName="name"
              placeholder="CertScanner"
              autocomplete="off"
              required
            />
            <dso-error>{{ errorText(form.controls.name) }}</dso-error>
          </dso-form-field>
          @if (!product) {
            <dso-form-field>
              <dso-label>Code</dso-label>
              <input
                dsoInput
                class="mono"
                formControlName="code"
                placeholder="CERTSCANNER"
                autocomplete="off"
                required
              />
              <dso-hint>Made from the name; you can change it</dso-hint>
              <dso-error>{{ errorText(form.controls.code, codeHelp) }}</dso-error>
            </dso-form-field>
          }
          <dso-form-field>
            <dso-label>Department</dso-label>
            <select dsoInput formControlName="departmentId" required>
              @for (department of data.departments; track department.id) {
                <option [ngValue]="department.id">{{ department.name }}</option>
              }
            </select>
            <dso-error>{{ errorText(form.controls.departmentId) }}</dso-error>
          </dso-form-field>
          <dso-form-field>
            <dso-label>Owner team</dso-label>
            <input dsoInput formControlName="ownerTeam" placeholder="Technology Architecture" />
            <dso-error>{{ errorText(form.controls.ownerTeam) }}</dso-error>
          </dso-form-field>
          <dso-form-field>
            <dso-label>Contact e-mail</dso-label>
            <input
              dsoInput
              type="email"
              formControlName="contactEmail"
              placeholder="team@bbh.com"
            />
            <dso-error>{{ errorText(form.controls.contactEmail) }}</dso-error>
          </dso-form-field>
        </div>
        @if (error(); as message) {
          <div class="banner" role="alert">{{ message }}</div>
        }
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-link" dsoDialogClose>Cancel</button>
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          {{ product ? 'Save' : 'Add product' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .modal-body {
      width: min(560px, 80vw);
    }

    .intro {
      margin: 0 0 10px;
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      align-items: start;
      gap: 14px 12px;
      padding-top: 6px;
    }

    .banner {
      margin: 12px 0 0;
    }

    @media (max-width: 600px) {
      .fields {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ProductDialog {
  protected readonly data = inject<ProductDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<ProductDialogResult, ProductDialog>>(DialogRef);
  private readonly api = inject(ProductsApi);
  private readonly detailsApi = inject(ProductDetailsApi);

  protected readonly product = this.data.product;
  protected readonly codeHelp = CODE_HELP;
  protected readonly errorText = errorText;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    name: text(this.product?.name, filled, max(200)),
    code: text('', filled, Validators.pattern(PRODUCT_CODE)),
    departmentId: new FormControl<number | null>(
      this.data.departments.find(
        (department) => department.id === (this.product?.departmentId ?? this.data.departmentId),
      )?.id ?? null,
      Validators.required,
    ),
    ownerTeam: text(this.product?.ownerTeam, max(200)),
    contactEmail: text(this.product?.contactEmail, Validators.email, max(320)),
  });

  private suggested = '';

  constructor() {
    const { name, code } = this.form.controls;
    if (this.product) {
      code.disable();
      return;
    }
    code.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      if (value !== value.toUpperCase()) {
        code.setValue(value.toUpperCase(), { emitEvent: false });
      }
    });
    name.valueChanges
      .pipe(
        map((value) => value.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        filter((value) => value !== '' && code.value === this.suggested),
        switchMap((value) => this.api.suggestCode(value).pipe(catchError(() => of('')))),
        takeUntilDestroyed(),
      )
      .subscribe((suggestion) => {
        if (suggestion && code.value === this.suggested) {
          this.suggested = suggestion;
          code.setValue(suggestion);
        }
      });
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const details = {
      name: value.name.trim(),
      departmentId: value.departmentId,
      ownerTeam: optional(value.ownerTeam),
      contactEmail: optional(value.contactEmail),
    };
    const product = this.product;
    this.saving.set(true);
    this.error.set(null);
    const request: Observable<ProductDetails> = product
      ? this.detailsApi.update(product.id, { ...details, version: product.version })
      : this.api.create({
          ...details,
          code: value.code,
          description: null,
          appScan: null,
          version: null,
          services: [],
        });
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error) => this.failed(error),
    });
  }

  private failed(error: unknown): void {
    if (this.product && error instanceof HttpErrorResponse && error.status === 409) {
      this.dialogRef.close(error);
      return;
    }
    const problems = fieldProblems(error);
    const unmatched = applyFieldProblems(this.form, problems);
    this.error.set(unmatched.length || !problems.length ? errorMessage(error) : null);
  }
}
