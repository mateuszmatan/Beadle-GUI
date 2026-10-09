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
        this.chart = this.highcharts.chart(this.element, { ...DEFAULTS, ...this.options() });
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
