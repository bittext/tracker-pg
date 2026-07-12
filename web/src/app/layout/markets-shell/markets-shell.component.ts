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
import { ThemeSettingsComponent } from '../../components/theme-settings/theme-settings.component';
import { WEB_RELEASE_VERSION } from '../../release-version';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

interface ApiVersionPayload {
  version: string;
  buildTime: string | null;
}

interface MarketsNavItem {
  path: string;
  label: string;
  icon: string;
  exact?: boolean;
}

@Component({
  selector: 'app-markets-shell',
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
  templateUrl: './markets-shell.component.html',
  styleUrl: './markets-shell.component.scss',
})
export class MarketsShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly http = inject(HttpClient);
  private readonly documentTitle = inject(Title);

  readonly brandTitle = APP_SHORT_NAME;
  readonly webReleaseVersion = WEB_RELEASE_VERSION;
  apiRelease: ApiVersionPayload | null = null;

  readonly navItems: MarketsNavItem[] = [
    { path: '/markets/overview', label: 'Overview', icon: 'dashboard', exact: true },
    { path: '/markets/workspace', label: 'Workspace', icon: 'work', exact: true },
    { path: '/markets/analytics', label: 'Analytics', icon: 'analytics', exact: true },
    { path: '/markets/execution', label: 'Execution', icon: 'bolt', exact: true },
    { path: '/markets/alerts', label: 'Alerts', icon: 'notifications', exact: true },
  ];

  ngOnInit(): void {
    this.documentTitle.setTitle(`${APP_DISPLAY_NAME} · Markets`);
    this.auth.refreshSession().pipe(catchError(() => of(null))).subscribe();
    const base = environment.apiBaseUrl || '';
    this.http
      .get<ApiVersionPayload>(`${base}/api/version`)
      .pipe(catchError(() => of(null as ApiVersionPayload | null)))
      .subscribe((v) => {
        this.apiRelease = v;
      });
  }

  get username(): string {
    return this.auth.username ?? '';
  }

  logout(): void {
    this.auth.logout(true);
  }
}
