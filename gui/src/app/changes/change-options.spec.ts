import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { changeOptions } from '../testing/change-fixtures';
import { ChangeOptionLists } from './change-options';

describe('ChangeOptionLists', () => {
  it('loads the lists once and names the change types by them', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const lists = TestBed.inject(ChangeOptionLists);
    TestBed.tick();

    expect(lists.options()).toBeNull();
    expect(lists.typeLabel('BUSINESS_CRITICAL')).toBe('BUSINESS_CRITICAL');
    http.expectOne('/api/changes/options').flush(changeOptions());
    await new Promise((resolve) => setTimeout(resolve));

    expect(lists.options()?.categories).toContain('Data Amendment');
    expect(lists.typeLabel('BUSINESS_CRITICAL')).toBe('Business Critical');
    http.verify();
  });
});
