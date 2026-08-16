import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, forkJoin, fromEvent, interval, merge } from 'rxjs';
import {
  RobinhoodCashIoAccountDto,
  RobinhoodCashIoCalendarDayDto,
  RobinhoodCashIoEntryDto,
  RobinhoodCashIoRequestDto,
  RobinhoodCashIoYtdDto,
  RobinhoodCashIoYtdEventDto,
} from '../../../models/finance.models';
import { RobinhoodCashIoApiService } from '../../../services/robinhood-cash-io-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type PeriodMode = 'month' | 'year';
type ViewMode = 'list' | 'calendar' | 'picture';

interface AccountBreakdown {
  suffix: string;
  label: string;
  totalIn: number;
  totalOut: number;
  net: number;
  count: number;
}

interface MonthBucket {
  month: number;
  label: string;
  totalIn: number;
  totalOut: number;
  net: number;
  count: number;
}

interface TimelineItem {
  entry: RobinhoodCashIoEntryDto;
  runningNet: number;
}

interface FlowBar {
  x: number;
  y: number;
  h: number;
  w: number;
  kind: string;
  date: string;
  amount: number;
  note: string;
  running: number;
  title: string;
  callout: string | null;
}

interface AdjPt {
  x: number;
  y: number;
  date: string;
  value: number;
  kind: string;
  amount: number;
  marker: boolean;
  title: string;
}

interface PlotTick {
  pos: number;
  label: string;
}

interface DayTab {
  date: string;
  dateLabel: string;
  opening: number;
  inputs: number;
  outputs: number;
  credits: number;
  debits: number;
  now: number;
  events: RobinhoodCashIoYtdEventDto[];
}

interface CalCell {
  trackKey: string;
  type: 'pad' | 'day';
  date?: string;
  label: string;
  dayMeta?: RobinhoodCashIoCalendarDayDto | null;
  isSelected: boolean;
  isToday: boolean;
  tone: 'none' | 'in' | 'out' | 'mixed';
  heat: number;
  tooltip: string;
}

