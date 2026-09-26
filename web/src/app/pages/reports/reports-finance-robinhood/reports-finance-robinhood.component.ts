import { Component, effect, inject } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { ReportsFinanceRobinhoodPerformanceComponent } from '../reports-finance-robinhood-performance/reports-finance-robinhood-performance.component';
import { ReportsFinanceRobinhoodDailyTrackerComponent } from '../reports-finance-robinhood-daily-tracker/reports-finance-robinhood-daily-tracker.component';
import { ReportsFinanceRobinhoodExecutedTradesComponent } from '../reports-finance-robinhood-executed-trades/reports-finance-robinhood-executed-trades.component';
import { ReportsFinanceRobinhoodPeriodBalancesComponent } from '../reports-finance-robinhood-period-balances/reports-finance-robinhood-period-balances.component';
import { ReportsFinanceRobinhoodCryptoTrackerComponent } from '../reports-finance-robinhood-crypto-tracker/reports-finance-robinhood-crypto-tracker.component';
import { ReportsFinanceRobinhoodOwnershipHistoryComponent } from '../reports-finance-robinhood-ownership-history/reports-finance-robinhood-ownership-history.component';
import { TradingJournalPanelComponent } from '../trading-journal-panel/trading-journal-panel.component';
import { MarketsJourneyComponent } from '../../markets/markets-journey/markets-journey.component';
import { InsightsRedesignRailComponent } from '../../../components/insights-redesign-rail/insights-redesign-rail.component';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { UiLayoutService } from '../../../services/ui-layout.service';
import { UiLayout } from '../../../models/ui-layout.models';

@Component({
  selector: 'app-reports-finance-robinhood',
  standalone: true,
  imports: [
    MatTabsModule,
    ReportsFinanceRobinhoodPerformanceComponent,
    ReportsFinanceRobinhoodDailyTrackerComponent,
    ReportsFinanceRobinhoodExecutedTradesComponent,
    ReportsFinanceRobinhoodPeriodBalancesComponent,
    ReportsFinanceRobinhoodCryptoTrackerComponent,
    ReportsFinanceRobinhoodOwnershipHistoryComponent,
    TradingJournalPanelComponent,
    MarketsJourneyComponent,
    InsightsRedesignRailComponent,
  ],
  templateUrl: './reports-finance-robinhood.component.html',
  styleUrl: './reports-finance-robinhood.component.scss',
})
export class ReportsFinanceRobinhoodComponent {
  readonly journalNav = inject(TradingJournalNavService);
  private readonly uiLayout = inject(UiLayoutService);
  private lastLayout: UiLayout | null = null;

  constructor() {
    effect(() => {
      const layout = this.uiLayout.layout();
      if (layout === 'spine' && this.lastLayout !== 'spine') {
        this.journalNav.openDailyTracker();
      }
      this.lastLayout = layout;
    });
  }
}
