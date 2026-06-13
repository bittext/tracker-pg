import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import {
  FinanceAlertEventDto,
  FinanceCrawlSnapshotDto,
  FinanceStockAlertDto,
  FinanceStockAlertRepeatMode,
  FinanceStockAlertRequestDto,
  FinanceStockAlertTriggerType,
  RobinhoodStocksSummaryDto,
  RobinhoodTransactionsDto,
  StockNewsDto,
  StockNewsItemDto,
  Surge52WeekHighsDto,
  Surge52WeekRowDto,
  BreakoutCandidatesDto,
  BreakoutCandidateRowDto,
} from '../../models/finance.models';
import { FinanceApiService, FinancePeriod } from '../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import { BankingPanelComponent } from './banking-panel/banking-panel.component';
import { FinanceTax1040PanelComponent } from './finance-tax-1040-panel/finance-tax-1040-panel.component';
import { MarketOverviewPanelComponent } from './market-overview-panel/market-overview-panel.component';
import { PredictsPanelComponent } from './predicts-panel/predicts-panel.component';
import { RobinhoodTradingPanelComponent } from './robinhood-trading-panel/robinhood-trading-panel.component';
import { TradingScreenersPanelComponent } from './trading-screeners-panel/trading-screeners-panel.component';
import { LoansPanelComponent } from './loans-panel/loans-panel.component';
import { InvestmentsPanelComponent } from './investments-panel/investments-panel.component';
import { MoneyPanelComponent } from './money-panel/money-panel.component';
import { CreditPanelComponent } from './credit-panel/credit-panel.component';
import { InsurancePanelComponent } from './insurance-panel/insurance-panel.component';

@Component({
  selector: 'app-finance',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
    FinanceTax1040PanelComponent,
    BankingPanelComponent,
    MarketOverviewPanelComponent,
    PredictsPanelComponent,
    RobinhoodTradingPanelComponent,
    TradingScreenersPanelComponent,
    LoansPanelComponent,
    InvestmentsPanelComponent,
    MoneyPanelComponent,
    CreditPanelComponent,
    InsurancePanelComponent,
  ],
  templateUrl: './finance.component.html',
  styleUrl: './finance.component.scss',
})
export class FinanceComponent implements OnInit {
  private static readonly FINANCE_COLUMNS_HIDDEN = new Set<string>(['SETTLE_DATE']);
  private static readonly STOCK_NEWS_LIMIT = 10;
  private static readonly RISING_52W_LIMIT = 5;
  private static readonly BREAKOUT_CANDIDATES_LIMIT = 20;

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Finance category tabs: 0=banking, 1=investments, 2=loans, 3=market, 4=money, 5=credit, 6=trading, 7=insurance, 8=taxes. */
  financeCategoryTabIndex = 0;
  /** Trading tabs: 0=robinhood, 1=news, 2=crawler, 3=52w, 4=break outs, 5=alerts, 6=transactions, 7=by symbol, 8=screeners, 9=summary, 10=predicts. */
  financeSubTabIndex = 0;

  stockSymbols: string[] = [];
  stockSymbolsLoading = false;
  private stockSymbolLoadWaiters: Array<() => void> = [];

  selectedInstrument = '';
  editableInstrument = '';
  companyName = '';

  /** Transactions panel: Robinhood rows (server-capped, server-filtered by period when not "all"). */
  rhTxns: RobinhoodTransactionsDto | null = null;
  rhDisplayedRows: Record<string, unknown>[] = [];
  rhTxnColumns: string[] = [];
  rhLoading = false;

  /** By instrument panel state. */
  financeSelectedSymbol = '';
  rhIndvTxns: RobinhoodTransactionsDto | null = null;
  rhIndvDisplayedRows: Record<string, unknown>[] = [];
  rhIndvTxnColumns: string[] = [];
  rhIndvLoading = false;

  /** Summary panel state. */
  stocksSummaryYear = new Date().getFullYear();
  stocksSummaryInstrument = '';
  rhSummary: RobinhoodStocksSummaryDto | null = null;
  rhSummaryLoading = false;

  rhStockNews: StockNewsDto | null = null;
  rhStockNewsLoading = false;
  crawlSnapshot: FinanceCrawlSnapshotDto | null = null;
  crawlLoading = false;
  rhRising52w: Surge52WeekHighsDto | null = null;
  rhRising52wLoading = false;
  breakoutCandidates: BreakoutCandidatesDto | null = null;
  breakoutLoading = false;

