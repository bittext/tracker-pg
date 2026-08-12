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
  RobinhoodSelectiveTradeAiInsightDto,
  RobinhoodSelectiveTradeCalendarDayDto,
  RobinhoodSelectiveTradeEntryDto,
  RobinhoodSelectiveTradeRequestDto,
  RobinhoodSelectiveTradeStatsDto,
} from '../../../models/finance.models';
import { RobinhoodSelectiveTradeApiService } from '../../../services/robinhood-selective-trade-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type PeriodMode = 'month' | 'year';
type ViewMode = 'list' | 'calendar';

interface CalCell {
  trackKey: string;
  type: 'pad' | 'day';
  date?: string;
  label: string;
  dayMeta?: RobinhoodSelectiveTradeCalendarDayDto | null;
  isSelected: boolean;
  isToday: boolean;
  tone: 'none' | 'worked' | 'didnt' | 'mixed';
  heat: number;
  tooltip: string;
}

@Component({
  selector: 'app-robinhood-selective-trade-panel',
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
  templateUrl: './robinhood-selective-trade-panel.component.html',
  styleUrl: './robinhood-selective-trade-panel.component.scss',
})
export class RobinhoodSelectiveTradePanelComponent implements OnInit {
  private readonly api = inject(RobinhoodSelectiveTradeApiService);
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
  readonly displayedColumns = ['date', 'symbol', 'outcome', 'note', 'actions'] as const;

  entries: RobinhoodSelectiveTradeEntryDto[] = [];
  calendarDays = new Map<string, RobinhoodSelectiveTradeCalendarDayDto>();
  stats: RobinhoodSelectiveTradeStatsDto | null = null;

  periodMode: PeriodMode = 'month';
  viewMode: ViewMode = 'list';
  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  selectedDate: string | null = null;

  loading = false;
  saving = false;
  analyzing = false;
  aiEnabled = false;
  aiConfigured = false;
  aiInsight: RobinhoodSelectiveTradeAiInsightDto | null = null;
  editingId: number | null = null;

  form: RobinhoodSelectiveTradeRequestDto = this.emptyForm();

