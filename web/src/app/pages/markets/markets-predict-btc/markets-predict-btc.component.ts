import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Btc15DeskDto, Btc15WindowDto, RhPredictCloseDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { predictInvested, predictPayback } from '../markets-predict/rh-predict-rollups';

export interface WindowSettle {
  closes: number;
  invested: number;
  payback: number;
  returned: number;
}

@Component({
  selector: 'app-markets-predict-btc',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatProgressBarModule, MatSnackBarModule, RouterLink],
  templateUrl: './markets-predict-btc.component.html',
  styleUrl: './markets-predict-btc.component.scss',
})
export class MarketsPredictBtcComponent implements OnInit, OnDestroy {
  private readonly api = inject(RhPredictApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);
  private readonly pollEveryMs = 5_000;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;
  private previousGaps = new Map<string, number | null>();
  private flashClearTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private closes: RhPredictCloseDto[] = [];
  /** Robinhood settles keyed by Kalshi window ticker. */
  private settlesByTicker = new Map<string, WindowSettle>();

  loading = false;
  asset: QuarterAsset = 'BTC';
  /** Open event-contract value. Shown only while a predict investment is active. */
  openValue: number | null = null;
  desk: Btc15DeskDto | null = null;
  date = todayCentral();
  nowMs = Date.now();
  /** Brief flash class per ticker when the live gap moves. */
  gapFlash = new Map<string, 'up' | 'down'>();

  ngOnInit(): void {
    this.asset = quarterAsset(this.route.snapshot.data['asset']);
    this.date = todayCentral();
    this.load(false);
    this.clockTimer = setInterval(() => {
      this.nowMs = Date.now();
    }, 250);
    this.pollTimer = setInterval(() => this.pollQuiet(), this.pollEveryMs);
  }

  ngOnDestroy(): void {
    if (this.pollTimer != null) {
      clearInterval(this.pollTimer);
    }
    if (this.clockTimer != null) {
      clearInterval(this.clockTimer);
    }
    for (const timer of this.flashClearTimers.values()) {
      clearTimeout(timer);
    }
  }

  get streaksLink(): string {
    return `/markets/predict/${this.asset.toLowerCase()}-15/streaks`;
  }

  get peers(): { label: string; path: string }[] {
    return (['BTC', 'ETH', 'DOGE', 'SOL'] as const)
      .filter((asset) => asset !== this.asset)
      .map((asset) => ({ label: `${asset} 15 min`, path: `/markets/predict/${asset.toLowerCase()}-15` }));
  }

  get activeWindow(): Btc15WindowDto | null {
    const windows = this.allWindows();
    const now = this.nowMs;
    return (
      windows.find((row) => row.status === 'open' || (row.unsettled && inWindow(row, now))) ??
      windows.find((row) => row.status === 'awaiting' || (row.unsettled && now >= new Date(row.windowEnd).getTime())) ??
      null
    );
  }

  get periodLabel(): string {
    const row = this.activeWindow;
    if (!row) {
      return '';
    }
    return `${formatClock(row.windowStart)} – ${formatClock(row.windowEnd)} CT`;
  }

  get countdownLabel(): string {
    const row = this.activeWindow;
    if (!row) {
      return '';
    }
    const end = new Date(row.windowEnd).getTime();
    const start = new Date(row.windowStart).getTime();
    if (this.nowMs < start) {
      return `Opens in ${formatRemain(start - this.nowMs)}`;
    }
    if (this.nowMs >= end) {
      return 'Awaiting settlement';
    }
    return formatRemain(end - this.nowMs);
  }

  get liveMovePct(): number | null {
    const row = this.activeWindow;
    if (row?.currentGap == null || row.target == null || !row.target) {
      return null;
    }
    return row.currentGap / row.target;
  }