  financeAlerts: FinanceStockAlertDto[] = [];
  financeAlertEvents: FinanceAlertEventDto[] = [];
  financeAlertsLoading = false;
  financeAlertSaving = false;
  financeAlertEvaluating = false;
  editingFinanceAlertId: number | null = null;
  financeAlertForm: FinanceStockAlertRequestDto = this.blankFinanceAlertForm();

  financePeriod: FinancePeriod = 'month';
  financeYear = new Date().getFullYear();
  financeMonth = new Date().getMonth() + 1;

  readonly monthChoices: { value: number; label: string }[] = [
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
    this.ensureStockSymbolsLoaded(undefined, true);
  }

  onInstrumentSelectChange(): void {
    this.editableInstrument = this.selectedInstrument ?? '';
  }

  onFinanceCategoryTabIndexChange(index: number): void {
    if (index === 6) {
      this.onFinanceSubTabIndexChange(this.financeSubTabIndex);
    }
  }

  onFinanceSubTabIndexChange(index: number): void {
    if (index === 2) {
      this.loadCrawlSnapshot();
    } else if (index === 3) {
      this.loadRising52WeekHighs();
    } else if (index === 4) {
      this.loadBreakoutCandidates();
    } else if (index === 5) {
      this.loadFinanceAlerts();
      this.loadFinanceAlertEvents();
    } else if (index === 6) {
      this.loadRobinhoodFinanceData();
    } else if (index === 7) {
      this.ensureStockSymbolsLoaded(() => {
        if (this.financeSelectedSymbol.trim()) {
          this.loadIndividualStockFinanceData();
        }
      }, true);
    } else if (index === 9) {
      this.ensureStockSymbolsLoaded(undefined, this.stockSymbols.length === 0);
      this.loadStocksSummary();
    }
  }

  onFinanceFilterSelectionChange(): void {
    if (this.financeCategoryTabIndex !== 6) {
      return;
    }
    if (this.financeSubTabIndex === 6) {
      this.loadRobinhoodFinanceData();
    } else if (this.financeSubTabIndex === 7) {
      if (this.financeSelectedSymbol.trim()) {
        this.loadIndividualStockFinanceData();
      }
    }
  }

  onStocksSummaryFilterChange(): void {
    if (this.financeCategoryTabIndex === 6 && this.financeSubTabIndex === 9) {
      this.loadStocksSummary();
    }
  }

  loadStockNews(): void {
    const instrument = this.editableInstrument.trim();
    const company = this.companyName.trim();
    if (!instrument && !company) {
      this.snackBar.open('Provide an instrument or company name to load news', 'Dismiss', { duration: 5000 });
      return;
    }
    this.rhStockNewsLoading = true;
    this.financeApi
      .robinhoodStockNews(instrument || undefined, company || undefined, FinanceComponent.STOCK_NEWS_LIMIT)
      .subscribe({
      next: (r) => {
        this.rhStockNews = r;
        this.rhStockNewsLoading = false;
      },
      error: (e) => {
        this.rhStockNewsLoading = false;
        this.rhStockNews = null;
        this.err('Could not load stock news', e);
      },
      });
  }

  stockNewsTrack(idx: number, item: StockNewsItemDto): string {
    return `${idx}\u0001${item.source}\u0001${item.publishedAt}\u0001${item.url}`;
  }

  loadCrawlSnapshot(): void {
    this.crawlLoading = true;
    this.financeApi.financeCrawlSnapshot().subscribe({
      next: (r) => {
        this.crawlSnapshot = r;
        this.crawlLoading = false;
      },
      error: (e) => {
        this.crawlLoading = false;
        this.crawlSnapshot = null;
        this.err('Could not load crawler snapshot', e);
      },
    });
  }

  loadRising52WeekHighs(): void {
    this.rhRising52wLoading = true;
    this.financeApi.robinhoodRising52WeekHighs(FinanceComponent.RISING_52W_LIMIT).subscribe({
      next: (r) => {
        this.rhRising52w = r;
        this.rhRising52wLoading = false;
      },
      error: (e) => {
        this.rhRising52wLoading = false;
        this.rhRising52w = null;
        this.err('Could not load names at 52-week high', e);
      },
    });
  }

