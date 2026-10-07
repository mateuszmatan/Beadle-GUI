import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/errors';
import { CHANGES, beadleProduct } from '../core/sections';
import { ChangesApi } from './change-api';
import { ChangeSummary } from './change-summary';

@Component({
  selector: 'dso-change-detail',
  imports: [RouterLink, MatButtonModule, MatProgressBarModule, ChangeSummary],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a routerLink="/beadle/changes">{{ section.heading }}</a>
        <span class="sep" aria-hidden="true">/</span>
        <span>{{ change.hasValue() ? change.value().number : 'Change' }}</span>
      </nav>
      @if (change.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (change.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      }
      @if (change.hasValue()) {
        @let c = change.value();
        <header class="page-header">
          <div>
            <h1>{{ c.number }}</h1>
            <p>{{ c.shortDescription }}</p>
          </div>
          <div class="actions">
            @if (c.url) {
              <a mat-stroked-button [href]="c.url" target="_blank" rel="noopener">ServiceNow</a>
            }
            @if (c.productId) {
              <a mat-stroked-button [routerLink]="productLink(c.productId)">Product</a>
            }
          </div>
        </header>
        <section class="card block">
          <h2>Summary</h2>
          <dso-change-summary [change]="c" />
        </section>
        <div class="columns">
          <section class="card block">
            <h2>Change tasks</h2>
            <ol class="tasks">
              @for (task of c.tasks; track task.number) {
                <li>
                  <span class="mono">{{ task.number }}</span>
                  <strong>{{ task.shortDescription }}</strong>
                  <span class="muted">{{ task.description }}</span>
                </li>
              }
            </ol>
          </section>
          <section class="card block">
            <h2>Description</h2>
            <pre class="text">{{ c.description }}</pre>
          </section>
        </div>
      }
    </div>
  `,
  styles: `
    .columns {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 12px;
      margin-top: 12px;
    }

    .block {
      padding: 12px 16px;

      h2 {
        margin: 0 0 8px;
        font-size: 15px;
      }
    }

    .text {
      margin: 0;
      font: inherit;
      font-size: 12.5px;
      white-space: pre-wrap;
    }

    .tasks {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0;
      padding-left: 20px;
      font-size: 12.5px;

      li {
        display: flex;
        flex-direction: column;
      }
    }

    @media (max-width: 900px) {
      .columns {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ChangeDetail {
  readonly id = input.required({ transform: numberAttribute });

  protected readonly section = CHANGES;
  protected readonly errorMessage = errorMessage;
  protected readonly productLink = beadleProduct;
  private readonly api = inject(ChangesApi);
  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
}
