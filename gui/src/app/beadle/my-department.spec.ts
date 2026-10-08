import { TestBed } from '@angular/core/testing';
import { MY_DEPARTMENT_KEY, MyDepartment } from './my-department';

describe('MyDepartment', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(MY_DEPARTMENT_KEY);
  });

  it('remembers the chosen department in the browser', () => {
    localStorage.setItem(MY_DEPARTMENT_KEY, '5');
    const department = TestBed.inject(MyDepartment);
    expect(department.departmentId()).toBe(5);

    department.choose(3);
    expect(department.departmentId()).toBe(3);
    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBe('3');

    department.choose(null);
    expect(department.departmentId()).toBeNull();
    expect(localStorage.getItem(MY_DEPARTMENT_KEY)).toBeNull();
  });

  it('ignores what is not a department id', () => {
    localStorage.setItem(MY_DEPARTMENT_KEY, 'x1');
    expect(TestBed.inject(MyDepartment).departmentId()).toBeNull();
  });

  it('works without the storage of the browser', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const department = TestBed.inject(MyDepartment);
    expect(department.departmentId()).toBeNull();

    department.choose(3);
    expect(department.departmentId()).toBe(3);
  });
});
