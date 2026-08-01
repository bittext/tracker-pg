import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  /** True for routes that render their own shell (life, markets, admin). */
  usesChildShell = this.isChildShellRoute(this.router.url);
  /** True for login, privacy, security, onboarding — minimal chrome. */
  onStandaloneRoute = this.isStandaloneRoute(this.router.url);

  constructor() {
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.usesChildShell = this.isChildShellRoute(this.router.url);
      this.onStandaloneRoute = this.isStandaloneRoute(this.router.url);
    });
  }

  ngOnInit(): void {
    this.theme.init();
  }

  private isChildShellRoute(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    return path.startsWith('/life') || path.startsWith('/markets') || path.startsWith('/admin');
  }

  private isStandaloneRoute(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    return (
      path === '/login' ||
      path === '/privacy' ||
      path === '/terms' ||
      path === '/security' ||
      path.startsWith('/onboarding/') ||
      path === '/logs'
    );
  }
}
