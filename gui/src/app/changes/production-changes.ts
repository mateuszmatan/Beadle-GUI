import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/errors';
import { CHANGES } from '../core/sections';
import { RelativeTimePipe } from '../shared/formatting';
import { ChangesApi, RISKS, labelOf } from './change-api';
import { windowText } from './change-model';
import { IntegrationNote } from './integration-note';

@Component({
  selector: 'dso-production-changes',
  imports: [RouterLink, MatButtonModule, MatProgressBarModule, RelativeTimePipe, IntegrationNote],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>{{ section.heading }}</h1>
          <p class="page-description">{{ section.description }}</p>
        </div>
        <div class="actions">
          <a mat-flat-button routerLink="/beadle/changes/new">Raise a production change</a>
        </div>
      </header>
      <dso-integration-note />
      @if (changes.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (changes.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (changes.hasValue()) {
        @if (changes.value().length) {
          <section class="card table-scroll">
            <table class="changes">
              <thead>
                <tr>
                  <th>Change</th>
                  <th>Product</th>
                  <th>Window</th>
                  <th>Short description</th>
                  <th>Risk</th>
                  <th>Tasks</th>
                  <th>Raised</th>
                </tr>
              </thead>
              <tbody>
                @for (change of changes.value(); track change.id) {
                  <tr>
                    <td>
                      <a class="mono quiet-link" [routerLink]="['/beadle/changes', change.id]">{{
                        change.number
                      }}</a>
                    </td>
                    <td>
                      {{ change.productName }}
                      <span class="muted">{{ change.departmentName }}</span>
                    </td>
                    <td>{{ windowText(change.window.start, change.window.end) }}</td>
                    <td>{{ change.shortDescription }}</td>
                    <td>{{ riskLabel(change.template.risk) }}</td>
                    <td>{{ change.tasks.length }}</td>
                    <td>{{ change.createdAt | relative }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        } @else {
          <div class="card empty-state">
            <h3>No production change yet</h3>
            <p>
              Choose a product, its Jira epics and stories and the change window. The portal writes
              the change and its change tasks.
            </p>
            <a mat-flat-button routerLink="/beadle/changes/new">Raise a production change</a>
          </div>
        }
      }
    </div>
  `,
  styles: `
    .changes {
      width: 100%;
      border-collapse: collapse;
      font-size: 12.5px;

      th {
        padding: 8px 10px;
        border-bottom: 1px solid var(--dso-border);
        color: var(--dso-muted);
        font-weight: 600;
        text-align: left;
        white-space: nowrap;
      }

      td {
        padding: 7px 10px;
        border-bottom: 1px solid var(--dso-border);
        vertical-align: top;
      }

      td .muted {
        display: block;
        font-size: 11.5px;
      }
    }
  `,
})
export class ProductionChanges {
  protected readonly section = CHANGES;
  protected readonly errorMessage = errorMessage;
  protected readonly windowText = windowText;
  protected readonly riskLabel = (value: string) => labelOf(RISKS, value);
  private readonly api = inject(ChangesApi);
  protected readonly changes = rxResource({ stream: () => this.api.list() });
}
