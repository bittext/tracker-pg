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
  FinanceCrawlSnapshotDto,
  RobinhoodStocksSummaryDto,
  RobinhoodTransactionsDto,
  StockNewsDto,
  StockNewsItemDto,
  Surge52WeekHighsDto,
  Surge52WeekRowDto,
} from '../../models/finance.models';
import { FinanceApiService, FinancePeriod } from '../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

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
  ],
  templateUrl: './finance.component.html',
  styleUrl: './finance.component.scss',
})
export class FinanceComponent implements OnInit {
  private static readonly FINANCE_COLUMNS_HIDDEN = new Set<string>(['SETTLE_DATE']);
  private static readonly STOCK_NEWS_LIMIT = 10;
  private static readonly RISING_52W_LIMIT = 5;

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Finance tabs: 0=news, 1=crawler, 2=52w high risers, 3=transactions, 4=by instrument, 5=summary. */
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

  onFinanceSubTabIndexChange(index: number): void {
    if (index === 1) {
      this.loadCrawlSnapshot();
    } else if (index === 2) {
      this.loadRising52WeekHighs();
    } else if (index === 3) {
      this.loadRobinhoodFinanceData();
    } else if (index === 4) {
      this.ensureStockSymbolsLoaded(() => {
        if (this.financeSelectedSymbol.trim()) {
          this.loadIndividualStockFinanceData();
        }
      }, true);
    } else if (index === 5) {
      this.ensureStockSymbolsLoaded(undefined, this.stockSymbols.length === 0);
      this.loadStocksSummary();
    }
  }

  onFinanceFilterSelectionChange(): void {
    if (this.financeSubTabIndex === 3) {
      this.loadRobinhoodFinanceData();
    } else if (this.financeSubTabIndex === 4) {
      if (this.financeSelectedSymbol.trim()) {
        this.loadIndividualStockFinanceData();
      }
    }
  }

  onStocksSummaryFilterChange(): void {
    if (this.financeSubTabIndex === 5) {
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
      const ta = this.parseFlexibleDate(this.activityDateValue(a))?.getTime();
      const tb = this.parseFlexibleDate(this.activityDateValue(b))?.getTime();
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
    if (this.isActivityDateColumn(column)) {
      return this.formatActivityDateTime(v);
    }
    if (this.isProcessDateColumn(column)) {
      return this.formatProcessDateOnly(v);
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

  private isActivityDateColumn(column: string): boolean {
    return column.trim().toUpperCase() === 'ACTIVITY_DATE';
  }

  private isProcessDateColumn(column: string): boolean {
    return column.trim().toUpperCase() === 'PROCESS_DATE';
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

  private formatProcessDateOnly(v: unknown): string {
    if (v == null || v === '') {
      return '—';
    }
    const d = this.parseFlexibleDate(v);
    if (d == null) {
      return this.formatRhCell(v);
    }
    try {
      return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(d);
    } catch {
      return d.toISOString().slice(0, 10);
    }
  }

  private formatActivityDateTime(v: unknown): string {
    if (v == null || v === '') {
      return '—';
    }
    const d = this.parseFlexibleDate(v);
    if (d == null) {
      return this.formatRhCell(v);
    }
    try {
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'medium',
      }).format(d);
    } catch {
      return d.toISOString();
    }
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
