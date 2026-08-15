import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
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
import { forkJoin } from 'rxjs';
import {
  RobinhoodCashIoAccountDto,
  RobinhoodCashIoCalendarDayDto,
  RobinhoodCashIoEntryDto,
  RobinhoodCashIoRequestDto,
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

  accounts: RobinhoodCashIoAccountDto[] = [];
  entries: RobinhoodCashIoEntryDto[] = [];
  calendarDays = new Map<string, RobinhoodCashIoCalendarDayDto>();

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

  refresh(): void {
    this.loading = true;
    const month = this.periodMode === 'month' ? this.month : null;
    const suffix = this.accountSuffix || null;
    forkJoin({
      ledger: this.api.ledger(this.year, month, suffix),
      calendar: this.api.calendar(this.year, month, suffix),
    }).subscribe({
      next: ({ ledger, calendar }) => {
        this.entries = ledger.entries;
        this.totalIn = ledger.totalIn;
        this.totalOut = ledger.totalOut;
        this.net = ledger.net;
        this.calendarDays = new Map(calendar.days.map((d) => [d.date, d]));
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastError(err);
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
        this.refresh();
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
        this.refresh();
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
