import { changeSchedule, changeTemplate, productionChange } from '../testing/change-fixtures';
import {
  implementationDateOf,
  secureCodingForm,
  secureCodingRequest,
  ticketName,
} from './secure-coding-model';

describe('secure coding model', () => {
  const change = productionChange({
    schedule: changeSchedule({ installationStart: '2026-03-05T12:00:00Z' }),
  });

  it('writes the implementation date as the local day of the installation start, MMDDYYYY', () => {
    const start = new Date(2026, 2, 5, 23, 30);
    expect(implementationDateOf(start.toISOString())).toBe('03052026');
    expect(implementationDateOf(new Date(2026, 11, 31, 0, 0).toISOString())).toBe('12312026');
    expect(implementationDateOf(null)).toBe('');
    expect(implementationDateOf(undefined)).toBe('');
  });

  it('starts from the secure coding defaults of the change and the day of its installation', () => {
    const form = secureCodingForm(change);

    expect(form.getRawValue()).toEqual({
      apoNumber: 'APO-12345',
      implementationDate: implementationDateOf('2026-03-05T12:00:00Z'),
      bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
      artifactLink: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
      qcApplicationLink: 'https://cert.qc.bbh.com',
    });
    expect(form.valid).toBe(true);
  });

  it('requires every input and a link for the scans', () => {
    const form = secureCodingForm(
      productionChange({
        template: changeTemplate({
          secureCoding: {
            apoNumber: null,
            bitbucketUrl: 'bitbucket.bbh.com/projects/CERT',
            artifactLink: null,
            qcApplicationLink: `https://${'q'.repeat(493)}`,
          },
        }),
      }),
    );

    expect(form.controls.apoNumber.hasError('required')).toBe(true);
    expect(form.controls.bitbucketUrl.hasError('pattern')).toBe(true);
    expect(form.controls.artifactLink.hasError('required')).toBe(true);
    expect(form.controls.qcApplicationLink.invalid).toBe(true);
    form.controls.apoNumber.setValue('A'.repeat(41));
    expect(form.controls.apoNumber.hasError('bytes')).toBe(true);
  });

  it('names the ticket APO-ID_APP-NAME-IMPLEMENTATION-DATE', () => {
    const form = secureCodingForm(change);
    const date = form.controls.implementationDate.value;

    expect(ticketName(form, 'CertScanner')).toBe(`APO-12345_CertScanner-${date}`);
    form.controls.apoNumber.setValue('  ');
    expect(ticketName(form, 'CertScanner')).toBe(`APO-ID_CertScanner-${date}`);
  });

  it('sends the trimmed inputs at the version of the change for the chosen department', () => {
    const form = secureCodingForm(change);
    form.patchValue({ apoNumber: ' APO-777 ', qcApplicationLink: ' https://cert-qc.bbh.com ' });

    expect(secureCodingRequest(form, change, 3)).toEqual({
      version: 4,
      departmentId: 3,
      apoNumber: 'APO-777',
      implementationDate: form.controls.implementationDate.value,
      bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
      artifactLink: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
      qcApplicationLink: 'https://cert-qc.bbh.com',
    });
  });
});
