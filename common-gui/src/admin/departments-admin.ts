import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { outputFromObservable, rxResource, toObservable } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { Notifier } from '../core/notifier';
import { DepartmentDialog } from '../departments/department-dialog';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';
import { DsoCell, GRID, GridColumn } from '../ui/grid';
import { DsoLoading } from '../ui/loading';

export interface DepartmentCount<D> {
  noun: string;
  count: (department: D) => number;
}

export interface DepartmentUsage<D extends Department = Department> {
  subject: string;
  counts?: readonly DepartmentCount<D>[];
  columns?: readonly GridColumn<D>[];
  blocker?: (department: D) => string | null;
}

@Component({
  selector: 'dso-departments-admin',
  imports: [GRID, DsoLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './departments-admin.html',
  styleUrl: './departments-admin.scss',
})
export class DepartmentsAdmin<D extends Department = Department> {
  readonly usage = input.required<DepartmentUsage<D>>();
  readonly cells = input<readonly DsoCell<D>[]>([]);

  private readonly api = inject(DepartmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly departments = rxResource({ stream: () => this.api.list<D>() });
  private readonly listed = computed(() =>
    this.departments.hasValue() ? this.departments.value() : [],
  );

  readonly loaded = outputFromObservable(toObservable(this.listed));

  protected readonly departmentId = (department: D) => department.id;
  protected readonly columns = computed<GridColumn<D>[]>(() => [
    { key: 'name', header: 'Department', value: (department) => department.name, minWidth: 150 },
    {
      key: 'products',
      header: 'Products',
      value: (department) => department.productCount,
      numeric: true,
      width: 100,
    },
    ...(this.usage().columns ?? []),
    { key: 'actions', header: '', width: 140 },
  ]);
  protected readonly empty = computed(
    () => this.departments.hasValue() && this.departments.value().length === 0,
  );
  protected readonly summary = computed(() => {
    const departments = this.listed();
    const counts: DepartmentCount<D>[] = [
      { noun: 'product', count: (department) => department.productCount },
      ...(this.usage().counts ?? []),
    ];
    const contents = counts.map(({ noun, count }) =>
      counted(
        departments.reduce((sum, department) => sum + count(department), 0),
        noun,
      ),
    );
    return `${counted(departments.length, 'department')} with ${contents.join(' and ')}`;
  });
  protected readonly help = computed(
    () =>
      'Every product belongs to one department, and people choose their department to see ' +
      `${this.usage().subject}. Only an empty department can be deleted.`,
  );

  protected readonly errorMessage = errorMessage;

  protected deleteHint(department: D): string | null {
    if (department.productCount) {
      const them = department.productCount === 1 ? 'it' : 'them';
      return (
        `Only an empty department can be deleted. ${department.name} still has ` +
        `${counted(department.productCount, 'product')}: move ${them} to another department first.`
      );
    }
    return this.usage().blocker?.(department) ?? null;
  }

  protected add(): void {
    this.edit(null, (saved) => `${saved.name} added. Add its products on the Products tab.`);
  }

  protected rename(department: D): void {
    this.edit(department, (saved) => `${department.name} renamed to ${saved.name}`);
  }

  protected delete(department: D): void {
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

  private edit(department: D | null, message: (saved: Department) => string): void {
    this.dialog
      .open<Department, Department | null, DepartmentDialog>(DepartmentDialog, { data: department })
      .closed.pipe(filter((saved): saved is Department => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.departments.reload();
      });
  }
}
