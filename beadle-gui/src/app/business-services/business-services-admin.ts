import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { Notifier } from '../core/notifier';
import { DepartmentDialog } from './business-service-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';
import { GRID, GridColumn } from '../ui/grid';
import { BeadleLoading } from '../ui/loading';

@Component({
  selector: 'beadle-departments-admin',
  imports: [GRID, BeadleLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './business-services-admin.html',
  styleUrl: './business-services-admin.scss',
})
export class DepartmentsAdmin {
  private readonly api = inject(DepartmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly departments = rxResource({ stream: () => this.api.list() });

  protected readonly departmentId = (department: Department) => department.id;
  protected readonly columns: GridColumn<Department>[] = [
    { key: 'name', header: 'Department', value: (department) => department.name, minWidth: 150 },
    {
      key: 'products',
      header: 'Products',
      value: (department) => department.productCount,
      numeric: true,
      width: 100,
    },
    { key: 'actions', header: '', width: 140 },
  ];
  protected readonly empty = computed(
    () => this.departments.hasValue() && this.departments.value().length === 0,
  );
  protected readonly summary = computed(() => {
    const departments = this.departments.hasValue() ? this.departments.value() : [];
    const products = departments.reduce((sum, department) => sum + department.productCount, 0);
    return `${counted(departments.length, 'department')} with ${counted(products, 'product')}`;
  });

  protected readonly errorMessage = errorMessage;

  protected deleteHint(department: Department): string | null {
    if (department.productCount) {
      const them = department.productCount === 1 ? 'it' : 'them';
      return (
        `Only an empty department can be deleted. ${department.name} still has ` +
        `${counted(department.productCount, 'product')}: move ${them} to another department first.`
      );
    }
    return department.changeCount
      ? `${department.name} cannot be deleted: it still has ${counted(department.changeCount, 'change')}.`
      : null;
  }

  protected add(): void {
    this.edit(null, (saved) => `${saved.name} added. Add its products on the Products tab.`);
  }

  protected rename(department: Department): void {
    this.edit(department, (saved) => `${department.name} renamed to ${saved.name}`);
  }

  protected delete(department: Department): void {
    this.dialog
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, {
        data: {
          title: `Delete the department ${department.name}?`,
          message:
            `${department.name} has no products, so nothing else is deleted with it. ` +
            'It disappears from every list of departments in Beadle. This cannot be undone.',
          confirmLabel: 'Delete department',
          danger: true,
        },
      })
      .closed.pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.delete(department.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${department.name} deleted`);
          this.departments.reload();
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private edit(department: Department | null, message: (saved: Department) => string): void {
    this.dialog
      .open<Department, Department | null, DepartmentDialog>(DepartmentDialog, { data: department })
      .closed.pipe(filter((saved): saved is Department => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.departments.reload();
      });
  }
}
