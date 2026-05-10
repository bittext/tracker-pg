import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  BreakoutCandidatesDto,
  StockNewsDto,
  Surge52WeekHighsDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type WatchlistNewsRow =
  | { symbol: string; ok: true; data: StockNewsDto }
  | { symbol: string; ok: false; error: string };

@Component({
  selector: 'app-trading-screeners-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  templateUrl: './trading-screeners-panel.component.html',
  styleUrl: './trading-screeners-panel.component.scss',
})
export class TradingScreenersPanelComponent implements OnInit {
  private static readonly NASDAQ_MID_CAP_LIMIT = 28;
  private static readonly HIGH_RISERS_LIMIT = 15;
  private static readonly BREAKOUT_LIMIT = 22;
  private static readonly WATCHLIST_NEWS_LIMIT = 4;
  private static readonly WATCHLIST_MAX_SYMBOLS = 12;

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Paste tickers: commas, spaces, or one per line. */
  watchlistInput = '';

  nasdaqMidCap: Surge52WeekHighsDto | null = null;
  nasdaqMidCapLoading = false;

  rising52w: Surge52WeekHighsDto | null = null;
  rising52wLoading = false;

  breakouts: BreakoutCandidatesDto | null = null;
  breakoutLoading = false;

  watchlistRows: WatchlistNewsRow[] = [];
  watchlistLoading = false;

  ngOnInit(): void {
    this.refreshBuiltinScans();
  }

  refreshBuiltinScans(): void {
    this.loadNasdaqMidCap();
    this.loadRising52w();
    this.loadBreakouts();
  }

  loadNasdaqMidCap(): void {
    this.nasdaqMidCapLoading = true;
    this.financeApi.robinhoodNasdaqMidCapScreener(TradingScreenersPanelComponent.NASDAQ_MID_CAP_LIMIT).subscribe({
      next: (r) => {
        this.nasdaqMidCap = r;
        this.nasdaqMidCapLoading = false;
      },
      error: (e) => {
        this.nasdaqMidCap = null;
        this.nasdaqMidCapLoading = false;
        this.err('Could not load NASDAQ mid-cap screener', e);
      },
    });
  }

  loadRising52w(): void {
    this.rising52wLoading = true;
    this.financeApi.robinhoodRising52WeekHighs(TradingScreenersPanelComponent.HIGH_RISERS_LIMIT).subscribe({
      next: (r) => {
        this.rising52w = r;
        this.rising52wLoading = false;
      },
      error: (e) => {
        this.rising52w = null;
        this.rising52wLoading = false;
        this.err('Could not load 52-week high risers', e);
      },
    });
  }

  loadBreakouts(): void {
    this.breakoutLoading = true;
    this.financeApi.robinhoodBreakoutCandidates(TradingScreenersPanelComponent.BREAKOUT_LIMIT).subscribe({
      next: (r) => {
        this.breakouts = r;
        this.breakoutLoading = false;
      },
      error: (e) => {
        this.breakouts = null;
        this.breakoutLoading = false;
        this.err('Could not load breakout candidates', e);
      },
    });
  }

  runWatchlistNewsSweep(): void {
    const symbols = this.parsedWatchlistSymbols();
    if (symbols.length === 0) {
      this.snackBar.open('Add one or more ticker symbols to screen.', 'Dismiss', { duration: 5000 });
      return;
    }
    this.watchlistLoading = true;
    this.watchlistRows = [];
    const lim = TradingScreenersPanelComponent.WATCHLIST_NEWS_LIMIT;
    const calls = symbols.map((symbol) =>
      this.financeApi.robinhoodStockNews(symbol, undefined, lim).pipe(
        map((data) => ({ symbol, ok: true as const, data } satisfies WatchlistNewsRow)),
        catchError((e) =>
          of({ symbol, ok: false as const, error: formatHttpErrorDetail(e) } satisfies WatchlistNewsRow),
        ),
      ),
    );
    forkJoin(calls).subscribe({
      next: (rows) => {
        this.watchlistRows = rows;
        this.watchlistLoading = false;
      },
      error: () => {
        this.watchlistLoading = false;
      },
    });
  }

  parsedWatchlistSymbols(): string[] {
    const raw = this.watchlistInput.split(/[\s,;]+/);
    const seen = new Set<string>();
    const out: string[] = [];
    for (const p of raw) {
      const s = p.trim().toUpperCase();
      if (!s || !/^[A-Z][A-Z0-9.\-]*$/.test(s)) {
        continue;
      }
      if (seen.has(s)) {
        continue;
      }
      seen.add(s);
      out.push(s);
      if (out.length >= TradingScreenersPanelComponent.WATCHLIST_MAX_SYMBOLS) {
        break;
      }
    }
    return out;
  }

  formatUsd(v: number | null | undefined): string {
    if (v == null || Number.isNaN(Number(v))) {
      return '—';
    }
    try {
      return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(v));
    } catch {
      return String(v);
    }
  }

  formatPct(n: number | null | undefined): string {
    if (n == null || Number.isNaN(Number(n))) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2, signDisplay: 'always' }).format(Number(n)) + '%';
  }

  /** Yahoo marketCap is USD raw float. */
  formatMarketCapUsd(v: number | null | undefined): string {
    if (v == null || Number.isNaN(Number(v)) || v <= 0) {
      return '—';
    }
    const b = v / 1e9;
    if (b >= 1) {
      return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(b) + 'B';
    }
    const m = v / 1e6;
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(m) + 'M';
  }

  exchangeLabel(row: { exchangeId?: string | null; fullExchangeName?: string | null }): string {
    const full = row.fullExchangeName?.trim();
    if (full) {
      return full;
    }
    const id = row.exchangeId?.trim();
    return id || '—';
  }

  nasdaqTrack(_i: number, row: { symbol: string }): string {
    return row.symbol;
  }

  risingTrack(_i: number, row: { symbol: string }): string {
    return row.symbol;
  }

  breakoutTrack(_i: number, row: { symbol: string }): string {
    return row.symbol;
  }

  watchTrack(_i: number, row: WatchlistNewsRow): string {
    return row.symbol;
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
