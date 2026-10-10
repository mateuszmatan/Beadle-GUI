import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { APP_NAME } from './app-name';

@Injectable()
export class PortalTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly app = `BBH ${inject(APP_NAME)}`;

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(page ? `${page} · ${this.app}` : this.app);
  }
}
