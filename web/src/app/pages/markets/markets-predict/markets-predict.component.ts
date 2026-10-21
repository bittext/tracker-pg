import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import {
  Btc15WindowDto,
  RhPredictCloseDto,
  RhPredictDeskDto,
  RhPredictFeedbackDto,
  RhPredictNoteDto,
  RhPredictNotesDeskDto,
} from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  predictInvested,
  predictPayback,
  PredictPeriodTotals,
  PredictYearRollup,
  rollupPredictCloses,
} from './rh-predict-rollups';

interface PredictReturnChart {
  width: number;
  height: number;
  padL: number;
  padR: number;
  line: string;
  zeroY: number;
  points: { id: number; x: number; y: number; up: boolean; amount: string; date: string; time: string; title: string }[];
  yLabels: { y: number; text: string }[];
}

@Component({
  selector: 'app-markets-predict',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './markets-predict.component.html',
  styleUrl: './markets-predict.component.scss',
})
export class MarketsPredictComponent implements OnInit, OnDestroy {
  private readonly api = inject(RhPredictApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly refreshEveryMs = 10_000;
  private readonly liveEveryMs = 5_000;
  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private liveTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  /** In-flight Robinhood pull; not bound to UI so quiet refreshes stay invisible. */
  private refreshInFlight = false;
  private liveInFlight = false;
  private liveWindows: Btc15WindowDto[] = [];

  readonly columns = ['closedAt', 'quantity', 'price', 'invested', 'payback', 'realized'] as const;

  readonly periodGrains = [
    { id: 'day', label: 'Day' },
    { id: 'hour', label: 'Hour' },
  ] as const;

  desk: RhPredictDeskDto | null = null;
  loading = false;
  /** Manual refresh only — drives the progress bar and button disable. */
  refreshing = false;
  draft = '';
  notes: RhPredictNoteDto[] = [];
  feedback: RhPredictFeedbackDto | null = null;
  savingNote = false;
  asking = false;
  clearingFeedback = false;
  selectedYear: number | null = new Date().getFullYear();
  /** Null keeps the year summary. "all" lists every month. A number is that month. */
  selectedMonth: number | 'all' | null = null;
  periodGrain: 'day' | 'hour' | null = null;
  nowMs = Date.now();

  ngOnInit(): void {
    this.load();
    this.loadNotes();
    this.loadLiveWindow();
    this.refreshTimer = setInterval(() => this.refresh(true), this.refreshEveryMs);
    this.liveTimer = setInterval(() => this.loadLiveWindow(), this.liveEveryMs);
    this.clockTimer = setInterval(() => {
      this.nowMs = Date.now();
    }, 250);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer != null) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
    if (this.liveTimer != null) {
      clearInterval(this.liveTimer);
      this.liveTimer = null;
    }
    if (this.clockTimer != null) {
      clearInterval(this.clockTimer);
      this.clockTimer = null;
    }
  }

  /** True when Robinhood still shows open contract mark. */
  get hasOpenBook(): boolean {
    const mark = this.desk?.openValue;
    return mark != null && mark > 0;
  }

  get liveWindow(): Btc15WindowDto | null {
    const now = this.nowMs;
    return (
      this.liveWindows.find((row) => row.status === 'open' || (row.unsettled && inLiveWindow(row, now))) ??
      this.liveWindows.find((row) => row.status === 'awaiting' || (row.unsettled && now >= new Date(row.windowEnd).getTime())) ??
      null
    );
  }

  get livePeriodLabel(): string {
    const row = this.liveWindow;
    if (!row) {
      return '';
    }
    return `${formatLiveClock(row.windowStart)} – ${formatLiveClock(row.windowEnd)} CT`;
  }

  get liveCountdown(): string {
    const row = this.liveWindow;
    if (!row) {
      return '';
    }
    const end = new Date(row.windowEnd).getTime();
    const start = new Date(row.windowStart).getTime();
    if (this.nowMs < start) {
      return `Opens in ${formatLiveRemain(start - this.nowMs)}`;
    }
    if (this.nowMs >= end) {
      return 'Awaiting settlement';
    }
    return formatLiveRemain(end - this.nowMs);
  }

