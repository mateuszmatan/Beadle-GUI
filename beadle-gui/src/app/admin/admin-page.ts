import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ADMIN, ADMIN_TABS } from '../core/sections';

@Component({
  selector: 'beadle-admin-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page admin">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
      </header>
      <nav class="tab-bar" [attr.aria-label]="section.heading">
        @for (tab of tabs; track tab.path) {
          <a [routerLink]="tab.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{
            tab.label
          }}</a>
        }
      </nav>
      <router-outlet />
    </div>
  `,
  styles: `
    .page-header {
      margin-bottom: 0;
      border-bottom: 0;
    }

    .tab-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 0 22px;
      margin-bottom: 12px;
      border-bottom: 1px solid var(--beadle-border);

      a {
        margin-bottom: -1px;
        padding: 6px 0 5px;
        border-bottom: 2px solid transparent;
        color: var(--beadle-muted);
        font-size: 13px;
        font-weight: 500;
        text-decoration: none;
        white-space: nowrap;

        &:hover,
        &:focus-visible {
          color: var(--beadle-navy);
        }

        &.active {
          color: var(--beadle-navy);
          border-bottom-color: var(--beadle-navy);
        }
      }
    }
  `,
})
export class AdminPage {
  protected readonly section = ADMIN;
  protected readonly tabs = ADMIN_TABS;
}