@Component({
  selector: 'app-robinhood-cash-io-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './robinhood-cash-io-panel.component.html',
  styleUrl: './robinhood-cash-io-panel.component.scss',
})
export class RobinhoodCashIoPanelComponent implements OnInit {
  private readonly api = inject(RobinhoodCashIoApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private refreshInFlight = false;

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  readonly monthOptions = [
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
  readonly displayedColumns = ['date', 'account', 'direction', 'amount', 'note', 'actions'] as const;
  readonly ytdSuffix = '3370';

  accounts: RobinhoodCashIoAccountDto[] = [];
  entries: RobinhoodCashIoEntryDto[] = [];
  calendarDays = new Map<string, RobinhoodCashIoCalendarDayDto>();
  ytd: RobinhoodCashIoYtdDto | null = null;
  ytdFocus: RobinhoodCashIoYtdEventDto | null = null;
  ytdAsOf: Date | null = null;

  periodMode: PeriodMode = 'month';
  viewMode: ViewMode = 'list';
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  accountSuffix = '';
  selectedDate: string | null = null;

  totalIn = 0;
  totalOut = 0;
  net = 0;

  loading = false;
  saving = false;
  editingId: number | null = null;

  form: RobinhoodCashIoRequestDto = this.emptyForm();
  customSuffix = '';

  ngOnInit(): void {
    this.api.accounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        if (!this.form.accountSuffix && accounts.length) {
          this.form.accountSuffix = accounts[0].suffix;
        }
      },
      error: (err) => this.toastError(err),
    });
    this.refresh();
    merge(
      interval(45_000),
      fromEvent(document, 'visibilitychange').pipe(filter(() => document.visibilityState === 'visible')),
    )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        filter(() => !this.loading && !this.saving && !this.refreshInFlight),
      )
      .subscribe(() => this.refresh({ silent: true }));
  }

  get yearOptions(): number[] {
    const y = new Date().getFullYear();
    const out: number[] = [];
    for (let i = y + 1; i >= y - 8; i--) {
      out.push(i);
    }
    return out;
  }

  get calendarTitle(): string {
    if (this.periodMode === 'year') {
      return String(this.year);
    }
    const m = this.monthOptions.find((o) => o.value === this.month)?.label ?? '';
    return `${m} ${this.year}`;
  }

  onPeriodModeChange(mode: PeriodMode): void {
    this.periodMode = mode;
    this.selectedDate = null;
    this.refresh();
  }

  onFiltersChange(): void {
    this.selectedDate = null;
    this.refresh();
  }

  refresh(opts?: { silent?: boolean }): void {
    const silent = !!opts?.silent;
    if (this.refreshInFlight && silent) {
      return;
    }
    this.refreshInFlight = true;
    if (!silent) {
      this.loading = true;
    }
    const month = this.periodMode === 'month' ? this.month : null;
    const suffix = this.accountSuffix || null;
    const ytdYear = new Date().getFullYear();
    const prevFocus = this.ytdFocus;
    forkJoin({
      ledger: this.api.ledger(this.year, month, suffix),
      calendar: this.api.calendar(this.year, month, suffix),
      ytd: this.api.ytd(ytdYear, this.ytdSuffix),
    }).subscribe({
      next: ({ ledger, calendar, ytd }) => {
        this.entries = ledger.entries;
        this.totalIn = ledger.totalIn;
        this.totalOut = ledger.totalOut;
        this.net = ledger.net;
        this.calendarDays = new Map(calendar.days.map((d) => [d.date, d]));
        this.ytd = ytd;
        this.ytdAsOf = new Date();
        if (silent && prevFocus) {
          this.ytdFocus =
            ytd.events.find(
              (e) =>
                e.date === prevFocus.date && e.kind === prevFocus.kind && e.amount === prevFocus.amount,
            ) ?? null;
        } else if (!silent) {
          this.ytdFocus = null;
        }
        this.loading = false;
        this.refreshInFlight = false;
      },
      error: (err) => {
        this.loading = false;
        this.refreshInFlight = false;
        if (!silent) {
          this.toastError(err);
        }
      },
    });
  }

  calendarRowsFor(month: number): CalCell[][] {
    const first = new Date(this.year, month - 1, 1);
    const daysInMonth = new Date(this.year, month, 0).getDate();
    const startPad = first.getDay();
    const today = this.isoToday();
    const cells: CalCell[] = [];
    for (let i = 0; i < startPad; i++) {
      cells.push({
        trackKey: `pad-${month}-${i}`,
        type: 'pad',
        label: '',
        isSelected: false,
        isToday: false,
        tone: 'none',
        heat: 0,
        tooltip: '',
      });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${this.year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const meta = this.calendarDays.get(date) ?? null;
      const tone = this.toneFor(meta);
      cells.push({
        trackKey: date,
        type: 'day',
        date,
        label: String(d),
        dayMeta: meta,
        isSelected: this.selectedDate === date,
        isToday: date === today,
        tone,
        heat: this.heatFor(meta),
        tooltip: this.tooltipFor(date, meta),
      });
    }
    while (cells.length % 7 !== 0) {
      cells.push({
        trackKey: `pad-end-${month}-${cells.length}`,
        type: 'pad',
        label: '',
        isSelected: false,
        isToday: false,
        tone: 'none',
        heat: 0,
        tooltip: '',
      });
    }
    const rows: CalCell[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      rows.push(cells.slice(i, i + 7));
    }
    return rows;
  }

  get monthCalendarRows(): CalCell[][] {
    return this.calendarRowsFor(this.month);
  }

  yearMonths(): number[] {
    return [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
  }

  monthLabel(m: number): string {
    return this.monthOptions.find((o) => o.value === m)?.label ?? String(m);
  }

  selectDay(date: string): void {
    this.selectedDate = date;
    this.form.activityDate = date;
    if (this.periodMode === 'year') {
      const m = Number(date.slice(5, 7));
      if (m >= 1 && m <= 12) {
        this.periodMode = 'month';
        this.month = m;
        this.viewMode = 'list';
        this.refresh();
        return;
      }
    }
  }

  selectMonth(month: number): void {
    this.periodMode = 'month';
    this.month = month;
    this.selectedDate = null;
    this.refresh();
  }

  shiftCalendar(delta: number): void {
    if (!this.canShiftCalendar(delta)) {
      return;
    }
    if (this.periodMode === 'year') {
      this.year += delta;
    } else {
      const next = new Date(this.year, this.month - 1 + delta, 1);
      this.year = next.getFullYear();
      this.month = next.getMonth() + 1;
    }
    this.selectedDate = null;
    this.refresh();
  }

  canShiftCalendar(delta: number): boolean {
    const years = this.yearOptions;
    const minY = years[years.length - 1];
    const maxY = years[0];
    if (this.periodMode === 'year') {
      const y = this.year + delta;
      return y >= minY && y <= maxY;
    }
    const next = new Date(this.year, this.month - 1 + delta, 1);
    const y = next.getFullYear();
    return y >= minY && y <= maxY;
  }

  filteredEntries(): RobinhoodCashIoEntryDto[] {
    if (!this.selectedDate) {
      return this.entries;
    }
    return this.entries.filter((e) => e.activityDate === this.selectedDate);
  }

  /** Share of cash movement that is input (0–100). */
  get inSharePct(): number {
    const t = this.totalIn + this.totalOut;
    return t <= 0 ? 50 : (this.totalIn / t) * 100;
  }

  get outSharePct(): number {
    return 100 - this.inSharePct;
  }

  accountBreakdown(): AccountBreakdown[] {
    const map = new Map<string, AccountBreakdown>();
    for (const e of this.filteredEntries()) {
      const cur = map.get(e.accountSuffix) ?? {
        suffix: e.accountSuffix,
        label: e.accountLabel || e.accountSuffix,
        totalIn: 0,
        totalOut: 0,
        net: 0,
        count: 0,
      };
      if (e.direction === 'OUT') {
        cur.totalOut += e.amount;
      } else {
        cur.totalIn += e.amount;
      }
      cur.net = cur.totalIn - cur.totalOut;
      cur.count += 1;
      map.set(e.accountSuffix, cur);
    }
    return [...map.values()].sort((a, b) => Math.abs(b.net) - Math.abs(a.net) || b.count - a.count);
  }

  monthBuckets(): MonthBucket[] {
    const buckets = this.monthOptions.map((m) => ({
      month: m.value,
      label: m.label.slice(0, 3),
      totalIn: 0,
      totalOut: 0,
      net: 0,
      count: 0,
    }));
    for (const e of this.filteredEntries()) {
      const mo = Number(e.activityDate.slice(5, 7));
      if (mo < 1 || mo > 12) {
        continue;
      }
      const b = buckets[mo - 1];
      if (e.direction === 'OUT') {
        b.totalOut += e.amount;
      } else {
        b.totalIn += e.amount;
      }
      b.net = b.totalIn - b.totalOut;
      b.count += 1;
    }
    return buckets;
  }

  monthBarMax(): number {
    return Math.max(1, ...this.monthBuckets().map((b) => b.totalIn + b.totalOut));
  }

  accountBarMax(): number {
    return Math.max(1, ...this.accountBreakdown().map((a) => Math.max(a.totalIn, a.totalOut)));
  }

  timelineItems(): TimelineItem[] {
    const sorted = [...this.filteredEntries()].sort((a, b) => {
      const d = a.activityDate.localeCompare(b.activityDate);
      if (d !== 0) {
        return d;
      }
      return a.id - b.id;
    });
    let running = 0;
    return sorted.map((entry) => {
      running += entry.direction === 'OUT' ? -entry.amount : entry.amount;
      return { entry, runningNet: running };
    });
  }

  ytdFlowBars(): FlowBar[] {
    const ytd = this.ytd;
    const events = (ytd?.events ?? []).filter((e) => e.kind !== 'START');
    if (!ytd || !events.length) {
      return [];
    }
    const max = Math.max(1, ...events.map((e) => Math.abs(e.amount)));
    const range = this.ytdPlotRange();
    const byDate = new Map<string, number>();
    const notable = new Set(
      [...events]
        .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
        .slice(0, 6)
        .map((e) => `${e.date}|${e.kind}|${e.amount}`),
    );
    return events.map((e) => {
      const slot = byDate.get(e.date) ?? 0;
      byDate.set(e.date, slot + 1);
      const credit = e.kind === 'INPUT' || e.kind === 'CREDIT';
      const h = Math.max(4, (Math.abs(e.amount) / max) * 40);
      const x = this.xForTime(this.timeForDate(e.date), range) + slot * 1.15;
      const key = `${e.date}|${e.kind}|${e.amount}`;
      return {
        x,
        y: credit ? 48 - h : 48,
        h,
        w: 1.7,
        kind: e.kind,
        date: e.date,
        amount: e.amount,
        note: e.note ?? '',
        running: e.runningAdjusted,
        title: this.ytdEventTitle(e),
        callout: notable.has(key) ? `${this.shortDate(e.date)} ${this.fmtCompact(e.amount)}` : null,
      };
    });
  }

  ytdAdjPoints(): AdjPt[] {
    const ytd = this.ytd;
    const events = ytd?.events ?? [];
    if (!ytd || !events.length) {
      return [];
    }
    const vals = events.map((e) => e.runningAdjusted);
    const min = Math.min(0, ...vals);
    const max = Math.max(...vals);
    const span = Math.max(1, max - min);
    const range = this.ytdPlotRange();
    return events.map((e) => ({
      x: this.xForTime(this.timeForDate(e.date), range),
      y: 86 - ((e.runningAdjusted - min) / span) * 72,
      date: e.date,
      value: e.runningAdjusted,
      kind: e.kind,
      amount: e.amount,
      marker: e.kind === 'START' || e.kind === 'INPUT' || e.kind === 'OUTPUT',
      title: `${this.ytdEventTitle(e)} · running ${this.fmtUsd(e.runningAdjusted)}`,
    }));
  }

  ytdAdjEnd(): AdjPt | null {
    const pts = this.ytdAdjPoints();
    return pts.length ? pts[pts.length - 1] : null;
  }

  ytdAdjStart(): AdjPt | null {
    const pts = this.ytdAdjPoints();
    return pts.length ? pts[0] : null;
  }

  ytdAdjPath(): string {
    const pts = this.ytdAdjPoints();
    if (!pts.length) {
      return '';
    }
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ');
  }

  ytdMonthTicks(): PlotTick[] {
    const ytd = this.ytd;
    if (!ytd) {
      return [];
    }
    const range = this.ytdPlotRange();
    const start = new Date(`${ytd.startDate}T00:00:00`);
    const end = new Date(range.to);
    const ticks: PlotTick[] = [];
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    while (cursor.getTime() <= end.getTime()) {
      const t = cursor.getTime();
      if (t >= range.from) {
        ticks.push({
          pos: this.xForTime(t, range),
          label: cursor.toLocaleDateString('en-US', { month: 'short' }),
        });
      }
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return ticks;
  }

  ytdFlowYTicks(): PlotTick[] {
    const events = (this.ytd?.events ?? []).filter((e) => e.kind !== 'START');
    const max = Math.max(1000, ...events.map((e) => Math.abs(e.amount)));
    const top = this.niceCeil(max);
    return [
      { pos: 48 - 40, label: '+' + this.fmtCompact(top) },
      { pos: 48, label: '$0' },
      { pos: 48 + 40, label: '−' + this.fmtCompact(top) },
    ];
  }

  ytdAdjYTicks(): PlotTick[] {
    const vals = (this.ytd?.events ?? []).map((e) => e.runningAdjusted);
    if (!vals.length) {
      return [];
    }
    const min = Math.min(0, ...vals);
    const max = Math.max(...vals);
    const span = Math.max(1, max - min);
    const ticks = [min, min + span / 2, max];
    return ticks.map((v) => ({
      pos: 86 - ((v - min) / span) * 72,
      label: this.fmtCompact(v),
    }));
  }

  ytdNotable(): RobinhoodCashIoYtdEventDto[] {
    return [...(this.ytd?.events ?? [])]
      .filter((e) => e.kind === 'INPUT' || e.kind === 'OUTPUT')
      .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount))
      .slice(0, 8);
  }

  todayTab(): DayTab | null {
    const ytd = this.ytd;
    if (!ytd) {
      return null;
    }
    const date = this.isoToday();
    const dateLabel = new Date(`${date}T00:00:00`).toLocaleDateString('en-US', {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
    const todayEvents = ytd.events.filter((e) => e.date === date && e.kind !== 'START');
    const prior = [...ytd.events].reverse().find((e) => e.date < date);
    const opening = prior ? prior.runningAdjusted : ytd.startingCash;
    let inputs = 0;
    let outputs = 0;
    let credits = 0;
    let debits = 0;
    for (const e of todayEvents) {
      if (e.kind === 'INPUT') {
        inputs += e.amount;
      } else if (e.kind === 'OUTPUT') {
        outputs += e.amount;
      } else if (e.kind === 'CREDIT') {
        credits += e.amount;
      } else if (e.kind === 'DEBIT') {
        debits += e.amount;
      }
    }
    const now = todayEvents.length ? todayEvents[todayEvents.length - 1].runningAdjusted : opening;
    return { date, dateLabel, opening, inputs, outputs, credits, debits, now, events: todayEvents };
  }

  prepareTodayEntry(direction: 'IN' | 'OUT'): void {
    this.cancelEdit();
    this.form.activityDate = this.isoToday();
    this.form.direction = direction;
    this.form.accountSuffix = this.ytdSuffix;
    this.customSuffix = '';
    const n = new Date();
    this.year = n.getFullYear();
    this.month = n.getMonth() + 1;
    this.periodMode = 'month';
    this.refresh({ silent: true });
  }

  focusYtdEvent(event: RobinhoodCashIoYtdEventDto): void {
    this.ytdFocus = event;
  }

  focusYtdBar(bar: FlowBar): void {
    const match = (this.ytd?.events ?? []).find(
      (e) => e.date === bar.date && e.kind === bar.kind && e.amount === bar.amount,
    );
    this.ytdFocus = match ?? null;
  }

  focusYtdPoint(pt: AdjPt): void {
    const match = (this.ytd?.events ?? []).find(
      (e) => e.date === pt.date && e.kind === pt.kind && e.runningAdjusted === pt.value,
    );
    this.ytdFocus = match ?? null;
  }

  isYtdFocused(date: string, kind: string, amount: number): boolean {
    const f = this.ytdFocus;
    return !!f && f.date === date && f.kind === kind && f.amount === amount;
  }

  ytdKindLabel(e: { kind: string }): string {
    switch (e.kind) {
      case 'START':
        return 'Start';
      case 'INPUT':
        return 'Input (deposit / transfer in)';
      case 'OUTPUT':
        return 'Output (withdrawal / transfer out)';
      case 'CREDIT':
        return 'Interest credit';
      case 'DEBIT':
        return 'Margin interest debit';
      default:
        return e.kind;
    }
  }

  ytdKindShort(kind: string): string {
    switch (kind) {
      case 'START':
        return 'Start';
      case 'INPUT':
        return 'Input';
      case 'OUTPUT':
        return 'Output';
      case 'CREDIT':
        return 'Interest';
      case 'DEBIT':
        return 'Margin';
      default:
        return kind;
    }
  }

  private ytdEventTitle(e: RobinhoodCashIoYtdEventDto): string {
    const note = e.note ? ` · ${e.note}` : '';
    return `${this.mediumDate(e.date)} · ${this.ytdKindShort(e.kind)} ${this.fmtUsd(e.amount)}${note}`;
  }

  private ytdPlotRange(): { from: number; to: number } {
    const ytd = this.ytd;
    if (!ytd) {
      return { from: 0, to: 1 };
    }
    const times = [this.timeForDate(ytd.startDate), ...ytd.events.map((e) => this.timeForDate(e.date))];
    const from = Math.min(...times);
    const to = Math.max(...times);
    return { from, to: to > from ? to : from + 1 };
  }

  private timeForDate(iso: string): number {
    return new Date(`${iso}T00:00:00`).getTime();
  }

  private xForTime(t: number, range: { from: number; to: number }): number {
    return 5 + ((t - range.from) / (range.to - range.from)) * 90;
  }

  shortDate(iso: string): string {
    const d = new Date(`${iso}T00:00:00`);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  private mediumDate(iso: string): string {
    const d = new Date(`${iso}T00:00:00`);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  private niceCeil(n: number): number {
    if (n <= 1000) {
      return 1000;
    }
    const exp = 10 ** Math.floor(Math.log10(n));
    return Math.ceil(n / exp) * exp;
  }

  private fmtUsd(n: number): string {
    return n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }

  private fmtCompact(n: number): string {
    const abs = Math.abs(n);
    const sign = n < 0 ? '−' : '';
    if (abs >= 1000) {
      const k = abs / 1000;
      return `${sign}$${k >= 10 ? k.toFixed(0) : k.toFixed(1)}k`;
    }
    return `${sign}$${abs.toFixed(0)}`;
  }

  clearDayFilter(): void {
    this.selectedDate = null;
  }

  startEdit(entry: RobinhoodCashIoEntryDto): void {
    this.editingId = entry.id;
    this.form = {
      accountSuffix: entry.accountSuffix,
      activityDate: entry.activityDate,
      direction: entry.direction === 'OUT' ? 'OUT' : 'IN',
      amount: entry.amount,
      note: entry.note ?? '',
    };
    this.customSuffix = '';
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    if (this.accounts.length && !this.form.accountSuffix) {
      this.form.accountSuffix = this.accounts[0].suffix;
    }
  }

  save(): void {
    const suffix = (this.customSuffix || this.form.accountSuffix || '').replace(/\D/g, '');
    if (!suffix) {
      this.snackBar.open('Pick or enter an account suffix.', 'Dismiss', { duration: 3500 });
      return;
    }
    if (!this.form.activityDate) {
      this.snackBar.open('Date is required.', 'Dismiss', { duration: 3500 });
      return;
    }
    const amount = Number(this.form.amount);
    if (!(amount > 0)) {
      this.snackBar.open('Amount must be greater than zero.', 'Dismiss', { duration: 3500 });
      return;
    }
    const body: RobinhoodCashIoRequestDto = {
      accountSuffix: suffix,
      activityDate: this.form.activityDate,
      direction: this.form.direction,
      amount,
      note: this.form.note?.trim() || null,
    };
    this.saving = true;
    const req$ =
      this.editingId != null ? this.api.update(this.editingId, body) : this.api.create(body);
    const wasEdit = this.editingId != null;
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.cancelEdit();
        this.refresh({ silent: true });
        this.snackBar.open(wasEdit ? 'Updated.' : 'Saved.', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.saving = false;
        this.toastError(err);
      },
    });
  }

  remove(entry: RobinhoodCashIoEntryDto): void {
    if (!confirm(`Delete ${entry.direction} $${entry.amount} on ${entry.activityDate}?`)) {
      return;
    }
    this.api.delete(entry.id).subscribe({
      next: () => {
        if (this.editingId === entry.id) {
          this.cancelEdit();
        }
        this.refresh({ silent: true });
      },
      error: (err) => this.toastError(err),
    });
  }

  private emptyForm(): RobinhoodCashIoRequestDto {
    return {
      accountSuffix: '',
      activityDate: this.isoToday(),
      direction: 'IN',
      amount: 0,
      note: '',
    };
  }

  private isoToday(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }

  private toneFor(meta: RobinhoodCashIoCalendarDayDto | null): 'none' | 'in' | 'out' | 'mixed' {
    if (!meta || meta.entryCount <= 0) {
      return 'none';
    }
    const hasIn = meta.totalIn > 0;
    const hasOut = meta.totalOut > 0;
    if (hasIn && hasOut) {
      return 'mixed';
    }
    if (hasOut) {
      return 'out';
    }
    if (hasIn) {
      return 'in';
    }
    return 'none';
  }

  private heatFor(meta: RobinhoodCashIoCalendarDayDto | null): number {
    if (!meta || meta.entryCount <= 0) {
      return 0;
    }
    const mag = Math.abs(meta.net);
    return Math.max(22, Math.min(72, 22 + Math.log10(mag + 1) * 18));
  }

  private tooltipFor(date: string, meta: RobinhoodCashIoCalendarDayDto | null): string {
    if (!meta || meta.entryCount <= 0) {
      return date;
    }
    return `${date}: in $${meta.totalIn.toFixed(2)} · out $${meta.totalOut.toFixed(2)} · net $${meta.net.toFixed(2)} (${meta.entryCount})`;
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err) || 'Request failed', 'Dismiss', { duration: 5000 });
  }
}
