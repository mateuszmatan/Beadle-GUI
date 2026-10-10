import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  output,
} from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { errorMessage } from '@common/core/errors';
import { Notifier } from '@common/core/notifier';
import { NOT_IN_A_DEPARTMENT } from '@common/departments/departments';
import { ConfirmDialog, ConfirmDialogData } from '@common/shared/confirm-dialog';
import { DsoLoading } from '@common/ui/loading';
import { DepartmentsApi } from '@common/core/api';
import { ProductsApi } from '../core/api';
import { Product } from '../core/models';
import { ProductDialog, ProductDialogData, ProductDialogResult } from './product-dialog';

@Component({
  selector: 'dso-product-admin',
  imports: [DsoLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (product.isLoading()) {
      <dso-loading />
    }
    @if (product.error(); as error) {
      <div class="banner" role="alert">
        <span>The product could not be loaded. {{ errorMessage(error) }}</span>
        <button type="button" class="btn btn-link" (click)="product.reload()">Try again</button>
      </div>
    } @else if (product.hasValue()) {
      @let p = product.value();
      <section class="card facts" aria-labelledby="facts-title">
        <header class="card-header">
          <h2 id="facts-title">Product details</h2>
          <div class="actions">
            <button type="button" class="btn btn-outline-primary" (click)="change()">
              Edit details
            </button>
            <button type="button" class="btn btn-link danger" (click)="delete()">
              Delete product
            </button>
          </div>
        </header>
        <dl class="pairs">
          <div>
            <dt>Product code</dt>
            <dd class="mono">{{ p.code }}</dd>
          </div>
          <div>
            <dt>Department</dt>
            <dd>{{ p.departmentName ?? notInADepartment }}</dd>
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

    .pairs div {
      align-items: baseline;
    }
  `,
})
export class ProductAdmin {
  readonly id = input.required<number>();
  readonly saved = output<Product>();
  readonly deleted = output<Product>();

  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly errorMessage = errorMessage;
  protected readonly notInADepartment = NOT_IN_A_DEPARTMENT;

  protected readonly product = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });

  protected change(): void {
    const product = this.product.value()!;
    this.dialog
      .open<ProductDialogResult, ProductDialogData, ProductDialog>(ProductDialog, {
        data: {
          departments: this.departments.hasValue() ? this.departments.value() : [],
          departmentId: null,
          product,
        },
      })
      .closed.subscribe((result) => {
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
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, {
        data: {
          title: `Delete the product ${product.name}?`,
          message: `${product.name} and its change template are deleted. This cannot be undone.`,
          confirmLabel: 'Delete product',
          danger: true,
        },
      })
      .closed.pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.delete(product.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${product.name} deleted`);
          this.deleted.emit(product);
        },
        error: (error) =>
          this.notifier.error(`${product.name} could not be deleted. ${errorMessage(error)}`),
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
              ? `The details of ${product.name} could not be saved. ${errorMessage(conflict)}`
              : `${product.name} was changed by someone else. Its latest version is shown now; make your change again.`,
          );
        },
        error: (error) => this.notifier.error(error),
      });
  }
}
