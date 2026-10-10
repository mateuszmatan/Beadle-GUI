import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AppShell } from '@common/shell/app-shell';
import { SECTIONS } from './core/sections';

@Component({
  selector: 'beadle-root',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<dso-app-shell [sections]="sections" />',
})
export class App {
  protected readonly sections = SECTIONS;
}