  get liveMovePct(): number | null {
    const row = this.liveWindow;
    if (row?.currentGap == null || row.target == null || !row.target) {
      return null;
    }
    return row.currentGap / row.target;
  }

  load(): void {
    this.loading = true;
    this.api.load().subscribe({
      next: (desk) => {
        this.apply(desk);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastError(err);
      },
    });
  }

  refresh(quiet = false): void {
    if (this.refreshInFlight || this.loading) {
      return;
    }
    if (quiet && typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }
    this.refreshInFlight = true;
    if (!quiet) {
      this.refreshing = true;
    }
    this.api.refresh().subscribe({
      next: (desk) => {
        this.applyQuiet(desk, quiet);
        this.refreshInFlight = false;
        this.refreshing = false;
        if (!quiet) {
          this.snackBar.open('Predict book updated from Robinhood.', 'Dismiss', { duration: 3000 });
        }
      },
      error: (err) => {
        this.refreshInFlight = false;
        this.refreshing = false;
        if (!quiet) {
          this.toastError(err);
        }
      },
    });
  }

  get years(): PredictYearRollup[] {
    const rolled = rollupPredictCloses(this.desk?.closes ?? []);
    const current = new Date().getFullYear();
    if (rolled.some((year) => year.year === current)) {
      return rolled;
    }
    return [{ year: current, months: [], closes: 0, invested: 0, payback: 0, returned: 0 }, ...rolled];
  }

  get activeYear(): PredictYearRollup | null {
    const years = this.years;
    if (!years.length) {
      return null;
    }
    const wanted = this.selectedYear ?? new Date().getFullYear();
    return years.find((year) => year.year === wanted) ?? years[0];
  }

  selectYear(year: number): void {
    this.selectedYear = Number(year);
    this.selectedMonth = null;
  }

  selectMonth(month: number | 'all' | 'blank'): void {
    if (month === 'all') {
      this.selectedMonth = 'all';
      return;
    }
    if (month === 'blank') {
      this.selectedMonth = null;
      return;
    }
    const value = Number(month);
    this.selectedMonth = Number.isFinite(value) && value >= 0 ? value : null;
  }

  get scopedMonths() {
    const year = this.activeYear;
    if (!year) {
      return [];
    }
    if (typeof this.selectedMonth !== 'number') {
      return year.months;
    }
    return year.months.filter((month) => month.month === this.selectedMonth);
  }

  get scopedTotals(): PredictPeriodTotals {
    const year = this.activeYear;
    if (!year) {
      return { closes: 0, invested: 0, payback: 0, returned: 0 };
    }
    if (typeof this.selectedMonth !== 'number') {
      return year;
    }
    return this.scopedMonths[0] ?? { closes: 0, invested: 0, payback: 0, returned: 0 };
  }

  get scopeLabel(): string {
    const year = this.activeYear;
    if (!year) {
      return '';
    }
    if (typeof this.selectedMonth !== 'number') {
      return String(year.year);
    }
    const month = year.months.find((row) => row.month === this.selectedMonth);
    return month ? `${month.label} ${year.year}` : String(year.year);
  }

  get breakdownTitle(): string {
    const scope = this.scopeLabel;
    if (this.periodGrain === 'day' || this.periodGrain === 'hour') {
      return `${scope} by ${this.periodGrain}`;
    }
    return `${scope} by month`;
  }

  /** Share of cash put in that came back as profit or loss. */
  get returnRate(): number | null {
    const invested = this.scopedTotals.invested;
    if (!invested) {
      return null;
    }
    return this.scopedTotals.returned / invested;
  }

  /** Unrealized on the open book; null when mark or cost is missing. */
  get unsettledPnl(): number | null {
    const mark = this.desk?.openValue;
    const cost = this.desk?.openCost;
    if (mark == null || cost == null) {
      return null;
    }
    return mark - cost;
  }

  get unsettledPnlRate(): number | null {
    const cost = this.desk?.openCost;
    const pnl = this.unsettledPnl;
    if (pnl == null || cost == null || !cost) {
      return null;
    }
    return pnl / cost;
  }

