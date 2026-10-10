import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { ChangesApi } from '../changes/change-api';
import { ProductsApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { Product } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { beadleProduct } from '../core/sections';
import { DepartmentGroup, byDepartment } from '@common/departments/departments';
import { RelativeTimePipe, counted } from '@common/shared/formatting';
import { DsoInput } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { ProductDialog, ProductDialogData } from './product-dialog';
import { DepartmentsApi } from '@common/core/api';

function tally(group: DepartmentGroup<Product>): string {
  return counted(group.department?.productCount ?? group.products.length, 'product');
}

@Component({
  selector: 'dso-beadle-products',
  imports: [ReactiveFormsModule, RouterLink, DsoInput, GRID, DsoLoading, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './beadle-products.html',
  styleUrl: './beadle-products.scss',
})
export class BeadleProducts {
  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly changesApi = inject(ChangesApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  protected readonly beadleProduct = beadleProduct;
  protected readonly productId = (product: Product) => product.id;
  protected readonly clickable = () => 'clickable';
  protected readonly columns: GridColumn<Product>[] = [
    { key: 'product', header: 'Product', value: (product) => product.name, minWidth: 240, flex: 2 },
    {
      key: 'ownerTeam',
      header: 'Owner team',
      value: (product) => product.ownerTeam ?? '–',
      minWidth: 160,
    },
    { key: 'template', header: 'Change template', width: 220 },
  ];
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
  protected readonly templates = rxResource({
    stream: () => this.changesApi.profiles(),
  });
  protected readonly stored = computed(
    () =>
      new Map(
        (this.templates.hasValue() ? this.templates.value() : []).map((template) => [
          template.productId,
          template,
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

  protected reload(): void {
    [this.products, this.departments]
      .filter((resource) => resource.error())
      .forEach((resource) => resource.reload());
  }

  protected open(product: Product): void {
    this.router.navigate(beadleProduct(product.id));
  }

  protected add(departmentId: number | null = null): void {
    this.dialog
      .open<Product, ProductDialogData, ProductDialog>(ProductDialog, {
        data: {
          departments: this.departments.hasValue() ? this.departments.value() : [],
          departmentId,
          product: null,
        },
      })
      .closed.pipe(filter((product): product is Product => !!product))
      .subscribe((product) => {
        this.notifier.success(`${product.name} added. Now fill in its change template.`);
        this.router.navigate(beadleProduct(product.id));
      });
  }
}
