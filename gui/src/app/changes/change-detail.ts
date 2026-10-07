import { ChangeDetectionStrategy, Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../core/errors';
import { CHANGES } from '../core/sections';
import { ChangesApi, IMPACTS, RISKS, TYPES, labelOf } from './change-api';
import { windowText } from './change-model';

@Component({
  selector: 'dso-change-detail',
  imports: [RouterLink, MatButtonModule, MatProgressBarModule],
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
        @let t = c.template;
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
              <a mat-stroked-button [routerLink]="['/beadle/admin/products', c.productId]"
                >Product</a
              >
            }
          </div>
        </header>
        <div class="columns">
          <section class="card block">
            <h2>Change</h2>
            <dl class="rows">
              <dt>Product</dt>
              <dd>{{ c.productName }} ({{ c.productCode }})</dd>
              <dt>Department</dt>
              <dd>{{ c.departmentName ?? 'not set' }}</dd>
              <dt>Window</dt>
              <dd>{{ windowText(c.window.start, c.window.end) }}</dd>
              <dt>Type</dt>
              <dd>{{ label(types, t.type) }} · {{ t.category }}</dd>
              <dt>Risk and impact</dt>
              <dd>{{ label(risks, t.risk) }} risk · {{ label(impacts, t.impact) }} impact</dd>
              <dt>Configuration item</dt>
              <dd>{{ t.configurationItem }}</dd>
              <dt>Assignment group</dt>
              <dd>{{ t.assignmentGroup }}</dd>
              <dt>Approvers</dt>
              <dd>{{ t.approvers.join(', ') }}</dd>
              <dt>Jira</dt>
              <dd class="mono">{{ c.epicKeys.concat(c.storyKeys).join(' ') }}</dd>
            </dl>
            <h3>Risk assessment</h3>
            <p class="text">{{ t.riskAssessment }}</p>
          </section>
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
        </div>
        <section class="card block">
          <h2>Description</h2>
          <pre class="text">{{ c.description }}</pre>
          <h3>Implementation plan</h3>
          <p class="text">{{ t.implementationPlan }}</p>
          <h3>Backout plan</h3>
          <p class="text">{{ t.backoutPlan }}</p>
          <h3>Test plan</h3>
          <p class="text">{{ t.testPlan }}</p>
        </section>
      }
    </div>
  `,
  styles: `
    .columns {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 12px;
      margin-bottom: 12px;
    }

    .block {
      padding: 12px 16px;

      h2 {
        margin: 0 0 8px;
        font-size: 15px;
      }

      h3 {
        margin: 10px 0 2px;
        font-size: 13px;
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
  protected readonly windowText = windowText;
  protected readonly label = labelOf;
  protected readonly types = TYPES;
  protected readonly risks = RISKS;
  protected readonly impacts = IMPACTS;
  private readonly api = inject(ChangesApi);
  protected readonly change = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.get(params),
  });
}
