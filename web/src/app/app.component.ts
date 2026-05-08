import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { catchError, filter, of } from 'rxjs';
import { environment } from '../environments/environment';
import { WEB_RELEASE_VERSION } from './release-version';
import { APP_DISPLAY_NAME, APP_SHORT_NAME } from './app-branding';
import { AuthService } from './services/auth.service';

/** Response from GET /api/version (Spring Boot build-info when packaged). */
interface ApiVersionPayload {
  name: string;
  group: string;
  artifact: string;
  version: string;
  buildTime: string | null;
}

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatTabsModule,
    MatButtonModule,
  ],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly documentTitle = inject(Title);
  /** Shown in the top bar; full name sets the browser tab title. */
  readonly brandTitle = APP_SHORT_NAME;
  readonly webReleaseVersion = WEB_RELEASE_VERSION;
  /** Populated from API after startup (null if unreachable). */
  apiRelease: ApiVersionPayload | null = null;
  /** True for routes that use the minimal shell (no main tab bar), e.g. login, privacy, and security. */
  onLoginRoute = this.isPublicStandaloneRoute(this.router.url);

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.onLoginRoute = this.isPublicStandaloneRoute(this.router.url);
    });
  }

  private isPublicStandaloneRoute(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    return (
      path === '/login' ||
      path === '/privacy' ||
      path === '/security' ||
      path.startsWith('/onboarding/')
    );
  }

  ngOnInit(): void {
    this.documentTitle.setTitle(APP_DISPLAY_NAME);
    const base = environment.apiBaseUrl || '';
    this.http
      .get<ApiVersionPayload>(`${base}/api/version`)
      .pipe(catchError(() => of(null as ApiVersionPayload | null)))
      .subscribe((v) => {
        this.apiRelease = v;
      });
  }

  /** Native tooltip: full web + API build info (hint in toolbar stays minimal). */
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

  get authenticated(): boolean {
    return this.auth.isAuthenticated();
  }

  get isAdmin(): boolean {
    return this.auth.isAdmin();
  }

  logout(): void {
    this.auth.logout(true);
  }
}
