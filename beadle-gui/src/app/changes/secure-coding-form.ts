import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Fields, formRevision } from '@common/shared/fields';
import { FORM_FIELD } from '@common/ui/form-field';
import { SECURE_CODING_FIELDS } from './change-sections';
import { SecureCodingForm, ticketName } from './secure-coding-model';

@Component({
  selector: 'dso-secure-coding-form',
  imports: [FORM_FIELD, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="form-fields">
      <dso-fields [group]="form()" [fields]="fields" />
      <dso-form-field class="span-6 read-only">
        <dso-label>Ticket name in CyberTrack</dso-label>
        <input dsoInput readonly class="mono" [value]="name()" />
        <dso-hint>APO number, application name and implementation date</dso-hint>
      </dso-form-field>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class SecureCodingFields {
  readonly form = input.required<SecureCodingForm>();
  readonly applicationName = input.required<string>();

  private readonly revision = formRevision(() => this.form());

  protected readonly fields = SECURE_CODING_FIELDS;
  protected readonly name = computed(() => {
    this.revision();
    return ticketName(this.form(), this.applicationName());
  });
}
