import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { BusinessServicesApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { BusinessService } from '../core/models';
import { Notifier } from '../core/notifier';
import { BusinessServiceDialog } from './business-service-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';
import { GRID, GridColumn } from '../ui/grid';
import { BeadleLoading } from '../ui/loading';

@Component({
  selector: 'beadle-business-services-admin',
  imports: [GRID, BeadleLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './business-services-admin.html',
  styleUrl: './business-services-admin.scss',
})
export class BusinessServicesAdmin {
  private readonly api = inject(BusinessServicesApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly businessServices = rxResource({ stream: () => this.api.list() });

  protected readonly businessServiceId = (businessService: BusinessService) => businessService.id;
  protected readonly columns: GridColumn<BusinessService>[] = [
    {
      key: 'name',
      header: 'Business service',
      value: (businessService) => businessService.name,
      minWidth: 150,
    },
    {
      key: 'products',
      header: 'Products',
      value: (businessService) => businessService.productCount,
      numeric: true,
      width: 100,
    },
    { key: 'actions', header: '', width: 140 },
  ];
  protected readonly empty = computed(
    () => this.businessServices.hasValue() && this.businessServices.value().length === 0,
  );
  protected readonly summary = computed(() => {
    const businessServices = this.businessServices.hasValue() ? this.businessServices.value() : [];
    const products = businessServices.reduce(
      (sum, businessService) => sum + businessService.productCount,
      0,
    );
    return `${counted(businessServices.length, 'business service')} with ${counted(products, 'product')}`;
  });

  protected readonly errorMessage = errorMessage;

  protected deleteHint(businessService: BusinessService): string | null {
    if (businessService.productCount) {
      const them = businessService.productCount === 1 ? 'it' : 'them';
      return (
        `Only an empty business service can be deleted. ${businessService.name} still has ` +
        `${counted(businessService.productCount, 'product')}: move ${them} to another business service first.`
      );
    }
    return businessService.changeCount
      ? `${businessService.name} cannot be deleted: it still has ${counted(businessService.changeCount, 'change')}.`
      : null;
  }

  protected add(): void {
    this.edit(null, (saved) => `${saved.name} added. Add its products on the Products tab.`);
  }

  protected rename(businessService: BusinessService): void {
    this.edit(businessService, (saved) => `${businessService.name} renamed to ${saved.name}`);
  }

  protected delete(businessService: BusinessService): void {
    this.dialog
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, {
        data: {
          title: `Delete the business service ${businessService.name}?`,
          message:
            `${businessService.name} has no products, so nothing else is deleted with it. ` +
            'It disappears from every list of business services in Beadle. This cannot be undone.',
          confirmLabel: 'Delete business service',
          danger: true,
        },
      })
      .closed.pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.delete(businessService.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${businessService.name} deleted`);
          this.businessServices.reload();
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private edit(
    businessService: BusinessService | null,
    message: (saved: BusinessService) => string,
  ): void {
    this.dialog
      .open<BusinessService, BusinessService | null, BusinessServiceDialog>(BusinessServiceDialog, {
        data: businessService,
      })
      .closed.pipe(filter((saved): saved is BusinessService => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.businessServices.reload();
      });
  }
}
