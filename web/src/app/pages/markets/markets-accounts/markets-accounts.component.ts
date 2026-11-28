import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';
import { ReportsFinanceRobinhoodAccountTrackerComponent } from '../../reports/reports-finance-robinhood-account-tracker/reports-finance-robinhood-account-tracker.component';

/** One tab per Robinhood account, each with its own value history and detailed change log. */
@Component({
  selector: 'app-markets-accounts',
  standalone: true,
  imports: [MatTabsModule, PageHeaderComponent, ReportsFinanceRobinhoodAccountTrackerComponent],
  template: `
    <app-page-header
      eyebrow="Markets"
      title="Accounts"
      subtitle="Each account on its own — current value, what changed at every close, and what it holds."
    />
    <mat-tab-group class="acct-tabs" animationDuration="180ms" mat-stretch-tabs="false">
      <mat-tab label="Individual">
        <ng-template matTabContent>
          <div class="acct-tabs__panel">
            <app-reports-finance-robinhood-account-tracker
              title="Individual brokerage"
              accountKind="INDIVIDUAL"
              fallbackSuffix="3370"
              blurb="The Individual brokerage account on its own: current value, what changed at each 9:00 PM CT close, and what it holds."
            />
          </div>
        </ng-template>
      </mat-tab>
      <mat-tab label="Roth IRA">
        <ng-template matTabContent>
          <div class="acct-tabs__panel">
            <app-reports-finance-robinhood-account-tracker
              title="Roth IRA"
              suffix="2835"
              blurb="The Roth IRA on its own: current value, what changed at each 9:00 PM CT close, and what it holds."
            />
          </div>
        </ng-template>
      </mat-tab>
      <mat-tab label="Agentic">
        <ng-template matTabContent>
          <div class="acct-tabs__panel">
            <app-reports-finance-robinhood-account-tracker
              title="Agentic account"
              accountKind="AGENTIC"
              fallbackSuffix="3550"
              blurb="The Agentic account on its own: current value, what changed at each 9:00 PM CT close, and what it holds."
            />
          </div>
        </ng-template>
      </mat-tab>
      <mat-tab label="Ammu's">
        <ng-template matTabContent>
          <div class="acct-tabs__panel">
            <app-reports-finance-robinhood-account-tracker
              title="Ammu's account"
              suffix="8696"
              blurb="Ammu's account on its own: current value, what changed at each 9:00 PM CT close, and what it holds."
            />
          </div>
        </ng-template>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: `
    .acct-tabs__panel {
      padding-top: 0.55rem;
    }
  `,
})
export class MarketsAccountsComponent {}
