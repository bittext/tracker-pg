import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import {
  RobinhoodExecutedTradeDto,
  RobinhoodExecutedTradesDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { ReportsFinanceRobinhoodTaxDeskComponent } from '../reports-finance-robinhood-tax-desk/reports-finance-robinhood-tax-desk.component';

interface DayPnlSummary {
  optionFills: number;
  spent: number;
  received: number;
  gains: number;
  losses: number;
  net: number;
  closed: number;
}

interface TradeDayGroup {
  key: string;
  label: string;
  trades: RobinhoodExecutedTradeDto[];
  summary: DayPnlSummary;
}

@Component({
  selector: 'app-reports-finance-robinhood-executed-trades',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTabsModule,
    CurrencyPipe,
    DecimalPipe,
    ReportsFinanceRobinhoodTaxDeskComponent,
  ],
  templateUrl: './reports-finance-robinhood-executed-trades.component.html',
  styleUrl: './reports-finance-robinhood-executed-trades.component.scss',
})
export class ReportsFinanceRobinhoodExecutedTradesComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly journalNav = inject(TradingJournalNavService);

  reportYear = new Date().getFullYear();
  sideFilter: 'all' | 'buy' | 'sell' = 'all';
  accountFilter = '';
  monthFilter: number | '' = '';
  dayFilter: number | '' = '';
  symbolFilter = '';
  loading = false;
  data: RobinhoodExecutedTradesDto | null = null;

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

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi.robinhoodExecutedTrades(this.reportYear).subscribe({
      next: (res) => {
        this.data = res;
        if (this.accountFilter && !res.accounts.some((a) => a.accountSuffix === this.accountFilter)) {
          this.accountFilter = '';
        }
        this.syncDayFilter();
        this.syncSymbolFilter();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load executed trades', 'Dismiss', {
          duration: 7000,
        });
      },
    });
  }

  /** Tickers currently in the list below (other filters applied; stock filter ignored). */
  symbolChoices(): string[] {
    const symbols = new Set<string>();
    for (const trade of this.tradesMatching({ ignoreSymbol: true })) {
      const ticker = this.underlying(trade.symbol);
      if (ticker) {
        symbols.add(ticker);
      }
    }
    return [...symbols].sort((a, b) => a.localeCompare(b));
  }

  dayChoices(): number[] {
    if (this.monthFilter === '') {
      return [];
    }
    const days = new Set<number>();
    for (const trade of this.tradesMatching({ ignoreDay: true })) {
      const parts = this.localDateParts(trade.executedAt);
      if (parts && parts.month === this.monthFilter) {
        days.add(parts.day);
      }
    }
    return [...days].sort((a, b) => a - b);
  }

  onMonthChange(): void {
    this.syncDayFilter();
    this.syncSymbolFilter();
  }

  onListFiltersChange(): void {
    this.syncSymbolFilter();
  }

  visibleTrades(): RobinhoodExecutedTradeDto[] {
    return this.tradesMatching({});
  }

  private tradesMatching(opts: { ignoreDay?: boolean; ignoreSymbol?: boolean }): RobinhoodExecutedTradeDto[] {
    const trades = this.data?.trades ?? [];
    return trades.filter((t) => {
      if (this.sideFilter === 'buy' && !this.isBuy(t.side)) {
        return false;
      }
      if (this.sideFilter === 'sell' && !this.isSell(t.side)) {
        return false;
      }
      if (this.accountFilter && t.accountSuffix !== this.accountFilter) {
        return false;
      }
      if (!opts.ignoreSymbol && this.symbolFilter && this.underlying(t.symbol) !== this.symbolFilter) {
        return false;
      }
      const parts = this.localDateParts(t.executedAt);
      if (this.monthFilter !== '' && (!parts || parts.month !== this.monthFilter)) {
        return false;
      }
      if (!opts.ignoreDay && this.dayFilter !== '' && (!parts || parts.day !== this.dayFilter)) {
        return false;
      }
      return true;
    });
  }

  private syncDayFilter(): void {
    if (this.monthFilter === '') {
      this.dayFilter = '';
      return;
    }
    if (this.dayFilter !== '' && !this.dayChoices().includes(this.dayFilter)) {
      this.dayFilter = '';
    }
  }

  private syncSymbolFilter(): void {
    if (this.symbolFilter && !this.symbolChoices().includes(this.symbolFilter)) {
      this.symbolFilter = '';
    }
  }

  private underlying(symbol: string | null | undefined): string {
    if (!symbol?.trim()) {
      return '';
    }
    return symbol.trim().split(/\s+/)[0].toUpperCase();
  }

  private localDateParts(iso: string | null): { year: number; month: number; day: number } | null {
    if (!iso) {
      return null;
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return null;
    }
    return { year: d.getFullYear(), month: d.getMonth() + 1, day: d.getDate() };
  }

  dayGroups(): TradeDayGroup[] {
    const groups = new Map<string, RobinhoodExecutedTradeDto[]>();
    for (const trade of this.visibleTrades()) {
      const key = this.dayKey(trade.executedAt);
      const list = groups.get(key) ?? [];
      list.push(trade);
      groups.set(key, list);
    }
    return [...groups.entries()]
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([key, trades]) => {
        const ordered = [...trades].sort(
          (a, b) => this.executedMs(a.executedAt) - this.executedMs(b.executedAt),
        );
        return {
          key,
          label: this.dayLabel(key),
          trades: ordered,
          summary: this.pnlSummary(ordered),
        };
      });
  }

  private executedMs(iso: string | null | undefined): number {
    if (!iso) {
      return Number.POSITIVE_INFINITY;
    }
    const t = Date.parse(iso);
    return Number.isNaN(t) ? Number.POSITIVE_INFINITY : t;
  }

  buyCount(): number {
    return this.visibleTrades().filter((t) => this.isBuy(t.side)).length;
  }

  sellCount(): number {
    return this.visibleTrades().filter((t) => this.isSell(t.side)).length;
  }

  isBuy(side: string | null | undefined): boolean {
    return (side ?? '').trim().toLowerCase().startsWith('buy');
  }

  isSell(side: string | null | undefined): boolean {
    return (side ?? '').trim().toLowerCase().startsWith('sell');
  }

  sideLabel(side: string | null | undefined): string {
    const s = (side ?? '').trim().toLowerCase().replace(/[\s-]+/g, '_');
    if (!s) {
      return 'Trade';
    }
    if (s.startsWith('buy_to_open') || s === 'bto') {
      return 'BTO';
    }
    if (s.startsWith('buy_to_close') || s === 'btc') {
      return 'BTC';
    }
    if (s.startsWith('sell_to_open') || s === 'sto') {
      return 'STO';
    }
    if (s.startsWith('sell_to_close') || s === 'stc') {
      return 'STC';
    }
    if (s.startsWith('buy')) {
      return 'Buy';
    }
    if (s.startsWith('sell')) {
      return 'Sell';
    }
    return side!.charAt(0).toUpperCase() + side!.slice(1).toLowerCase();
  }

  isOption(trade: RobinhoodExecutedTradeDto | string | null | undefined): boolean {
    if (trade && typeof trade === 'object') {
      if (this.symbolLooksLikeOption(trade.symbol)) {
        return true;
      }
      if (trade.quantity != null && trade.averagePrice != null && trade.notional != null) {
        const raw = trade.quantity * trade.averagePrice;
        const x100 = raw * 100;
        if (Math.abs(trade.notional - x100) < 0.06 && Math.abs(trade.notional - raw) > 0.06) {
          return true;
        }
      }
      const side = (trade.side ?? '').trim();
      const tickerOnly = !!trade.symbol?.trim() && !/\s/.test(trade.symbol.trim());
      const whole = trade.quantity != null && Number.isInteger(Number(trade.quantity));
      const type = (trade.orderType ?? '').toLowerCase();
      return !side && tickerOnly && whole && (type === 'limit' || type === 'market');
    }
    return this.symbolLooksLikeOption(typeof trade === 'string' ? trade : null);
  }

  private symbolLooksLikeOption(symbol: string | null | undefined): boolean {
    const s = (symbol ?? '').trim().toUpperCase();
    if (!s) {
      return false;
    }
    if (s.includes(' CALL') || s.includes(' PUT') || s.includes(' $')) {
      return true;
    }
    return /^[A-Z]{1,6}\d{6}[CP]\d{8}$/.test(s.replace(/\s+/g, ''));
  }

  qtyLabel(trade: RobinhoodExecutedTradeDto): string {
    const qty = trade.quantity;
    if (qty == null) {
      return '';
    }
    const formatted = new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(qty);
    const option = this.isOption(trade);
    const unit = option ? (qty === 1 ? 'contract' : 'contracts') : qty === 1 ? 'share' : 'shares';
    return `${formatted} ${unit}`;
  }

  yearPnl(): DayPnlSummary {
    return this.pnlSummary(this.visibleTrades());
  }

  pnlSummary(trades: RobinhoodExecutedTradeDto[]): DayPnlSummary {
    let optionFills = 0;
    let spent = 0;
    let received = 0;
    let gains = 0;
    let losses = 0;
    let net = 0;
    let closed = 0;
    for (const trade of trades) {
      if (this.isOption(trade) && trade.notional != null) {
        optionFills += 1;
        if (this.isSell(trade.side)) {
          received += trade.notional;
        } else {
          spent += trade.notional;
        }
      }
      if (trade.realizedPnl == null) {
        continue;
      }
      closed += 1;
      net += trade.realizedPnl;
      if (trade.realizedPnl > 0) {
        gains += trade.realizedPnl;
      } else if (trade.realizedPnl < 0) {
        losses += Math.abs(trade.realizedPnl);
      }
    }
    return { optionFills, spent, received, gains, losses, net, closed };
  }

  timeLabel(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return '';
    }
    return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  }

  private dayKey(iso: string | null): string {
    if (!iso) {
      return 'unknown';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return 'unknown';
    }
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private dayLabel(key: string): string {
    if (key === 'unknown') {
      return 'Unknown date';
    }
    const [year, month, day] = key.split('-').map((p) => Number(p));
    const date = new Date(year, month - 1, day);
    const today = new Date();
    const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const yest = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    const yestKey = `${yest.getFullYear()}-${String(yest.getMonth() + 1).padStart(2, '0')}-${String(yest.getDate()).padStart(2, '0')}`;
    if (key === todayKey) {
      return 'Today';
    }
    if (key === yestKey) {
      return 'Yesterday';
    }
    return date.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  }
}
