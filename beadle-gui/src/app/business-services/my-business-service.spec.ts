import { TestBed } from '@angular/core/testing';
import { MY_BUSINESS_SERVICE_KEY, MyBusinessService } from './my-business-service';

describe('MyBusinessService', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(MY_BUSINESS_SERVICE_KEY);
  });

  it('remembers the chosen business service in the browser', () => {
    localStorage.setItem(MY_BUSINESS_SERVICE_KEY, '5');
    const businessService = TestBed.inject(MyBusinessService);
    expect(businessService.businessServiceId()).toBe(5);

    businessService.choose(3);
    expect(businessService.businessServiceId()).toBe(3);
    expect(localStorage.getItem(MY_BUSINESS_SERVICE_KEY)).toBe('3');

    businessService.choose(null);
    expect(businessService.businessServiceId()).toBeNull();
    expect(localStorage.getItem(MY_BUSINESS_SERVICE_KEY)).toBeNull();
  });

  it('ignores what is not a business service id', () => {
    localStorage.setItem(MY_BUSINESS_SERVICE_KEY, 'x1');
    expect(TestBed.inject(MyBusinessService).businessServiceId()).toBeNull();
  });

  it('works without the storage of the browser', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const businessService = TestBed.inject(MyBusinessService);
    expect(businessService.businessServiceId()).toBeNull();

    businessService.choose(3);
    expect(businessService.businessServiceId()).toBe(3);
  });
});
