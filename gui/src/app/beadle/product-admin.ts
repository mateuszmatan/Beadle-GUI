import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage, fieldProblems } from '../core/errors';
import { Product, ProductRequest } from '../core/models';
import { Notifier } from '../core/notifier';
import { BEADLE_PRODUCTS } from '../core/sections';
import { NOT_IN_A_DEPARTMENT } from '../products/departments';
import { ServiceDialog, ServiceDialogData } from '../self-service/service-dialog';
import {
  WizardService,
  fromService,
  problemText,
  serviceRequest,
} from '../self-service/self-service-model';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { TARGET_LABELS, TOOL_LABELS } from '../shared/fields';
import {
  ProductDialog,
  ProductDialogData,
  ProductDialogResult,
  storedRequest,
} from './product-dialog';

interface SaveError {
  message: string;
  problems: string[];
}

@Component({
  selector: 'dso-product-admin',
  imports: [MatButtonModule, MatProgressBarModule, MatTableModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (product.isLoading() || busy()) {
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
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="change()">
              Change
            </button>
            <button mat-button type="button" class="danger" [disabled]="busy()" (click)="delete()">
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

      <section class="card services">
        <header class="card-header">
          <h2>Services</h2>
          <button mat-stroked-button type="button" [disabled]="busy()" (click)="openService(null)">
            Add service
          </button>
        </header>
        @if (saveError(); as error) {
          <div class="banner danger" role="alert">
            <div>
              <strong>{{ error.message }}</strong>
              @if (error.problems.length) {
                <ul class="problems">
                  @for (problem of error.problems; track problem) {
                    <li>{{ problem }}</li>
                  }
                </ul>
              }
            </div>
          </div>
        }
        @if (p.services.length === 0) {
          <p class="muted none">{{ p.name }} has no services yet.</p>
        } @else {
          <div class="table-scroll">
            <table mat-table [dataSource]="p.services">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>Service</th>
                <td mat-cell *matCellDef="let service" class="name">{{ service.name }}</td>
              </ng-container>
              <ng-container matColumnDef="description">
                <th mat-header-cell *matHeaderCellDef>What it does</th>
                <td mat-cell *matCellDef="let service">{{ service.description ?? '–' }}</td>
              </ng-container>
              <ng-container matColumnDef="tool">
                <th mat-header-cell *matHeaderCellDef>Build tool</th>
                <td mat-cell *matCellDef="let service">{{ toolLabels[service.build.tool] }}</td>
              </ng-container>
              <ng-container matColumnDef="target">
                <th mat-header-cell *matHeaderCellDef>Runs on</th>
                <td mat-cell *matCellDef="let service">
                  {{ targetLabels[service.deployment.target] }}
                </td>
              </ng-container>
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let service; let index = index" class="actions">
                  <button mat-button type="button" [disabled]="busy()" (click)="openService(index)">
                    Change
                  </button>
                  <button
                    mat-button
                    type="button"
                    class="danger"
                    [disabled]="busy()"
                    (click)="removeService(index)"
                  >
                    Remove
                  </button>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let service; columns: columns"></tr>
            </table>
          </div>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 12px;
    }

    .card {
      margin-bottom: 12px;
      padding: 8px 12px;
    }

    .card-header {
      align-items: center;
    }

    .card-header .actions {
      display: flex;
      gap: 6px;
    }

    .services {
      padding-bottom: 4px;
    }

    .table-scroll {
      margin: 0 -12px;
    }

    .mat-mdc-table {
      width: 100%;
    }

    td.mat-mdc-cell {
      padding-top: 3px;
      padding-bottom: 3px;
    }

    .name {
      font-weight: 600;
      color: var(--dso-navy);
    }

    td.actions {
      width: 170px;
      text-align: right;
      white-space: nowrap;
    }

    .none {
      margin: 4px 0 8px;
      font-size: 12px;
    }

    .banner {
      margin: 0 0 8px;
    }
  `,
})
export class ProductAdmin {
  readonly id = input.required<number>();
  readonly saved = output<Product>();

  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['name', 'description', 'tool', 'target', 'actions'];
  protected readonly toolLabels: Record<string, string> = TOOL_LABELS;
  protected readonly targetLabels: Record<string, string> = TARGET_LABELS;
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
  protected readonly busy = signal(false);
  protected readonly saveError = signal<SaveError | null>(null);

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
    this.confirm({
      title: `Delete ${product.name}?`,
      message:
        `${product.name} is deleted with its services, their pipelines and keys, and its ServiceNow defaults. ` +
        'Jenkins jobs using those keys stop working. This cannot be undone.',
      confirmLabel: 'Delete product',
      danger: true,
    })
      .pipe(switchMap(() => this.api.delete(product.id)))
      .subscribe({
        next: () => {
          this.notifier.success(`${product.name} deleted`);
          this.router.navigate([BEADLE_PRODUCTS.path]);
        },
        error: (error) => this.notifier.error(error),
      });
  }

  protected openService(index: number | null): void {
    const product = this.product.value()!;
    const services = product.services.map(fromService);
    this.dialog
      .open<ServiceDialog, ServiceDialogData, WizardService>(ServiceDialog, {
        data: {
          pipeline: 'FULL',
          service: index === null ? null : services[index],
          takenNames: services
            .filter((_, position) => position !== index)
            .map((service) => service.name),
        },
      })
      .afterClosed()
      .pipe(filter((service): service is WizardService => !!service))
      .subscribe((service) =>
        index === null
          ? this.saveServices(
              product,
              [...services, service],
              service,
              `${service.name} added to ${product.name}`,
            )
          : this.saveServices(
              product,
              services.map((current, position) => (position === index ? service : current)),
              service,
              `${service.name} saved`,
            ),
      );
  }

  protected removeService(index: number): void {
    const product = this.product.value()!;
    const services = product.services.map(fromService);
    const removed = services[index];
    this.confirm({
      title: `Remove ${removed.name}?`,
      message:
        `${removed.name} is removed from ${product.name} with its pipelines and their keys. ` +
        'Jenkins jobs using those keys stop working.',
      confirmLabel: 'Remove service',
      danger: true,
    }).subscribe(() =>
      this.saveServices(
        product,
        services.filter((_, position) => position !== index),
        null,
        `${removed.name} removed from ${product.name}`,
      ),
    );
  }

  private saveServices(
    product: Product,
    services: WizardService[],
    changed: WizardService | null,
    message: string,
  ): void {
    const stored = (service: WizardService) =>
      product.services.find((candidate) => candidate.id === service.id);
    const request: ProductRequest = {
      ...storedRequest(product),
      services: services.map((service) =>
        service === changed ? serviceRequest(service, 'FULL', stored(service)) : stored(service)!,
      ),
    };
    this.busy.set(true);
    this.saveError.set(null);
    this.api
      .update(product.id, request)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => this.done(saved, message),
        error: (error) =>
          error instanceof HttpErrorResponse && error.status === 409
            ? this.reload(error, product)
            : this.saveError.set({
                message: errorMessage(error),
                problems: fieldProblems(error).map((problem) => problemText(problem, services)),
              }),
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

  private confirm(data: ConfirmDialogData) {
    return this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true));
  }
}