  rising52wTrack(_idx: number, row: Surge52WeekRowDto): string {
    return row.symbol;
  }

  loadBreakoutCandidates(): void {
    this.breakoutLoading = true;
    this.financeApi.robinhoodBreakoutCandidates(FinanceComponent.BREAKOUT_CANDIDATES_LIMIT).subscribe({
      next: (r) => {
        this.breakoutCandidates = r;
        this.breakoutLoading = false;
      },
      error: (e) => {
        this.breakoutCandidates = null;
        this.breakoutLoading = false;
        this.err('Could not load breakout candidates', e);
      },
    });
  }

  breakoutRowTrack(_idx: number, row: BreakoutCandidateRowDto): string {
    return row.symbol;
  }

  loadFinanceAlerts(): void {
    this.financeAlertsLoading = true;
    this.financeApi.financeAlerts().subscribe({
      next: (rows) => {
        this.financeAlerts = rows;
        this.financeAlertsLoading = false;
      },
      error: (e) => {
        this.financeAlertsLoading = false;
        this.err('Could not load finance alerts', e);
      },
    });
  }

  loadFinanceAlertEvents(): void {
    this.financeApi.financeAlertEvents(50).subscribe({
      next: (rows) => {
        this.financeAlertEvents = rows;
      },
      error: (e) => this.err('Could not load alert history', e),
    });
  }

