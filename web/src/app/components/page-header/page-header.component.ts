import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  template: `
    <header class="page-header">
      <div class="page-header__text">
        <h1 class="page-header__title">{{ title }}</h1>
        @if (subtitle) {
          <p class="page-header__subtitle muted">{{ subtitle }}</p>
        }
        <ng-content select="[pageHeaderSubtitle]" />
      </div>
      @if (hasActions) {
        <div class="page-header__actions">
          <ng-content select="[pageHeaderActions]" />
        </div>
      }
    </header>
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.75rem 1rem;
      margin-bottom: 1.25rem;
    }

    .page-header__title {
      margin: 0;
      font-family: var(--app-font-display);
      font-size: 1.5rem;
      font-weight: 600;
      letter-spacing: -0.02em;
      color: var(--app-text);
    }

    .page-header__subtitle {
      margin: 0.35rem 0 0;
      font-size: 0.9375rem;
      line-height: 1.5;
      max-width: 52rem;
    }

    .page-header__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }

    .muted {
      color: var(--app-text-muted);
    }
  `,
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() subtitle = '';
  @Input() hasActions = false;
}
