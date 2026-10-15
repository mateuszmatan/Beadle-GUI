import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from '@common/core/app-config';
import { App } from './app/app';
import { routes } from './app/app.routes';

bootstrapApplication(App, appConfig('Beadle', routes)).catch((err) => console.error(err));
