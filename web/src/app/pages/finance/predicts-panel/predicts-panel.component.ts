import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import {
  PredictsBucketPointDto,
  PredictsLeaderboardDto,
  PredictsLeaderboardEntryDto,
  PredictsMentionDto,
  PredictsSourceHealthDto,
  PredictsSymbolSummaryDto,
  PredictsTickerDto,
  PredictsTimeseriesDto,
} from '../../../models/finance-predicts.models';
import { FinancePredictsApiService } from '../../../services/finance-predicts-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type PredictsWindow = '5m' | '15m' | '1h' | '1d';
type LeaderboardKind = 'hot' | 'positive' | 'surge';

interface ChartBar {
  /** Source so we can color the chip / segment. */
  source: string;
  /** SVG x percent in [0, 100]. */
  x: number;
  /** Bar height percent in [0, 100]. */
  height: number;
  /** Raw count. */
  count: number;
  /** Date for tooltip / legend. */
  bucketStart: string;
}

interface SentimentLinePoint {
  /** SVG x percent. */
  x: number;
  /** SVG y percent (0 = top). */
  y: number;
  /** Sentiment avg in [-1, +1]. */
  value: number;
  /** Date for tooltip. */
  bucketStart: string;
}

/**
 * Finance → Trading → Predicts panel. Loads once on first activation and exposes three sections:
 * a source-strip with health for each ingest source, three leaderboards (hot/positive/surge), and a
 * per-ticker drilldown with KPI cards, a custom inline SVG chart (mentions bars + sentiment line),
 * and a recent-mentions list. Mirrors the no-external-charting convention used by admin-usage-panel.
 */
