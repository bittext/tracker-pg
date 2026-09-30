import { Injectable, signal } from '@angular/core';

/** Cross-tab navigation between Daily Tracker and Trading Journal. */
@Injectable({ providedIn: 'root' })
export class TradingJournalNavService {
  /**
   * Robinhood analytics mat-tab index:
   * 0 Performance, 1 YTD, 2 Daily Tracker, 3 Trades, 4 Balances, 5 Ownership history, 6 Journal, 7 Crypto, 8 Roadmap, 9 Margin.
   */
  readonly analyticsTabIndex = signal(0);
  /** Nested Trades tabs: 0 Ledger, 1 Tax desk. */
  readonly tradesInnerTabIndex = signal(0);
  readonly requestedDate = signal<string | null>(null);
  /** Shared Insights rail — year, ticker, and account for the redesign layout. */
  readonly insightsYear = signal(new Date().getFullYear());
  readonly insightsSymbol = signal('');
  readonly insightsAccountSuffix = signal('');

  openJournal(dateIso?: string | null): void {
    if (dateIso) {
      this.requestedDate.set(dateIso);
    }
    this.analyticsTabIndex.set(6);
  }

  openYtdCheck(): void {
    this.analyticsTabIndex.set(1);
  }

  openDailyTracker(dateIso?: string | null): void {
    if (dateIso) {
      this.requestedDate.set(dateIso);
    }
    this.analyticsTabIndex.set(2);
  }

  openOwnershipHistory(): void {
    this.analyticsTabIndex.set(5);
  }

  openExecutedTrades(): void {
    this.analyticsTabIndex.set(3);
    this.tradesInnerTabIndex.set(0);
  }

  openTaxDesk(): void {
    this.analyticsTabIndex.set(3);
    this.tradesInnerTabIndex.set(1);
  }

  openMarginWatch(): void {
    this.analyticsTabIndex.set(9);
  }

  consumeRequestedDate(): string | null {
    const d = this.requestedDate();
    this.requestedDate.set(null);
    return d;
  }
}
