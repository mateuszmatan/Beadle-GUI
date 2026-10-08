import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { errorMessage } from '../core/errors';
import { Department } from '../core/models';
import { Notifier } from '../core/notifier';
import { DepartmentDialog } from '../products/department-dialog';
import { BarChart, BarRow } from '../shared/bar-chart';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';

@Component({
  selector: 'dso-departments-admin',
  imports: [MatButtonModule, MatProgressBarModule, MatTableModule, BarChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './departments-admin.html',
  styleUrl: './departments-admin.scss',
})
export class DepartmentsAdmin {
  readonly pipelines = input(false);

  private readonly api = inject(DepartmentsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifier = inject(Notifier);

  protected readonly departments = rxResource({ stream: () => this.api.list() });
  protected readonly columns = computed(() =>
    this.pipelines()
      ? ['name', 'products', 'services', 'pipelines', 'actions']
      : ['name', 'products', 'actions'],
  );
  protected readonly summary = computed(() => {
    const departments = this.departments.hasValue() ? this.departments.value() : [];
    const sum = (count: (department: Department) => number) =>
      departments.reduce((total, department) => total + count(department), 0);
    return [
      counted(departments.length, 'department'),
      counted(
        sum((department) => department.productCount),
        'product',
      ),
      ...(this.pipelines()
        ? [
            counted(
              sum((department) => department.serviceCount),
              'service',
            ),
          ]
        : []),
    ].join(' · ');
  });
  protected readonly chart = computed<BarRow[]>(() =>
    this.pipelines() && this.departments.hasValue()
      ? this.departments.value().map((department) => ({
          label: department.name,
          note: `${counted(department.pipelineCount, 'pipeline')} · ${counted(department.productCount, 'product')}`,
          segments: [
            { swatch: 'active', label: 'active', count: department.activePipelineCount },
            {
              swatch: 'disabled',
              label: 'invalidated',
              count: department.pipelineCount - department.activePipelineCount,
            },
          ],
        }))
      : [],
  );

  protected readonly errorMessage = errorMessage;

  protected invalidated(department: Department): number {
    return department.pipelineCount - department.activePipelineCount;
  }

  protected deleteHint(department: Department): string | null {
    return department.productCount
      ? `${department.name} still has ${counted(department.productCount, 'product')}. ` +
          'Move them to another department first.'
      : null;
  }

  protected add(): void {
    this.edit(null, (saved) => `${saved.name} added`);
  }

  protected rename(department: Department): void {
    this.edit(department, (saved) => `${department.name} renamed to ${saved.name}`);
  }

  protected delete(department: Department): void {
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
        data: {
          title: `Delete ${department.name}?`,
          message: 'The department has no products. It is removed from the portal.',
          confirmLabel: 'Delete department',
          danger: true,
        },
      })
      .afterClosed()
      .pipe(
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
      .open<DepartmentDialog, Department | null, Department>(DepartmentDialog, { data: department })
      .afterClosed()
      .pipe(filter((saved): saved is Department => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.departments.reload();
      });
  }
}
