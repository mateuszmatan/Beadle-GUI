import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminArea } from '../core/sections';

@Component({
  selector: 'dso-admin-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page admin">
      <header class="page-header">
        <div>
          <h1>{{ area().section.heading }}</h1>
          <p class="page-description">{{ area().section.description }}</p>
        </div>
      </header>
      <nav class="tab-bar" [attr.aria-label]="area().section.heading">
        @for (tab of area().tabs; track tab.path) {
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
      border-bottom: 1px solid var(--dso-border);

      a {
        margin-bottom: -1px;
        padding: 6px 0 5px;
        border-bottom: 2px solid transparent;
        color: var(--dso-muted);
        font-size: 13px;
        font-weight: 500;
        text-decoration: none;
        white-space: nowrap;

        &:hover,
        &:focus-visible {
          color: var(--dso-navy);
        }

        &.active {
          color: var(--dso-navy);
          border-bottom-color: var(--dso-navy);
        }
      }
    }
  `,
})
export class AdminPage {
  readonly area = input.required<AdminArea>();
}
