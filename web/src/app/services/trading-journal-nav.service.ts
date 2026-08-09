import { Injectable, signal } from '@angular/core';

/** Cross-tab navigation between Daily Tracker and Trading Journal. */
@Injectable({ providedIn: 'root' })
export class TradingJournalNavService {
  /**
   * Robinhood analytics mat-tab index:
   * 0 Performance, 1 Daily Tracker, 2 Ownership history, 3 Journal, 4 Crypto, 5 Roadmap.
   */
  readonly analyticsTabIndex = signal(0);
  readonly requestedDate = signal<string | null>(null);

  openJournal(dateIso: string): void {
    this.requestedDate.set(dateIso);
    this.analyticsTabIndex.set(3);
  }

  openDailyTracker(dateIso?: string | null): void {
    if (dateIso) {
      this.requestedDate.set(dateIso);
    }
    this.analyticsTabIndex.set(1);
  }

  openOwnershipHistory(): void {
    this.analyticsTabIndex.set(2);
  }

  consumeRequestedDate(): string | null {
    const d = this.requestedDate();
    this.requestedDate.set(null);
    return d;
  }
}
