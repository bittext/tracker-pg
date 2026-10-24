import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
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
  RhPredictDailyDto,
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
  points: {
    id: number;
    x: number;
    y: number;
    up: boolean;
    runningUp: boolean;
    amount: string;
    running: string;
    date: string;
    time: string;
    title: string;
  }[];
  yLabels: { y: number; text: string }[];
}

/** Open funding bound to one 15-minute Kalshi window. */
interface WindowStake {
  ticker: string;
  windowStart: string;
  windowEnd: string;
  label: string;
  principal: number;
  markAtBind: number;
}

const WINDOW_STAKE_KEY = 'predict-window-stake-v1';

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
  private readonly refreshEveryMs = 5_000;
  private readonly liveEveryMs = 5_000;
  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private liveTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  /** In-flight Robinhood pull; not bound to UI so quiet refreshes stay invisible. */
  private refreshInFlight = false;
  private liveInFlight = false;
  private liveWindows: Btc15WindowDto[] = [];
  private ethWindows: Btc15WindowDto[] = [];
  private dogeWindows: Btc15WindowDto[] = [];
  private solWindows: Btc15WindowDto[] = [];
  private priorLiveGap: number | null = null;
  private priorOpenValue: number | null = null;
  private gapFlashClear: ReturnType<typeof setTimeout> | null = null;
  /** Fallback cost when Robinhood does not report open_cost — mark when this window's book was anchored. */
  private estimatedOpenCost: number | null = null;
  /** Close count and 15-minute ticker the fallback cost was anchored to. */
  private estimateAnchorCloses = -1;
  private estimateAnchorTicker: string | null = null;

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
  /** Always start on day breakdown for the current year. */
  periodGrain: 'day' | 'hour' | null = 'day';
  /** Central YYYY-MM-DD when reviewing one day's snapshot; null = whole selected month/year. */
  selectedDate: string | null = null;
  dailyDays: RhPredictDailyDto[] = [];
  nowMs = Date.now();
  /** Brief flash when the live gap slides. */
  gapSlide: 'up' | 'down' | null = null;
  /** When the contract value on screen was read from Robinhood. */
  contractValueAsOf: string | null = null;
  /** Open-mark principal trend across quiet Robinhood pulls. */
  openMarkTrend: 'up' | 'down' | null = null;
  openMarkDelta: number | null = null;
  /** Invested principal tied to the current 15-minute window when funding is open. */
  activeStake: WindowStake | null = null;

  ngOnInit(): void {
    this.activeStake = readWindowStake();
    this.load();
    this.loadDaily();
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
    if (this.gapFlashClear != null) {
      clearTimeout(this.gapFlashClear);
    }
  }

  get liveLeanClass(): string {
    const live = this.liveWindow;
    if (live?.liveAbove === true) {
      return 'pred__live--up';
    }
    if (live?.liveAbove === false) {
      return 'pred__live--down';
    }
    return '';
  }

  get liveSlideClass(): string {
    if (this.gapSlide === 'up') {
      return 'pred__live--slide-up';
    }
    if (this.gapSlide === 'down') {
      return 'pred__live--slide-down';
    }
    return '';
  }

  get openMarkTrendClass(): string {
    if (this.openMarkTrend === 'up') {
      return 'gain';
    }
    if (this.openMarkTrend === 'down') {
      return 'loss';
    }
    return '';
  }

  get liveWindow(): Btc15WindowDto | null {
    return activeQuarter(this.liveWindows, this.nowMs);
  }

  get ethLiveWindow(): Btc15WindowDto | null {
    return activeQuarter(this.ethWindows, this.nowMs);
  }

  get dogeLiveWindow(): Btc15WindowDto | null {
    return activeQuarter(this.dogeWindows, this.nowMs);
  }

  get solLiveWindow(): Btc15WindowDto | null {
    return activeQuarter(this.solWindows, this.nowMs);
  }

  get liveQuotes(): { label: string; row: Btc15WindowDto | null; pct: number | null }[] {
    return [
      { label: 'BTC', row: this.liveWindow, pct: moveShare(this.liveWindow) },
      { label: 'ETH', row: this.ethLiveWindow, pct: moveShare(this.ethLiveWindow) },
      { label: 'DOGE', row: this.dogeLiveWindow, pct: moveShare(this.dogeLiveWindow) },
      { label: 'SOL', row: this.solLiveWindow, pct: moveShare(this.solLiveWindow) },
    ];
  }

  get livePeriodLabel(): string {
    const row = this.liveWindow ?? this.ethLiveWindow ?? this.dogeLiveWindow ?? this.solLiveWindow;
    if (!row) {
      return '';
    }
    return `${formatLiveClock(row.windowStart)} – ${formatLiveClock(row.windowEnd)} CT`;
  }

  get liveCountdown(): string {
    const row = this.liveWindow ?? this.ethLiveWindow ?? this.dogeLiveWindow ?? this.solLiveWindow;
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

  /** True when this strip is a funded window timer (open principal on the current period). */
  get hasWindowStake(): boolean {
    return this.activeStake != null && this.desk?.openValue != null && this.desk.openValue > 0;
  }

  /** Live strip while any of the 15-minute windows is still open. */
  get showLiveStrip(): boolean {
    return this.liveQuotes.some((quote) => quote.row != null);
  }

  get windowPrincipal(): number | null {
    return this.activeStake?.principal ?? null;
  }

  get liveBelowTarget(): boolean {
    const live = this.liveWindow;
    if (!live) {
      return false;
    }
    if (live.liveAbove === false) {
      return true;
    }
    if (live.liveAbove === true) {
      return false;
    }
    return (live.currentGap ?? 0) < 0;
  }

  /** Length of the current consecutive below-target run ending at the live window. */
  get currentBelowRun(): number {
    const windows = [...this.liveWindows].sort(
      (a, b) => new Date(a.windowStart).getTime() - new Date(b.windowStart).getTime(),
    );
    let run = 0;
    for (const row of windows) {
      const below =
        row.aboveTarget === false ||
        (row.unsettled &&
          (row.liveAbove === false || (row.liveAbove == null && (row.currentGap ?? 0) < 0))) ||
        (!row.unsettled && row.aboveTarget == null && (row.endGap ?? 0) < 0);
      const above =
        row.aboveTarget === true ||
        (row.unsettled && row.liveAbove === true) ||
        (!row.unsettled && row.aboveTarget == null && (row.endGap ?? 0) > 0);
      if (below) {
        run += 1;
      } else if (row.unsettled && row.liveAbove == null && row.currentGap == null) {
        /* no print yet — keep run */
      } else if (above || !row.unsettled) {
        run = 0;
      }
    }
    return run;
  }

  signedGap(value: number | null | undefined): string {
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
    this.selectedDate = null;
  }

  selectMonth(month: number | 'all' | 'blank'): void {
    this.selectedDate = null;
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

  /** Focus the book on one Central calendar day from the day strip. */
  selectDaily(date: string): void {
    if (this.selectedDate === date) {
      this.selectedDate = null;
      return;
    }
    this.selectedDate = date;
    const parts = date.split('-').map(Number);
    if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
      return;
    }
    const [year, month] = parts;
    this.selectedYear = year;
    this.selectedMonth = month - 1;
    this.periodGrain = 'day';
  }

  clearDaily(): void {
    this.selectedDate = null;
  }

  /** Rollup day key (`year-monthIndex-day`) for the selected Central date. */
  get selectedDayRowKey(): string | null {
    return this.selectedDate ? dayKeyFromIso(this.selectedDate) : null;
  }

  get selectedDaily(): RhPredictDailyDto | null {
    if (!this.selectedDate) {
      return null;
    }
    return this.dailyDays.find((row) => row.date === this.selectedDate) ?? null;
  }

  /** Tap a day row in the breakdown to focus that Central calendar day. */
  onPeriodRowActivate(level: string, key: string): void {
    if (level !== 'day') {
      return;
    }
    const parts = key.split('-').map(Number);
    if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
      return;
    }
    const [year, month, day] = parts;
    const iso = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    this.selectDaily(iso);
  }

  get todayCentralDate(): string {
    return todayCentral();
  }

  dayStripLabel(isoDate: string): string {
    return formatCentralDayLabel(isoDate);
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
    const daily = this.selectedDaily;
    if (daily) {
      return {
        closes: daily.closes,
        invested: daily.invested,
        payback: daily.payback,
        returned: daily.returned,
      };
    }
    const year = this.activeYear;
    if (!year) {
      return { closes: 0, invested: 0, payback: 0, returned: 0 };
    }
    if (typeof this.selectedMonth !== 'number') {
      return year;
    }
    return this.scopedMonths[0] ?? { closes: 0, invested: 0, payback: 0, returned: 0 };
  }


  get openCostBasis(): number | null {
    const reported = this.desk?.openCost;
    if (reported != null) {
      return reported;
    }
    return this.estimatedOpenCost;
  }

  /** Mark minus cost while contracts are still open. */
  get unrealizedPnl(): number | null {
    const mark = this.desk?.openValue;
    const cost = this.openCostBasis;
    if (mark == null || cost == null) {
      return null;
    }
    return mark - cost;
  }

  /**
   * Cash still in open contracts for the current 15-minute window, and the live
   * profit or loss against that amount. A settled close is already in the return
   * totals and is not kept as principal here.
   */
  get liveBook(): { principal: number; mark: number; pnl: number } | null {
    const mark = this.desk?.openValue;
    if (mark == null || mark <= 0) {
      return null;
    }
    const principal = this.openCostBasis ?? this.windowPrincipal;
    if (principal == null || principal <= 0) {
      return null;
    }
    return { principal, mark, pnl: mark - principal };
  }

  /** Settled return for the current scope. The live open mark stays on its own tile until Robinhood posts the close. */
  get displayTotals(): PredictPeriodTotals {
    return this.scopedTotals;
  }

  get displayWeekReturn(): number {
    return this.desk?.realizedWeek ?? 0;
  }

  get scopeLabel(): string {
    if (this.selectedDate) {
      return formatCentralDayLabel(this.selectedDate);
    }
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

  /** Share of settled cash put in that came back as profit or loss. */
  get returnRate(): number | null {
    const invested = this.displayTotals.invested;
    if (!invested) {
      return null;
    }
    return this.displayTotals.returned / invested;
  }

  get scopedCloses(): RhPredictCloseDto[] {
    const closes = this.desk?.closes ?? [];
    if (this.selectedDate) {
      return closes.filter((row) => centralDateKey(row.closedAt) === this.selectedDate);
    }
    const year = this.activeYear;
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
    const slot = 92;
    const padL = 68;
    const padR = 28;
    const padT = 44;
    const padB = 40;
    const width = padL + rows.length * slot + padR;
    const height = 264;
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
        runningUp: step.running >= 0,
        amount: this.signedUsd(step.row.realized),
        running: this.signedUsd(step.running),
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
    if (this.selectedDate) {
      const day = this.scopedMonths
        .flatMap((month) => month.days)
        .find((row) => row.key === dayKeyFromIso(this.selectedDate!));
      if (!day) {
        return [];
      }
      if (this.periodGrain === 'hour') {
        return day.hours.map((hour) => ({
          key: hour.key,
          label: hour.label,
          level: 'hour',
          totals: hour,
        }));
      }
      return [{ key: day.key, label: day.label, level: 'day', totals: day }];
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

  private loadDaily(): void {
    this.api.daily().subscribe({
      next: (days) => {
        this.dailyDays = days ?? [];
      },
      error: () => {
        /* Day strip is additive; desk still works without it. */
      },
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
    const day = todayCentral();
    forkJoin({
      btc: this.api.btc15(day),
      eth: this.api.eth15(day),
      doge: this.api.doge15(day),
      sol: this.api.sol15(day),
    }).subscribe({
      next: ({ btc, eth, doge, sol }) => {
        this.liveWindows = btc.hours.flatMap((hour) => hour.windows);
        this.ethWindows = eth.hours.flatMap((hour) => hour.windows);
        this.dogeWindows = doge.hours.flatMap((hour) => hour.windows);
        this.solWindows = sol.hours.flatMap((hour) => hour.windows);
        this.noteGapSlide();
        this.syncWindowStake();
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

  /**
   * When a new 15-minute period begins and open funding is still on the book,
   * bind that invested principal to this window and restart the countdown UI around it.
   */
  private syncWindowStake(): void {
    const desk = this.desk;
    if (desk) {
      this.syncEstimatedOpenCost(desk.openValue, desk.openCost, desk.closeCount);
    }
    const live = this.liveWindow;
    const mark = this.desk?.openValue;
    const principal = this.openCostBasis ?? mark;

    if (mark == null || mark <= 0 || principal == null || principal <= 0) {
      if (mark == null || mark <= 0) {
        this.activeStake = null;
        writeWindowStake(null);
      }
      return;
    }

    if (!live) {
      return;
    }

    const prior = this.activeStake;
    const changed =
      prior == null ||
      prior.ticker !== live.ticker ||
      Math.abs(prior.principal - principal) > 0.005;
    if (!changed) {
      return;
    }
    this.activeStake = {
      ticker: live.ticker,
      windowStart: live.windowStart,
      windowEnd: live.windowEnd,
      label: live.label,
      principal,
      markAtBind: mark,
    };
    writeWindowStake(this.activeStake);
  }

  private noteGapSlide(): void {
    const gap = this.liveWindow?.currentGap ?? null;
    const prior = this.priorLiveGap;
    this.priorLiveGap = gap;
    if (prior == null || gap == null || gap === prior) {
      return;
    }
    this.gapSlide = gap > prior ? 'up' : 'down';
    if (this.gapFlashClear != null) {
      clearTimeout(this.gapFlashClear);
    }
    this.gapFlashClear = setTimeout(() => {
      this.gapSlide = null;
      this.gapFlashClear = null;
    }, 900);
  }

  private noteOpenMarkTrend(next: number | null | undefined): void {
    if (next == null) {
      return;
    }
    const prior = this.priorOpenValue;
    this.priorOpenValue = next;
    if (prior == null || next === prior) {
      return;
    }
    this.openMarkDelta = next - prior;
    this.openMarkTrend = next > prior ? 'up' : 'down';
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
    this.syncEstimatedOpenCost(desk.openValue, desk.openCost, desk.closeCount);
    if (desk.openValue != null) {
      this.priorOpenValue = desk.openValue;
    }
    this.noteContractValueAsOf(desk);
    this.desk = desk;
    this.syncWindowStake();
    this.loadDaily();
  }

  /** Patch numbers in place on quiet refresh so layout/scroll stay put. */
  private applyQuiet(desk: RhPredictDeskDto, quiet: boolean): void {
    if (!quiet || this.desk == null) {
      this.apply(desk);
      return;
    }
    const current = this.desk;
    const priorCloses = current.closeCount;
    this.noteOpenMarkTrend(desk.openValue);
    this.syncEstimatedOpenCost(desk.openValue, desk.openCost, desk.closeCount);
    current.openValue = desk.openValue;
    current.openCost = desk.openCost;
    this.noteContractValueAsOf(desk);
    current.realizedWeek = desk.realizedWeek;
    current.realizedAll = desk.realizedAll;
    current.closeCount = desk.closeCount;
    // Leave lastSyncedAt alone so the header does not tick every auto-pull.
    current.warnings = desk.warnings ?? [];
    current.closes = desk.closes ?? [];
    this.syncWindowStake();
    if (quiet && desk.closeCount > priorCloses) {
      this.snackBar.open('Settlement landed — returns updated.', 'Dismiss', { duration: 3500 });
      this.loadDaily();
    }
  }

  /** Contract value is the Predict event-contract balance. Stamp each Robinhood read. */
  private noteContractValueAsOf(desk: RhPredictDeskDto): void {
    this.contractValueAsOf = desk.lastSyncedAt ?? new Date().toISOString();
  }

  /**
   * Cost of the contracts still open when Robinhood does not send one.
   * Re-anchor when the 15-minute window changes or a close lands, so a settled
   * position is not still treated as cash in this window.
   */
  private syncEstimatedOpenCost(
    mark: number | null | undefined,
    reported: number | null | undefined,
    closeCount: number,
  ): void {
    if (reported != null) {
      this.estimatedOpenCost = null;
      return;
    }
    const ticker = this.liveWindow?.ticker ?? null;
    if (mark == null || mark <= 0) {
      this.estimatedOpenCost = null;
      this.estimateAnchorCloses = closeCount;
      this.estimateAnchorTicker = ticker;
      return;
    }
    const windowChanged = ticker != null && ticker !== this.estimateAnchorTicker;
    const settledSinceAnchor = this.estimateAnchorCloses >= 0 && closeCount > this.estimateAnchorCloses;
    if (this.estimatedOpenCost == null || windowChanged || settledSinceAnchor) {
      this.estimatedOpenCost = mark;
      this.estimateAnchorCloses = closeCount;
      this.estimateAnchorTicker = ticker;
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

function centralDateKey(iso: string): string {
  const when = new Date(iso);
  if (Number.isNaN(when.getTime())) {
    return '';
  }
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(when);
}

function formatCentralDayLabel(isoDate: string): string {
  const when = new Date(`${isoDate}T12:00:00`);
  if (Number.isNaN(when.getTime())) {
    return isoDate;
  }
  return when.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

/** Match PredictDayRollup.key from an ISO date (YYYY-MM-DD). */
function dayKeyFromIso(isoDate: string): string {
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return isoDate;
  }
  const [year, month, day] = parts;
  return `${year}-${month - 1}-${day}`;
}

function readWindowStake(): WindowStake | null {
  try {
    const raw = sessionStorage.getItem(WINDOW_STAKE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as WindowStake;
    if (!parsed?.ticker || !parsed.windowEnd || !(parsed.principal > 0)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function writeWindowStake(stake: WindowStake | null): void {
  try {
    if (stake == null) {
      sessionStorage.removeItem(WINDOW_STAKE_KEY);
      return;
    }
    sessionStorage.setItem(WINDOW_STAKE_KEY, JSON.stringify(stake));
  } catch {
    /* ignore quota / private mode */
  }
}

function activeQuarter(windows: Btc15WindowDto[], now: number): Btc15WindowDto | null {
  return (
    windows.find((row) => row.status === 'open' || (row.unsettled && inLiveWindow(row, now))) ??
    windows.find((row) => row.status === 'awaiting' || (row.unsettled && now >= new Date(row.windowEnd).getTime())) ??
    null
  );
}

function moveShare(row: Btc15WindowDto | null): number | null {
  if (row?.currentGap == null || row.target == null || !row.target) {
    return null;
  }
  return row.currentGap / row.target;
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
