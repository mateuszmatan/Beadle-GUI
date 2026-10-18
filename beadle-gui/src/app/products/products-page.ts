import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs';
import { ChangesApi } from '../changes/change-api';
import { BusinessServicesApi, ProductsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Product } from '../core/models';
import { Notifier } from '../core/notifier';
import { productPath } from '../core/sections';
import { BusinessServiceGroup, byBusinessService } from '../business-services/business-services';
import { RelativeTimePipe, counted } from '../shared/formatting';
import { BeadleInput } from '../ui/form-field';
import { GRID, GridColumn } from '../ui/grid';
import { BeadleLoading } from '../ui/loading';
import { ProductDialog, ProductDialogData } from './product-dialog';

function tally(group: BusinessServiceGroup<Product>): string {
  return counted(group.businessService?.productCount ?? group.products.length, 'product');
}

@Component({
  selector: 'beadle-products-page',
  imports: [ReactiveFormsModule, RouterLink, BeadleInput, GRID, BeadleLoading, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './products-page.html',
  styleUrl: './products-page.scss',
})
export class ProductsPage {
  private readonly api = inject(ProductsApi);
  private readonly businessServicesApi = inject(BusinessServicesApi);
  private readonly changesApi = inject(ChangesApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);

  protected readonly productLink = productPath;
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
  protected readonly businessServices = rxResource({
    stream: () => this.businessServicesApi.list(),
  });
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
    this.products.hasValue() && this.businessServices.hasValue()
      ? byBusinessService(this.businessServices.value(), this.products.value()).filter(
          (group) => group.products.length || !this.query(),
        )
      : null,
  );
  protected readonly empty = computed(() =>
    (this.groups() ?? []).every((group) => group.products.length === 0),
  );
  protected readonly total = computed(() => {
    const groups = this.groups() ?? [];
    const placed = groups.filter((group) => group.businessService);
    const products = placed.reduce((sum, group) => sum + group.products.length, 0);
    const unassigned = groups.find((group) => !group.businessService)?.products.length ?? 0;
    const total = `${counted(products, 'product')} in ${counted(placed.length, 'business service')}`;
    return unassigned ? `${total} · ${unassigned} without a business service` : total;
  });

  protected readonly errorMessage = errorMessage;
  protected readonly tally = tally;

  protected reload(): void {
    [this.products, this.businessServices]
      .filter((resource) => resource.error())
      .forEach((resource) => resource.reload());
  }

  protected open(product: Product): void {
    this.router.navigate(productPath(product.id));
  }

  protected add(businessServiceId: number | null = null): void {
    this.dialog
      .open<Product, ProductDialogData, ProductDialog>(ProductDialog, {
        data: {
          businessServices: this.businessServices.hasValue() ? this.businessServices.value() : [],
          businessServiceId,
          product: null,
        },
      })
      .closed.pipe(filter((product): product is Product => !!product))
      .subscribe((product) => {
        this.notifier.success(`${product.name} added. Now fill in its change template.`);
        this.router.navigate(productPath(product.id));
      });
  }
}
