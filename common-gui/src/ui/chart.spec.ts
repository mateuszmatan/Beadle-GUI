import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { chartOptions } from '../testing/highcharts';
import { DsoChart, plainText, withPlainText } from './chart';

@Component({
  imports: [DsoChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<dso-chart [options]="options" label="Runs per department" />`,
})
class Host {
  readonly options = {
    xAxis: { categories: ['<a href="javascript:alert(1)">Custody</a>', 'R&D'] },
    series: [{ name: '<b>Passed</b>', data: [1, 2] }, { data: [3, 4] }],
  };
}

describe('DsoChart', () => {
  it('turns markup in user text into plain text before Highcharts reads it', () => {
    expect(plainText(`<img src=x onerror="alert('x')"> & more`)).toBe(
      '&lt;img src=x onerror=&quot;alert(&#39;x&#39;)&quot;&gt; &amp; more',
    );
  });

  it('draws categories and series names as plain text and keeps everything else', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();

    const options = chartOptions(fixture.nativeElement.querySelector('dso-chart'));
    expect(options.xAxis.categories).toEqual([
      '&lt;a href=&quot;javascript:alert(1)&quot;&gt;Custody&lt;/a&gt;',
      'R&amp;D',
    ]);
    expect(options.series).toEqual([
      { name: '&lt;b&gt;Passed&lt;/b&gt;', data: [1, 2] },
      { data: [3, 4] },
    ]);
    expect(options.credits).toEqual({ enabled: false });
  });

  it('handles several axes and options without axes or series', () => {
    expect(withPlainText({ xAxis: [{ categories: ['<x>'] }, { min: 0 }] })).toEqual({
      xAxis: [{ categories: ['&lt;x&gt;'] }, { min: 0 }],
    });
    expect(withPlainText({ chart: { type: 'bar' } })).toEqual({ chart: { type: 'bar' } });
  });
});
