import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, interval, of } from 'rxjs';
import { catchError, filter, switchMap } from 'rxjs/operators';
import {
  RobinhoodExecutedTradeDto,
  RobinhoodExecutedTradesDto,
  RobinhoodRhCryptoHoldingDto,
  RobinhoodRhCryptoTrackerDayDto,
  RobinhoodRhCryptoTrackerReportDto,
  RobinhoodRhDailySnapshotDetailDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerManualCaptureDto,
  RobinhoodRhDailyTrackerRefreshHintDto,
  RobinhoodRhDailyTrackerReportDto,
  RobinhoodRhPeriodAccountFigureDto,
  RobinhoodRhPeriodBalanceRowDto,
  RobinhoodRhPeriodBalancesDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  RhPerfDetailDialogData,
  RobinhoodPerformanceDetailDialogComponent,
} from './robinhood-performance-detail-dialog.component';

interface LiveAccountRow {
  suffix: string;
  label: string;
  shortLabel: string;
  now: number | null;
  sinceClose: number | null;
  yearChange: number | null;
  yearStart: number | null;
}

interface SymbolPnlRow {
  symbol: string;
  realized: number;
  sells: number;
  wins: number;
  losses: number;
}

@Component({
  selector: 'app-reports-finance-robinhood-performance',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-performance.component.html',
  styleUrl: './reports-finance-robinhood-performance.component.scss',
})
export class ReportsFinanceRobinhoodPerformanceComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  readonly journalNav = inject(TradingJournalNavService);

  private static readonly AUTO_REFRESH_MS = 25_000;

  reportYear = new Date().getFullYear();
  loading = false;
  softRefreshing = false;
  capturing = false;
  error: string | null = null;

  daily: RobinhoodRhDailyTrackerReportDto | null = null;
  balances: RobinhoodRhPeriodBalancesDto | null = null;
  trades: RobinhoodExecutedTradesDto | null = null;
  crypto: RobinhoodRhCryptoTrackerReportDto | null = null;
  snapshotDetails: RobinhoodRhDailySnapshotDetailDto[] = [];

  private lastKnownSnapshotId = 0;
  private refreshPollReady = false;

  ngOnInit(): void {
    this.load();
    this.startAutoRefresh();
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    });
  }

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  load(opts?: { silent?: boolean }): void {
    const silent = opts?.silent ?? false;
    if (silent) {
      this.softRefreshing = true;
    } else {
      this.loading = true;
      this.error = null;
    }
    const currentYear = new Date().getFullYear();
    const months = this.reportYear === currentYear ? [new Date().getMonth() + 1] : null;
    forkJoin({
      daily: this.financeApi.robinhoodDailyTracker(this.reportYear, months),
      balances: this.financeApi.robinhoodDailyTrackerPeriodBalances(this.reportYear),
      trades: this.financeApi.robinhoodExecutedTrades(this.reportYear),
      crypto: this.financeApi.robinhoodCryptoTracker(this.reportYear, months ?? undefined),
    }).subscribe({
      next: ({ daily, balances, trades, crypto }) => {
        this.daily = daily;
        this.balances = balances;
        this.trades = trades;
        this.crypto = crypto;
        this.loading = false;
        this.softRefreshing = false;
        this.loadSnapshotDetails();
        if (!silent) {
          this.syncRefreshHint(true);
        }
      },
      error: (err) => {
        this.loading = false;
        this.softRefreshing = false;
        const message = formatHttpErrorDetail(err) || 'Could not load Performance';
        this.error = silent ? this.error : message;
        if (!silent) {
          this.snackBar.open(message, 'Dismiss', { duration: 7000 });
        }
      },
    });
  }

  captureNow(): void {
    this.capturing = true;
    forkJoin({
      daily: this.financeApi.robinhoodDailyTrackerCapture(true),
      crypto: this.financeApi.robinhoodCryptoTrackerCapture(true).pipe(catchError(() => of(null))),
    }).subscribe({
      next: () => {
        this.capturing = false;
        this.load();
      },
      error: (err) => {
        this.capturing = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Capture failed', 'Dismiss', { duration: 7000 });
        this.load({ silent: true });
      },
    });
  }

  nowTotal(): number | null {
    return this.latestLive()?.total ?? this.balances?.yearBalance?.combinedEnd ?? null;
  }

  nowAtLabel(): string {
    const live = this.latestLive();
    if (!live) {
      return 'No captures yet';
    }
    return `${live.kind === 'CLOSE' ? 'Official close' : 'Live'} · ${this.formatWhen(live.at)}`;
  }

  sinceLastClose(): number | null {
    const live = this.latestLive();
    if (!live) {
      return null;
    }
    if (live.priorClose == null) {
      return null;
    }
    return live.total - live.priorClose;
  }

  yearChange(): number | null {
    return this.balances?.yearBalance?.combinedChange ?? this.daily?.yearCombinedChange ?? null;
  }

  yearStart(): number | null {
    return this.balances?.yearBalance?.combinedStart ?? null;
  }

  yearEndLabel(): string {
    const row = this.balances?.yearBalance;
    if (!row) {
      return 'Year change';
    }
    return row.currentPeriod ? 'Year so far' : 'Year change';
  }

  realizedPnl(): number {
    return this.closedSells().reduce((sum, t) => sum + (t.realizedPnl ?? 0), 0);
  }

  winRate(): number | null {
    const sells = this.closedSells();
    const decided = sells.filter((t) => (t.realizedPnl ?? 0) !== 0);
    if (!decided.length) {
      return null;
    }
    const wins = decided.filter((t) => (t.realizedPnl ?? 0) > 0).length;
    return (wins / decided.length) * 100;
  }

  sellCount(): number {
    return this.closedSells().length;
  }

  tradingDays(): number {
    const days = new Set<string>();
    for (const t of this.closedSells()) {
      if (t.executedAt) {
        days.add(this.centralDate(t.executedAt));
      }
    }
    return days.size;
  }

  cryptoNow(): number | null {
    return this.latestCrypto()?.total ?? null;
  }

  cryptoChange(): number | null {
    return this.latestCrypto()?.change ?? null;
  }

  stocksNow(): number | null {
    return this.sumDetail('equityMarketValue');
  }

  cashNow(): number | null {
    return this.sumDetail('cashBalance');
  }

  stocksChange(): number | null {
    return this.sumDetail('equityMarketValueChange');
  }

  cashChange(): number | null {
    return this.sumDetail('cashBalanceChange');
  }

  optionsNow(): number | null {
    return this.sumOptionField('marketValue');
  }

  optionsChange(): number | null {
    return this.sumOptionField('marketValueChange');
  }

  stocksAreClosed(): boolean {
    const change = this.stocksChange();
    return change != null && Math.abs(change) < 0.005 && this.sinceLastClose() != null;
  }

  cryptoAtLabel(): string {
    const row = this.latestCrypto();
    if (!row) {
      return this.crypto?.status === 'NOT_CONNECTED' ? 'Crypto not connected' : 'No crypto captures';
    }
    return this.formatWhen(row.at);
  }

  cryptoHoldings(): RobinhoodRhCryptoHoldingDto[] {
    const holdings = this.latestCrypto()?.holdings ?? [];
    return [...holdings].sort((a, b) => (b.marketValue ?? 0) - (a.marketValue ?? 0)).slice(0, 6);
  }

  accountRows(): LiveAccountRow[] {
    const live = this.latestLive();
    const year = this.balances?.yearBalance;
    const columns = this.daily?.accounts?.length
      ? this.daily.accounts
      : (this.balances?.accounts ?? []).map((a) => ({
          accountSuffix: a.accountSuffix,
          label: a.label,
          accountKind: '',
        }));
    return columns.map((col) => {
      const liveAcct = live?.accounts.find((a) => a.suffix === col.accountSuffix);
      const prior = live?.priors.find((a) => a.suffix === col.accountSuffix)?.value ?? null;
      const yearFig = this.figureFor(year, col.accountSuffix);
      const now = liveAcct?.value ?? yearFig?.end ?? null;
      return {
        suffix: col.accountSuffix,
        label: col.label,
        shortLabel: this.shortLabel(col.label, col.accountSuffix),
        now,
        sinceClose: now != null && prior != null ? now - prior : null,
        yearChange: yearFig?.change ?? null,
        yearStart: yearFig?.start ?? null,
      };
    });
  }

  monthRows(): RobinhoodRhPeriodBalanceRowDto[] {
    return (this.balances?.months ?? []).filter(
      (row) => row.combinedStart != null || row.combinedEnd != null || row.combinedChange != null,
    );
  }

  monthBarWidth(change: number | null): number {
    const values = this.monthRows()
      .map((r) => Math.abs(r.combinedChange ?? 0))
      .filter((n) => n > 0);
    const peak = values.length ? Math.max(...values) : 0;
    if (!peak || change == null) {
      return 0;
    }
    return Math.max(6, Math.round((Math.abs(change) / peak) * 100));
  }

  symbolRows(): SymbolPnlRow[] {
    const bySymbol = new Map<string, SymbolPnlRow>();
    for (const t of this.closedSells()) {
      const symbol = (t.symbol || '—').trim();
      const row = bySymbol.get(symbol) ?? { symbol, realized: 0, sells: 0, wins: 0, losses: 0 };
      const pnl = t.realizedPnl ?? 0;
      row.realized += pnl;
      row.sells += 1;
      if (pnl > 0) {
        row.wins += 1;
      } else if (pnl < 0) {
        row.losses += 1;
      }
      bySymbol.set(symbol, row);
    }
    return [...bySymbol.values()].sort((a, b) => Math.abs(b.realized) - Math.abs(a.realized)).slice(0, 8);
  }

  recentSells(): RobinhoodExecutedTradeDto[] {
    return this.closedSells().slice(0, 8);
  }

  scheduleLabel(): string {
    return this.daily?.autoCaptureScheduleLabel || this.crypto?.autoCaptureScheduleLabel || 'hourly + 9:00 PM CT close';
  }

  isGain(value: number | null | undefined): boolean {
    return value != null && value > 0;
  }

  isLoss(value: number | null | undefined): boolean {
    return value != null && value < 0;
  }

  pct(start: number | null | undefined, change: number | null | undefined): number | null {
    if (start == null || change == null || !Number.isFinite(start) || start === 0) {
      return null;
    }
    return (change / start) * 100;
  }

  formatWhen(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return `${new Date(iso).toLocaleString('en-US', {
      timeZone: 'America/Chicago',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    })} CT`;
  }

  formatTradeWhen(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleString('en-US', {
      timeZone: 'America/Chicago',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  }

  monthShort(label: string): string {
    return label.replace(/\s+\d{4}$/, '');
  }

  openDailyTracker(): void {
    this.journalNav.openDailyTracker();
  }

  openTrades(): void {
    this.journalNav.openExecutedTrades();
  }

  openCrypto(): void {
    this.journalNav.analyticsTabIndex.set(6);
  }

  openMonthDetail(row: RobinhoodRhPeriodBalanceRowDto): void {
    const accounts = (this.balances?.accounts ?? []).map((col) => {
      const fig = this.figureFor(row, col.accountSuffix);
      return {
        suffix: col.accountSuffix,
        label: col.label,
        start: fig?.start ?? null,
        end: fig?.end ?? null,
        change: fig?.change ?? null,
      };
    });
    this.openDetail({
      title: row.label,
      subtitle:
        'Official 9:00 PM CT closes for this month. Sells below are realized FIFO in the same window — they are not the same as the account-value change.',
      accounts,
      trades: this.closedSells().filter((t) => this.tradeInRange(t, row.periodStart, row.periodEnd)),
    });
  }

  openSymbolDetail(symbol: string): void {
    this.openDetail({
      title: symbol,
      subtitle: `Realized FIFO sells for ${symbol} in ${this.reportYear}.`,
      accounts: [],
      trades: this.closedSells().filter((t) => (t.symbol || '').trim() === symbol),
    });
  }

  openAccountDetail(acct: LiveAccountRow): void {
    this.openDetail({
      title: `${acct.shortLabel} ••••${acct.suffix}`,
      subtitle: `Year ${this.reportYear} official close change and realized sells on this account.`,
      accounts: [
        {
          suffix: acct.suffix,
          label: acct.label,
          start: acct.yearStart,
          end: acct.now,
          change: acct.yearChange,
        },
      ],
      trades: this.closedSells().filter((t) => t.accountSuffix === acct.suffix),
    });
  }

  private openDetail(data: RhPerfDetailDialogData): void {
    this.dialog.open(RobinhoodPerformanceDetailDialogComponent, {
      width: 'min(820px, 96vw)',
      maxWidth: '96vw',
      maxHeight: '88vh',
      data,
    });
  }

  private tradeInRange(trade: RobinhoodExecutedTradeDto, start: string | null, end: string | null): boolean {
    if (!trade.executedAt) {
      return false;
    }
    const day = this.centralDate(trade.executedAt);
    if (start && day < start) {
      return false;
    }
    if (end && day > end) {
      return false;
    }
    return true;
  }

  private loadSnapshotDetails(): void {
    const ids = [...new Set((this.latestLive()?.accounts ?? []).map((a) => a.snapshotId).filter((id) => id > 0))];
    if (!ids.length) {
      this.snapshotDetails = [];
      return;
    }
    forkJoin(
      ids.map((id) => this.financeApi.robinhoodDailyTrackerSnapshot(id).pipe(catchError(() => of(null)))),
    ).subscribe({
      next: (rows) => {
        this.snapshotDetails = rows.filter((row): row is RobinhoodRhDailySnapshotDetailDto => row != null);
      },
    });
  }

  private sumDetail(
    field: 'equityMarketValue' | 'cashBalance' | 'equityMarketValueChange' | 'cashBalanceChange',
  ): number | null {
    if (!this.snapshotDetails.length) {
      return null;
    }
    return this.snapshotDetails.reduce((sum, row) => sum + (row[field] ?? 0), 0);
  }

  private sumOptionField(field: 'marketValue' | 'marketValueChange'): number | null {
    if (!this.snapshotDetails.length) {
      return null;
    }
    let sum = 0;
    for (const row of this.snapshotDetails) {
      for (const item of row.holdings ?? []) {
        if ((item.holding?.positionType ?? '').toLowerCase() !== 'option') {
          continue;
        }
        sum += field === 'marketValue' ? (item.holding.marketValue ?? 0) : (item.marketValueChange ?? 0);
      }
    }
    return sum;
  }

  private closedSells(): RobinhoodExecutedTradeDto[] {
    return (this.trades?.trades ?? []).filter(
      (t) => (t.side ?? '').toLowerCase() === 'sell' && t.realizedPnl != null,
    );
  }

  private figureFor(
    row: RobinhoodRhPeriodBalanceRowDto | null | undefined,
    suffix: string,
  ): RobinhoodRhPeriodAccountFigureDto | undefined {
    return row?.accounts.find((a) => a.accountSuffix === suffix);
  }

  private shortLabel(label: string, suffix: string): string {
    const lower = label.toLowerCase();
    if (lower.includes('individual')) {
      return 'Individual';
    }
    if (lower.includes('managed')) {
      return 'Managed';
    }
    if (lower.includes('ammu')) {
      return 'Ammu';
    }
    if (lower.includes('agentic')) {
      return 'Agentic';
    }
    return `••••${suffix}`;
  }

  private latestDayCapture(day: RobinhoodRhDailyTrackerDayDto): RobinhoodRhDailyTrackerManualCaptureDto | null {
    const all = [...(day.intradayCaptures ?? []), ...(day.manualCaptures ?? [])];
    if (!all.length) {
      return null;
    }
    return all.reduce((best, row) =>
      new Date(row.capturedAt).getTime() > new Date(best.capturedAt).getTime() ? row : best,
    );
  }

  private latestLive(): {
    at: string;
    total: number;
    kind: 'LIVE' | 'CLOSE';
    priorClose: number | null;
    accounts: { suffix: string; value: number; snapshotId: number }[];
    priors: { suffix: string; value: number }[];
  } | null {
    let best: {
      at: string;
      total: number;
      kind: 'LIVE' | 'CLOSE';
      priorClose: number | null;
      accounts: { suffix: string; value: number; snapshotId: number }[];
      priors: { suffix: string; value: number }[];
    } | null = null;
    for (const day of this.daily?.days ?? []) {
      const priors = (day.priorPull?.accounts ?? []).map((a) => ({
        suffix: a.accountSuffix,
        value: a.totalAccountValue,
      }));
      const priorClose = day.priorPull?.combinedTotal ?? null;
      if (day.hasScheduledSnapshot && day.snapshotAt) {
        const candidate = {
          at: day.snapshotAt,
          total: day.combinedTotal,
          kind: 'CLOSE' as const,
          priorClose: day.hasPreviousScheduledSnapshot ? day.combinedTotal - day.combinedTotalChangeFromPrevious : priorClose,
          accounts: day.accounts.map((a) => ({
            suffix: a.accountSuffix,
            value: a.totalAccountValue,
            snapshotId: a.snapshotId,
          })),
          priors,
        };
        if (!best || new Date(candidate.at).getTime() >= new Date(best.at).getTime()) {
          best = candidate;
        }
      }
      const live = this.latestDayCapture(day);
      if (live) {
        const afterOfficialClose =
          day.hasScheduledSnapshot &&
          !!day.snapshotAt &&
          new Date(live.capturedAt).getTime() > new Date(day.snapshotAt).getTime();
        const candidate = {
          at: live.capturedAt,
          total: live.combinedTotal,
          kind: 'LIVE' as const,
          priorClose: afterOfficialClose ? day.combinedTotal : priorClose,
          accounts: live.accounts.map((a) => ({
            suffix: a.accountSuffix,
            value: a.totalAccountValue,
            snapshotId: a.snapshotId,
          })),
          priors: afterOfficialClose
            ? day.accounts.map((a) => ({ suffix: a.accountSuffix, value: a.totalAccountValue }))
            : priors,
        };
        if (!best || new Date(candidate.at).getTime() > new Date(best.at).getTime()) {
          best = candidate;
        }
      }
    }
    return best;
  }

  private latestCrypto(): {
    at: string;
    total: number;
    change: number;
    holdings: RobinhoodRhCryptoHoldingDto[];
  } | null {
    let best: { at: string; total: number; change: number; holdings: RobinhoodRhCryptoHoldingDto[] } | null = null;
    for (const day of this.crypto?.days ?? []) {
      const consider = (at: string, total: number, change: number, holdings: RobinhoodRhCryptoHoldingDto[]) => {
        if (!best || new Date(at).getTime() >= new Date(best.at).getTime()) {
          best = { at, total, change, holdings };
        }
      };
      if (day.snapshotAt) {
        consider(day.snapshotAt, day.totalValue, day.changeFromPrevious, day.holdings ?? []);
      }
      this.latestCryptoCapture(day, consider);
    }
    return best;
  }

  private latestCryptoCapture(
    day: RobinhoodRhCryptoTrackerDayDto,
    consider: (at: string, total: number, change: number, holdings: RobinhoodRhCryptoHoldingDto[]) => void,
  ): void {
    for (const cap of [...(day.intradayCaptures ?? []), ...(day.manualCaptures ?? [])]) {
      const holdings = (cap.accounts ?? []).flatMap((a) => a.holdings ?? []);
      const vsPriorClose = day.changeFromPrevious + (cap.totalValue - day.totalValue);
      consider(
        cap.snapshotAt,
        cap.totalValue,
        vsPriorClose,
        holdings.length ? holdings : (day.holdings ?? []),
      );
    }
  }

  private centralDate(iso: string): string {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Chicago',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date(iso));
  }

  private readonly onVisibilityChange = (): void => {
    if (document.hidden || !this.refreshPollReady || this.loading || this.capturing) {
      return;
    }
    this.financeApi.robinhoodDailyTrackerRefreshHint().subscribe({
      next: (hint) => this.onRefreshHint(hint, true),
    });
  };

  private startAutoRefresh(): void {
    interval(ReportsFinanceRobinhoodPerformanceComponent.AUTO_REFRESH_MS)
      .pipe(
        filter(
          () =>
            this.refreshPollReady &&
            !document.hidden &&
            !this.loading &&
            !this.capturing &&
            !this.softRefreshing,
        ),
        switchMap(() => this.financeApi.robinhoodDailyTrackerRefreshHint()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (hint) => this.onRefreshHint(hint, true),
        error: () => {
          /* ignore transient poll failures */
        },
      });
  }

  private onRefreshHint(hint: RobinhoodRhDailyTrackerRefreshHintDto, fromPoll: boolean): void {
    const id = hint.latestSnapshotId ?? 0;
    if (fromPoll && id === this.lastKnownSnapshotId) {
      return;
    }
    this.lastKnownSnapshotId = id;
    if (!fromPoll || !this.daily) {
      return;
    }
    this.load({ silent: true });
  }

  private syncRefreshHint(markPollReady: boolean): void {
    this.financeApi.robinhoodDailyTrackerRefreshHint().subscribe({
      next: (hint) => {
        this.onRefreshHint(hint, false);
        if (markPollReady) {
          this.refreshPollReady = true;
        }
      },
      error: () => {
        if (markPollReady) {
          this.refreshPollReady = true;
        }
      },
    });
  }
}
