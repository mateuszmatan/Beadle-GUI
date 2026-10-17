import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/core/app-config';
import { AppShell } from './app/shell/app-shell';

bootstrapApplication(AppShell, appConfig).catch((err) => console.error(err));
