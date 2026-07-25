import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  InvestmentThenNowOutlookDto,
  InvestmentThenNowOverlayResponseDto,
  InvestmentThenNowOverlaySeriesDto,
  InvestmentThenNowResultDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type ChartMode = 'pct' | 'usd';

@Component({
  selector: 'app-investment-then-now-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './investment-then-now-panel.component.html',
  styleUrl: './investment-then-now-panel.component.scss',
})
export class InvestmentThenNowPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  symbol = '';
  investedAmount = 78198.72;
  asOfDate = '2026-06-28';

  loading = false;
  saving = false;
  loadingList = false;
  loadingOverlay = false;
  loadingOutlook = false;
  result: InvestmentThenNowResultDto | null = null;
  saved: InvestmentThenNowResultDto[] = [];

  overlay: InvestmentThenNowOverlayResponseDto | null = null;
  chartMode: ChartMode = 'pct';
  hiddenSeriesIds = new Set<number>();

  outlook: InvestmentThenNowOutlookDto | null = null;
  outlookHorizon: 3 | 6 | 12 = 6;
  outlookMissing = false;

  readonly chartW = 640;
  readonly chartH = 220;
  readonly padL = 48;
  readonly padR = 12;
  readonly padT = 12;
  readonly padB = 28;

  readonly presets = [
    { label: 'AAPL', symbol: 'AAPL' },
    { label: 'MSFT', symbol: 'MSFT' },
    { label: 'NVDA', symbol: 'NVDA' },
    { label: 'AMZN', symbol: 'AMZN' },
    { label: 'GOOGL', symbol: 'GOOGL' },
    { label: 'SPY', symbol: 'SPY' },
    { label: 'QQQ', symbol: 'QQQ' },
  ];

  ngOnInit(): void {
    this.reloadSaved();
    this.loadCachedOutlook();
  }

  applyPreset(symbol: string): void {
    this.symbol = symbol;
  }

  compute(save: boolean): void {
    const symbol = this.symbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Enter a company symbol', 'Dismiss', { duration: 4000 });
      return;
    }
    if (!(this.investedAmount > 0)) {
      this.snackBar.open('Invested amount must be positive', 'Dismiss', { duration: 4000 });
      return;
    }
    if (!this.asOfDate) {
      this.snackBar.open('Pick an as-of date', 'Dismiss', { duration: 4000 });
      return;
    }
    if (save) {
      this.saving = true;
    } else {
      this.loading = true;
    }
    this.financeApi
      .computeInvestmentThenNow({
        symbol,
        investedAmount: this.investedAmount,
        asOfDate: this.asOfDate,
        save,
      })
      .subscribe({
        next: (r) => {
          this.result = r;
          this.loading = false;
          this.saving = false;
          if (save) {
            this.snackBar.open('Saved for reference', 'Dismiss', { duration: 2500 });
            this.reloadSaved();
          }
        },
        error: (err) => {
          this.loading = false;
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  selectSaved(row: InvestmentThenNowResultDto): void {
    this.result = row;
    this.symbol = row.symbol;
    this.investedAmount = row.investedAmount;
    this.asOfDate = row.asOfDate;
  }

  deleteSaved(row: InvestmentThenNowResultDto, event?: Event): void {
    event?.stopPropagation();
    if (row.id == null) {
      return;
    }
    this.financeApi.deleteInvestmentThenNow(row.id).subscribe({
      next: () => {
        if (this.result?.id === row.id) {
          this.result = null;
        }
        this.reloadSaved();
        this.snackBar.open('Removed', 'Dismiss', { duration: 2000 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  reloadSaved(): void {
    this.loadingList = true;
    this.financeApi.listInvestmentThenNow().subscribe({
      next: (rows) => {
        this.saved = rows;
        this.loadingList = false;
        this.reloadOverlay();
      },
      error: (err) => {
        this.loadingList = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  reloadOverlay(): void {
    if (this.saved.length === 0) {
      this.overlay = { series: [], warnings: [] };
      return;
    }
    this.loadingOverlay = true;
    this.financeApi.investmentThenNowOverlaySeries().subscribe({
      next: (resp) => {
        this.overlay = resp;
        this.loadingOverlay = false;
        if (resp.warnings?.length) {
          this.snackBar.open(resp.warnings.slice(0, 2).join(' · '), 'Dismiss', { duration: 6000 });
        }
      },
      error: (err) => {
        this.loadingOverlay = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  toggleSeries(id: number): void {
    if (this.hiddenSeriesIds.has(id)) {
      this.hiddenSeriesIds.delete(id);
    } else {
      this.hiddenSeriesIds.add(id);
    }
    this.hiddenSeriesIds = new Set(this.hiddenSeriesIds);
  }

  isSeriesVisible(id: number): boolean {
    return !this.hiddenSeriesIds.has(id);
  }

  seriesColor(row: { id?: number | null; symbol?: string }): string {
    if (row.id != null) {
      const fromOverlay = this.overlay?.series.find((s) => s.id === row.id);
      if (fromOverlay) {
        return fromOverlay.colorHint;
      }
    }
    if (row.symbol) {
      const bySym = this.overlay?.series.find((s) => s.symbol === row.symbol);
      if (bySym) {
        return bySym.colorHint;
      }
    }
    return '#4f46e5';
  }

  loadCachedOutlook(): void {
    this.financeApi.getInvestmentThenNowOutlook().subscribe({
      next: (o) => {
        this.outlook = o;
        this.outlookMissing = false;
        this.outlookHorizon = (o.horizonMonths === 3 || o.horizonMonths === 12 ? o.horizonMonths : 6) as 3 | 6 | 12;
      },
      error: () => {
        this.outlookMissing = true;
        this.outlook = null;
      },
    });
  }

  generateOutlook(force = false): void {
    if (this.saved.length === 0) {
      this.snackBar.open('Save at least one answer before generating an outlook', 'Dismiss', { duration: 4000 });
      return;
    }
    this.loadingOutlook = true;
    this.financeApi
      .generateInvestmentThenNowOutlook({
        horizonMonths: this.outlookHorizon,
        force,
      })
      .subscribe({
        next: (o) => {
          this.outlook = o;
          this.outlookMissing = false;
          this.loadingOutlook = false;
          this.snackBar.open(o.cached ? 'Loaded cached outlook' : 'Outlook generated', 'Dismiss', {
            duration: 2500,
          });
        },
        error: (err) => {
          this.loadingOutlook = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  /** Visible historical series for the SVG. */
  visibleOverlaySeries(): InvestmentThenNowOverlaySeriesDto[] {
    return (this.overlay?.series ?? []).filter((s) => this.isSeriesVisible(s.id));
  }

  private chartDomain(): {
    minT: number;
    maxT: number;
    minV: number;
    maxV: number;
  } | null {
    const hist = this.visibleOverlaySeries();
    const times: number[] = [];
    const values: number[] = [];
    for (const s of hist) {
      for (const p of s.points) {
        times.push(Date.parse(p.date));
        values.push(this.chartMode === 'pct' ? p.valuePct : p.valueUsd);
      }
    }
    if (this.outlook) {
      for (const sym of this.outlook.symbols) {
        const series = this.overlay?.series.find(
          (s) => s.symbol === sym.symbol && this.isSeriesVisible(s.id),
        );
        if (!series) {
          continue;
        }
        const lastClose = series.points[series.points.length - 1]?.close;
        const asOfClose = series.points[0]?.close;
        if (!lastClose || !asOfClose) {
          continue;
        }
        for (const path of [sym.forwardBase, sym.forwardBull, sym.forwardBear]) {
          for (const fp of path ?? []) {
            times.push(Date.parse(fp.date));
            if (this.chartMode === 'pct') {
              values.push((fp.price / asOfClose) * 100);
            } else {
              values.push(series.shares * fp.price);
            }
          }
        }
      }
    }
    if (!times.length || !values.length) {
      return null;
    }
    let minV = Math.min(...values);
    let maxV = Math.max(...values);
    if (minV === maxV) {
      minV -= 1;
      maxV += 1;
    }
    return { minT: Math.min(...times), maxT: Math.max(...times), minV, maxV };
  }

  private mapPoint(t: number, v: number, domain: NonNullable<ReturnType<InvestmentThenNowPanelComponent['chartDomain']>>): {
    x: number;
    y: number;
  } {
    const plotW = this.chartW - this.padL - this.padR;
    const plotH = this.chartH - this.padT - this.padB;
    const x =
      this.padL + ((t - domain.minT) / Math.max(domain.maxT - domain.minT, 1)) * plotW;
    const y = this.padT + (1 - (v - domain.minV) / Math.max(domain.maxV - domain.minV, 0.01)) * plotH;
    return { x, y };
  }

  historicalPath(series: InvestmentThenNowOverlaySeriesDto): string {
    const domain = this.chartDomain();
    if (!domain || series.points.length === 0) {
      return '';
    }
    return series.points
      .map((p, i) => {
        const v = this.chartMode === 'pct' ? p.valuePct : p.valueUsd;
        const { x, y } = this.mapPoint(Date.parse(p.date), v, domain);
        return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
  }

  /** Dashed forward base path for a symbol, starting from last historical point. */
  forwardPath(
    series: InvestmentThenNowOverlaySeriesDto,
    forward: { date: string; price: number }[] | undefined,
  ): string {
    const domain = this.chartDomain();
    if (!domain || !forward?.length || series.points.length === 0) {
      return '';
    }
    const last = series.points[series.points.length - 1];
    const asOfClose = series.points[0].close;
    const pts: { t: number; v: number }[] = [
      {
        t: Date.parse(last.date),
        v: this.chartMode === 'pct' ? last.valuePct : last.valueUsd,
      },
    ];
    for (const fp of forward) {
      pts.push({
        t: Date.parse(fp.date),
        v: this.chartMode === 'pct' ? (fp.price / asOfClose) * 100 : series.shares * fp.price,
      });
    }
    return pts
      .map((p, i) => {
        const { x, y } = this.mapPoint(p.t, p.v, domain);
        return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
  }

  outlookForwardFor(symbol: string): {
    base: string;
    bull: string;
    bear: string;
    color: string;
  } | null {
    if (!this.outlook) {
      return null;
    }
    const series = this.overlay?.series.find((s) => s.symbol === symbol && this.isSeriesVisible(s.id));
    const sym = this.outlook.symbols.find((s) => s.symbol === symbol);
    if (!series || !sym) {
      return null;
    }
    return {
      base: this.forwardPath(series, sym.forwardBase),
      bull: this.forwardPath(series, sym.forwardBull),
      bear: this.forwardPath(series, sym.forwardBear),
      color: series.colorHint,
    };
  }

  yAxisLabels(): { y: number; label: string }[] {
    const domain = this.chartDomain();
    if (!domain) {
      return [];
    }
    const mid = (domain.minV + domain.maxV) / 2;
    const fmt = (v: number) =>
      this.chartMode === 'pct'
        ? `${v.toFixed(0)}%`
        : v.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
    return [
      { y: this.padT, label: fmt(domain.maxV) },
      { y: this.padT + (this.chartH - this.padT - this.padB) / 2, label: fmt(mid) },
      { y: this.chartH - this.padB, label: fmt(domain.minV) },
    ];
  }

  xAxisLabels(): { x: number; label: string }[] {
    const domain = this.chartDomain();
    if (!domain) {
      return [];
    }
    const mid = (domain.minT + domain.maxT) / 2;
    const label = (t: number) =>
      new Date(t).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    return [
      { x: this.padL, label: label(domain.minT) },
      { x: this.padL + (this.chartW - this.padL - this.padR) / 2, label: label(mid) },
      { x: this.chartW - this.padR, label: label(domain.maxT) },
    ];
  }

  todayLineX(): number | null {
    const domain = this.chartDomain();
    if (!domain) {
      return null;
    }
    const today = Date.now();
    if (today < domain.minT || today > domain.maxT) {
      return null;
    }
    return this.mapPoint(today, domain.minV, domain).x;
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'itn__pnl--pos' : 'itn__pnl--neg';
  }

  questionPreview(): string {
    const name = this.symbol.trim() || '«stock name»';
    const amt = this.investedAmount?.toLocaleString('en-US', {
      style: 'currency',
      currency: 'USD',
    });
    return `${amt} invested on ${this.asOfDate} in ${name} stocks — how much would it be worth now?`;
  }
}
