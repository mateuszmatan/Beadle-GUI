import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Product } from '../core/models';
import { Notifier } from '../core/notifier';
import { NOT_IN_A_DEPARTMENT } from '../products/departments';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { ProductDialog, ProductDialogData, ProductDialogResult } from './product-dialog';

@Component({
  selector: 'dso-product-admin',
  imports: [MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (product.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (product.error(); as error) {
      <div class="banner">{{ errorMessage(error) }}</div>
    } @else if (product.hasValue()) {
      @let p = product.value();
      <section class="card facts">
        <header class="card-header">
          <h2>Product</h2>
          <div class="actions">
            <button mat-stroked-button type="button" (click)="change()">Change</button>
            <button mat-button type="button" class="danger" (click)="delete()">
              Delete product
            </button>
          </div>
        </header>
        <dl class="pairs">
          <div>
            <dt>Code</dt>
            <dd class="mono">{{ p.code }}</dd>
          </div>
          <div>
            <dt>Department</dt>
            <dd>{{ departmentName() }}</dd>
          </div>
          <div>
            <dt>Owner team</dt>
            <dd>{{ p.ownerTeam ?? '–' }}</dd>
          </div>
          <div>
            <dt>Contact e-mail</dt>
            <dd>
              @if (p.contactEmail) {
                <a [href]="'mailto:' + p.contactEmail">{{ p.contactEmail }}</a>
              } @else {
                –
              }
            </dd>
          </div>
        </dl>
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 12px;
    }

    .card {
      padding: 8px 12px;
    }

    .card-header {
      align-items: center;
    }

    .card-header .actions {
      display: flex;
      gap: 6px;
    }
  `,
})
export class ProductAdmin {
  readonly id = input.required<number>();
  readonly saved = output<Product>();
  readonly deleted = output<Product>();

  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errorMessage = errorMessage;

  protected readonly product = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly departmentName = computed(() => {
    const id = this.product.hasValue() ? this.product.value().departmentId : null;
    const departments = this.departments.hasValue() ? this.departments.value() : [];
    return id === null
      ? NOT_IN_A_DEPARTMENT
      : (departments.find((department) => department.id === id)?.name ?? '–');
  });

  protected change(): void {
    const product = this.product.value()!;
    this.dialog
      .open<ProductDialog, ProductDialogData, ProductDialogResult>(ProductDialog, {
        data: {
          departments: this.departments.hasValue() ? this.departments.value() : [],
          departmentId: null,
          product,
        },
      })
      .afterClosed()
      .subscribe((result) => {
        if (result instanceof HttpErrorResponse) {
          this.reload(result, product);
        } else if (result) {
          this.done(result, `${result.name} saved`);
        }
      });
  }

  protected delete(): void {
    const product = this.product.value()!;
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: `Delete ${product.name}?`,
          message:
            `${product.name} is deleted with its change template and its DevSecOps pipelines and keys. ` +
            'Jenkins jobs using those keys stop working. This cannot be undone.',
          confirmLabel: 'Delete product',
          danger: true,
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.delete(product.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${product.name} deleted`);
          this.deleted.emit(product);
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private done(saved: Product, message: string): void {
    this.product.set(saved);
    this.notifier.success(message);
    this.saved.emit(saved);
  }

  private reload(conflict: HttpErrorResponse, product: Product): void {
    this.api
      .get(product.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (latest) => {
          this.product.set(latest);
          this.notifier.error(
            latest.version === product.version
              ? conflict
              : `${product.name} was changed by someone else. Its latest version is shown now; make your change again.`,
          );
        },
        error: (error) => this.notifier.error(error),
      });
  }
}
