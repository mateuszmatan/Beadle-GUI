import { DepartmentUsage } from '@common/admin/departments-admin';
import { counted } from '@common/shared/formatting';
import { BeadleDepartment } from '../core/models';

export const DEPARTMENT_USAGE: DepartmentUsage<BeadleDepartment> = {
  subject: 'its changes',
  blocker: (department) =>
    department.changeCount
      ? `${department.name} cannot be deleted: it still has ${counted(department.changeCount, 'change')}.`
      : null,
};
