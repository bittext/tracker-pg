import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, forkJoin, of } from 'rxjs';
import { RobinhoodRhDailyTrackerReportDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';

/** First year the Daily Tracker has data for. */
const TRACKER_FIRST_YEAR = 2026;
/** The ladder: $20k first, then $50k, $100k and on up. */
const MILESTONES = [20_000, 50_000, 100_000, 250_000, 500_000, 1_000_000, 2_500_000, 5_000_000];
const FALLBACK_INDIVIDUAL_SUFFIX = '3370';
const PACE_WINDOW_DAYS = 30;

interface ValuePoint {
  date: string;
  at: string;
  value: number;
  kind: 'CLOSE' | 'LIVE';
}

export interface MilestoneCard {
  amount: number;
  status: 'reached' | 'next' | 'ahead';
  reachedOn: string | null;
  valueAtReach: number | null;
  daysFromStart: number | null;
  daysFromPrevious: number | null;
  /** Closes since reaching it that finished back under the line. */
  slippedCloses: number;
  /** Next card only. */
  progressPct: number | null;
  remaining: number | null;
  etaDays: number | null;
}

@Component({
  selector: 'app-reports-finance-robinhood-milestones',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-milestones.component.html',
  styleUrl: './reports-finance-robinhood-milestones.component.scss',
})
export class ReportsFinanceRobinhoodMilestonesComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);

  loading = false;
  error: string | null = null;
  reports: RobinhoodRhDailyTrackerReportDto[] = [];

  private seriesCache: ValuePoint[] = [];
  private suffixCache = FALLBACK_INDIVIDUAL_SUFFIX;
  private cardsCache: MilestoneCard[] = [];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = !this.reports.length;
    this.error = null;
    const years: number[] = [];
    for (let y = TRACKER_FIRST_YEAR; y <= new Date().getFullYear(); y++) {
      years.push(y);
    }
    forkJoin(
      years.map((y) => this.financeApi.robinhoodDailyTracker(y, null).pipe(catchError(() => of(null)))),
    ).subscribe((rows) => {
      const ok = rows.filter((r): r is RobinhoodRhDailyTrackerReportDto => r != null);
      this.loading = false;
      if (!ok.length) {
        this.error = this.reports.length ? null : 'Could not load the Individual account history.';
        return;
      }
      this.reports = ok;
      this.rebuild();
    });
  }

  suffix(): string {
    return this.suffixCache;
  }

  series(): ValuePoint[] {
    return this.seriesCache;
  }

  cards(): MilestoneCard[] {
    return this.cardsCache;
  }

  hasData(): boolean {
    return this.seriesCache.length > 0;
  }

  now(): ValuePoint | null {
    return this.seriesCache.length ? this.seriesCache[this.seriesCache.length - 1] : null;
  }

  start(): ValuePoint | null {
    return this.seriesCache[0] ?? null;
  }

  peak(): ValuePoint | null {
    let best: ValuePoint | null = null;
    for (const p of this.seriesCache) {
      if (!best || p.value > best.value) {
        best = p;
      }
    }
    return best;
  }

  reachedCount(): number {
    return this.cardsCache.filter((c) => c.status === 'reached').length;
  }

  nextCard(): MilestoneCard | null {
    return this.cardsCache.find((c) => c.status === 'next') ?? null;
  }

  latestReached(): MilestoneCard | null {
    const reached = this.cardsCache.filter((c) => c.status === 'reached');
    return reached.length ? reached[reached.length - 1] : null;
  }

  /** Shown cards: everything reached, the next target, and the next two beyond it. */
  visibleCards(): MilestoneCard[] {
    const nextIdx = this.cardsCache.findIndex((c) => c.status === 'next');
    const end = nextIdx < 0 ? this.cardsCache.length : Math.min(this.cardsCache.length, nextIdx + 3);
    return this.cardsCache.slice(0, end);
  }

  chart(): {
    path: string;
    area: string;
    lines: { y: number; label: string; reached: boolean }[];
    dots: { x: number; y: number; label: string }[];
    width: number;
    height: number;
  } | null {
    const pts = this.seriesCache;
    if (pts.length < 2) {
      return null;
    }
    const width = 640;
    const height = 240;
    const pad = { l: 56, r: 12, t: 12, b: 18 };
    const next = this.nextCard();
    const maxValue = Math.max(...pts.map((p) => p.value), next?.amount ?? 0, 1);
    const minValue = Math.min(...pts.map((p) => p.value), 0);
    const t0 = new Date(pts[0].at).getTime();
    const t1 = new Date(pts[pts.length - 1].at).getTime();
    const span = Math.max(t1 - t0, 1);
    const x = (p: ValuePoint) => pad.l + ((new Date(p.at).getTime() - t0) / span) * (width - pad.l - pad.r);
    const y = (v: number) =>
      height - pad.b - ((v - minValue) / Math.max(maxValue - minValue, 1)) * (height - pad.t - pad.b);
    const path = pts.map((p, i) => `${i ? 'L' : 'M'}${x(p).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');
    const last = pts[pts.length - 1];
    const area = `${path} L${x(last).toFixed(1)},${y(minValue).toFixed(1)} L${x(pts[0]).toFixed(1)},${y(minValue).toFixed(1)} Z`;
    const lines = this.cardsCache
      .filter((c) => c.amount <= maxValue && c.amount >= minValue)
      .map((c) => ({ y: y(c.amount), label: this.shortMoney(c.amount), reached: c.status === 'reached' }));
    const dots = this.cardsCache
      .filter((c) => c.status === 'reached' && c.reachedOn)
      .map((c) => {
        const point = pts.find((p) => p.date === c.reachedOn) ?? pts[0];
        return { x: x(point), y: y(point.value), label: this.shortMoney(c.amount) };
      });
    return { path, area, lines, dots, width, height };
  }

  shortMoney(amount: number): string {
    if (amount >= 1_000_000) {
      return `$${amount / 1_000_000}M`;
    }
    return `$${amount / 1000}k`;
  }

  private rebuild(): void {
    this.suffixCache = this.resolveSuffix();
    this.seriesCache = this.buildSeries();
    this.cardsCache = this.buildCards();
  }

  private resolveSuffix(): string {
    for (let i = this.reports.length - 1; i >= 0; i--) {
      const col = this.reports[i].accounts.find((a) => (a.accountKind || '').toUpperCase() === 'INDIVIDUAL');
      if (col) {
        return col.accountSuffix;
      }
    }
    return FALLBACK_INDIVIDUAL_SUFFIX;
  }

  /** Official 9 PM CT closes for the Individual account, oldest first, plus the freshest live capture. */
  private buildSeries(): ValuePoint[] {
    const suffix = this.suffixCache;
    const closes = new Map<string, ValuePoint>();
    let live: ValuePoint | null = null;
    for (const report of this.reports) {
      for (const day of report.days) {
        const cell = day.accounts.find((a) => a.accountSuffix === suffix);
        if (cell && day.hasScheduledSnapshot && day.snapshotAt) {
          closes.set(day.snapshotDate, {
            date: day.snapshotDate,
            at: day.snapshotAt,
            value: cell.totalAccountValue,
            kind: 'CLOSE',
          });
        }
        for (const cap of [...(day.intradayCaptures ?? []), ...(day.manualCaptures ?? [])]) {
          const acct = cap.accounts.find((a) => a.accountSuffix === suffix);
          if (acct && (!live || new Date(cap.capturedAt) > new Date(live.at))) {
            live = {
              date: this.centralDate(cap.capturedAt),
              at: cap.capturedAt,
              value: acct.totalAccountValue,
              kind: 'LIVE',
            };
          }
        }
      }
    }
    const series = [...closes.values()].sort((a, b) => a.at.localeCompare(b.at));
    const latest = series[series.length - 1];
    if (live && (!latest || new Date(live.at) > new Date(latest.at))) {
      series.push(live);
    }
    return series;
  }

  private buildCards(): MilestoneCard[] {
    const pts = this.seriesCache;
    if (!pts.length) {
      return [];
    }
    const now = pts[pts.length - 1];
    const start = pts[0];
    const pace = this.dailyPace();
    const cards: MilestoneCard[] = [];
    let previousReachedOn: string | null = null;
    let base = start.value;
    let nextAssigned = false;
    for (const amount of MILESTONES) {
      const idx = pts.findIndex((p) => p.value >= amount);
      if (idx >= 0) {
        const hit = pts[idx];
        const slipped = pts.slice(idx + 1).filter((p) => p.kind === 'CLOSE' && p.value < amount).length;
        cards.push({
          amount,
          status: 'reached',
          reachedOn: hit.date,
          valueAtReach: hit.value,
          daysFromStart: this.daysBetween(start.date, hit.date),
          daysFromPrevious: previousReachedOn ? this.daysBetween(previousReachedOn, hit.date) : null,
          slippedCloses: slipped,
          progressPct: null,
          remaining: null,
          etaDays: null,
        });
        previousReachedOn = hit.date;
        base = amount;
        continue;
      }
      const isNext = !nextAssigned;
      nextAssigned = true;
      const remaining = amount - now.value;
      cards.push({
        amount,
        status: isNext ? 'next' : 'ahead',
        reachedOn: null,
        valueAtReach: null,
        daysFromStart: null,
        daysFromPrevious: null,
        slippedCloses: 0,
        progressPct: isNext
          ? Math.max(0, Math.min(100, ((now.value - base) / Math.max(amount - base, 1)) * 100))
          : null,
        remaining: isNext ? remaining : null,
        etaDays: isNext && pace > 0 ? Math.ceil(remaining / pace) : null,
      });
    }
    return cards;
  }

  /** Average dollars per day over the last 30 days of closes (deposits included). */
  private dailyPace(): number {
    const pts = this.seriesCache;
    if (pts.length < 2) {
      return 0;
    }
    const last = pts[pts.length - 1];
    const cutoff = new Date(last.at).getTime() - PACE_WINDOW_DAYS * 86_400_000;
    const first = pts.find((p) => new Date(p.at).getTime() >= cutoff) ?? pts[0];
    const days = Math.max(this.daysBetween(first.date, last.date), 1);
    return (last.value - first.value) / days;
  }

  private daysBetween(a: string, b: string): number {
    return Math.max(0, Math.round((new Date(`${b}T00:00:00`).getTime() - new Date(`${a}T00:00:00`).getTime()) / 86_400_000));
  }

  private centralDate(iso: string): string {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Chicago',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date(iso));
  }
}
