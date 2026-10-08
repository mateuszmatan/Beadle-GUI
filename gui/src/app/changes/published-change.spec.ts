import { TestBed } from '@angular/core/testing';
import { productionChange } from '../testing/change-fixtures';
import { PublishedChange } from './published-change';

describe('PublishedChange', () => {
  it('hands the published change once to the page of that change', () => {
    const published = TestBed.inject(PublishedChange);
    const change = productionChange();

    published.hand(change);
    expect(published.take(8)).toBeNull();
    expect(published.take(7)).toBeNull();

    published.hand(change);
    expect(published.take(7)).toBe(change);
    expect(published.take(7)).toBeNull();
  });
});
