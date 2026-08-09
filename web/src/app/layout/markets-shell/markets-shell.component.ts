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
import { MARKETS_FOOTER_NAV, MARKETS_PRIMARY_NAV, NavEntry } from '../../config/app-nav.config';
import { ThemeSettingsComponent } from '../../components/theme-settings/theme-settings.component';
import { MarketsRoadmapSummaryComponent } from '../../pages/markets/markets-roadmap-summary/markets-roadmap-summary.component';
import { WEB_RELEASE_VERSION } from '../../release-version';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

interface ApiVersionPayload {
  version: string;
  buildTime: string | null;
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
    MarketsRoadmapSummaryComponent,
  ],
  templateUrl: './markets-shell.component.html',
  styleUrl: './markets-shell.component.scss',
})
export class MarketsShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  private readonly http = inject(HttpClient);
  private readonly documentTitle = inject(Title);
  private readonly router = inject(Router);

  readonly brandTitle = APP_SHORT_NAME;
  readonly webReleaseVersion = WEB_RELEASE_VERSION;
  readonly primaryNav = MARKETS_PRIMARY_NAV;
  readonly footerNav = MARKETS_FOOTER_NAV;
  apiRelease: ApiVersionPayload | null = null;
  showRoadmapStrip = true;

  ngOnInit(): void {
    this.documentTitle.setTitle(`${APP_DISPLAY_NAME} · Markets`);
    this.auth.refreshSession().pipe(catchError(() => of(null))).subscribe();
    this.updateStripVisibility(this.router.url);
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => {
      this.updateStripVisibility(e.urlAfterRedirects);
    });
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

  navAriaLabel(item: NavEntry): string | null {
    return item.ariaLabel ?? null;
  }

  logout(): void {
    this.auth.logout(true);
  }

  private updateStripVisibility(url: string): void {
    const path = url.split('?')[0] ?? url;
    this.showRoadmapStrip = !path.startsWith('/markets/roadmap') && !path.startsWith('/markets/journey');
  }
}
