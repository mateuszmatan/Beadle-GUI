import { FormGroup } from '@angular/forms';
import { filled, text, url } from '@common/shared/form-controls';
import { ProductionChange, SecureCoding, SecureCodingRequest } from './change-api';
import { fits } from './change-model';
import { LINK_MAX } from './change-template-model';

const twoDigits = (value: number) => String(value).padStart(2, '0');

export function implementationDateOf(installationStart: string | null | undefined): string {
  if (!installationStart) {
    return '';
  }
  const day = new Date(installationStart);
  return `${twoDigits(day.getMonth() + 1)}${twoDigits(day.getDate())}${day.getFullYear()}`;
}

export function secureCodingForm(change: Pick<ProductionChange, 'template' | 'schedule'>) {
  const defaults: SecureCoding = change.template.secureCoding;
  return new FormGroup({
    apoNumber: text(defaults.apoNumber, filled, fits(40)),
    implementationDate: text(implementationDateOf(change.schedule.installationStart)),
    bitbucketUrl: url(defaults.bitbucketUrl, LINK_MAX, filled),
    artifactLink: url(defaults.artifactLink, LINK_MAX, filled),
    qcApplicationLink: url(defaults.qcApplicationLink, LINK_MAX, filled),
  });
}

export type SecureCodingForm = ReturnType<typeof secureCodingForm>;

export function ticketName(form: SecureCodingForm, applicationName: string): string {
  const { apoNumber, implementationDate } = form.getRawValue();
  return `${apoNumber.trim() || 'APO-ID'}_${applicationName}-${implementationDate}`;
}

export function secureCodingRequest(
  form: SecureCodingForm,
  change: ProductionChange,
  departmentId: number,
): SecureCodingRequest {
  const value = form.getRawValue();
  return {
    version: change.version!,
    departmentId,
    apoNumber: value.apoNumber.trim(),
    implementationDate: value.implementationDate,
    bitbucketUrl: value.bitbucketUrl.trim(),
    artifactLink: value.artifactLink.trim(),
    qcApplicationLink: value.qcApplicationLink.trim(),
  };
}
