import { DOCUMENT } from '@angular/common';
import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { ConfirmDialog, ConfirmDialogData } from '../shared/confirm-dialog';
import { HasUnsavedChanges } from './unsaved-changes';

/** The draft of the page that was open when the session ended, kept in the tab while the browser signs in again. */
export const DRAFT_KEY = 'beadle.signIn.draft';

export const SESSION_ENDED = 'Your session ended';

export const DRAFT_KEPT =
  'Beadle signed you out, for example after a while without activity, before your changes on this page were saved. ' +
  'They are kept in this browser tab and come back on this page once you have signed in again.';

export const DRAFT_LOST =
  'Beadle signed you out, for example after a while without activity, before your changes on this page were saved. ' +
  'Signing in again starts this page anew, so stay on it first to copy what you need.';

/**
 * A page that keeps its unsaved entries over a new sign-in: {@link SessionEnd} keeps its draft in the tab under its
 * name, and the page takes it back with {@link SessionEnd.takeDraft} when it opens again after the sign-in.
 */
export interface KeepsDraft {
  readonly draftName: string;
  draft(): unknown;
}

type ShownPage = Partial<HasUnsavedChanges & KeepsDraft>;

interface KeptDraft {
  page: string;
  draft: unknown;
}

/**
 * What happens when the session at Beadle ends while a page is open: an API call answers 401 because the session
 * timed out or the pod that held it went away. A page without unsaved changes signs in again at once; a page with
 * them asks first, and keeps its draft in the tab if it can, so the work is not lost on the way through Ping.
 */
@Injectable({ providedIn: 'root' })
export class SessionEnd {
  private readonly dialog = inject(Dialog);
  private readonly document = inject(DOCUMENT);
  private page: ShownPage | null = null;
  private asking = false;

  /** The page the router outlet of the shell shows now. */
  shown(page: object): void {
    this.page = page as ShownPage;
  }

  /** The page the router outlet of the shell no longer shows: leaving it in the app gives up a draft it kept. */
  hidden(page: object): void {
    if (this.page !== page) {
      return;
    }
    const name = this.page.draftName;
    this.page = null;
    if (name && this.kept()?.page === name) {
      this.forget();
    }
  }

  /**
   * The session ended: `signIn` sends the browser to sign in again, at once without unsaved changes, else once the
   * user chose to in the dialog, which says whether the changes are kept. Calls that fail meanwhile ask no more.
   */
  ended(signIn: () => void): void {
    if (this.asking) {
      return;
    }
    const page = this.page;
    if (!page?.hasUnsavedChanges?.()) {
      signIn();
      return;
    }
    this.asking = true;
    const kept = this.keep(page);
    this.dialog
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, {
        data: {
          title: SESSION_ENDED,
          message: kept ? DRAFT_KEPT : DRAFT_LOST,
          confirmLabel: 'Sign in again',
          cancelLabel: 'Stay on this page',
        },
      })
      .closed.subscribe((again) => {
        this.asking = false;
        if (again === true) {
          signIn();
        }
      });
  }

  /** The draft the page of this name kept before the last sign-in, once; null when it kept none. */
  takeDraft<T>(page: string): T | null {
    const kept = this.kept();
    if (kept?.page !== page) {
      return null;
    }
    this.forget();
    return kept.draft as T;
  }

  private keep(page: ShownPage): boolean {
    if (!page.draftName || !page.draft) {
      return false;
    }
    try {
      const kept: KeptDraft = { page: page.draftName, draft: page.draft() };
      this.storage()?.setItem(DRAFT_KEY, JSON.stringify(kept));
      return this.storage() !== null;
    } catch {
      return false;
    }
  }

  private kept(): KeptDraft | null {
    try {
      const kept: unknown = JSON.parse(this.storage()?.getItem(DRAFT_KEY) ?? 'null');
      return kept && typeof kept === 'object' && typeof (kept as KeptDraft).page === 'string'
        ? (kept as KeptDraft)
        : null;
    } catch {
      return null;
    }
  }

  private forget(): void {
    try {
      this.storage()?.removeItem(DRAFT_KEY);
    } catch {
      // Without session storage no draft was kept.
    }
  }

  private storage(): Storage | null {
    return this.document.defaultView?.sessionStorage ?? null;
  }
}
