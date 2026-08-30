import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CompanyFinancialsQuarterDto,
  CompanyFinancialsResponseDto,
  CompanyFinancialsSearchHistoryItemDto,
  SymbolSearchMatchDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

/** A bare ticker, e.g. AAPL, BRK.B, RDS-A — no spaces, short. */
const TICKER_PATTERN = /^[A-Za-z][A-Za-z.\-]{0,6}$/;

interface MarginBar {
  path: string;
  positive: boolean;
  valueLabel: string | null;
  labelX: number;
  labelY: number;
  tooltip: string;
  hitX: number;
  hitWidth: number;
}

interface MarginChart {
  bars: MarginBar[];
  baselineY: number;
  hasData: boolean;
}

@Component({
  selector: 'app-company-financials-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    DecimalPipe,
  ],
  templateUrl: './company-financials-panel.component.html',
  styleUrl: './company-financials-panel.component.scss',
})
export class CompanyFinancialsPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  query = '';
  loading = false;
  searching = false;
  result: CompanyFinancialsResponseDto | null = null;
  searchMatches: SymbolSearchMatchDto[] = [];
  searchedQuery = '';

  recentSearches: CompanyFinancialsSearchHistoryItemDto[] = [];
  recentLoading = false;

  readonly presets = ['AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL'];

  readonly chartW = 560;
  readonly chartH = 150;
  private readonly padL = 6;
  private readonly padR = 6;
  private readonly padTop = 22;
  private readonly padBottom = 22;
  private readonly barGap = 4;
  private readonly barMaxWidth = 24;
  private readonly barRadius = 4;

  ngOnInit(): void {
    this.loadRecentSearches();
  }

  applyPreset(symbol: string): void {
    this.query = symbol;
    this.load();
  }

  load(): void {
    const raw = this.query.trim();
    if (!raw) {
      this.snackBar.open('Enter a company name or symbol', 'Dismiss', { duration: 4000 });
      return;
    }
    this.searchMatches = [];
    if (TICKER_PATTERN.test(raw)) {
      this.loadQuarters(raw.toUpperCase());
      return;
    }
    this.resolveSymbol(raw);
  }

  private resolveSymbol(query: string): void {
    this.searching = true;
    this.result = null;
    this.financeApi.companyFinancialsSymbolSearch(query).subscribe({
      next: (r) => {
        this.searching = false;
        if (r.autoSelected && r.matches.length === 1) {
          this.loadQuarters(r.matches[0].symbol);
          return;
        }
        if (!r.matches.length) {
          this.snackBar.open(`No matching company found for "${query}"`, 'Dismiss', { duration: 5000 });
          return;
        }
        this.searchedQuery = query;
        this.searchMatches = r.matches;
      },
      error: (err) => {
        this.searching = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  selectMatch(m: SymbolSearchMatchDto): void {
    this.searchMatches = [];
    this.query = m.symbol;
    this.loadQuarters(m.symbol);
  }

  private loadQuarters(symbol: string): void {
    this.loading = true;
    this.result = null;
    this.financeApi.companyFinancialsQuarters(symbol).subscribe({
      next: (r) => {
        this.loading = false;
        this.result = r;
        this.loadRecentSearches();
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  loadFromHistory(item: CompanyFinancialsSearchHistoryItemDto): void {
    this.query = item.symbol;
    this.loadQuarters(item.symbol);
  }

  private loadRecentSearches(): void {
    this.recentLoading = true;
    this.financeApi.companyFinancialsRecentSearches().subscribe({
      next: (rows) => {
        this.recentLoading = false;
        this.recentSearches = rows;
      },
      error: () => {
        this.recentLoading = false;
      },
    });
  }

  removeRecentSearch(item: CompanyFinancialsSearchHistoryItemDto, event: Event): void {
    event.stopPropagation();
    const prior = this.recentSearches;
    this.recentSearches = this.recentSearches.filter((r) => r.id !== item.id);
    this.financeApi.companyFinancialsDeleteRecentSearch(item.id).subscribe({
      error: (err) => {
        this.recentSearches = prior;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  /** Newest quarter first for the table. */
  get quartersDesc() {
    return this.result ? [...this.result.quarters].reverse() : [];
  }

  verdictClass(verdict: string | undefined): string {
    switch (verdict) {
      case 'Improving':
        return 'cf__verdict--up';
      case 'Declining':
        return 'cf__verdict--down';
      case 'Mixed':
        return 'cf__verdict--mixed';
      default:
        return 'cf__verdict--unknown';
    }
  }

  /** Net margin per quarter as a bar chart, growing from a zero baseline. Single series: no legend. */
  get marginChart(): MarginChart {
    const quarters = this.result?.quarters ?? [];
    const withMargin = quarters
      .map((q, i) => ({ q, i }))
      .filter((p): p is { q: CompanyFinancialsQuarterDto; i: number } => p.q.netMarginPct != null);
    if (!withMargin.length) {
      return { bars: [], baselineY: 0, hasData: false };
    }

    const plotW = this.chartW - this.padL - this.padR;
    const plotTop = this.padTop;
    const plotBottom = this.chartH - this.padBottom;
    const plotH = plotBottom - plotTop;
    const baselineY = plotTop + plotH / 2;
    const halfH = plotH / 2 - 6;

    const maxAbs = Math.max(...withMargin.map((p) => Math.abs(p.q.netMarginPct as number)), 0.5);
    const n = quarters.length;
    const slot = plotW / n;
    const barWidth = Math.min(this.barMaxWidth, Math.max(3, slot - this.barGap));

    let extremeIdx = withMargin[0].i;
    let extremeAbs = Math.abs(withMargin[0].q.netMarginPct as number);
    for (const p of withMargin) {
      const abs = Math.abs(p.q.netMarginPct as number);
      if (abs > extremeAbs) {
        extremeAbs = abs;
        extremeIdx = p.i;
      }
    }
    const lastIdx = withMargin[withMargin.length - 1].i;

    const bars: MarginBar[] = withMargin.map((p) => {
      const value = p.q.netMarginPct as number;
      const x = this.padL + p.i * slot + (slot - barWidth) / 2;
      const barLen = (Math.abs(value) / maxAbs) * halfH;
      const radius = Math.min(this.barRadius, barLen / 2);
      const positive = value >= 0;
      const path = positive
        ? this.barPathUp(x, barWidth, baselineY, baselineY - barLen, radius)
        : this.barPathDown(x, barWidth, baselineY, baselineY + barLen, radius);
      const showLabel = p.i === extremeIdx || p.i === lastIdx;
      const valueLabel = showLabel ? `${value >= 0 ? '+' : ''}${value.toFixed(1)}%` : null;
      return {
        path,
        positive,
        valueLabel,
        labelX: x + barWidth / 2,
        labelY: positive ? baselineY - barLen - 6 : baselineY + barLen + 14,
        tooltip: `${p.q.fiscalDateEnding}: ${value >= 0 ? '+' : ''}${value.toFixed(1)}% net margin`,
        hitX: this.padL + p.i * slot,
        hitWidth: slot,
      };
    });

    return { bars, baselineY, hasData: true };
  }

  private barPathUp(x: number, w: number, baseY: number, topY: number, r: number): string {
    return `M ${x} ${baseY} L ${x} ${topY + r} Q ${x} ${topY} ${x + r} ${topY} L ${x + w - r} ${topY} Q ${x + w} ${topY} ${x + w} ${topY + r} L ${x + w} ${baseY} Z`;
  }

  private barPathDown(x: number, w: number, baseY: number, botY: number, r: number): string {
    return `M ${x} ${baseY} L ${x} ${botY - r} Q ${x} ${botY} ${x + r} ${botY} L ${x + w - r} ${botY} Q ${x + w} ${botY} ${x + w} ${botY - r} L ${x + w} ${baseY} Z`;
  }
}
