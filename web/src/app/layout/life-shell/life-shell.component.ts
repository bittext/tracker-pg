import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { APP_DISPLAY_NAME, APP_SHORT_NAME } from '../../app-branding';
import {
  LIFE_ADMIN_NAV,
  LIFE_PRIMARY_NAV,
  LIFE_SWITCH_NAV,
  NavEntry,
} from '../../config/app-nav.config';
import { ThemeSettingsComponent } from '../../components/theme-settings/theme-settings.component';
import { WEB_RELEASE_VERSION, formatReleaseUpdatedAt } from '../../release-version';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

interface ApiVersionPayload {
  name: string;
  group: string;
  artifact: string;
  version: string;
  buildTime: string | null;
}

@Component({
  selector: 'app-life-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    ThemeSettingsComponent,
  ],
  templateUrl: './life-shell.component.html',
  styleUrl: './life-shell.component.scss',
})
export class LifeShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly http = inject(HttpClient);
  private readonly documentTitle = inject(Title);

  readonly brandTitle = APP_SHORT_NAME;
  readonly webReleaseVersion = WEB_RELEASE_VERSION;
  readonly primaryNav = LIFE_PRIMARY_NAV;
  readonly marketsSwitch = LIFE_SWITCH_NAV;
  readonly adminNav = LIFE_ADMIN_NAV;
  apiRelease: ApiVersionPayload | null = null;

  ngOnInit(): void {
    this.documentTitle.setTitle(APP_DISPLAY_NAME);
    this.auth.refreshSession().pipe(catchError(() => of(null))).subscribe();
    const base = environment.apiBaseUrl || '';
    this.http
      .get<ApiVersionPayload>(`${base}/api/version`)
      .pipe(catchError(() => of(null as ApiVersionPayload | null)))
      .subscribe((v) => {
        this.apiRelease = v;
      });
  }

  get releaseUpdatedLabel(): string | null {
    return formatReleaseUpdatedAt(this.apiRelease?.buildTime);
  }

  get releaseHintTooltip(): string {
    const web = `Web ${this.webReleaseVersion}`;
    if (this.apiRelease?.version) {
      const api = `API ${this.apiRelease.version}`;
      const time = this.releaseUpdatedLabel
        ? ` · updated ${this.releaseUpdatedLabel} · last published ${this.releaseUpdatedLabel}`
        : '';
      return `${web} · ${api}${time}`;
    }
    return `${web} (API not loaded)`;
  }

  get username(): string {
    return this.auth.username ?? '';
  }

  visibleNav(items: NavEntry[]): NavEntry[] {
    return items.filter((item) => {
      if (item.adminOnly && !this.auth.isAdmin()) {
        return false;
      }
      return true;
    });
  }

  navAriaLabel(item: NavEntry): string | null {
    return item.ariaLabel ?? null;
  }

  logout(): void {
    this.auth.logout(true);
  }
}
