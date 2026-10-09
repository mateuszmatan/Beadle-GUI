import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { text, toast } from '../testing/dom';
import { Notifier } from './notifier';

describe('Notifier', () => {
  let notifier: Notifier;
  let announce: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    notifier = TestBed.inject(Notifier);
    announce = vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockResolvedValue();
  });

  afterEach(() => notifier.dismiss());

  const settled = () => TestBed.inject(ApplicationRef).whenStable();

  it('shows one toast at a time and announces it politely, an error assertively', async () => {
    notifier.success('CertScanner is saved');
    await settled();

    expect(text(toast())).toContain('CertScanner is saved');
    expect(announce).toHaveBeenCalledWith('CertScanner is saved', 'polite');

    notifier.error(new Error('The portal API cannot be reached.'));
    await settled();

    expect(document.querySelectorAll('dso-toast')).toHaveLength(1);
    expect(toast()!.classList).toContain('error');
    expect(announce).toHaveBeenLastCalledWith(expect.any(String), 'assertive');
  });
});
