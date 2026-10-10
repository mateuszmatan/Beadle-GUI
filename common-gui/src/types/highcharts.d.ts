declare module 'highcharts/js/highcharts' {
  export interface Chart {
    destroy(): void;
    reflow(): void;
  }

  export interface HighchartsStatic {
    chart(renderTo: HTMLElement, options: object): Chart;
  }

  const Highcharts: HighchartsStatic;
  export default Highcharts;
}

declare const AG_GRID_LICENSE_KEY: string;
