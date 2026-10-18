import { counted } from '../shared/formatting';

export type Action =
  | 'CHANGE_PREVIEWED'
  | 'CHANGE_RAISED'
  | 'CHANGE_EDITED'
  | 'CHANGE_TASKS_CREATED'
  | 'SECURE_CODING_TICKET_CREATED'
  | 'REMINDER_SENT'
  | 'CHANGE_SYNCED'
  | 'PRODUCT_CREATED'
  | 'PRODUCT_UPDATED'
  | 'PRODUCT_DELETED'
  | 'CHANGE_PROFILE_UPDATED'
  | 'BUSINESS_SERVICE_CREATED'
  | 'BUSINESS_SERVICE_RENAMED'
  | 'BUSINESS_SERVICE_DELETED'
  | 'USER_SIGNED_IN'
  | 'CALLBACK_PROTECH'
  | 'CALLBACK_INTERNAL';

export type ActionOutcome = 'SUCCESS' | 'FAILURE';

export type ObjectType =
  'CHANGE' | 'PRODUCT' | 'CHANGE_PROFILE' | 'BUSINESS_SERVICE' | 'TICKET' | 'USER';

export interface ActionRecord {
  id: number;
  at: string;
  user: string;
  action: Action;
  objectType: ObjectType | null;
  objectId: number | null;
  objectKey: string | null;
  details: string | null;
  outcome: ActionOutcome;
  message: string | null;
  correlationId: string | null;
  durationMs: number;
}

export interface ActionHistoryPage {
  content: ActionRecord[];
  page: number;
  size: number;
  total: number;
}

/** What the filters of the page hold: an empty text means the filter is not used; days are `yyyy-mm-dd`. */
export interface ActionFilters {
  user: string;
  action: string;
  objectKey: string;
  outcome: string;
  from: string;
  to: string;
}

/** The query parameters of `GET /api/action-history`; the ones left out are not filtered on. */
export interface ActionHistoryQuery {
  user?: string;
  action?: string;
  objectKey?: string;
  outcome?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export const NO_FILTERS: ActionFilters = {
  user: '',
  action: '',
  objectKey: '',
  outcome: '',
  from: '',
  to: '',
};

export const ACTIONS: Record<Action, string> = {
  CHANGE_PREVIEWED: 'Change previewed',
  CHANGE_RAISED: 'Change raised',
  CHANGE_EDITED: 'Change edited',
  CHANGE_TASKS_CREATED: 'Change tasks created',
  SECURE_CODING_TICKET_CREATED: 'Secure coding ticket created',
  REMINDER_SENT: 'Reminder sent',
  CHANGE_SYNCED: 'Change synced from ProTech',
  PRODUCT_CREATED: 'Product created',
  PRODUCT_UPDATED: 'Product updated',
  PRODUCT_DELETED: 'Product deleted',
  CHANGE_PROFILE_UPDATED: 'Change template saved',
  BUSINESS_SERVICE_CREATED: 'Business service created',
  BUSINESS_SERVICE_RENAMED: 'Business service renamed',
  BUSINESS_SERVICE_DELETED: 'Business service deleted',
  USER_SIGNED_IN: 'Signed in',
  CALLBACK_PROTECH: 'ProTech callback',
  CALLBACK_INTERNAL: 'Beadle-Internal callback',
};

export const OUTCOMES: Record<ActionOutcome, string> = {
  SUCCESS: 'Success',
  FAILURE: 'Failure',
};

export const OBJECT_TYPES: Record<ObjectType, string> = {
  CHANGE: 'Change',
  PRODUCT: 'Product',
  CHANGE_PROFILE: 'Change template of product',
  BUSINESS_SERVICE: 'Business service',
  TICKET: 'Ticket',
  USER: 'User',
};

export const PAGE_SIZES: readonly number[] = [25, 50, 100, 200];
export const DEFAULT_PAGE_SIZE = 50;

const AT = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

export function actionText(action: string): string {
  return ACTIONS[action as Action] ?? action;
}

export function outcomeText(outcome: string): string {
  return OUTCOMES[outcome as ActionOutcome] ?? outcome;
}

/** The object as a person names it: the type, then the key (change number, product code, ticket key) or the id. */
export function objectText(
  record: Pick<ActionRecord, 'objectType' | 'objectId' | 'objectKey'>,
): string {
  if (!record.objectType) {
    return '–';
  }
  const type = OBJECT_TYPES[record.objectType] ?? record.objectType;
  const name = record.objectKey ?? (record.objectId === null ? null : `#${record.objectId}`);
  return name ? `${type} ${name}` : type;
}

export function atText(at: string): string {
  return AT.format(new Date(at));
}

export function durationText(milliseconds: number): string {
  return milliseconds < 1000
    ? `${milliseconds} ms`
    : `${(milliseconds / 1000).toFixed(milliseconds < 10_000 ? 1 : 0)} s`;
}

/**
 * The details pretty-printed when they are JSON, else as recorded: rows written before Beadle
 * trimmed long details inside the JSON may have been cut at 32 KB.
 */
export function detailsText(details: string | null): string {
  if (!details) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(details), null, 2);
  } catch {
    return details;
  }
}

/** The start of a day of the person's calendar as an instant, or undefined for no day. */
export function dayStart(day: string, later = 0): string | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) {
    return undefined;
  }
  const [, year, month, date] = match.map(Number);
  return new Date(year, month - 1, date + later).toISOString();
}

/** True when both days are given and the last one comes before the first. */
export function reversed(filters: Pick<ActionFilters, 'from' | 'to'>): boolean {
  return !!filters.from && !!filters.to && filters.to < filters.from;
}

/** The query of a page: the `to` day counts in full, so it ends where the next day starts. */
export function queryOf(filters: ActionFilters, page: number, size: number): ActionHistoryQuery {
  const query: ActionHistoryQuery = {
    user: filters.user || undefined,
    action: filters.action || undefined,
    objectKey: filters.objectKey.trim() || undefined,
    outcome: filters.outcome || undefined,
    from: dayStart(filters.from),
    to: dayStart(filters.to, 1),
    page,
    size,
  };
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined),
  ) as unknown as ActionHistoryQuery;
}

export function rangeText(page: ActionHistoryPage): string {
  if (!page.total) {
    return 'No rows';
  }
  if (!page.content.length) {
    return `${counted(page.total, 'row')}, none on this page`;
  }
  const first = page.page * page.size + 1;
  return `Rows ${first}–${first + page.content.length - 1} of ${page.total}`;
}

export function lastPage(page: ActionHistoryPage): number {
  return Math.max(0, Math.ceil(page.total / page.size) - 1);
}
