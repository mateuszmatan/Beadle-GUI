import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { DepartmentsApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Product, ProductSummary } from '../core/models';
import { Notifier } from '../core/notifier';
import { beadleProduct } from '../core/sections';
import { DepartmentGroup, byDepartment } from '../products/departments';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { ProductDialog, ProductDialogData } from './product-dialog';

export interface StoredDefaults {
  productId: number;
  productName: string;
  version: number;
  updatedAt: string;
}

function tally(group: DepartmentGroup): string {
  const { productCount, serviceCount } = group.department ?? {
    productCount: group.products.length,
    serviceCount: group.products.reduce((sum, product) => sum + product.serviceCount, 0),
  };
  return `${counted(productCount, 'product')} · ${counted(serviceCount, 'service')}`;
}

@Component({
  selector: 'dso-beadle-products',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './beadle-products.html',
  styleUrl: './beadle-products.scss',
})
export class BeadleProducts {
  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  protected readonly beadleProduct = beadleProduct;
  protected readonly columns = ['product', 'ownerTeam', 'services', 'defaults'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly query = toSignal(
    this.search.valueChanges.pipe(
      debounceTime(250),
      map((value) => value.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  protected readonly products = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly departments = rxResource({ stream: () => this.departmentsApi.list() });
  protected readonly defaults = rxResource({
    stream: () => this.http.get<StoredDefaults[]>('/api/change-profiles'),
  });
  protected readonly stored = computed(
    () =>
      new Map(
        (this.defaults.hasValue() ? this.defaults.value() : []).map((defaults) => [
          defaults.productId,
          defaults,
        ]),
      ),
  );

  protected readonly groups = computed(() =>
    this.products.hasValue() && this.departments.hasValue()
      ? byDepartment(this.departments.value(), this.products.value()).filter(
          (group) => group.products.length || !this.query(),
        )
      : null,
  );
  protected readonly empty = computed(() =>
    (this.groups() ?? []).every((group) => group.products.length === 0),
  );
  protected readonly total = computed(() => {
    const groups = this.groups() ?? [];
    const placed = groups.filter((group) => group.department);
    const products = placed.reduce((sum, group) => sum + group.products.length, 0);
    const unassigned = groups.find((group) => !group.department)?.products.length ?? 0;
    const total = `${counted(products, 'product')} in ${counted(placed.length, 'department')}`;
    return unassigned ? `${total} · ${unassigned} not in a department` : total;
  });

  protected readonly errorMessage = errorMessage;
  protected readonly tally = tally;

  protected open(product: ProductSummary): void {
    this.router.navigate(beadleProduct(product.id));
  }

  protected add(departmentId: number | null = null): void {
    this.dialog
      .open<ProductDialog, ProductDialogData, Product>(ProductDialog, {
        data: {
          departments: this.departments.hasValue() ? this.departments.value() : [],
          departmentId,
          product: null,
        },
      })
      .afterClosed()
      .pipe(filter((product): product is Product => !!product))
      .subscribe((product) => {
        this.notifier.success(`${product.name} added`);
        this.router.navigate(beadleProduct(product.id));
      });
  }
}
