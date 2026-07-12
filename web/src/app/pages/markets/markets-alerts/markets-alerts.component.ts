import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';
import { MARKETS_ALERT_RULES_PAGE } from '../../../config/app-nav.config';

@Component({
  selector: 'app-markets-alerts',
  standalone: true,
  imports: [RouterLink, MatButtonModule, PageHeaderComponent],
  template: `
    <app-page-header
      [title]="copy.title"
      subtitle="Overview of stock price and session alert rules. Create and edit alerts in the trading workspace."
    />
    <p class="muted">
      Open <a routerLink="/markets/workspace">Workspace</a> and use the
      <strong>{{ copy.workspaceAlertsTab }}</strong> sub-tab to create and evaluate alerts.
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
export class MarketsAlertsComponent {
  readonly copy = MARKETS_ALERT_RULES_PAGE;
}
