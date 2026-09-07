import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, interval, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import {
  RobinhoodAgenticAutoTradeRunDto,
  RobinhoodAgenticOrderDto,
  RobinhoodAgenticPositionDto,
  RobinhoodAgenticSettingsDto,
  RobinhoodAgenticStatusDto,
  RobinhoodAgenticSyncedOrderDto,
  RobinhoodCsvImportResultDto,
  RobinhoodCsvSavedImportDto,
  RobinhoodRhCryptoAutoTradeRunDto,
  RobinhoodRhCryptoAutoTradeSettingsDto,
  RobinhoodRhCryptoOrderDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerManualCaptureDto,
  RobinhoodRhDailyTrackerReportDto,
  RobinhoodRhDailyTradeDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface DeskLive {
  at: string;
  total: number;
  kind: 'LIVE' | 'CLOSE';
  priorClose: number | null;
  agentic: number | null;
}

interface BlotterRow {
  key: string;
  when: string;
  source: string;
  symbol: string;
  side: string;
  qty: number | null;
  price: number | null;
  state: string;
  today: boolean;
}

@Component({
  selector: 'app-robinhood-trading-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './robinhood-trading-panel.component.html',
  styleUrl: './robinhood-trading-panel.component.scss',
})
export class RobinhoodTradingPanelComponent implements OnInit {
  private static readonly LIVE_POSITIONS_LIMIT = 50;
  private static readonly EXCLUDED_ACCOUNT_SUFFIX = '4123';
  private static readonly AGENTIC_SUFFIX = '3550';
  private static readonly AUTO_REFRESH_MS = 30_000;

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  readonly journalNav = inject(TradingJournalNavService);

  loading = false;
  capturing = false;
  agenticSyncing = false;

  daily: RobinhoodRhDailyTrackerReportDto | null = null;
  agenticStatus: RobinhoodAgenticStatusDto | null = null;
  agenticSettings: RobinhoodAgenticSettingsDto | null = null;
  agenticPositions: RobinhoodAgenticPositionDto[] = [];
  agenticSyncedOrders: RobinhoodAgenticSyncedOrderDto[] = [];
  agenticOrders: RobinhoodAgenticOrderDto[] = [];
  agenticRuns: RobinhoodAgenticAutoTradeRunDto[] = [];
  cryptoSettings: RobinhoodRhCryptoAutoTradeSettingsDto | null = null;
  cryptoRuns: RobinhoodRhCryptoAutoTradeRunDto[] = [];
  cryptoOrders: RobinhoodRhCryptoOrderDto[] = [];

  agenticTokenJson = '';
  agenticSavingTokens = false;
  agenticSavingSettings = false;
  agenticReviewingOrder = false;
  approvingOrderId: number | null = null;

  orderSymbol = '';
  orderAssetClass: 'equity' | 'crypto' = 'equity';
  orderSide: 'buy' | 'sell' = 'buy';
  orderType: 'market' | 'limit' = 'market';
  orderQuantity: number | null = null;
  orderLimitPrice: number | null = null;
  orderSellAll = false;

  settingsRequireApproval = true;
  settingsMaxNotional: number | null = null;
  settingsAllowedSymbols = '';
  autoTradeEnabled = false;
  autoTradeKillSwitch = false;
  autoTradeRequireApproval = true;
  autoTradeMinPositivityBuy = 15;
  autoTradeMaxPositivitySell = -15;
  autoTradeMinSpikeZ = 1.5;
  autoTradeMinMentions24h = 5;
  autoTradeOrderQuantity = 1;
  autoTradeMaxTradesPerDay = 3;
  autoTradeMaxDailyNotional: number | null = null;
  autoTradeCooldownMinutes = 60;
  autoTradeMarketHoursOnly = true;
  autoTradeEvaluating = false;
  cryptoEvaluating = false;

  csvApplyToDb = false;
  csvSelectedFile: File | null = null;
  csvSelectedLabel: string | null = null;
  csvUploading = false;
  csvDirectResult: RobinhoodCsvImportResultDto | null = null;
  csvDirectoryResult: RobinhoodCsvSavedImportDto | null = null;
  directoryImportConfigured = false;

  ngOnInit(): void {
    this.loadDesk();
    this.startAutoRefresh();
    this.financeApi.robinhoodCsvImportUploadStatus().subscribe({
      next: (s) => {
        this.directoryImportConfigured = s.configured;
      },
      error: () => {
        this.directoryImportConfigured = false;
      },
    });
  }

  loadDesk(): void {
    this.loading = true;
    const now = new Date();
    forkJoin({
      daily: this.financeApi
        .robinhoodDailyTracker(now.getFullYear(), [now.getMonth() + 1])
        .pipe(catchError(() => of(null))),
      agentic: this.financeApi.robinhoodAgenticStatus().pipe(catchError(() => of(null))),
      cryptoSettings: this.financeApi.robinhoodCryptoAutoTradeSettings().pipe(catchError(() => of(null))),
      cryptoRuns: this.financeApi.robinhoodCryptoAutoTradeRuns().pipe(catchError(() => of([] as RobinhoodRhCryptoAutoTradeRunDto[]))),
      cryptoOrders: this.financeApi.robinhoodCryptoOrders().pipe(catchError(() => of([] as RobinhoodRhCryptoOrderDto[]))),
    }).subscribe({
      next: ({ daily, agentic, cryptoSettings, cryptoRuns, cryptoOrders }) => {
        this.daily = daily;
        this.agenticStatus = agentic;
        this.cryptoSettings = cryptoSettings;
        this.cryptoRuns = cryptoRuns;
        this.cryptoOrders = cryptoOrders;
        if (agentic?.connected) {
          this.loadAgenticBook();
        } else {
          this.agenticPositions = [];
          this.agenticSyncedOrders = [];
          this.agenticOrders = [];
          this.agenticRuns = [];
          this.agenticSettings = null;
          this.loading = false;
        }
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load trade desk — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 7000,
        });
      },
    });
  }

  private loadAgenticBook(): void {
    forkJoin({
      positions: this.financeApi.robinhoodAgenticPositions().pipe(catchError(() => of({ positions: [] }))),
      synced: this.financeApi.robinhoodAgenticSyncedOrders().pipe(catchError(() => of({ orders: [] }))),
      settings: this.financeApi.robinhoodAgenticSettings().pipe(catchError(() => of(null))),
      orders: this.financeApi.robinhoodAgenticOrders().pipe(catchError(() => of({ orders: [] }))),
      runs: this.financeApi.robinhoodAgenticAutoTradeRuns().pipe(catchError(() => of([] as RobinhoodAgenticAutoTradeRunDto[]))),
    }).subscribe({
      next: ({ positions, synced, settings, orders, runs }) => {
        this.agenticPositions = positions.positions;
        this.agenticSyncedOrders = synced.orders;
        this.agenticOrders = orders.orders;
        this.agenticRuns = runs;
        this.agenticSettings = settings;
        if (settings) {
          this.applySettingsFromDto(settings);
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  private startAutoRefresh(): void {
    interval(RobinhoodTradingPanelComponent.AUTO_REFRESH_MS)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(() =>
          forkJoin({
            daily: this.financeApi
              .robinhoodDailyTracker(new Date().getFullYear(), [new Date().getMonth() + 1])
              .pipe(catchError(() => of(this.daily))),
            agentic: this.financeApi.robinhoodAgenticStatus().pipe(catchError(() => of(this.agenticStatus))),
          }),
        ),
      )
      .subscribe({
        next: ({ daily, agentic }) => {
          if (document.visibilityState === 'hidden') {
            return;
          }
          this.daily = daily;
          this.agenticStatus = agentic;
          if (agentic?.connected) {
            this.financeApi.robinhoodAgenticPositions().subscribe({
              next: (p) => {
                this.agenticPositions = p.positions;
              },
            });
            this.financeApi.robinhoodAgenticOrders().subscribe({
              next: (o) => {
                this.agenticOrders = o.orders;
              },
            });
            this.financeApi.robinhoodAgenticSyncedOrders().subscribe({
              next: (o) => {
                this.agenticSyncedOrders = o.orders;
              },
            });
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
        this.loadDesk();
      },
      error: (e) => {
        this.capturing = false;
        this.snackBar.open(formatHttpErrorDetail(e) || 'Capture failed', 'Dismiss', { duration: 7000 });
      },
    });
  }

  syncAgentic(): void {
    this.agenticSyncing = true;
    this.financeApi.robinhoodAgenticSync().subscribe({
      next: (r) => {
        this.agenticSyncing = false;
        this.snackBar.open(r.message || 'Sync complete', undefined, { duration: 5000 });
        this.loadDesk();
      },
      error: (e) => {
        this.agenticSyncing = false;
        this.snackBar.open(`Sync failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  nowLive(): DeskLive | null {
    let best: DeskLive | null = null;
    for (const day of this.daily?.days ?? []) {
      const priorClose = day.priorPull?.combinedTotal ?? null;
      if (day.hasScheduledSnapshot && day.snapshotAt) {
        const candidate: DeskLive = {
          at: day.snapshotAt,
          total: day.combinedTotal,
          kind: 'CLOSE',
          priorClose: day.hasPreviousScheduledSnapshot
            ? day.combinedTotal - day.combinedTotalChangeFromPrevious
            : priorClose,
          agentic: this.accountValue(day, RobinhoodTradingPanelComponent.AGENTIC_SUFFIX),
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
        const candidate: DeskLive = {
          at: live.capturedAt,
          total: live.combinedTotal,
          kind: 'LIVE',
          priorClose: afterOfficialClose ? day.combinedTotal : priorClose,
          agentic:
            live.accounts.find((a) => a.accountSuffix === RobinhoodTradingPanelComponent.AGENTIC_SUFFIX)
              ?.totalAccountValue ?? null,
        };
        if (!best || new Date(candidate.at).getTime() > new Date(best.at).getTime()) {
          best = candidate;
        }
      }
    }
    return best;
  }

  sinceLastClose(): number | null {
    const live = this.nowLive();
    if (live == null || live.priorClose == null) {
      return null;
    }
    return live.total - live.priorClose;
  }

  agenticBook(): number {
    return this.displayedLivePositions().reduce((sum, p) => sum + (p.marketValue ?? 0), 0);
  }

  pendingReviews(): RobinhoodAgenticOrderDto[] {
    return this.agenticOrders.filter((o) => o.status === 'pending_approval');
  }

  displayedLivePositions(): RobinhoodAgenticPositionDto[] {
    return [...this.agenticPositions]
      .filter((p) => this.isOpenPosition(p) && !this.isExcludedAccount(p))
      .sort((a, b) => Math.abs(b.marketValue ?? 0) - Math.abs(a.marketValue ?? 0))
      .slice(0, RobinhoodTradingPanelComponent.LIVE_POSITIONS_LIMIT);
  }

  todayFills(): RobinhoodRhDailyTradeDto[] {
    const today = this.centralDate(new Date().toISOString());
    const rows: RobinhoodRhDailyTradeDto[] = [];
    for (const day of this.daily?.days ?? []) {
      for (const trade of day.trades ?? []) {
        if (trade.executedAt && this.centralDate(trade.executedAt) === today) {
          rows.push(trade);
        }
      }
    }
    return rows.sort((a, b) => (b.executedAt ?? '').localeCompare(a.executedAt ?? ''));
  }

  blotterRows(): BlotterRow[] {
    const today = this.centralDate(new Date().toISOString());
    const rows: BlotterRow[] = [];
    for (const o of this.agenticSyncedOrders) {
      const when = o.updatedAt ?? o.createdAt ?? o.syncedAt;
      rows.push({
        key: `rh-${o.robinhoodOrderId}-${o.accountNumber}`,
        when,
        source: 'Robinhood',
        symbol: o.symbol,
        side: o.side ?? '—',
        qty: o.quantity,
        price: o.averagePrice ?? o.limitPrice,
        state: o.state ?? '—',
        today: !!when && this.centralDate(when) === today,
      });
    }
    for (const o of this.cryptoOrders) {
      const when = o.placedAt ?? o.createdAt;
      rows.push({
        key: `crypto-${o.id}`,
        when,
        source: 'Crypto',
        symbol: o.symbol,
        side: o.side,
        qty: o.assetQuantity,
        price: o.estimatedNotional,
        state: o.status,
        today: !!when && this.centralDate(when) === today,
      });
    }
    return rows
      .sort((a, b) => (b.when || '').localeCompare(a.when || ''))
      .slice(0, 20);
  }

  recentRuns(): { key: string; kind: string; at: string; status: string; message: string }[] {
    const equity = this.agenticRuns.slice(0, 4).map((r) => ({
      key: `eq-${r.id}`,
      kind: 'Stocks',
      at: r.finishedAt ?? r.startedAt,
      status: r.status,
      message: r.message,
    }));
    const crypto = this.cryptoRuns.slice(0, 4).map((r) => ({
      key: `cr-${r.id}`,
      kind: 'Crypto',
      at: r.finishedAt ?? r.startedAt,
      status: r.status,
      message: r.message,
    }));
    return [...equity, ...crypto]
      .sort((a, b) => (b.at || '').localeCompare(a.at || ''))
      .slice(0, 6);
  }

  connected(): boolean {
    return !!this.agenticStatus?.connected;
  }

  marketSessionLabel(): string {
    const now = new Date();
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Chicago',
      weekday: 'short',
      hour: 'numeric',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(now);
    const weekday = parts.find((p) => p.type === 'weekday')?.value ?? '';
    const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? '0');
    const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? '0');
    const mins = hour * 60 + minute;
    const weekend = weekday === 'Sat' || weekday === 'Sun';
    if (weekend) {
      return 'Weekend · stocks closed · crypto live';
    }
    if (mins >= 8 * 60 + 30 && mins < 15 * 60) {
      return 'Regular session · 8:30 AM–3:00 PM CT';
    }
    if (mins >= 7 * 60 && mins < 8 * 60 + 30) {
      return 'Pre-market · stocks open 8:30 AM CT';
    }
    if (mins >= 15 * 60 && mins < 16 * 60) {
      return 'After hours · official close 9:00 PM CT';
    }
    return 'Overnight · stocks closed · crypto live';
  }

  openAnalytics(): void {
    this.journalNav.analyticsTabIndex.set(0);
  }

  openCrypto(): void {
    this.journalNav.analyticsTabIndex.set(6);
  }

  reviewAgenticOrder(): void {
    const symbol = this.orderSymbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Enter a symbol', undefined, { duration: 4500 });
      return;
    }
    const sellAll = this.orderAssetClass === 'crypto' && this.orderSide === 'sell' && this.orderSellAll;
    if (!sellAll && (this.orderQuantity == null || this.orderQuantity <= 0)) {
      this.snackBar.open('Enter a positive quantity', undefined, { duration: 4500 });
      return;
    }
    if (this.orderType === 'limit' && (this.orderLimitPrice == null || this.orderLimitPrice <= 0)) {
      this.snackBar.open('Enter a limit price', undefined, { duration: 4500 });
      return;
    }
    this.agenticReviewingOrder = true;
    this.financeApi
      .robinhoodAgenticReviewOrder({
        symbol,
        side: this.orderSide,
        type: this.orderType,
        quantity: sellAll ? null : this.orderQuantity,
        limitPrice: this.orderType === 'limit' ? this.orderLimitPrice : null,
        assetClass: this.orderAssetClass,
        sellAll,
      })
      .subscribe({
        next: (o) => {
          this.agenticReviewingOrder = false;
          const msg =
            o.status === 'placed'
              ? `Order placed (${o.symbol} ${o.side})`
              : o.status === 'pending_approval'
                ? `Queued for approval — ${o.symbol} ${o.side}`
                : `Order reviewed — status: ${o.status}`;
          this.snackBar.open(msg, undefined, { duration: 6000 });
          this.loadDesk();
        },
        error: (e) => {
          this.agenticReviewingOrder = false;
          this.snackBar.open(`Order review failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }

  approveOrder(order: RobinhoodAgenticOrderDto): void {
    this.approvingOrderId = order.id;
    this.financeApi.robinhoodAgenticApproveOrder(order.id).subscribe({
      next: (o) => {
        this.approvingOrderId = null;
        this.snackBar.open(
          o.status === 'placed' ? `Placed ${o.symbol} ${o.side}` : `Approve finished — ${o.status}`,
          undefined,
          { duration: 6000 },
        );
        this.loadDesk();
      },
      error: (e) => {
        this.approvingOrderId = null;
        this.snackBar.open(`Approve failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  rejectOrder(order: RobinhoodAgenticOrderDto): void {
    this.approvingOrderId = order.id;
    this.financeApi.robinhoodAgenticRejectOrder(order.id).subscribe({
      next: () => {
        this.approvingOrderId = null;
        this.snackBar.open(`Rejected ${order.symbol} ${order.side}`, undefined, { duration: 4500 });
        this.loadDesk();
      },
      error: (e) => {
        this.approvingOrderId = null;
        this.snackBar.open(`Reject failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  evaluateAutoTradeNow(): void {
    this.autoTradeEvaluating = true;
    this.financeApi.robinhoodAgenticEvaluateAutoTrade().subscribe({
      next: (r) => {
        this.autoTradeEvaluating = false;
        this.snackBar.open(r.message || 'Stock auto-trade evaluation complete', undefined, { duration: 7000 });
        this.loadDesk();
      },
      error: (e) => {
        this.autoTradeEvaluating = false;
        this.snackBar.open(`Auto-trade failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  evaluateCryptoNow(): void {
    this.cryptoEvaluating = true;
    this.financeApi.robinhoodCryptoAutoTradeEvaluate().subscribe({
      next: (r) => {
        this.cryptoEvaluating = false;
        this.snackBar.open(r.message || 'Crypto auto-trade evaluation complete', undefined, { duration: 7000 });
        this.loadDesk();
      },
      error: (e) => {
        this.cryptoEvaluating = false;
        this.snackBar.open(`Crypto evaluate failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  activateKillSwitch(): void {
    this.autoTradeKillSwitch = true;
    this.autoTradeEnabled = false;
    this.saveAgenticSettings();
    if (this.cryptoSettings) {
      this.financeApi
        .robinhoodCryptoAutoTradeSaveSettings({
          autoTradeEnabled: false,
          autoTradeKillSwitch: true,
          autoTradeMinPositivityBuy: this.cryptoSettings.autoTradeMinPositivityBuy,
          autoTradeMaxPositivitySell: this.cryptoSettings.autoTradeMaxPositivitySell,
          autoTradeMinSpikeZ: this.cryptoSettings.autoTradeMinSpikeZ,
          autoTradeMinMentions24h: this.cryptoSettings.autoTradeMinMentions24h,
          autoTradeOrderQuoteAmount: this.cryptoSettings.autoTradeOrderQuoteAmount,
          autoTradeMaxTradesPerDay: this.cryptoSettings.autoTradeMaxTradesPerDay,
          autoTradeMaxDailyNotional: this.cryptoSettings.autoTradeMaxDailyNotional,
          autoTradeCooldownMinutes: this.cryptoSettings.autoTradeCooldownMinutes,
          allowedSymbols: this.cryptoSettings.allowedSymbols,
        })
        .subscribe({
          next: (s) => {
            this.cryptoSettings = s;
          },
        });
    }
  }

  saveAgenticSettings(): void {
    this.agenticSavingSettings = true;
    this.financeApi
      .robinhoodAgenticSaveSettings({
        requireApproval: this.settingsRequireApproval,
        maxOrderNotional: this.settingsMaxNotional,
        allowedSymbols: this.settingsAllowedSymbols,
        autoTradeEnabled: this.autoTradeEnabled,
        autoTradeKillSwitch: this.autoTradeKillSwitch,
        autoTradeRequireApproval: this.autoTradeRequireApproval,
        autoTradeMinPositivityBuy: this.autoTradeMinPositivityBuy,
        autoTradeMaxPositivitySell: this.autoTradeMaxPositivitySell,
        autoTradeMinSpikeZ: this.autoTradeMinSpikeZ,
        autoTradeMinMentions24h: this.autoTradeMinMentions24h,
        autoTradeOrderQuantity: this.autoTradeOrderQuantity,
        autoTradeMaxTradesPerDay: this.autoTradeMaxTradesPerDay,
        autoTradeMaxDailyNotional: this.autoTradeMaxDailyNotional,
        autoTradeCooldownMinutes: this.autoTradeCooldownMinutes,
        autoTradeMarketHoursOnly: this.autoTradeMarketHoursOnly,
      })
      .subscribe({
        next: (s) => {
          this.agenticSavingSettings = false;
          this.agenticSettings = s;
          this.applySettingsFromDto(s);
          this.snackBar.open('Desk settings saved', undefined, { duration: 4500 });
        },
        error: (e) => {
          this.agenticSavingSettings = false;
          this.snackBar.open(`Save settings failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }

  saveAgenticTokens(): void {
    const raw = this.agenticTokenJson.trim();
    if (!raw) {
      this.snackBar.open('Paste .tokens.json contents or access_token', undefined, { duration: 4500 });
      return;
    }
    let accessToken = raw;
    let refreshToken = '';
    try {
      const parsed = JSON.parse(raw) as { access_token?: string; refresh_token?: string };
      if (parsed.access_token) {
        accessToken = parsed.access_token;
        refreshToken = parsed.refresh_token ?? '';
      }
    } catch {
      // treat as bare access token
    }
    this.agenticSavingTokens = true;
    this.financeApi.robinhoodAgenticSaveTokens(accessToken, refreshToken).subscribe({
      next: () => {
        this.agenticSavingTokens = false;
        this.agenticTokenJson = '';
        this.snackBar.open('Agentic tokens saved', undefined, { duration: 4500 });
        this.loadDesk();
      },
      error: (e) => {
        this.agenticSavingTokens = false;
        this.snackBar.open(`Save tokens failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  disconnectAgentic(): void {
    this.financeApi.robinhoodAgenticDisconnect().subscribe({
      next: () => {
        this.snackBar.open('Agentic disconnected', undefined, { duration: 4500 });
        this.loadDesk();
      },
      error: (e) => {
        this.snackBar.open(`Disconnect failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  onCsvSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    this.csvSelectedFile = f ?? null;
    this.csvSelectedLabel = f?.name ?? null;
    this.csvDirectResult = null;
    this.csvDirectoryResult = null;
  }

  uploadCsvDirect(): void {
    const f = this.csvSelectedFile;
    if (!f) {
      this.snackBar.open('Choose a CSV file first', undefined, { duration: 4500 });
      return;
    }
    this.csvUploading = true;
    this.csvDirectResult = null;
    this.financeApi.robinhoodImportCsv(f, this.csvApplyToDb).subscribe({
      next: (r) => {
        this.csvDirectResult = r;
        this.csvUploading = false;
        this.snackBar.open(this.importResultMessage(r), undefined, { duration: 6500 });
      },
      error: (e) => {
        this.csvUploading = false;
        this.snackBar.open(`CSV import failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  uploadCsvToDirectory(): void {
    const f = this.csvSelectedFile;
    if (!f) {
      this.snackBar.open('Choose a CSV file first', undefined, { duration: 4500 });
      return;
    }
    this.csvUploading = true;
    this.csvDirectoryResult = null;
    this.financeApi.robinhoodCsvSaveToImportDirectory(f, this.csvApplyToDb).subscribe({
      next: (r) => {
        this.csvDirectoryResult = r;
        this.csvUploading = false;
        this.snackBar.open(this.importResultMessage(r.importResult), undefined, { duration: 6500 });
      },
      error: (e) => {
        this.csvUploading = false;
        this.snackBar.open(`CSV save/import failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  fillFromPosition(p: RobinhoodAgenticPositionDto): void {
    this.orderSymbol = p.symbol;
    this.orderAssetClass = 'equity';
    this.orderSide = 'sell';
    this.orderQuantity = p.quantity;
    this.orderSellAll = false;
  }

  isGain(value: number | null | undefined): boolean {
    return value != null && value > 0;
  }

  isLoss(value: number | null | undefined): boolean {
    return value != null && value < 0;
  }

  isOpenPosition(p: RobinhoodAgenticPositionDto): boolean {
    return p.quantity != null && p.quantity !== 0;
  }

  positionTypeLabel(p: RobinhoodAgenticPositionDto): string {
    return p.positionType === 'option' ? 'Option' : 'Equity';
  }

  optionContractLabel(p: RobinhoodAgenticPositionDto): string {
    if (p.positionType !== 'option') {
      return '—';
    }
    const type = p.optionType ? p.optionType.toUpperCase() : '?';
    const strike = p.strikePrice ?? '—';
    const exp = p.expirationDate ? p.expirationDate.slice(0, 10) : '—';
    return `${type} ${strike} · ${exp}`;
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

  nowAtLabel(): string {
    const live = this.nowLive();
    if (!live) {
      return 'No captures yet';
    }
    return `${live.kind === 'CLOSE' ? 'Official close' : 'Live'} · ${this.formatWhen(live.at)}`;
  }

  private applySettingsFromDto(s: RobinhoodAgenticSettingsDto): void {
    this.settingsRequireApproval = s.requireApproval;
    this.settingsMaxNotional = s.maxOrderNotional;
    this.settingsAllowedSymbols = s.allowedSymbols ?? '';
    this.autoTradeEnabled = s.autoTradeEnabled;
    this.autoTradeKillSwitch = s.autoTradeKillSwitch;
    this.autoTradeRequireApproval = s.autoTradeRequireApproval;
    this.autoTradeMinPositivityBuy = s.autoTradeMinPositivityBuy;
    this.autoTradeMaxPositivitySell = s.autoTradeMaxPositivitySell;
    this.autoTradeMinSpikeZ = s.autoTradeMinSpikeZ;
    this.autoTradeMinMentions24h = s.autoTradeMinMentions24h;
    this.autoTradeOrderQuantity = s.autoTradeOrderQuantity;
    this.autoTradeMaxTradesPerDay = s.autoTradeMaxTradesPerDay;
    this.autoTradeMaxDailyNotional = s.autoTradeMaxDailyNotional;
    this.autoTradeCooldownMinutes = s.autoTradeCooldownMinutes;
    this.autoTradeMarketHoursOnly = s.autoTradeMarketHoursOnly;
  }

  private importResultMessage(r: RobinhoodCsvImportResultDto): string {
    if (r.apply && r.errorCount === 0) {
      return `Imported ${r.insertedRows} row(s)`;
    }
    if (r.apply) {
      return `Import finished with ${r.errorCount} error(s)`;
    }
    return `Dry-run: parsed ${r.parsedRows} row(s)`;
  }

  private isExcludedAccount(p: RobinhoodAgenticPositionDto): boolean {
    const masked = p.accountNumberMasked ?? '';
    return masked.endsWith(RobinhoodTradingPanelComponent.EXCLUDED_ACCOUNT_SUFFIX);
  }

  private accountValue(day: RobinhoodRhDailyTrackerDayDto, suffix: string): number | null {
    const cell = day.accounts.find((a) => a.accountSuffix === suffix);
    return cell?.totalAccountValue ?? null;
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

  private centralDate(iso: string): string {
    return new Date(iso).toLocaleDateString('en-CA', { timeZone: 'America/Chicago' });
  }
}
