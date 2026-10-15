import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { UserApi } from '../core/api';
import { APP_NAME } from '../core/app-name';
import { BROWSER_NAVIGATION } from '../core/auth';
import { errorMessage } from '../core/errors';
import { Notifier } from '../core/notifier';
import { PortalSection } from '../core/sections';

@Component({
  selector: 'dso-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  readonly sections = input.required<readonly PortalSection[]>();

  protected readonly name = inject(APP_NAME);

  private readonly users = inject(UserApi);
  private readonly navigate = inject(BROWSER_NAVIGATION);
  private readonly notifier = inject(Notifier);

  private readonly me = rxResource({ stream: () => this.users.me() });
  /** The signed-in user once Beadle has named them; a 401 sends the browser to sign in instead. */
  protected readonly user = computed(() => (this.me.hasValue() ? this.me.value() : null));
  protected readonly signingOut = signal(false);

  /** Ends the session and starts the app again from `/`, which signs in anew when Beadle asks for it. */
  protected signOut(): void {
    if (this.signingOut()) {
      return;
    }
    this.signingOut.set(true);
    this.users.signOut().subscribe({
      next: () => this.navigate('/'),
      error: (error: unknown) => {
        this.signingOut.set(false);
        this.notifier.error(`You could not be signed out. ${errorMessage(error)}`);
      },
    });
  }
}
