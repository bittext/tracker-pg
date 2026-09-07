import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodCryptoTradingStatusDto,
  RobinhoodRhCryptoAutoTradeRunDto,
  RobinhoodRhCryptoAutoTradeSettingsDto,
  RobinhoodRhCryptoAutoTradeSettingsRequestDto,
  RobinhoodRhCryptoHoldingDto,
  RobinhoodRhCryptoOrderDto,
  RobinhoodRhCryptoTrackerDayDto,
  RobinhoodRhCryptoTrackerReportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail, formatHttpErrorMessage } from '../../../util/http-error';

type FeedbackKind = 'ok' | 'error' | 'info';

@Component({
  selector: 'app-reports-finance-robinhood-crypto-tracker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-crypto-tracker.component.html',
  styleUrl: './reports-finance-robinhood-crypto-tracker.component.scss',
})
export class ReportsFinanceRobinhoodCryptoTrackerComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  reportMonths: number[] = [new Date().getMonth() + 1];
  loading = false;
  capturing = false;
  savingCredentials = false;
  connectExpanded = false;
  autoTradeExpanded = false;
  autoTradeAdvancedExpanded = false;
  tracker: RobinhoodRhCryptoTrackerReportDto | null = null;
  cryptoStatus: RobinhoodCryptoTradingStatusDto | null = null;
  autoTradeSettings: RobinhoodRhCryptoAutoTradeSettingsDto | null = null;
  autoTradeRuns: RobinhoodRhCryptoAutoTradeRunDto[] = [];
  cryptoOrders: RobinhoodRhCryptoOrderDto[] = [];

  apiKey = '';
  privateKeyBase64 = '';
  connectError: string | null = null;
  captureFeedback: { kind: FeedbackKind; message: string } | null = null;

  autoTradeEnabled = false;
  autoTradeKillSwitch = false;
  autoTradeOrderQuoteAmount = 25;
  autoTradeMaxTradesPerDay = 3;
  autoTradeMaxDailyNotional: number | null = 500;
  autoTradeCooldownMinutes = 60;
  autoTradeMinPositivityBuy = 15;
  autoTradeMaxPositivitySell = -15;
  autoTradeMinSpikeZ = 1.5;
  autoTradeMinMentions24h = 5;
  allowedSymbols: string[] = ['BTC', 'ETH'];
  autoTradeSaving = false;
  autoTradeEvaluating = false;
  autoTradeLastMessage = '';
  liveHoldings: RobinhoodRhCryptoHoldingDto[] = [];
  costLoading = false;
  costError: string | null = null;

  readonly coinChoices = ['BTC', 'ETH', 'SOL', 'DOGE', 'ADA', 'XRP', 'AVAX', 'LINK'] as const;
  private static readonly DEFAULT_SELL_FEE_RATE = 0.0095;

  readonly expandedDays = new Set<string>();

  readonly monthChoices = [
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' },
  ];

  ngOnInit(): void {
    this.load();
    this.loadCryptoStatus();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  load(): void {
    this.loading = true;
    const months = this.normalizedReportMonths();
    this.financeApi.robinhoodCryptoTracker(this.reportYear, months).subscribe({
      next: (t) => {
        this.tracker = t;
        this.loading = false;
      },
      error: (err) => {
        this.tracker = null;
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  loadCryptoStatus(): void {
    this.financeApi.robinhoodCryptoTradingStatus().subscribe({
      next: (s) => {
        this.cryptoStatus = s;
        if (!s.connected) {
          this.connectExpanded = true;
        } else {
          this.loadAutoTradePanel();
          this.loadCost();
        }
      },
      error: () => {
        this.cryptoStatus = null;
      },
    });
  }

  loadAutoTradePanel(): void {
    this.financeApi.robinhoodCryptoAutoTradeSettings().subscribe({
      next: (s) => {
        this.autoTradeSettings = s;
        this.applyAutoTradeSettings(s);
        if (s.autoTradeEnabled) {
          this.autoTradeExpanded = true;
        }
      },
      error: () => {
        this.autoTradeSettings = null;
      },
    });
    this.financeApi.robinhoodCryptoAutoTradeRuns().subscribe({
      next: (runs) => {
        this.autoTradeRuns = runs;
      },
      error: () => {
        this.autoTradeRuns = [];
      },
    });
    this.financeApi.robinhoodCryptoOrders().subscribe({
      next: (orders) => {
        this.cryptoOrders = orders;
      },
      error: () => {
        this.cryptoOrders = [];
      },
    });
  }

  toggleAutoTrade(): void {
    this.autoTradeExpanded = !this.autoTradeExpanded;
  }

  toggleAutoTradeAdvanced(): void {
    this.autoTradeAdvancedExpanded = !this.autoTradeAdvancedExpanded;
  }

  isCoinAllowed(coin: string): boolean {
    return this.allowedSymbols.includes(coin);
  }

  toggleCoin(coin: string): void {
    if (this.isCoinAllowed(coin)) {
      this.allowedSymbols = this.allowedSymbols.filter((c) => c !== coin);
    } else {
      this.allowedSymbols = [...this.allowedSymbols, coin];
    }
  }

  saveAutoTradeSettings(): void {
    this.autoTradeSaving = true;
    const body: RobinhoodRhCryptoAutoTradeSettingsRequestDto = {
      autoTradeEnabled: this.autoTradeEnabled,
      autoTradeKillSwitch: this.autoTradeKillSwitch,
      autoTradeMinPositivityBuy: this.autoTradeMinPositivityBuy,
      autoTradeMaxPositivitySell: this.autoTradeMaxPositivitySell,
      autoTradeMinSpikeZ: this.autoTradeMinSpikeZ,
      autoTradeMinMentions24h: this.autoTradeMinMentions24h,
      autoTradeOrderQuoteAmount: this.autoTradeOrderQuoteAmount,
      autoTradeMaxTradesPerDay: this.autoTradeMaxTradesPerDay,
      autoTradeMaxDailyNotional: this.autoTradeMaxDailyNotional,
      autoTradeCooldownMinutes: this.autoTradeCooldownMinutes,
      allowedSymbols: this.allowedSymbols,
    };
    this.financeApi.robinhoodCryptoAutoTradeSaveSettings(body).subscribe({
      next: (s) => {
        this.autoTradeSettings = s;
        this.applyAutoTradeSettings(s);
        this.autoTradeSaving = false;
        this.snackBar.open('Auto-trade settings saved.', 'Dismiss', { duration: 4000 });
      },
      error: (err) => {
        this.autoTradeSaving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  evaluateAutoTradeNow(): void {
    this.autoTradeEvaluating = true;
    this.financeApi.robinhoodCryptoAutoTradeEvaluate().subscribe({
      next: (r) => {
        this.autoTradeEvaluating = false;
        this.autoTradeLastMessage = r.message;
        this.loadAutoTradePanel();
        this.snackBar.open(r.message || 'Evaluate complete.', 'Dismiss', { duration: 6000 });
      },
      error: (err) => {
        this.autoTradeEvaluating = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  panicStopAutoTrade(): void {
    this.autoTradeKillSwitch = true;
    this.autoTradeEnabled = false;
    this.saveAutoTradeSettings();
  }

  private applyAutoTradeSettings(s: RobinhoodRhCryptoAutoTradeSettingsDto): void {
    this.autoTradeEnabled = s.autoTradeEnabled;
    this.autoTradeKillSwitch = s.autoTradeKillSwitch;
    this.autoTradeMinPositivityBuy = s.autoTradeMinPositivityBuy;
    this.autoTradeMaxPositivitySell = s.autoTradeMaxPositivitySell;
    this.autoTradeMinSpikeZ = s.autoTradeMinSpikeZ;
    this.autoTradeMinMentions24h = s.autoTradeMinMentions24h;
    this.autoTradeOrderQuoteAmount = s.autoTradeOrderQuoteAmount;
    this.autoTradeMaxTradesPerDay = s.autoTradeMaxTradesPerDay;
    this.autoTradeMaxDailyNotional = s.autoTradeMaxDailyNotional;
    this.autoTradeCooldownMinutes = s.autoTradeCooldownMinutes;
    this.allowedSymbols = s.allowedSymbols?.length ? [...s.allowedSymbols] : ['BTC', 'ETH'];
    this.autoTradeLastMessage = s.autoTradeLastRunMessage ?? '';
  }

  onMonthsChange(): void {
    this.load();
  }

  selectAllMonths(): void {
    this.reportMonths = this.monthChoices.map((m) => m.value);
    this.load();
  }

  clearMonthSelection(): void {
    this.reportMonths = [];
    this.load();
  }

  monthsFilterLabel(): string {
    if (!this.reportMonths.length) {
      return 'All months';
    }
    if (this.reportMonths.length === 1) {
      const m = this.monthChoices.find((c) => c.value === this.reportMonths[0]);
      return m?.label ?? '1 month';
    }
    return `${this.reportMonths.length} months`;
  }

  toggleConnect(): void {
    this.connectExpanded = !this.connectExpanded;
  }

  saveCredentials(): void {
    const apiKey = this.apiKey.trim();
    const privateKeyBase64 = this.privateKeyBase64.trim();
    if (!apiKey || !privateKeyBase64) {
      this.connectError = 'API key and private key are both required.';
      return;
    }
    this.connectError = null;
    this.savingCredentials = true;
    this.financeApi.robinhoodCryptoTradingSaveCredentials({ apiKey, privateKeyBase64 }).subscribe({
      next: (s) => {
        this.cryptoStatus = s;
        this.savingCredentials = false;
        this.apiKey = '';
        this.privateKeyBase64 = '';
        this.connectExpanded = false;
        this.showCaptureFeedback('ok', 'Crypto Trading API credentials saved.');
        this.load();
        this.loadAutoTradePanel();
      },
      error: (err) => {
        this.savingCredentials = false;
        this.connectError = formatHttpErrorMessage(err);
      },
    });
  }

  disconnect(): void {
    this.financeApi.robinhoodCryptoTradingDisconnect().subscribe({
      next: () => {
        this.cryptoStatus = null;
        this.loadCryptoStatus();
        this.load();
        this.snackBar.open('Crypto Trading API disconnected.', 'Dismiss', { duration: 4000 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  captureNow(): void {
    this.capturing = true;
    this.captureFeedback = null;
    this.financeApi.robinhoodCryptoTrackerCapture(true).subscribe({
      next: (r) => {
        this.capturing = false;
        this.showCaptureFeedback(r.ok ? 'ok' : 'error', r.message);
        this.loadCryptoStatus();
        this.load();
        this.loadCost();
      },
      error: (err) => {
        this.capturing = false;
        this.showCaptureFeedback('error', formatHttpErrorMessage(err));
      },
    });
  }

  loadCost(): void {
    this.costLoading = true;
    this.costError = null;
    this.financeApi.robinhoodCryptoTradingSync().subscribe({
      next: (sync) => {
        const fromPortfolios = (sync.portfolios ?? []).flatMap((p) => p.holdings ?? []);
        this.liveHoldings = this.mergeHoldings(fromPortfolios.length ? fromPortfolios : (sync.holdings ?? []));
        this.costLoading = false;
        if (!sync.ok && !this.liveHoldings.length) {
          this.costError = sync.message || 'Could not load crypto cost';
        }
      },
      error: (err) => {
        this.costLoading = false;
        this.liveHoldings = [];
        this.costError = formatHttpErrorDetail(err);
      },
    });
  }

  costRows(): RobinhoodRhCryptoHoldingDto[] {
    if (this.liveHoldings.length) {
      return this.liveHoldings;
    }
    const latest = this.tracker?.days?.[0];
    if (!latest) {
      return [];
    }
    const fromAccounts = (latest.accounts ?? []).flatMap((a) => a.holdings ?? []);
    return this.mergeHoldings(fromAccounts.length ? fromAccounts : (latest.holdings ?? []));
  }

  sellFeeRate(row?: RobinhoodRhCryptoHoldingDto | null): number {
    const rate = row?.sellFeeRate;
    if (rate != null && rate > 0) {
      return rate;
    }
    return ReportsFinanceRobinhoodCryptoTrackerComponent.DEFAULT_SELL_FEE_RATE;
  }

  sellAllFee(row: RobinhoodRhCryptoHoldingDto): number {
    return (row.marketValue ?? 0) * this.sellFeeRate(row);
  }

  sellAllNet(row: RobinhoodRhCryptoHoldingDto): number {
    return (row.marketValue ?? 0) - this.sellAllFee(row);
  }

  breakevenPrice(row: RobinhoodRhCryptoHoldingDto): number | null {
    const qty = row.quantity ?? 0;
    const cost = row.costBasis ?? 0;
    const rate = this.sellFeeRate(row);
    if (qty <= 0 || cost <= 0 || rate >= 1) {
      return null;
    }
    return cost / (qty * (1 - rate));
  }

  sellAllFeeTotal(): number {
    return this.costRows().reduce((sum, row) => sum + this.sellAllFee(row), 0);
  }

  sellAllMarketTotal(): number {
    return this.costRows().reduce((sum, row) => sum + (row.marketValue ?? 0), 0);
  }

  sellAllFeePct(): number {
    const mv = this.sellAllMarketTotal();
    return mv > 0 ? (this.sellAllFeeTotal() / mv) * 100 : this.sellFeeRate() * 100;
  }

  buyFeesTotal(): number {
    return this.costRows().reduce((sum, row) => sum + (row.buyFees ?? 0), 0);
  }

  costBasisTotal(): number {
    return this.costRows().reduce((sum, row) => sum + (row.costBasis ?? 0), 0);
  }

  dogeRow(): RobinhoodRhCryptoHoldingDto | null {
    return this.costRows().find((row) => (row.symbol || '').toUpperCase() === 'DOGE') ?? null;
  }

  formatExactPrice(value: number | null | undefined, maxDigits = 12): string {
    if (value == null || Number.isNaN(value)) {
      return '—';
    }
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: maxDigits,
      useGrouping: false,
    });
  }

  formatQty(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '—';
    }
    return value.toLocaleString('en-US', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 8,
      useGrouping: true,
    });
  }

  private mergeHoldings(rows: RobinhoodRhCryptoHoldingDto[]): RobinhoodRhCryptoHoldingDto[] {
    const bySymbol = new Map<string, RobinhoodRhCryptoHoldingDto>();
    for (const row of rows) {
      const symbol = (row.symbol || '').toUpperCase();
      if (!symbol) {
        continue;
      }
      const existing = bySymbol.get(symbol);
      if (!existing) {
        bySymbol.set(symbol, { ...row, symbol });
        continue;
      }
      const quantity = (existing.quantity ?? 0) + (row.quantity ?? 0);
      const costBasis = (existing.costBasis ?? 0) + (row.costBasis ?? 0);
      const marketValue = (existing.marketValue ?? 0) + (row.marketValue ?? 0);
      const buyFees = (existing.buyFees ?? 0) + (row.buyFees ?? 0);
      const lifetimeFees = (existing.lifetimeFees ?? 0) + (row.lifetimeFees ?? 0);
      existing.quantity = quantity;
      existing.costBasis = costBasis;
      existing.marketValue = marketValue;
      existing.buyFees = buyFees;
      existing.lifetimeFees = lifetimeFees;
      existing.averageBuyPrice = quantity > 0 ? costBasis / quantity : existing.averageBuyPrice;
      existing.currentUnitPrice = quantity > 0 ? marketValue / quantity : existing.currentUnitPrice;
      existing.unrealizedPnL = marketValue - costBasis;
      existing.unrealizedPnLPercent = costBasis > 0 ? (existing.unrealizedPnL / costBasis) * 100 : 0;
      existing.sellFeeRate = Math.max(existing.sellFeeRate ?? 0, row.sellFeeRate ?? 0);
    }
    return [...bySymbol.values()].sort((a, b) => (b.marketValue ?? 0) - (a.marketValue ?? 0));
  }

  isConnected(): boolean {
    return this.tracker?.cryptoConnected === true || this.cryptoStatus?.connected === true;
  }

  connectionStatusLabel(): string {
    if (!this.tracker?.sidecarConfigured && !this.cryptoStatus?.sidecarConfigured) {
      return 'Sidecar not configured';
    }
    if (!this.isConnected()) {
      return 'Crypto API not connected';
    }
    return 'Crypto API connected';
  }

  connectionStatusClass(): string {
    if (!this.tracker?.sidecarConfigured && !this.cryptoStatus?.sidecarConfigured) {
      return 'rh-crypto__status--muted';
    }
    return this.isConnected() ? 'rh-crypto__status--ok' : 'rh-crypto__status--warn';
  }

  dayKey(day: RobinhoodRhCryptoTrackerDayDto): string {
    return day.snapshotDate || day.snapshotAt;
  }

  isDayExpanded(day: RobinhoodRhCryptoTrackerDayDto): boolean {
    return this.expandedDays.has(this.dayKey(day));
  }

  toggleDay(day: RobinhoodRhCryptoTrackerDayDto): void {
    const key = this.dayKey(day);
    if (this.expandedDays.has(key)) {
      this.expandedDays.delete(key);
    } else {
      this.expandedDays.add(key);
    }
  }

  captureKindLabel(kind: string): string {
    switch (kind) {
      case 'MANUAL':
        return 'manual';
      case 'INTRADAY':
        return 'hourly';
      case 'SCHEDULED':
        return '9 PM close';
      default:
        return (kind || '').toLowerCase();
    }
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'rh-crypto__pnl--pos' : 'rh-crypto__pnl--neg';
  }

  formatDelta(value: number): string {
    const abs = Math.abs(value);
    const formatted = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(abs);
    return value >= 0 ? `+${formatted}` : `−${formatted}`;
  }

  syncStatusClass(): string {
    const status = this.cryptoStatus?.lastSyncStatus?.toLowerCase() ?? '';
    if (status === 'ok') {
      return 'rh-crypto__sync rh-crypto__sync--ok';
    }
    if (status === 'error') {
      return 'rh-crypto__sync rh-crypto__sync--error';
    }
    return 'rh-crypto__sync rh-crypto__sync--muted';
  }

  syncStatusIcon(): string {
    const status = this.cryptoStatus?.lastSyncStatus?.toLowerCase() ?? '';
    if (status === 'ok') {
      return 'check_circle';
    }
    if (status === 'error') {
      return 'error_outline';
    }
    return 'sync';
  }

  bannerClass(kind: FeedbackKind): string {
    return `rh-crypto__banner rh-crypto__banner--${kind}`;
  }

  bannerIcon(kind: FeedbackKind): string {
    switch (kind) {
      case 'ok':
        return 'check_circle';
      case 'error':
        return 'error_outline';
      default:
        return 'info';
    }
  }

  dismissCaptureFeedback(): void {
    this.captureFeedback = null;
  }

  private showCaptureFeedback(kind: FeedbackKind, message: string): void {
    this.captureFeedback = { kind, message };
  }

  private normalizedReportMonths(): number[] | undefined {
    if (!this.reportMonths.length) {
      return undefined;
    }
    return [...this.reportMonths].sort((a, b) => a - b);
  }
}