@Component({
  selector: 'app-predicts-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './predicts-panel.component.html',
  styleUrl: './predicts-panel.component.scss',
})
export class PredictsPanelComponent implements OnInit {
  private readonly api = inject(FinancePredictsApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly tickers = signal<PredictsTickerDto[]>([]);
  readonly sources = signal<PredictsSourceHealthDto[]>([]);
  readonly hotBoard = signal<PredictsLeaderboardDto | null>(null);
  readonly positiveBoard = signal<PredictsLeaderboardDto | null>(null);
  readonly surgeBoard = signal<PredictsLeaderboardDto | null>(null);

  readonly summary = signal<PredictsSymbolSummaryDto | null>(null);
  readonly mentions = signal<PredictsMentionDto[]>([]);
  readonly timeseries = signal<PredictsTimeseriesDto | null>(null);

  readonly selectedSymbol = signal<string>('');
  readonly addSymbolInput = signal<string>('');
  readonly selectedWindow = signal<PredictsWindow>('1h');
  readonly selectedSource = signal<string>('all');
  readonly selectedDays = signal<number>(7);

  readonly loadingTop = signal<boolean>(false);
  readonly loadingDrilldown = signal<boolean>(false);
  readonly addingTicker = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  /** Source chips on the drilldown: 'all' + every source present in the summary. */
  readonly sourceChips = computed<string[]>(() => {
    const s = this.summary();
    if (!s) {
      return ['all'];
    }
    return ['all', ...s.sources.filter((row) => row.mentions24h > 0).map((row) => row.source)];
  });

  /** Bars for the inline mention-count chart (top of the drilldown). */
  readonly chartBars = computed<ChartBar[]>(() => {
    const ts = this.timeseries();
    if (!ts || ts.points.length === 0) {
      return [];
    }
    const max = ts.points.reduce((m, p) => Math.max(m, p.msgCount), 0);
    const span = Math.max(ts.points.length, 1);
    return ts.points.map((p, i) => ({
      source: p.source,
      x: (i / span) * 100,
      height: max === 0 ? 0 : Math.max(2, Math.round((p.msgCount / max) * 100)),
      count: p.msgCount,
      bucketStart: p.bucketStart,
    }));
  });

  /** Width of each bar in SVG units, derived from the number of buckets. */
  readonly chartBarWidth = computed<number>(() => {
    const ts = this.timeseries();
    if (!ts || ts.points.length === 0) {
      return 4;
    }
    return Math.max(1, Math.min(8, 100 / Math.max(ts.points.length, 1)));
  });

  /** Polyline points for the rolling-sentiment line (avg per bucket, normalized to [-1,+1]). */
  readonly sentimentLine = computed<SentimentLinePoint[]>(() => {
    const ts = this.timeseries();
    if (!ts || ts.points.length === 0) {
      return [];
    }
    const span = Math.max(ts.points.length - 1, 1);
    return ts.points.map((p, i) => ({
      x: (i / span) * 100,
      // Normalize -1..+1 → 100..0 (top of viewBox).
      y: 50 - 50 * (typeof p.sentimentAvg === 'number' ? Math.max(-1, Math.min(1, p.sentimentAvg)) : 0),
      value: typeof p.sentimentAvg === 'number' ? p.sentimentAvg : 0,
      bucketStart: p.bucketStart,
    }));
  });

  /** SVG-friendly path for the line. */
  readonly sentimentPath = computed<string>(() => {
    const points = this.sentimentLine();
    if (points.length === 0) {
      return '';
    }
    return points
      .map((p, i) => (i === 0 ? `M ${p.x.toFixed(2)} ${p.y.toFixed(2)}` : `L ${p.x.toFixed(2)} ${p.y.toFixed(2)}`))
      .join(' ');
  });

  ngOnInit(): void {
    this.refreshTop();
  }

  // ----------------------- top-of-page refresh -----------------------

  refreshTop(): void {
    this.loadingTop.set(true);
    this.errorMessage.set(null);
    forkJoin({
      tickers: this.api.listTickers(),
      sources: this.api.listSourceHealth(),
      hot: this.api.leaderboard('hot', 10),
      positive: this.api.leaderboard('positive', 10),
      surge: this.api.leaderboard('surge', 10),
    }).subscribe({
      next: (res) => {
        this.tickers.set(res.tickers);
        this.sources.set(res.sources);
        this.hotBoard.set(res.hot);
        this.positiveBoard.set(res.positive);
        this.surgeBoard.set(res.surge);
        const sym = this.selectedSymbol();
        const first = res.tickers[0]?.symbol ?? '';
        if (!sym && first) {
          this.selectedSymbol.set(first);
          this.refreshDrilldown();
        }
        this.loadingTop.set(false);
      },
      error: (err) => {
        this.errorMessage.set(formatHttpErrorDetail(err) ?? 'Failed to load Predicts dashboard');
        this.loadingTop.set(false);
      },
    });
  }

  // ----------------------- drilldown -----------------------

  refreshDrilldown(): void {
    const symbol = this.selectedSymbol().trim().toUpperCase();
    if (!symbol) {
      return;
    }
    this.loadingDrilldown.set(true);
    forkJoin({
      summary: this.api.summary(symbol),
      mentions: this.api.mentions(symbol, 25),
      ts: this.api.timeseries(symbol, this.selectedWindow(), this.selectedSource(), this.selectedDays()),
    }).subscribe({
      next: (res) => {
        this.summary.set(res.summary);
        this.mentions.set(res.mentions);
        this.timeseries.set(res.ts);
        this.loadingDrilldown.set(false);
      },
      error: (err) => {
        this.snackBar.open(
          formatHttpErrorDetail(err) ?? `Failed to load Predicts data for ${symbol}`,
          'Dismiss',
          { duration: 4000 },
        );
        this.loadingDrilldown.set(false);
      },
    });
  }

  selectSymbolFromBoard(entry: PredictsLeaderboardEntryDto): void {
    this.selectedSymbol.set(entry.symbol);
    this.refreshDrilldown();
  }

  // ----------------------- ticker add/remove -----------------------

  addTrackedTicker(): void {
    const symbol = this.addSymbolInput().trim();
    if (!symbol) {
      return;
    }
    this.addingTicker.set(true);
    this.api.addTicker({ symbol }).subscribe({
      next: (created) => {
        this.tickers.update((list) => [...list, created]);
        this.addSymbolInput.set('');
        this.addingTicker.set(false);
        this.snackBar.open(`Tracking ${created.symbol}`, 'OK', { duration: 2500 });
        this.selectedSymbol.set(created.symbol);
        this.refreshDrilldown();
      },
      error: (err) => {
        this.addingTicker.set(false);
        this.snackBar.open(
          formatHttpErrorDetail(err) ?? `Failed to track ${symbol}`,
          'Dismiss',
          { duration: 4500 },
        );
      },
    });
  }

  removeTracked(ticker: PredictsTickerDto): void {
    if (ticker.autoSeeded) {
      this.snackBar.open(
        `${ticker.symbol} is auto-seeded from your Robinhood holdings — it'll re-appear after the next sync.`,
        'OK',
        { duration: 5000 },
      );
    }
    this.api.deleteTicker(ticker.id).subscribe({
      next: () => {
        this.tickers.update((list) => list.filter((t) => t.id !== ticker.id));
        if (this.selectedSymbol() === ticker.symbol) {
          this.selectedSymbol.set('');
          this.summary.set(null);
          this.mentions.set([]);
          this.timeseries.set(null);
        }
      },
      error: (err) => {
        this.snackBar.open(
          formatHttpErrorDetail(err) ?? `Failed to remove ${ticker.symbol}`,
          'Dismiss',
          { duration: 4500 },
        );
      },
    });
  }

  // ----------------------- window / source toggles -----------------------

  onWindowChange(value: PredictsWindow): void {
    this.selectedWindow.set(value);
    this.refreshDrilldown();
  }

  onSourceChipClick(value: string): void {
    this.selectedSource.set(value);
    this.refreshDrilldown();
  }

  onDaysChange(value: number): void {
    this.selectedDays.set(Math.max(1, Math.min(60, value)));
    this.refreshDrilldown();
  }

  // ----------------------- presentational helpers -----------------------

  sourceColor(source: string): string {
    switch (source.toLowerCase()) {
      case 'stocktwits':
        return '#0ea5e9';
      case 'reddit':
        return '#f97316';
      case 'x':
        return '#111827';
      default:
        return '#6b7280';
    }
  }

  positivityClass(value: number | null | undefined): string {
    if (value == null) {
      return 'neutral';
    }
    if (value > 10) {
      return 'positive';
    }
    if (value < -10) {
      return 'negative';
    }
    return 'neutral';
  }

  spikeClass(value: number | null | undefined): string {
    if (value == null) {
      return 'neutral';
    }
    if (value >= 2) {
      return 'positive';
    }
    if (value >= 1) {
      return 'warm';
    }
    return 'neutral';
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) {
      return '–';
    }
    if (Math.abs(value) >= 1000) {
      return `${(value / 1000).toFixed(1)}k`;
    }
    return value.toLocaleString();
  }

  formatPct(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '–';
    }
    return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`;
  }

  formatZ(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '0.0σ';
    }
    return `${value.toFixed(1)}σ`;
  }

  formatTimestamp(value: string | null | undefined): string {
    if (!value) {
      return '–';
    }
    return new Date(value).toLocaleString();
  }
}
