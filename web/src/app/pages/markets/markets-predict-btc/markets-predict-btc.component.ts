import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Btc15DeskDto, Btc15WindowDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

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
  private readonly pollEveryMs = 5_000;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private pollInFlight = false;
  private previousGaps = new Map<string, number | null>();
  private flashClearTimers = new Map<string, ReturnType<typeof setTimeout>>();

  loading = false;
  desk: Btc15DeskDto | null = null;
  date = todayCentral();
  nowMs = Date.now();
  /** Brief flash class per ticker when the live gap moves. */
  gapFlash = new Map<string, 'up' | 'down'>();

  ngOnInit(): void {
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

  load(quiet = false): void {
    if (!quiet) {
      this.loading = true;
    }
    if (this.pollInFlight && quiet) {
      return;
    }
    this.pollInFlight = true;
    const priorOpen = this.openTickers();
    this.api.btc15(this.date).subscribe({
      next: (desk) => {
        this.noteGapFlashes(desk);
        const settledNow = this.newlySettled(priorOpen, desk);
        this.desk = desk;
        this.loading = false;
        this.pollInFlight = false;
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
      next: () => this.snack.open('Predict book updated from Robinhood.', 'Dismiss', { duration: 3000 }),
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

function todayCentral(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
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
