import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Page titles read "Add product · BBH DevSecOps Management Portal". */
@Injectable()
export class PortalTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(
      page ? `${page} · BBH DevSecOps Management Portal` : 'BBH DevSecOps Management Portal',
    );
  }
}
