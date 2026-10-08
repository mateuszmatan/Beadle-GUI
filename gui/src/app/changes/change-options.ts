import { Injectable, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ChangeOptions, ChangeType, ChangesApi, labelOf } from './change-api';

@Injectable({ providedIn: 'root' })
export class ChangeOptionLists {
  private readonly api = inject(ChangesApi);

  readonly loaded = rxResource({ stream: () => this.api.options() });
  readonly options = computed<ChangeOptions | null>(() =>
    this.loaded.hasValue() ? this.loaded.value() : null,
  );

  typeLabel(type: ChangeType): string {
    return labelOf(this.options()?.types ?? [], type);
  }
}
