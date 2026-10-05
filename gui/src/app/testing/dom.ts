export const text = (element: ParentNode | null | undefined): string =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

export function fieldOf(root: ParentNode, label: string): HTMLElement | null {
  return (
    [...root.querySelectorAll<HTMLElement>('mat-form-field')].find(
      (field) => field.querySelector('mat-label')?.textContent?.trim() === label,
    ) ?? null
  );
}

export function inputOf(root: ParentNode, label: string): HTMLInputElement {
  return fieldOf(root, label)?.querySelector<HTMLInputElement>('input, textarea')!;
}

export function checkboxOf(root: ParentNode, label: string): HTMLInputElement {
  const box = [...root.querySelectorAll<HTMLElement>('mat-checkbox')].find((checkbox) =>
    text(checkbox).startsWith(label),
  );
  return box?.querySelector<HTMLInputElement>('input')!;
}