  /** Settled + live below-target runs for the selected Central day. */
  get belowStreaks(): {
    below: number;
    settled: number;
    current: number;
    longest: number;
    runs: { length: number; from: string; to: string }[];
  } | null {
    const windows = this.allWindows();
    if (!windows.length) {
      return null;
    }
    let below = 0;
    let settled = 0;
    let run = 0;
    let longest = 0;
    let current = 0;
    let runStart: Btc15WindowDto | null = null;
    let runEnd: Btc15WindowDto | null = null;
    const runs: { length: number; from: string; to: string }[] = [];
    const ordered = [...windows].sort(
      (a, b) => new Date(a.windowStart).getTime() - new Date(b.windowStart).getTime(),
    );
    const closeRun = () => {
      if (run > 0 && runStart && runEnd) {
        runs.push({ length: run, from: runStart.label, to: runEnd.label });
      }
      run = 0;
      runStart = null;
      runEnd = null;
      current = 0;
    };
    for (const row of ordered) {
      const miss = this.isBelowTarget(row);
      if (row.aboveTarget != null || (!row.unsettled && row.endGap != null)) {
        settled += 1;
      }
      if (miss) {
        below += 1;
        if (run === 0) {
          runStart = row;
        }
        runEnd = row;
        run += 1;
        longest = Math.max(longest, run);
        current = run;
      } else if (row.unsettled && row.liveAbove == null && row.currentGap == null) {
        // Open with no print yet — do not break or extend the run.
        current = run;
      } else {
        closeRun();
      }
    }
    if (run > 0 && runStart && runEnd) {
      runs.push({ length: run, from: runStart.label, to: runEnd.label });
    }
    return { below, settled, current, longest, runs: runs.filter((r) => r.length >= 2).reverse() };
  }

  /** True when the window finished (or is leaning) below target. */
  isBelowTarget(row: Btc15WindowDto): boolean {
    if (row.aboveTarget === false) {
      return true;
    }
    if (row.aboveTarget === true) {
      return false;
    }
    if (row.unsettled) {
      if (row.liveAbove === false) {
        return true;
      }
      if (row.liveAbove === true) {
        return false;
      }
      return (row.currentGap ?? 0) < 0;
    }
    return (row.endGap ?? 0) < 0;
  }

  signed(value: number | null): string {
    if (value == null) {
      return '—';
    }
    const text = Math.abs(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    if (value > 0) {
      return `+${text}`;
    }
    if (value < 0) {
      return `−${text}`;
    }
    return text;
  }

  gapClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '';
    }
    return value > 0 ? 'btc__up' : 'btc__down';
  }

  rowFlash(ticker: string): string {
    const flash = this.gapFlash.get(ticker);
    if (flash === 'up') {
      return 'btc__flash-up';
    }
    if (flash === 'down') {
      return 'btc__flash-down';
    }
    return '';
  }

  displayEndPrice(row: Btc15WindowDto): number | null {
    return row.unsettled ? row.currentPrice : row.endPrice;
  }

  displayEndGap(row: Btc15WindowDto): number | null {
    return row.unsettled ? row.currentGap : row.endGap;
  }

  settleFor(row: Btc15WindowDto): WindowSettle | null {
    return this.settlesByTicker.get(row.ticker) ?? null;
  }

  moneyClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '';
    }
    return value > 0 ? 'btc__up' : 'btc__down';
  }

  share(returned: number, invested: number): number | null {
    if (!invested) {
      return null;
    }
    return returned / invested;
  }

  load(quiet = false): void {
    if (!quiet) {
      this.loading = true;
    }
    if (this.pollInFlight && quiet) {
      return;
    }
    this.pollInFlight = true;
    const priorOpen = this.openTickers();
    const desk$ = this.api.quarter15(this.asset.toLowerCase() as 'btc' | 'eth' | 'doge' | 'sol', this.date);
    desk$.subscribe({
      next: (desk) => {
        this.noteGapFlashes(desk);
        const settledNow = this.newlySettled(priorOpen, desk);
        this.desk = desk;
        this.rebuildSettles();
        this.loading = false;
        this.pollInFlight = false;
        this.loadCloses(quiet);
        if (settledNow.length) {
          this.onWindowsSettled(settledNow);
        }
      },
      error: (err) => {
        this.loading = false;
        this.pollInFlight = false;
        if (!quiet) {
          this.snack.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
        }
      },
    });
  }

  private loadCloses(quiet: boolean): void {
    this.api.load().subscribe({
      next: (desk) => {
        this.closes = desk.closes ?? [];
        this.openValue = desk.openValue;
        this.rebuildSettles();
      },
      error: (err) => {
        if (!quiet) {
          this.snack.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
        }
      },
    });
  }

  private rebuildSettles(): void {
    this.settlesByTicker.clear();
    const windows = this.allWindows();
    if (!windows.length || !this.closes.length) {
      return;
    }
    const day = this.desk?.date ?? this.date;
    for (const close of this.closes) {
      const when = new Date(close.closedAt).getTime();
      if (Number.isNaN(when)) {
        continue;
      }
      if (centralDay(close.closedAt) !== day) {
        continue;
      }
      const window = windowForClose(when, windows);
      if (!window) {
        continue;
      }
      const invested = predictInvested(close);
      const payback = predictPayback(close);
      const prior = this.settlesByTicker.get(window.ticker) ?? {
        closes: 0,
        invested: 0,
        payback: 0,
        returned: 0,
      };
      prior.closes += 1;
      prior.invested += invested;
      prior.payback += payback;
      prior.returned += close.realized;
      this.settlesByTicker.set(window.ticker, prior);
    }
  }

  private pollQuiet(): void {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }
    if (this.date !== todayCentral()) {
      return;
    }
    this.load(true);
  }

  private allWindows(): Btc15WindowDto[] {
    return this.desk?.hours.flatMap((hour) => hour.windows) ?? [];
  }

  private openTickers(): Set<string> {
    return new Set(this.allWindows().filter((row) => row.unsettled).map((row) => row.ticker));
  }

  private newlySettled(priorOpen: Set<string>, desk: Btc15DeskDto): Btc15WindowDto[] {
    if (!priorOpen.size) {
      return [];
    }
    const settled: Btc15WindowDto[] = [];
    for (const hour of desk.hours) {
      for (const row of hour.windows) {
        if (priorOpen.has(row.ticker) && !row.unsettled) {
          settled.push(row);
        }
      }
    }
    return settled;
  }

  private onWindowsSettled(rows: Btc15WindowDto[]): void {
    const labels = rows.map((row) => row.label).join(', ');
    this.snack.open(`Settled ${labels}. Refreshing Predict returns…`, 'Dismiss', { duration: 4000 });
    this.api.refresh().subscribe({
      next: (desk) => {
        this.closes = desk.closes ?? [];
        this.rebuildSettles();
        this.snack.open('Predict book updated from Robinhood.', 'Dismiss', { duration: 3000 });
      },
      error: () => {
        /* quiet — BTC page already has the settled print */
      },
    });
  }

  private noteGapFlashes(desk: Btc15DeskDto): void {
    for (const hour of desk.hours) {
      for (const row of hour.windows) {
        if (!row.unsettled) {
          this.previousGaps.delete(row.ticker);
          continue;
        }
        const next = row.currentGap;
        const prev = this.previousGaps.get(row.ticker);
        this.previousGaps.set(row.ticker, next);
        if (prev == null || next == null || next === prev) {
          continue;
        }
        const direction: 'up' | 'down' = next > prev ? 'up' : 'down';
        this.gapFlash.set(row.ticker, direction);
        const existing = this.flashClearTimers.get(row.ticker);
        if (existing != null) {
          clearTimeout(existing);
        }
        this.flashClearTimers.set(
          row.ticker,
          setTimeout(() => {
            this.gapFlash.delete(row.ticker);
            this.flashClearTimers.delete(row.ticker);
          }, 900),
        );
      }
    }
  }
}

