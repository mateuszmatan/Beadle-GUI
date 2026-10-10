import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MyDepartment } from '@common/departments/my-department';
import { Notifier } from '@common/core/notifier';
import { changeTemplate, changeUpdate, productionChange } from '../testing/change-fixtures';
import { buttonOf, inputOf, text } from '@common/testing/dom';
import { ProductionChange } from './change-api';
import { ChangeSecureCoding, refusalOf } from './change-secure-coding';
import { PublishedChange } from './published-change';
import { implementationDateOf } from './secure-coding-model';

describe('refusalOf', () => {
  it('says why a change cannot get a secure coding ticket here', () => {
    expect(refusalOf(productionChange(), 3)).toBeNull();
    expect(refusalOf(productionChange({ state: 'CLOSED' }), 3)).toBe(
      'CHG0012345 is closed in ProTech and can no longer be changed',
    );
    expect(
      refusalOf(productionChange({ template: changeTemplate({ secureCodingTicket: 'SCP-1' }) }), 3),
    ).toBe('CHG0012345 already has the secure coding ticket SCP-1');
    expect(refusalOf(productionChange(), 5)).toBe('Only Corporate Technology can change it');
    expect(refusalOf(productionChange({ update: changeUpdate() }), 3)).toContain('ProTech');
  });
});

describe('ChangeSecureCoding', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChangeSecureCoding>;
  let navigate: ReturnType<typeof vi.spyOn>;

  const page = () => fixture.nativeElement as HTMLElement;
  const form = () => fixture.componentInstance['form']()!;
  const date = implementationDateOf(productionChange().schedule.installationStart);

  async function settle() {
    TestBed.tick();
    await new Promise((resolve) => setTimeout(resolve));
    TestBed.tick();
    fixture.detectChanges();
  }

  async function show(change: ProductionChange = productionChange(), departmentId = 3) {
    TestBed.inject(MyDepartment).choose(departmentId);
    fixture = TestBed.createComponent(ChangeSecureCoding);
    fixture.componentRef.setInput('id', 7);
    await settle();
    http.expectOne('/api/changes/7').flush(change);
    await settle();
  }

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await settle();
  }

  async function create() {
    buttonOf(page(), 'Create the secure coding ticket in CyberTrack').click();
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    TestBed.inject(MyDepartment).choose(null);
  });

  it('creates the secure coding ticket in CyberTrack from the defaults of the change and goes back to it', async () => {
    const success = vi.spyOn(TestBed.inject(Notifier), 'success');
    const hand = vi.spyOn(TestBed.inject(PublishedChange), 'hand');
    await show();

    expect(text(page().querySelector('h1'))).toBe('Secure coding ticket of CHG0012345');
    expect(text(page().querySelector('.breadcrumb'))).toBe(
      'Changes/CHG0012345/Secure coding ticket',
    );
    expect(inputOf(page(), 'APO number').value).toBe('APO-12345');
    expect(inputOf(page(), 'Implementation date').value).toBe(date);
    expect(inputOf(page(), 'Ticket name in CyberTrack').value).toBe(
      `APO-12345_CertScanner-${date}`,
    );
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);

    await type('APO number', 'APO-24680');
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(true);
    await create();

    const request = http.expectOne({ method: 'POST', url: '/api/changes/7/secure-coding' });
    expect(request.request.body).toEqual({
      version: 4,
      departmentId: 3,
      apoNumber: 'APO-24680',
      implementationDate: date,
      bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
      artifactLink: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
      qcApplicationLink: 'https://cert.qc.bbh.com',
    });
    const ticketed = productionChange({
      template: changeTemplate({ secureCodingTicket: 'SCP-1234' }),
    });
    request.flush(ticketed);
    await settle();

    expect(hand).toHaveBeenCalledWith(ticketed);
    expect(success).toHaveBeenCalledWith(
      'SCP-1234 is created in CyberTrack and sent to ProTech as the secure coding ticket of CHG0012345.',
    );
    expect(navigate).toHaveBeenCalledWith(['/changes', 7]);
    expect(fixture.componentInstance.hasUnsavedChanges()).toBe(false);
  });

  it('asks for every input before CyberTrack is asked and shows why CyberTrack refused it', async () => {
    await show();

    await type('Artifact link', ' ');
    await create();
    http.expectNone({ method: 'POST', url: '/api/changes/7/secure-coding' });
    expect(text(page().querySelector('.problems-banner strong'))).toBe(
      'Some fields need your attention.',
    );

    await type('Artifact link', 'https://nexus.bbh.com/cert-4.2.jar');
    await create();
    http.expectOne({ method: 'POST', url: '/api/changes/7/secure-coding' }).flush(
      {
        detail: 'must be a date written MMDDYYYY, such as 10152026',
        errors: [
          {
            field: 'implementationDate',
            message: 'must be a date written MMDDYYYY, such as 10152026',
          },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(text(page().querySelector('.problems-banner strong'))).toBe(
      'The secure coding ticket could not be created: must be a date written MMDDYYYY, such as 10152026',
    );
    expect([...page().querySelectorAll('.problems li')].map(text)).toEqual([
      'Implementation date: must be a date written MMDDYYYY, such as 10152026',
    ]);
    expect(form().controls.implementationDate.errors).toEqual({
      server: 'must be a date written MMDDYYYY, such as 10152026',
    });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('says why the ticket cannot be created here and offers the way back', async () => {
    await show(productionChange({ template: changeTemplate({ secureCodingTicket: 'SCP-7' }) }));

    expect(text(page().querySelector('.refused span'))).toBe(
      'CHG0012345 already has the secure coding ticket SCP-7',
    );
    expect(page().querySelector('dso-secure-coding-form')).toBeNull();
    expect(page().querySelector('.refused a')?.getAttribute('href')).toBe('/changes/7');
  });

  it('says why the change could not be loaded', async () => {
    TestBed.inject(MyDepartment).choose(3);
    fixture = TestBed.createComponent(ChangeSecureCoding);
    fixture.componentRef.setInput('id', 7);
    await settle();
    http
      .expectOne('/api/changes/7')
      .flush({ detail: 'Change 7 does not exist' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect(text(page().querySelector('.banner span'))).toBe(
      'The change could not be loaded: Change 7 does not exist',
    );
  });
});
