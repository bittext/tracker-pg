import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';

@Component({
  selector: 'app-markets-alerts',
  standalone: true,
  imports: [RouterLink, MatButtonModule, PageHeaderComponent],
  template: `
    <app-page-header
      title="Alerts"
      subtitle="Stock price and session alerts are managed in the trading workspace."
    />
    <p class="muted">
      Open <a routerLink="/markets/workspace">Workspace</a> and use the <strong>Alerts</strong> sub-tab to create and
      evaluate finance alerts.
    </p>
    <a mat-flat-button color="primary" routerLink="/markets/workspace">Go to workspace</a>
  `,
  styles: `
    .muted {
      color: var(--app-text-muted);
      margin-bottom: 1rem;
    }
  `,
})
export class MarketsAlertsComponent {}
