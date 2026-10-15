import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { APP_NAME } from '../core/app-name';
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
}
