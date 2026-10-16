import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { DRAFT_KEPT, DRAFT_KEY, DRAFT_LOST, SESSION_ENDED, SessionEnd } from './session-end';

describe('SessionEnd', () => {
  let sessionEnd: SessionEnd;
  let signIn: ReturnType<typeof vi.fn<() => void>>;
  let answers: Subject<boolean | undefined>;
  let open: ReturnType<typeof vi.spyOn>;

  const page = (unsaved: boolean, draft?: unknown) => ({
    hasUnsavedChanges: () => unsaved,
    ...(draft === undefined ? {} : { draftName: 'new-change', draft: () => draft }),
  });
  const kept = () => JSON.parse(sessionStorage.getItem(DRAFT_KEY) ?? 'null');

  beforeEach(() => {
    signIn = vi.fn<() => void>();
    answers = new Subject();
    sessionEnd = TestBed.inject(SessionEnd);
    open = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({ closed: answers } as unknown as DialogRef<unknown>);
  });

  afterEach(() => sessionStorage.clear());

  it('signs in again at once while no page or a page without unsaved changes is open', () => {
    sessionEnd.ended(signIn);
    sessionEnd.shown(page(false, { step: 2 }));
    sessionEnd.ended(signIn);

    expect(signIn).toHaveBeenCalledTimes(2);
    expect(open).not.toHaveBeenCalled();
    expect(kept()).toBeNull();
  });

  it('keeps the draft of a page with unsaved changes and signs in again once the user chooses to', () => {
    sessionEnd.shown(page(true, { step: 2, texts: ['Mine'] }));

    sessionEnd.ended(signIn);
    sessionEnd.ended(signIn);

    expect(open).toHaveBeenCalledTimes(1);
    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: {
        title: SESSION_ENDED,
        message: DRAFT_KEPT,
        confirmLabel: 'Sign in again',
        cancelLabel: 'Stay on this page',
      },
    });
    expect(kept()).toEqual({ page: 'new-change', draft: { step: 2, texts: ['Mine'] } });
    expect(signIn).not.toHaveBeenCalled();

    answers.next(true);
    expect(signIn).toHaveBeenCalledTimes(1);
    expect(sessionEnd.takeDraft('changes')).toBeNull();
    expect(sessionEnd.takeDraft('new-change')).toEqual({ step: 2, texts: ['Mine'] });
    expect(sessionEnd.takeDraft('new-change')).toBeNull();
  });

  it('lets the user stay on the page to copy what they typed, and asks again on the next end', () => {
    sessionEnd.shown(page(true));

    sessionEnd.ended(signIn);
    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: expect.objectContaining({ message: DRAFT_LOST }),
    });
    answers.next(undefined);
    sessionEnd.ended(signIn);

    expect(signIn).not.toHaveBeenCalled();
    expect(open).toHaveBeenCalledTimes(2);
    expect(kept()).toBeNull();
  });

  it('gives up the draft of a page the user leaves within the app, not the draft of another page', () => {
    const wizard = page(true, { step: 2 });
    const other = page(false);
    sessionEnd.shown(wizard);
    sessionEnd.ended(signIn);

    sessionEnd.hidden(other);
    expect(kept()).not.toBeNull();
    sessionEnd.hidden(wizard);
    expect(kept()).toBeNull();
  });

  it('says that the changes are lost when the tab cannot keep the draft', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    sessionEnd.shown(page(true, { step: 2 }));

    sessionEnd.ended(signIn);

    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: expect.objectContaining({ message: DRAFT_LOST }),
    });
    vi.restoreAllMocks();
  });

  it('takes no draft from a value it did not write', () => {
    sessionStorage.setItem(DRAFT_KEY, '{not json');
    expect(sessionEnd.takeDraft('new-change')).toBeNull();
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ draft: { step: 2 } }));
    expect(sessionEnd.takeDraft('new-change')).toBeNull();
  });
});