type QuarterAsset = 'BTC' | 'ETH' | 'DOGE' | 'SOL';

function quarterAsset(value: unknown): QuarterAsset {
  const asset = String(value ?? '').toUpperCase();
  if (asset === 'ETH' || asset === 'DOGE' || asset === 'SOL') {
    return asset;
  }
  return 'BTC';
}

function todayCentral(): string {
  return centralDay(new Date().toISOString());
}

function centralDay(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(iso));
}

function inWindow(row: Btc15WindowDto, now: number): boolean {
  return new Date(row.windowStart).getTime() <= now && now < new Date(row.windowEnd).getTime();
}

function formatClock(iso: string): string {
  const when = new Date(iso);
  if (Number.isNaN(when.getTime())) {
    return '';
  }
  return when.toLocaleTimeString('en-US', {
    timeZone: 'America/Chicago',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatRemain(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/** Map a Robinhood event close onto the 15-minute window it most likely settled. */
function windowForClose(closedAt: number, windows: Btc15WindowDto[]): Btc15WindowDto | null {
  const graceMs = 15 * 60_000;
  let best: Btc15WindowDto | null = null;
  let bestScore = Number.POSITIVE_INFINITY;
  for (const window of windows) {
    const start = new Date(window.windowStart).getTime();
    const end = new Date(window.windowEnd).getTime();
    if (Number.isNaN(start) || Number.isNaN(end)) {
      continue;
    }
    if (closedAt < start || closedAt >= end + graceMs) {
      continue;
    }
    // Prefer settlement just after the window end over a mid-window exit score.
    const score = closedAt >= end ? closedAt - end : end - closedAt + 1_000_000;
    if (score < bestScore) {
      bestScore = score;
      best = window;
    }
  }
  return best;
}