  ngOnInit(): void {
    this.api.aiStatus().subscribe({
      next: (s) => {
        this.aiEnabled = !!s.enabled;
        this.aiConfigured = !!s.configured;
      },
      error: () => {
        this.aiEnabled = false;
        this.aiConfigured = false;
      },
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

  get aiReady(): boolean {
    return this.aiEnabled && this.aiConfigured;
  }

  onPeriodModeChange(mode: PeriodMode): void {
    this.periodMode = mode;
    this.selectedDate = null;
    this.aiInsight = null;
    this.refresh();
  }

  onFiltersChange(): void {
    this.selectedDate = null;
    this.aiInsight = null;
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    const month = this.periodMode === 'month' ? this.month : null;
    forkJoin({
      ledger: this.api.ledger(this.year, month),
      calendar: this.api.calendar(this.year, month),
    }).subscribe({
      next: ({ ledger, calendar }) => {
        this.entries = ledger.entries;
        this.stats = ledger.stats;
        this.calendarDays = new Map(calendar.days.map((d) => [d.date, d]));
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastError(err);
      },
    });
  }

  runAiAnalyze(): void {
    if (!this.aiReady) {
      this.snackBar.open('AI is not configured on the server.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.analyzing = true;
    const month = this.periodMode === 'month' ? this.month : null;
    this.api.aiAnalyze(this.year, month).subscribe({
      next: (insight) => {
        this.aiInsight = insight;
        this.analyzing = false;
      },
      error: (err) => {
        this.analyzing = false;
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
      cells.push(this.padCell(`pad-${month}-${i}`));
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${this.year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const meta = this.calendarDays.get(date) ?? null;
      cells.push({
        trackKey: date,
        type: 'day',
        date,
        label: String(d),
        dayMeta: meta,
        isSelected: this.selectedDate === date,
        isToday: date === today,
        tone: this.toneFor(meta),
        heat: this.heatFor(meta),
        tooltip: this.tooltipFor(date, meta),
      });
    }
    while (cells.length % 7 !== 0) {
      cells.push(this.padCell(`pad-end-${month}-${cells.length}`));
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
      }
    }
  }

  filteredEntries(): RobinhoodSelectiveTradeEntryDto[] {
    if (!this.selectedDate) {
      return this.entries;
    }
    return this.entries.filter((e) => e.activityDate === this.selectedDate);
  }

  clearDayFilter(): void {
    this.selectedDate = null;
  }

  outcomeLabel(outcome: string): string {
    switch (outcome) {
      case 'WORKED':
        return 'Worked';
      case 'DIDNT':
        return "Didn't";
      case 'MIXED':
        return 'Mixed';
      default:
        return outcome;
    }
  }

  startEdit(entry: RobinhoodSelectiveTradeEntryDto): void {
    this.editingId = entry.id;
    this.form = {
      activityDate: entry.activityDate,
      symbol: entry.symbol ?? '',
      outcome: entry.outcome === 'DIDNT' || entry.outcome === 'MIXED' ? entry.outcome : 'WORKED',
      note: entry.note ?? '',
      accountSuffix: entry.accountSuffix ?? '',
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  save(): void {
    if (!this.form.activityDate) {
      this.snackBar.open('Date is required.', 'Dismiss', { duration: 3500 });
      return;
    }
    if (!this.form.outcome) {
      this.snackBar.open('Outcome is required.', 'Dismiss', { duration: 3500 });
      return;
    }
    const body: RobinhoodSelectiveTradeRequestDto = {
      activityDate: this.form.activityDate,
      symbol: this.form.symbol?.trim() || null,
      outcome: this.form.outcome,
      note: this.form.note?.trim() || null,
      accountSuffix: this.form.accountSuffix?.replace(/\D/g, '') || null,
    };
    this.saving = true;
    const wasEdit = this.editingId != null;
    const req$ = wasEdit ? this.api.update(this.editingId!, body) : this.api.create(body);
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.cancelEdit();
        this.aiInsight = null;
        this.refresh();
        this.snackBar.open(wasEdit ? 'Updated.' : 'Logged.', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.saving = false;
        this.toastError(err);
      },
    });
  }

  remove(entry: RobinhoodSelectiveTradeEntryDto): void {
    if (!confirm(`Delete ${entry.symbol || 'trade'} on ${entry.activityDate}?`)) {
      return;
    }
    this.api.delete(entry.id).subscribe({
      next: () => {
        if (this.editingId === entry.id) {
          this.cancelEdit();
        }
        this.aiInsight = null;
        this.refresh();
      },
      error: (err) => this.toastError(err),
    });
  }

  private emptyForm(): RobinhoodSelectiveTradeRequestDto {
    return {
      activityDate: this.isoToday(),
      symbol: '',
      outcome: 'WORKED',
      note: '',
      accountSuffix: '',
    };
  }

  private isoToday(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }

  private padCell(trackKey: string): CalCell {
    return {
      trackKey,
      type: 'pad',
      label: '',
      isSelected: false,
      isToday: false,
      tone: 'none',
      heat: 0,
      tooltip: '',
    };
  }

  private toneFor(meta: RobinhoodSelectiveTradeCalendarDayDto | null): CalCell['tone'] {
    if (!meta || meta.entryCount <= 0) {
      return 'none';
    }
    if (meta.worked && !meta.didnt && !meta.mixed) {
      return 'worked';
    }
    if (meta.didnt && !meta.worked && !meta.mixed) {
      return 'didnt';
    }
    return 'mixed';
  }

  private heatFor(meta: RobinhoodSelectiveTradeCalendarDayDto | null): number {
    if (!meta || meta.entryCount <= 0) {
      return 0;
    }
    return Math.max(24, Math.min(78, 20 + meta.entryCount * 18));
  }

  private tooltipFor(date: string, meta: RobinhoodSelectiveTradeCalendarDayDto | null): string {
    if (!meta || meta.entryCount <= 0) {
      return date;
    }
    return `${date}: ${meta.entryCount} · worked ${meta.worked} · didn't ${meta.didnt} · mixed ${meta.mixed}`;
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err) || 'Request failed', 'Dismiss', { duration: 5000 });
  }
}
