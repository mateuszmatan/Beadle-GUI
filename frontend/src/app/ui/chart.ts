import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  afterRenderEffect,
  inject,
  input,
} from '@angular/core';
import Highcharts, { Chart, HighchartsStatic } from 'highcharts/js/highcharts';

export const HIGHCHARTS = new InjectionToken<HighchartsStatic>('Highcharts', {
  providedIn: 'root',
  factory: () => Highcharts,
});

const ENTITIES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

interface Axis {
  categories?: string[];
}

interface Series {
  name?: string;
}

export function plainText(text: string): string {
  return text.replace(/[&<>"']/g, (character) => ENTITIES[character]);
}

function axisText(axis: Axis): Axis {
  return axis.categories ? { ...axis, categories: axis.categories.map(plainText) } : axis;
}

export function withPlainText(options: Record<string, unknown>): Record<string, unknown> {
  const { xAxis, series } = options as { xAxis?: Axis | Axis[]; series?: Series[] };
  return {
    ...options,
    ...(xAxis && { xAxis: Array.isArray(xAxis) ? xAxis.map(axisText) : axisText(xAxis) }),
    ...(series && {
      series: series.map((item) => (item.name ? { ...item, name: plainText(item.name) } : item)),
    }),
  };
}

const DEFAULTS = {
  title: { text: null },
  credits: { enabled: false },
  legend: { enabled: false },
};

@Component({
  selector: 'dso-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'dso-chart', role: 'img', '[attr.aria-label]': 'label()' },
  template: '',
  styles: ':host { display: block; }',
})
export class DsoChart {
  readonly options = input.required<Record<string, unknown>>();
  readonly label = input.required<string>();

  private readonly highcharts = inject(HIGHCHARTS);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private chart?: Chart;

  constructor() {
    afterRenderEffect({
      write: () => {
        this.chart?.destroy();
        this.chart = this.highcharts.chart(this.element, {
          ...DEFAULTS,
          ...withPlainText(this.options()),
        });
      },
    });
    const resized =
      typeof ResizeObserver === 'undefined'
        ? undefined
        : new ResizeObserver(() => this.chart?.reflow());
    resized?.observe(this.element);
    inject(DestroyRef).onDestroy(() => {
      resized?.disconnect();
      this.chart?.destroy();
    });
  }
}
