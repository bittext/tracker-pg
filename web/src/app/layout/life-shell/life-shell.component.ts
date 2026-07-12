import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { catchError, filter, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { APP_DISPLAY_NAME, APP_SHORT_NAME } from '../../app-branding';
import { ThemeSettingsComponent } from '../../components/theme-settings/theme-settings.component';
import { WEB_RELEASE_VERSION } from '../../release-version';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

interface ApiVersionPayload {
  name: string;
  group: string;
  artifact: string;
  version: string;
  buildTime: string | null;
}

interface ShellNavItem {
  path: string;
  label: string;
  icon: string;
  exact?: boolean;
  adminOnly?: boolean;
  marketsOnly?: boolean;
}

interface ShellNavGroup {
  title?: string;
  items: ShellNavItem[];
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
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly http = inject(HttpClient);
  private readonly documentTitle = inject(Title);

  readonly brandTitle = APP_SHORT_NAME;
  readonly webReleaseVersion = WEB_RELEASE_VERSION;
  apiRelease: ApiVersionPayload | null = null;

  readonly navGroups: ShellNavGroup[] = [
    {
      items: [{ path: '/life/welcome', label: 'Welcome', icon: 'home', exact: true }],
    },
    {
      title: 'Wellness',
      items: [{ path: '/life/exercise', label: 'Exercise', icon: 'fitness_center', exact: true }],
    },
    {
      title: 'Productivity',
      items: [{ path: '/life/management', label: 'Management', icon: 'dashboard', exact: true }],
    },
    {
      items: [{ path: '/life/journal', label: 'Journal', icon: 'menu_book', exact: true }],
    },
    {
      items: [{ path: '/life/money', label: 'Money', icon: 'account_balance_wallet', exact: true }],
    },
    {
      title: 'Insights',
      items: [
        { path: '/life/insights/exercise', label: 'Exercise trends', icon: 'trending_up', exact: true },
        { path: '/life/insights/management', label: 'Management', icon: 'bar_chart', exact: true },
        { path: '/life/insights/journal', label: 'Journal', icon: 'auto_stories', exact: true },
        { path: '/life/insights/banking', label: 'Banking', icon: 'account_balance', exact: true },
      ],
    },
    {
      items: [
        { path: '/life/settings', label: 'Settings', icon: 'settings', exact: true },
        { path: '/life/contact', label: 'Contact', icon: 'mail_outline', exact: true },
      ],
    },
  ];

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

  get releaseHintTooltip(): string {
    const web = `Web ${this.webReleaseVersion}`;
    if (this.apiRelease?.version) {
      const api = `API ${this.apiRelease.version}`;
      const time = this.apiRelease.buildTime ? ` · ${this.apiRelease.buildTime}` : '';
      return `${web} · ${api}${time}`;
    }
    return `${web} (API not loaded)`;
  }

  get username(): string {
    return this.auth.username ?? '';
  }

  visibleItems(group: ShellNavGroup): ShellNavItem[] {
    return group.items.filter((item) => {
      if (item.adminOnly && !this.auth.isAdmin()) {
        return false;
      }
      if (item.marketsOnly && !this.auth.canAccessMarkets()) {
        return false;
      }
      return true;
    });
  }

  logout(): void {
    this.auth.logout(true);
  }
}
