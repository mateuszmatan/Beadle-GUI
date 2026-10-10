import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { rxResource } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { Notifier } from '../core/notifier';
import { DepartmentDialog } from '../products/department-dialog';
import { BarChart, BarRow } from '../shared/bar-chart';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';
import { GRID, GridColumn } from '../ui/grid';
import { DsoLoading } from '../ui/loading';

@Component({
  selector: 'dso-departments-admin',
  imports: [GRID, DsoLoading, BarChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './departments-admin.html',
  styleUrl: './departments-admin.scss',
})
export class DepartmentsAdmin {
  readonly pipelines = input(false);

  private readonly api = inject(DepartmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly departments = rxResource({ stream: () => this.api.list() });
  protected readonly departmentId = (department: Department) => department.id;
  protected readonly columns = computed<GridColumn<Department>[]>(() => [
    { key: 'name', header: 'Department', value: (department) => department.name, minWidth: 150 },
    {
      key: 'products',
      header: 'Products',
      value: (department) => department.productCount,
      numeric: true,
      width: 100,
    },
    ...(this.pipelines()
      ? [
          {
            key: 'services',
            header: 'Services',
            value: (department: Department) => department.serviceCount,
            numeric: true,
            width: 100,
          },
          {
            key: 'pipelines',
            header: 'Pipelines',
            value: (department: Department) => department.pipelineCount,
            minWidth: 150,
          },
        ]
      : []),
    { key: 'actions', header: '', width: 140 },
  ]);
  protected readonly empty = computed(
    () => this.departments.hasValue() && this.departments.value().length === 0,
  );
  protected readonly summary = computed(() => {
    const departments = this.departments.hasValue() ? this.departments.value() : [];
    const total = (noun: string, count: (department: Department) => number) =>
      counted(
        departments.reduce((sum, department) => sum + count(department), 0),
        noun,
      );
    const products = total('product', (department) => department.productCount);
    const contents = this.pipelines()
      ? `${products} and ${total('service', (department) => department.serviceCount)}`
      : products;
    return `${counted(departments.length, 'department')} with ${contents}`;
  });
  protected readonly help = computed(
    () =>
      'Every product belongs to one department, and people choose their department to see ' +
      (this.pipelines() ? 'its products and pipelines. ' : 'its changes. ') +
      'Only an empty department can be deleted.',
  );
  protected readonly chart = computed<BarRow[]>(() =>
    this.pipelines() && this.departments.hasValue()
      ? this.departments.value().map((department) => {
          const invalidated = this.invalidated(department);
          return {
            label: department.name,
            note: [
              counted(department.pipelineCount, 'pipeline'),
              ...(invalidated ? [this.keysInvalidated(invalidated)] : []),
            ].join(', '),
            segments: [
              { swatch: 'active', label: 'active', count: department.activePipelineCount },
              { swatch: 'disabled', label: 'key invalidated', count: invalidated },
            ],
          };
        })
      : [],
  );

  protected readonly errorMessage = errorMessage;

  protected invalidated(department: Department): number {
    return department.pipelineCount - department.activePipelineCount;
  }

  protected keysInvalidated(count: number): string {
    return `${counted(count, 'key')} invalidated`;
  }

  protected deleteHint(department: Department): string | null {
    if (department.productCount) {
      const them = department.productCount === 1 ? 'it' : 'them';
      return (
        `Only an empty department can be deleted. ${department.name} still has ` +
        `${counted(department.productCount, 'product')}: move ${them} to another department first.`
      );
    }
    return department.changeCount
      ? `${department.name} cannot be deleted: it has ` +
          `${counted(department.changeCount, 'change')} raised in Beadle.`
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
            'It disappears from every list of departments in the portal. This cannot be undone.',
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
