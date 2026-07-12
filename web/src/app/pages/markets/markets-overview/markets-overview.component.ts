import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';
import { RhAccountsTrackPanelComponent } from '../../finance/rh-accounts-track-panel/rh-accounts-track-panel.component';

@Component({
  selector: 'app-markets-overview',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    PageHeaderComponent,
    RhAccountsTrackPanelComponent,
  ],
  template: `
    <app-page-header
      title="Markets overview"
      subtitle="Quick links and account snapshot for your trading workspace."
    />

    <div class="markets-overview-kpis">
      <mat-card appearance="outlined" class="kpi-card">
        <mat-card-header>
          <mat-card-title>Workspace</mat-card-title>
          <mat-card-subtitle>Trading tools &amp; screeners</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-flat-button color="primary" routerLink="/markets/workspace">Open workspace</a>
        </mat-card-actions>
      </mat-card>
      <mat-card appearance="outlined" class="kpi-card">
        <mat-card-header>
          <mat-card-title>Analytics</mat-card-title>
          <mat-card-subtitle>Performance, daily tracker, crypto</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-stroked-button routerLink="/markets/analytics">View analytics</a>
        </mat-card-actions>
      </mat-card>
      <mat-card appearance="outlined" class="kpi-card">
        <mat-card-header>
          <mat-card-title>Execution</mat-card-title>
          <mat-card-subtitle>Robinhood trading panel</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-stroked-button routerLink="/markets/execution">Open execution</a>
        </mat-card-actions>
      </mat-card>
    </div>

    <section class="markets-overview-panel" aria-labelledby="rh-track-heading">
      <h2 id="rh-track-heading" class="section-title">Account track</h2>
      <app-rh-accounts-track-panel />
    </section>
  `,
  styles: `
    .markets-overview-kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1rem;
      margin-bottom: 1.5rem;
    }

    .kpi-card mat-card-actions {
      padding: 0 1rem 1rem;
    }

    .section-title {
      margin: 0 0 0.75rem;
      font-size: 1.125rem;
      font-weight: 600;
    }
  `,
})
export class MarketsOverviewComponent {}