  get scopedCloses(): RhPredictCloseDto[] {
    const year = this.activeYear;
    const closes = this.desk?.closes ?? [];
    if (!year) {
      return closes;
    }
    return closes.filter((row) => {
      const closed = new Date(row.closedAt);
      if (Number.isNaN(closed.getTime()) || closed.getFullYear() !== year.year) {
        return false;
      }
      return typeof this.selectedMonth !== 'number' || closed.getMonth() === this.selectedMonth;
    });
  }

  /** Running return after each close, oldest first, for the selected year and month. */
  get returnChart(): PredictReturnChart | null {
    const rows = [...this.scopedCloses].reverse();
    if (!rows.length) {
      return null;
    }
    const slot = 84;
    const padL = 68;
    const padR = 28;
    const padT = 28;
    const padB = 40;
    const width = padL + rows.length * slot + padR;
    const height = 248;
    let running = 0;
    const steps = rows.map((row) => {
      running += row.realized;
      return { row, running };
    });
    const min = Math.min(0, ...steps.map((step) => step.running));
    const max = Math.max(0, ...steps.map((step) => step.running));
    const span = max - min || 1;
    const innerH = height - padT - padB;
    const xAt = (index: number) => padL + slot / 2 + index * slot;
    const yAt = (value: number) => padT + ((max - value) / span) * innerH;
    const points = steps.map((step, index) => {
      const when = new Date(step.row.closedAt);
      const valid = !Number.isNaN(when.getTime());
      const whenLabel = valid
        ? when.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
        : '';
      return {
        id: step.row.id,
        x: xAt(index),
        y: yAt(step.running),
        up: step.row.realized >= 0,
        amount: this.signedUsd(step.row.realized),
        date: valid ? when.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : '',
        time: valid ? when.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }) : '',
        title: `${whenLabel}\nThis close ${this.usd(step.row.realized)}\nRunning ${this.usd(step.running)}`,
      };
    });
    const yLabels: { y: number; text: string }[] = [];
    for (const value of [max, 0, min]) {
      const y = yAt(value);
      if (yLabels.some((label) => Math.abs(label.y - y) < 14)) {
        continue;
      }
      yLabels.push({ y, text: this.axisUsd(value) });
    }
    return {
      width,
      height,
      padL,
      padR,
      line: points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' '),
      zeroY: yAt(0),
      points,
      yLabels,
    };
  }

  get upCount(): number {
    return this.scopedCloses.filter((row) => row.realized > 0).length;
  }

  get downCount(): number {
    return this.scopedCloses.filter((row) => row.realized < 0).length;
  }

  get periodRows(): { key: string; label: string; level: string; totals: PredictPeriodTotals }[] {
    const year = this.activeYear;
    if (!year) {
      return [];
    }
    if (this.periodGrain == null) {
      if (this.selectedMonth !== 'all') {
        return [];
      }
      return year.months.map((month) => ({
        key: month.key,
        label: month.label,
        level: 'month',
        totals: month,
      }));
    }
    const months = this.scopedMonths;
    if (this.periodGrain === 'day') {
      return months.flatMap((month) =>
        month.days.map((day) => ({ key: day.key, label: day.label, level: 'day', totals: day })),
      );
    }
    return months.flatMap((month) =>
      month.days.flatMap((day) =>
        day.hours.map((hour) => ({
          key: hour.key,
          label: `${day.label}, ${hour.label}`,
          level: 'hour',
          totals: hour,
        })),
      ),
    );
  }

  get accounts(): string[] {
    const seen = new Set<string>();
    const labels: string[] = [];
    for (const row of this.desk?.closes ?? []) {
      const label = `${row.accountLabel} ••••${row.accountSuffix}`;
      if (seen.has(label)) {
        continue;
      }
      seen.add(label);
      labels.push(label);
    }
    return labels;
  }

  invested(row: RhPredictCloseDto): number {
    return predictInvested(row);
  }

  /** Return as a share of cash put in. Null when nothing was invested. */
  share(returned: number, invested: number): number | null {
    if (!invested) {
      return null;
    }
    return returned / invested;
  }

  payback(row: RhPredictCloseDto): number {
    return predictPayback(row);
  }

  moneyClass(value: number | null | undefined): string {
    const n = value ?? 0;
    if (n > 0) {
      return 'gain';
    }
    if (n < 0) {
      return 'loss';
    }
    return '';
  }

  saveNote(): void {
    const body = this.draft.trim();
    if (!body) {
      return;
    }
    this.savingNote = true;
    this.api.addNote(body).subscribe({
      next: (desk) => {
        this.applyNotes(desk);
        this.draft = '';
        this.savingNote = false;
      },
      error: (err) => {
        this.savingNote = false;
        this.toastError(err);
      },
    });
  }

  removeNote(id: number): void {
    this.api.deleteNote(id).subscribe({
      next: (desk) => this.applyNotes(desk),
      error: (err) => this.toastError(err),
    });
  }

  askFeedback(): void {
    this.asking = true;
    this.api.noteFeedback().subscribe({
      next: (desk) => {
        this.applyNotes(desk);
        this.asking = false;
      },
      error: (err) => {
        this.asking = false;
        this.toastError(err);
      },
    });
  }

  removeFeedback(): void {
    this.clearingFeedback = true;
    this.api.clearFeedback().subscribe({
      next: (desk) => {
        this.applyNotes(desk);
        this.clearingFeedback = false;
      },
      error: (err) => {
        this.clearingFeedback = false;
        this.toastError(err);
      },
    });
  }

  private loadNotes(): void {
    this.api.notes().subscribe({
      next: (desk) => this.applyNotes(desk),
      error: (err) => this.toastError(err),
    });
  }

  private loadLiveWindow(): void {
    if (this.liveInFlight) {
      return;
    }
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
      return;
    }
    this.liveInFlight = true;
    const priorOpen = new Set(this.liveWindows.filter((row) => row.unsettled).map((row) => row.ticker));
    this.api.btc15(todayCentral()).subscribe({
      next: (desk) => {
        this.liveWindows = desk.hours.flatMap((hour) => hour.windows);
        this.liveInFlight = false;
        const settled = this.liveWindows.filter((row) => priorOpen.has(row.ticker) && !row.unsettled);
        if (settled.length) {
          this.refresh(true);
        }
      },
      error: () => {
        this.liveInFlight = false;
      },
    });
  }

  private applyNotes(desk: RhPredictNotesDeskDto): void {
    this.notes = desk.notes ?? [];
    this.feedback = desk.feedback;
  }

  private signedUsd(value: number): string {
    const abs = Math.abs(value);
    const body =
      abs >= 100
        ? abs.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })
        : abs.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
    if (value > 0) {
      return `+${body}`;
    }
    if (value < 0) {
      return `-${body}`;
    }
    return body;
  }

  private usd(value: number): string {
    return value.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }

  private axisUsd(value: number): string {
    const abs = Math.abs(value);
    const sign = value < 0 ? '-' : '';
    if (abs >= 1000) {
      return `${sign}$${(abs / 1000).toFixed(abs >= 10000 ? 0 : 1)}k`;
    }
    return `${sign}$${Math.round(abs)}`;
  }

  private apply(desk: RhPredictDeskDto): void {
    this.desk = desk;
  }

  /** Patch numbers in place on quiet refresh so layout/scroll stay put. */
  private applyQuiet(desk: RhPredictDeskDto, quiet: boolean): void {
    if (!quiet || this.desk == null) {
      this.desk = desk;
      return;
    }
    const current = this.desk;
    const priorCloses = current.closeCount;
    current.openValue = desk.openValue;
    current.openCost = desk.openCost;
    current.realizedWeek = desk.realizedWeek;
    current.realizedAll = desk.realizedAll;
    current.closeCount = desk.closeCount;
    // Leave lastSyncedAt alone so the header does not tick every auto-pull.
    current.warnings = desk.warnings ?? [];
    current.closes = desk.closes ?? [];
    if (quiet && desk.closeCount > priorCloses) {
      this.snackBar.open('Settlement landed — returns updated.', 'Dismiss', { duration: 3500 });
    }
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
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

function inLiveWindow(row: Btc15WindowDto, now: number): boolean {
  return new Date(row.windowStart).getTime() <= now && now < new Date(row.windowEnd).getTime();
}

function formatLiveClock(iso: string): string {
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

function formatLiveRemain(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
