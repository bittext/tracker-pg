import { Injectable, signal } from '@angular/core';

/** Cross-tab navigation between Daily Tracker and Trading Journal. */
@Injectable({ providedIn: 'root' })
export class TradingJournalNavService {
  /**
   * Robinhood analytics mat-tab index:
   * 0 Performance, 1 Daily Tracker, 2 Trades, 3 Balances, 4 Ownership history, 5 Journal, 6 Crypto, 7 Roadmap.
   */
  readonly analyticsTabIndex = signal(0);
  /** Nested Trades tabs: 0 Ledger, 1 Tax desk. */
  readonly tradesInnerTabIndex = signal(0);
  readonly requestedDate = signal<string | null>(null);
  /** Shared Insights rail — year, ticker, and account for the redesign layout. */
  readonly insightsYear = signal(new Date().getFullYear());
  readonly insightsSymbol = signal('');
  readonly insightsAccountSuffix = signal('');

  openJournal(dateIso: string): void {
    this.requestedDate.set(dateIso);
    this.analyticsTabIndex.set(5);
  }

  openDailyTracker(dateIso?: string | null): void {
    if (dateIso) {
      this.requestedDate.set(dateIso);
    }
    this.analyticsTabIndex.set(1);
  }

  openOwnershipHistory(): void {
    this.analyticsTabIndex.set(4);
  }

  openExecutedTrades(): void {
    this.analyticsTabIndex.set(2);
    this.tradesInnerTabIndex.set(0);
  }

  openTaxDesk(): void {
    this.analyticsTabIndex.set(2);
    this.tradesInnerTabIndex.set(1);
  }

  consumeRequestedDate(): string | null {
    const d = this.requestedDate();
    this.requestedDate.set(null);
    return d;
  }
}
