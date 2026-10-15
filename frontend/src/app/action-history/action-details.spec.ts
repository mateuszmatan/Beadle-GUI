import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { buttonOf, text } from '../testing/dom';
import { actionRecord } from '../testing/fixtures';
import { ActionDetails, openDetails } from './action-details';
import { ActionRecord } from './action-history-model';

describe('ActionDetails', () => {
  let fixture: ComponentFixture<ActionDetails>;
  const close = vi.fn();

  async function render(record: ActionRecord) {
    TestBed.configureTestingModule({
      imports: [ActionDetails],
      providers: [
        { provide: DIALOG_DATA, useValue: record },
        { provide: DialogRef, useValue: { close } },
      ],
    });
    fixture = TestBed.createComponent(ActionDetails);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const rows = () =>
    Object.fromEntries(
      [...page().querySelectorAll('dl.rows dt')].map((term) => [
        text(term),
        text(term.nextElementSibling),
      ]),
    );

  afterEach(() => close.mockReset());

  it('shows who did what to which object, how it ended and the request as JSON', async () => {
    await render(actionRecord());

    expect(text(page().querySelector('h2'))).toBe('Change raised: Change CHG0031001');
    expect(rows()).toEqual({
      When: expect.stringMatching(/^15 Oct 2026, \d\d:00:05 in your time zone, /),
      User: 'Mateusz Matan',
      Object: 'Change CHG0031001',
      Outcome: 'Success',
      Duration: '812 ms',
      'Correlation id': '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10',
    });
    expect(page().querySelector('.chip')?.classList).toContain('success');
    expect(page().querySelector('.message')).toBeNull();
    expect(text(page().querySelector('dso-panel .panel-title'))).toBe('Request');
    expect(page().querySelector('dso-panel')?.classList).toContain('expanded');
    expect(page().querySelector('pre')?.textContent).toBe(
      '{\n  "command": {\n    "productId": 1,\n    "fixVersion": "CERT 4.2"\n  }\n}',
    );

    buttonOf(page(), 'Close').click();
    expect(close).toHaveBeenCalled();
  });

  it('shows the message of a failure and says when nothing was recorded for the request', async () => {
    await render(
      actionRecord({
        action: 'CALLBACK_INTERNAL',
        objectType: 'TICKET',
        objectId: null,
        objectKey: 'SCP-123',
        outcome: 'FAILURE',
        message: 'Beadle-ProTech did not answer',
        details: null,
        correlationId: null,
        durationMs: 5012,
      }),
    );

    expect(text(page().querySelector('h2'))).toBe('Beadle-Internal callback: Ticket SCP-123');
    expect(rows()).toMatchObject({ Outcome: 'Failure', Duration: '5.0 s', 'Correlation id': '–' });
    expect(page().querySelector('.chip')?.classList).toContain('danger');
    expect(text(page().querySelector('.message h3'))).toBe('Message');
    expect(text(page().querySelector('.message .banner'))).toBe('Beadle-ProTech did not answer');
    expect(page().querySelector('.message .banner')?.classList).toContain('danger');
    expect(page().querySelector('pre')).toBeNull();
    expect(text(page().querySelector('dso-panel .none'))).toBe(
      'Nothing was recorded for the request.',
    );
  });

  it('opens as a dialog with the record', () => {
    const dialog = { open: vi.fn() } as unknown as Dialog;
    const record = actionRecord();

    openDetails(dialog, record);

    expect(dialog.open).toHaveBeenCalledWith(ActionDetails, { data: record });
  });
});
