import { HighchartsStatic } from 'highcharts/js/highcharts';

const drawn = new WeakMap<Element, Record<string, any>>();

export const fakeHighcharts: HighchartsStatic = {
  chart(element, options) {
    drawn.set(element, options as Record<string, any>);
    element.classList.add('drawn');
    return { destroy: () => element.classList.remove('drawn'), reflow: () => undefined };
  },
};

export function chartOptions(element: Element | null | undefined): any {
  return (element && drawn.get(element)) ?? {};
}
