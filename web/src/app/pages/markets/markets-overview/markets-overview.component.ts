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
      subtitle="Account snapshot and quick links. RH Accounts Track lives here only."
    />

    <div class="markets-overview-kpis">
      <mat-card appearance="outlined" class="kpi-card">
        <mat-card-header>
          <mat-card-title>Trade</mat-card-title>
          <mat-card-subtitle>Robinhood execution &amp; orders</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-flat-button color="primary" routerLink="/markets/trade">Open trade</a>
        </mat-card-actions>
      </mat-card>
      <mat-card appearance="outlined" class="kpi-card">
        <mat-card-header>
          <mat-card-title>Research</mat-card-title>
          <mat-card-subtitle>Screeners, news, predicts, backtest</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-stroked-button routerLink="/markets/research">Open research</a>
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
          <mat-card-title>Roadmap</mat-card-title>
          <mat-card-subtitle>Road to my first million — targets vs actuals</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions>
          <a mat-stroked-button routerLink="/markets/roadmap">Open roadmap</a>
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
