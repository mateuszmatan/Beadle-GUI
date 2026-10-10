import { DEFAULT_DIALOG_CONFIG, Dialog, DialogRef } from '@angular/cdk/dialog';
import { ApplicationInitStatus, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  TitleStrategy,
  provideRouter,
} from '@angular/router';
import { SvgIconRegistryService } from 'angular-svg-icon';
import { Observable, firstValueFrom, of } from 'rxjs';
import { ConfirmDialog } from '../shared/confirm-dialog';
import { APP_NAME } from './app-name';
import { appConfig } from './app-config';
import { PortalTitleStrategy } from './title-strategy';
import { HasUnsavedChanges, unsavedChangesGuard } from './unsaved-changes';

@Component({ template: '' })
class Blank {}

describe('PortalTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'titled', title: 'Edit product', component: Blank },
          { path: 'untitled', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: PortalTitleStrategy },
        { provide: APP_NAME, useValue: 'Test Portal' },
      ],
    });
  });

  it('puts the page title before the name of the app', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/titled');
    expect(TestBed.inject(Title).getTitle()).toBe('Edit product · BBH Test Portal');

    await router.navigateByUrl('/untitled');
    expect(TestBed.inject(Title).getTitle()).toBe('BBH Test Portal');
  });
});

describe('unsavedChangesGuard', () => {
  const page = (unsaved: boolean): HasUnsavedChanges => ({ hasUnsavedChanges: () => unsaved });

  function guard(unsaved: boolean) {
    return TestBed.runInInjectionContext(() =>
      unsavedChangesGuard(
        page(unsaved),
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      ),
    );
  }

  it('lets the page go when nothing is unsaved', () => {
    expect(guard(false)).toBe(true);
  });

  it('asks before discarding changes and stays unless the user confirms', async () => {
    const open = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValueOnce({ closed: of(true) } as unknown as DialogRef<unknown>)
      .mockReturnValueOnce({ closed: of(undefined) } as unknown as DialogRef<unknown>);

    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(true);
    expect(await firstValueFrom(guard(true) as Observable<boolean>)).toBe(false);
    expect(open).toHaveBeenCalledWith(ConfirmDialog, {
      data: expect.objectContaining({ title: 'Discard your changes?', danger: true }),
    });
  });
});

describe('appConfig', () => {
  it('names the app, sets the title strategy, square dialogs and registers the icons', async () => {
    const routes = [{ path: 'home', component: Blank }];
    TestBed.configureTestingModule({ providers: appConfig('Test Portal', routes).providers });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(TestBed.inject(APP_NAME)).toBe('Test Portal');
    expect(TestBed.inject(Router).config).toEqual(routes);
    expect(TestBed.inject(TitleStrategy)).toBeInstanceOf(PortalTitleStrategy);
    expect(TestBed.inject(DEFAULT_DIALOG_CONFIG)).toEqual(
      expect.objectContaining({ panelClass: 'dso-dialog' }),
    );
    const svg = await firstValueFrom(
      TestBed.inject(SvgIconRegistryService).getSvgByName('search')!,
    );
    expect(svg?.tagName.toLowerCase()).toBe('svg');
  });
});