  saveFinanceAlert(): void {
    const req = this.normalizedFinanceAlertRequest();
    if (req == null) {
      return;
    }
    this.financeAlertSaving = true;
    const call =
      this.editingFinanceAlertId == null
        ? this.financeApi.createFinanceAlert(req)
        : this.financeApi.updateFinanceAlert(this.editingFinanceAlertId, req);
    call.subscribe({
      next: () => {
        this.financeAlertSaving = false;
        this.cancelFinanceAlertEdit();
        this.loadFinanceAlerts();
        this.snackBar.open('Finance alert saved', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.financeAlertSaving = false;
        this.err('Could not save finance alert', e);
      },
    });
  }

  editFinanceAlert(row: FinanceStockAlertDto): void {
    this.editingFinanceAlertId = row.id;
    this.financeAlertForm = {
      symbol: row.symbol,
      triggerType: row.triggerType,
      thresholdValue: Number(row.thresholdValue),
      repeatMode: row.repeatMode,
      cooldownMinutes: row.cooldownMinutes,
      enabled: row.enabled,
    };
  }

  cancelFinanceAlertEdit(): void {
    this.editingFinanceAlertId = null;
    this.financeAlertForm = this.blankFinanceAlertForm();
  }

  deleteFinanceAlert(row: FinanceStockAlertDto): void {
    this.financeApi.deleteFinanceAlert(row.id).subscribe({
      next: () => {
        this.loadFinanceAlerts();
        this.loadFinanceAlertEvents();
        this.snackBar.open(`Deleted alert for ${row.symbol}`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete finance alert', e),
    });
  }

  evaluateFinanceAlerts(): void {
    this.financeAlertEvaluating = true;
    this.financeApi.evaluateFinanceAlerts().subscribe({
      next: (r) => {
        this.financeAlertEvaluating = false;
        this.loadFinanceAlerts();
        this.loadFinanceAlertEvents();
        this.snackBar.open(
          `Checked ${r.checkedAlerts} alert(s); triggered ${r.triggeredAlerts}`,
          undefined,
          { duration: 3500 },
        );
      },
      error: (e) => {
        this.financeAlertEvaluating = false;
        this.err('Could not evaluate finance alerts', e);
      },
    });
  }

  financeAlertTrack(_idx: number, row: FinanceStockAlertDto): number {
    return row.id;
  }

  financeAlertEventTrack(_idx: number, row: FinanceAlertEventDto): number {
    return row.id;
  }

  triggerLabel(t: FinanceStockAlertTriggerType | null | undefined): string {
    return t === 'SESSION_CHANGE_PERCENT_AT_OR_ABOVE' ? 'Session rise % at/above' : 'Price at/above';
  }

  repeatLabel(r: FinanceStockAlertRepeatMode | null | undefined, armed = true): string {
    if (r === 'REPEAT') {
      return armed ? 'Repeat · armed' : 'Repeat · waiting for dip';
    }
    return 'Once';
  }

  loadRobinhoodFinanceData(): void {
    this.rhLoading = true;
    this.financeApi
      .robinhoodTransactions(this.normalizedFinancePeriod(), this.financeYear, this.financeMonth)
      .subscribe({
        next: (r) => {
          const rows = (Array.isArray(r.rows) ? r.rows : []) as Record<string, unknown>[];
          this.rhTxns = { ...r, rows };
          this.rhTxnColumns = this.financeColumnKeysFromRows(rows);
          this.rhDisplayedRows = this.sortFinanceRowsByActivityDateAsc(rows);
          this.rhLoading = false;
        },
        error: (e) => {
          this.rhLoading = false;
          this.err('Could not load Robinhood transactions', e);
        },
      });
  }

  loadIndividualStockFinanceData(): void {
    const sym = this.financeSelectedSymbol.trim();
    if (!sym) {
      this.snackBar.open('Choose an instrument', 'Dismiss', { duration: 5000 });
      return;
    }
    this.rhIndvLoading = true;
    this.financeApi
      .robinhoodTransactions(this.normalizedFinancePeriod(), this.financeYear, this.financeMonth, sym)
      .subscribe({
        next: (r) => {
          const rows = (Array.isArray(r.rows) ? r.rows : []) as Record<string, unknown>[];
          this.rhIndvTxns = { ...r, rows };
          this.rhIndvTxnColumns = this.financeColumnKeysFromRows(rows);
          this.rhIndvDisplayedRows = this.sortFinanceRowsByActivityDateAsc(rows);
          this.rhIndvLoading = false;
        },
        error: (e) => {
          this.rhIndvLoading = false;
          this.err('Could not load transactions for instrument', e);
        },
      });
  }

  loadStocksSummary(): void {
    this.rhSummaryLoading = true;
    const sym = this.stocksSummaryInstrument.trim();
    this.financeApi.robinhoodStocksSummary(this.stocksSummaryYear, sym || undefined).subscribe({
      next: (r) => {
        this.rhSummary = r;
        this.rhSummaryLoading = false;
      },
      error: (e) => {
        this.rhSummaryLoading = false;
        this.rhSummary = null;
        this.err('Could not load stocks summary', e);
      },
    });
  }

  stocksSummaryRowTrack(row: { instrument: string; contract: string; financialYear: number }): string {
    return `${row.instrument}\u0001${row.contract}\u0001${row.financialYear}`;
  }

  /** Coerce mat-select value to a known period (avoids stray string values). */
  normalizedFinancePeriod(): FinancePeriod {
    const p = this.financePeriod as string;
    if (p === 'all' || p === 'year' || p === 'month') {
      return p;
    }
    return 'month';
  }

  private blankFinanceAlertForm(): FinanceStockAlertRequestDto {
    return {
      symbol: '',
      triggerType: 'PRICE_AT_OR_ABOVE',
      thresholdValue: 0,
      repeatMode: 'ONCE',
      cooldownMinutes: 1440,
      enabled: true,
    };
  }

  private normalizedFinanceAlertRequest(): FinanceStockAlertRequestDto | null {
    const symbol = this.financeAlertForm.symbol.trim().toUpperCase();
    const threshold = Number(this.financeAlertForm.thresholdValue);
    const cooldown = Number(this.financeAlertForm.cooldownMinutes);
    if (!symbol) {
      this.snackBar.open('Provide a stock symbol', 'Dismiss', { duration: 5000 });
      return null;
    }
    if (!Number.isFinite(threshold)) {
      this.snackBar.open('Provide a valid threshold', 'Dismiss', { duration: 5000 });
      return null;
    }
    if (!Number.isFinite(cooldown) || cooldown < 0) {
      this.snackBar.open('Cooldown must be zero or greater', 'Dismiss', { duration: 5000 });
      return null;
    }
    return {
      symbol,
      triggerType: this.financeAlertForm.triggerType,
      thresholdValue: threshold,
      repeatMode: this.financeAlertForm.repeatMode,
      cooldownMinutes: Math.floor(cooldown),
      enabled: !!this.financeAlertForm.enabled,
    };
  }

  private ensureStockSymbolsLoaded(done?: () => void, force = false): void {
    if (!force && this.stockSymbols.length > 0) {
      done?.();
      return;
    }
    if (done) {
      this.stockSymbolLoadWaiters.push(done);
    }
    if (this.stockSymbolsLoading) {
      return;
    }
    this.stockSymbolsLoading = true;
    this.financeApi.robinhoodStockSymbols().subscribe({
      next: (list) => {
        this.stockSymbols = Array.isArray(list)
          ? [...list].sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
          : [];
        this.stockSymbolsLoading = false;
        const waiters = this.stockSymbolLoadWaiters;
        this.stockSymbolLoadWaiters = [];
        for (const w of waiters) {
          w();
        }
      },
      error: (e) => {
        this.stockSymbolsLoading = false;
        this.stockSymbolLoadWaiters = [];
        this.err('Could not load instruments', e);
      },
    });
  }

  private activityDateValue(row: Record<string, unknown>): unknown {
    const key = Object.keys(row).find((k) => k.toUpperCase() === 'ACTIVITY_DATE');
    return key !== undefined ? row[key] : undefined;
  }

  private sortFinanceRowsByActivityDateAsc(rows: Record<string, unknown>[]): Record<string, unknown>[] {
    return [...rows].sort((a, b) => {
      const ta = this.utcCalendarDateForSort(this.activityDateValue(a))?.getTime();
      const tb = this.utcCalendarDateForSort(this.activityDateValue(b))?.getTime();
      if (ta == null && tb == null) {
        return 0;
      }
      if (ta == null) {
        return 1;
      }
      if (tb == null) {
        return -1;
      }
      return ta - tb;
    });
  }

  private financeColumnKeysFromRows(rows: Record<string, unknown>[]): string[] {
    const keys = new Set<string>();
    for (const row of rows) {
      if (row && typeof row === 'object') {
        for (const k of Object.keys(row)) {
          if (!FinanceComponent.FINANCE_COLUMNS_HIDDEN.has(k.toUpperCase())) {
            keys.add(k);
          }
        }
      }
    }
    return Array.from(keys).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
  }

  financeCell(row: Record<string, unknown>, column: string): unknown {
    if (column in row) {
      return row[column];
    }
    const match = Object.keys(row).find((k) => k.toUpperCase() === column.toUpperCase());
    return match != null ? row[match] : undefined;
  }

  financeYearChoices(): number[] {
    const y = new Date().getFullYear();
    return Array.from({ length: 17 }, (_, i) => y - 10 + i);
  }

  formatFinanceCell(column: string, v: unknown): string {
    if (this.isRobinhoodTableDateColumn(column)) {
      return this.formatRobinhoodUtcCalendarDateOnly(v);
    }
    if (this.isAmountOrPriceColumn(column)) {
      return this.formatUsdCurrency(v);
    }
    return this.formatRhCell(v);
  }

  formatSummaryDate(iso: string | null | undefined): string {
    if (iso == null || iso === '') {
      return '—';
    }
    const d = this.parseFlexibleDate(iso);
    if (d == null) {
      return String(iso);
    }
    try {
      return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(d);
    } catch {
      return iso.slice(0, 10);
    }
  }

  formatSummaryQty(n: number | null | undefined): string {
    if (n == null || Number.isNaN(Number(n))) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 }).format(n);
  }

  formatNewsPublished(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = this.parseFlexibleDate(iso);
    if (d == null) {
      return String(iso);
    }
    try {
      return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(d);
    } catch {
      return iso;
    }
  }

  sentimentClass(): string {
    return this.newsSentimentClass(this.rhStockNews);
  }

  growthClass(): string {
    return this.newsGrowthClass(this.rhStockNews);
  }

  stressClass(): string {
    return this.newsStressClass(this.rhStockNews);
  }

  newsSentimentClass(n: StockNewsDto | null | undefined): string {
    const label = n?.analysis?.overallSentiment?.toLowerCase();
    if (label === 'positive') {
      return 'sentiment-positive';
    }
    if (label === 'negative') {
      return 'sentiment-negative';
    }
    return 'sentiment-neutral';
  }

  newsGrowthClass(n: StockNewsDto | null | undefined): string {
    const label = n?.analysis?.projectedGrowthLabel?.toLowerCase();
    if (label === 'bullish') {
      return 'growth-bullish';
    }
    if (label === 'cautious') {
      return 'growth-cautious';
    }
    return 'growth-sideways';
  }

  newsStressClass(n: StockNewsDto | null | undefined): string {
    const label = n?.analysis?.stressSignals?.emphasis?.toLowerCase();
    if (label === 'high') {
      return 'stress-high';
    }
    if (label === 'moderate') {
      return 'stress-moderate';
    }
    return 'stress-low';
  }

  formatPct(n: number | null | undefined): string {
    if (n == null || Number.isNaN(Number(n))) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2, signDisplay: 'always' }).format(Number(n)) + '%';
  }

  private parseFlexibleDate(v: unknown): Date | null {
    if (v instanceof Date) {
      return Number.isNaN(v.getTime()) ? null : v;
    }
    if (typeof v === 'number' && Number.isFinite(v)) {
      const d = new Date(v);
      return Number.isNaN(d.getTime()) ? null : d;
    }
    if (typeof v === 'string') {
      const s = v.trim();
      if (!s) {
        return null;
      }
      const d = new Date(s);
      return Number.isNaN(d.getTime()) ? null : d;
    }
    return null;
  }

  formatRhCell(v: unknown): string {
    if (v == null || v === '') {
      return '—';
    }
    if (typeof v === 'object') {
      return JSON.stringify(v);
    }
    return String(v);
  }

  /** Robinhood JDBC dates are UTC instants; show the UTC calendar day (avoids “previous evening” in US timezones). */
  private isRobinhoodTableDateColumn(column: string): boolean {
    const u = column.trim().toUpperCase();
    return u === 'ACTIVITY_DATE' || u === 'PROCESS_DATE' || u === 'SETTLE_DATE';
  }

  private isAmountOrPriceColumn(column: string): boolean {
    const u = column.trim().toUpperCase();
    return u === 'AMOUNT' || u === 'PRICE';
  }

  private formatUsdCurrency(v: unknown): string {
    if (v == null || v === '') {
      return '—';
    }
    const n = this.parseFlexibleNumber(v);
    if (n == null) {
      return this.formatRhCell(v);
    }
    try {
      return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(n);
    } catch {
      return String(v);
    }
  }

  private parseFlexibleNumber(v: unknown): number | null {
    if (typeof v === 'number' && Number.isFinite(v)) {
      return v;
    }
    if (typeof v === 'string') {
      const t = v.trim();
      if (!t) {
        return null;
      }
      const x = Number(t);
      return Number.isNaN(x) ? null : x;
    }
    return null;
  }

  /**
   * Normalizes API/JDBC timestamps to a Date at UTC midnight for the UTC calendar day, then formats with
   * {@code timeZone: 'UTC'} so May 1 stored as {@code 2026-05-01T00:00:00Z} shows as May 1 (not Apr 30 evening local).
   */
  private formatRobinhoodUtcCalendarDateOnly(v: unknown): string {
    if (v == null || v === '') {
      return '—';
    }
    const cal = this.utcCalendarDateForSort(v);
    if (cal == null) {
      return this.formatRhCell(v);
    }
    try {
      return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(cal);
    } catch {
      const y = cal.getUTCFullYear();
      const m = String(cal.getUTCMonth() + 1).padStart(2, '0');
      const d = String(cal.getUTCDate()).padStart(2, '0');
      return `${y}-${m}-${d}`;
    }
  }

  /** UTC midnight for the UTC calendar day of {@code v}; used for sort + display. */
  private utcCalendarDateForSort(v: unknown): Date | null {
    if (v instanceof Date) {
      if (Number.isNaN(v.getTime())) {
        return null;
      }
      return new Date(Date.UTC(v.getUTCFullYear(), v.getUTCMonth(), v.getUTCDate()));
    }
    if (typeof v === 'number' && Number.isFinite(v)) {
      const d = new Date(v);
      if (Number.isNaN(d.getTime())) {
        return null;
      }
      return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
    }
    if (typeof v === 'string') {
      const s = v.trim();
      const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
      if (m) {
        const y = Number(m[1]);
        const mo = Number(m[2]) - 1;
        const da = Number(m[3]);
        if (!Number.isFinite(y) || !Number.isFinite(mo) || !Number.isFinite(da)) {
          return null;
        }
        return new Date(Date.UTC(y, mo, da));
      }
    }
    const d = this.parseFlexibleDate(v);
    if (d == null || Number.isNaN(d.getTime())) {
      return null;
    }
    return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
