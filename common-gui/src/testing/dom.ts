export const text = (element: ParentNode | null | undefined): string =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

export function fieldOf(root: ParentNode, label: string): HTMLElement | null {
  return (
    [...root.querySelectorAll<HTMLElement>('dso-form-field')].find(
      (field) => field.querySelector('dso-label')?.textContent?.trim() === label,
    ) ?? null
  );
}

export function inputOf(root: ParentNode, label: string): HTMLInputElement {
  return fieldOf(root, label)?.querySelector<HTMLInputElement>('input, textarea')!;
}

export function selectOf(root: ParentNode, label: string): HTMLSelectElement {
  return fieldOf(root, label)?.querySelector<HTMLSelectElement>('select')!;
}

export function choose(select: HTMLSelectElement, label: string): void {
  const option = [...select.options].find((entry) => text(entry) === label);
  if (!option) {
    throw new Error(`No option ${label} in ${[...select.options].map(text).join(', ')}`);
  }
  option.selected = true;
  select.dispatchEvent(new Event('change'));
}

export function optionsOf(select: HTMLSelectElement): string[] {
  return [...select.options].map(text);
}

export function checkboxOf(root: ParentNode, label: string): HTMLInputElement {
  const box = [...root.querySelectorAll<HTMLElement>('dso-checkbox')].find((checkbox) =>
    text(checkbox).startsWith(label),
  );
  return box?.querySelector<HTMLInputElement>('input')!;
}

export function buttonOf(root: ParentNode, label: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => text(button) === label || button.getAttribute('aria-label') === label,
  )!;
}

export function gridRows(root: ParentNode): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>('.ag-center-cols-container .ag-row')];
}

export function gridCell(row: ParentNode, column: string): HTMLElement {
  return row.querySelector<HTMLElement>(`.ag-cell[col-id="${column}"]`)!;
}

export function gridColumn(root: ParentNode, column: string): string[] {
  return gridRows(root).map((row) => text(gridCell(row, column)));
}

export function gridHeaders(root: ParentNode): string[] {
  return [...root.querySelectorAll('.ag-header-row-column .ag-header-cell')].map(text);
}

export function gridFilter(root: ParentNode, label: string): HTMLInputElement & HTMLSelectElement {
  return root.querySelector(`.ag-floating-filter [aria-label="Filter by ${label}"]`)!;
}

export async function sortBy(root: ParentNode, column: string): Promise<void> {
  root
    .querySelector<HTMLElement>(`.ag-header-cell[col-id="${column}"] .ag-header-cell-label`)!
    .click();
  await settleGrid();
}

export function settleGrid(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve));
}

export function dialogElement(): HTMLElement | null {
  return document.querySelector('.cdk-dialog-container');
}

export function toast(): HTMLElement | null {
  return document.querySelector('dso-toast');
}
