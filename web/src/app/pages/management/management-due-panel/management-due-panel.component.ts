import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementDueDayDto,
  ManagementDueItemWriteBody,
  ManagementDueMonthDto,
  ManagementDueOccurrenceDto,
  ManagementDueSide,
  ManagementDueSuggestionDto,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface DueCalCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  day?: ManagementDueDayDto;
  trackKey: string;
}

@Component({
  selector: 'app-management-due-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
  ],
  templateUrl: './management-due-panel.component.html',
  styleUrl: './management-due-panel.component.scss',
})
export class ManagementDuePanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  selectedIso = '';
  monthData: ManagementDueMonthDto | null = null;
  calRows: DueCalCell[][] = [];
  loading = false;
  saving = false;
  editingItemId: number | null = null;

  draft = this.emptyDraft();

  private static readonly CLEAR_LATER_KEY = 'tracker.due.clearedLater.v1';

  ngOnInit(): void {
    const today = this.todayIso();
    this.selectedIso = today;
    this.rebuildCalendar();
    this.refreshAll();
  }

  refreshAll(): void {
    this.maybeClearLaterDates();
  }

  get monthlySuggestions(): ManagementDueSuggestionDto[] {
    return this.suggestions.filter((row) => (row.kind || 'MONTHLY') !== 'BIG_DEBIT');
  }

  get bigDebitSuggestions(): ManagementDueSuggestionDto[] {
    return this.suggestions.filter((row) => row.kind === 'BIG_DEBIT');
  }

  get calendarTitle(): string {
    return new Date(this.year, this.month - 1, 1).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  get selectedDay(): ManagementDueDayDto | null {
    if (!this.monthDataMatchesView() || !this.selectedIso) {
      return null;
    }
    return this.monthData!.days.find((d) => this.dayDate(d) === this.selectedIso) ?? null;
  }

  get suggestions(): ManagementDueSuggestionDto[] {
    return this.monthData?.suggestions ?? [];
  }

  prevMonth(): void {
    if (this.month === 1) {
      this.loadMonth(this.year - 1, 12);
    } else {
      this.loadMonth(this.year, this.month - 1);
    }
  }

  nextMonth(): void {
    if (this.month === 12) {
      this.loadMonth(this.year + 1, 1);
    } else {
      this.loadMonth(this.year, this.month + 1);
    }
  }

  selectDay(iso: string): void {
    this.selectedIso = iso;
    if (!this.editingItemId) {
      this.draft.oneOffDate = iso;
      if (!this.draft.recurring) {
        this.draft.dayOfMonth = Number(iso.slice(8, 10));
      }
    }
  }

  startCreate(): void {
    this.editingItemId = null;
    this.draft = this.emptyDraft();
    this.draft.oneOffDate = this.selectedIso || this.todayIso();
    this.draft.dayOfMonth = Number((this.draft.oneOffDate || '01').slice(8, 10));
  }

  startEdit(row: ManagementDueOccurrenceDto): void {
    this.editingItemId = row.itemId;
    this.draft = {
      side: row.side,
      counterparty: row.counterparty,
      recurring: row.recurring,
      dayOfMonth: row.dayOfMonth ?? Number(row.occurrenceDate.slice(8, 10)),
      oneOffDate: row.oneOffDate ?? row.occurrenceDate,
      amount: row.amountOverride == null ? '' : String(row.amountOverride),
      notes: row.notes || '',
    };
  }

  cancelEdit(): void {
    this.editingItemId = null;
    this.draft = this.emptyDraft();
  }

  saveItem(): void {
    const counterparty = this.draft.counterparty.trim();
    if (!counterparty) {
      this.snackBar.open('Name the business', undefined, { duration: 2200 });
      return;
    }
    const amount = this.parseAmount(this.draft.amount);
    const body: ManagementDueItemWriteBody = {
      side: this.draft.side,
      counterparty,
      recurring: this.draft.recurring,
      dayOfMonth: this.draft.recurring ? this.draft.dayOfMonth : null,
      oneOffDate: this.draft.recurring ? null : this.draft.oneOffDate || this.selectedIso,
      amountOverride: amount,
      notes: this.draft.notes.trim(),
      startYear: this.year,
      startMonth: this.month,
    };
    this.saving = true;
    const wasUpdate = this.editingItemId != null;
    const req = wasUpdate
      ? this.api.updateDueItem(this.editingItemId as number, body)
      : this.api.createDueItem(body);
    req.subscribe({
      next: (month) => {
        this.saving = false;
        this.editingItemId = null;
        this.draft = this.emptyDraft();
        this.applyMonth(month);
        this.snackBar.open(wasUpdate ? 'Updated' : 'Added', undefined, { duration: 1600 });
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not save', undefined, { duration: 3200 });
      },
    });
  }

  addSuggestion(row: ManagementDueSuggestionDto): void {
    this.editingItemId = null;
    const lastDay = new Date(this.year, this.month, 0).getDate();
    const day = Math.min(Math.max(row.typicalDay || 1, 1), lastDay);
    const oneOff = this.isoFor(this.year, this.month, day);
    const monthly = row.kind !== 'BIG_DEBIT';
    this.draft = {
      side: row.side,
      counterparty: row.counterparty,
      recurring: monthly,
      dayOfMonth: day,
      oneOffDate: oneOff,
      amount: row.estimatedAmount == null ? '' : String(row.estimatedAmount),
      notes: '',
    };
    this.selectedIso = oneOff;
  }

  settle(row: ManagementDueOccurrenceDto, settled: boolean): void {
    this.saving = true;
    this.api
      .settleDueItem(row.itemId, {
        year: this.year,
        month: this.month,
        settled,
        settledAmount: settled ? row.displayAmount : null,
      })
      .subscribe({
        next: (month) => {
          this.saving = false;
          this.applyMonth(month);
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err) || 'Could not update', undefined, { duration: 3200 });
        },
      });
  }

  deleteItem(row: ManagementDueOccurrenceDto): void {
    if (typeof window !== 'undefined' && !window.confirm(`Remove “${row.counterparty}”?`)) {
      return;
    }
    this.saving = true;
    this.api.deleteDueItem(row.itemId, this.year, this.month).subscribe({
      next: (month) => {
        this.saving = false;
        if (this.editingItemId === row.itemId) {
          this.cancelEdit();
        }
        this.applyMonth(month);
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not delete', undefined, { duration: 3200 });
      },
    });
  }

  money(value: number | null | undefined): string {
    if (value == null || Number.isNaN(Number(value))) {
      return '—';
    }
    return Number(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }

  signedMoney(row: ManagementDueOccurrenceDto): string {
    const n = row.displayAmount;
    if (n == null) {
      return 'est. —';
    }
    const prefix = row.side === 'PAYABLE' ? '−' : '+';
    return `${prefix}${this.money(n)}`;
  }

  calendarAmount(row: ManagementDueOccurrenceDto): string {
    const n = row.displayAmount;
    if (n == null) {
      return '—';
    }
    const prefix = row.side === 'PAYABLE' ? '−' : '+';
    return `${prefix}${this.compactMoney(n)}`;
  }

  dayNet(day: ManagementDueDayDto | undefined): string {
    if (!day || (day.payableCount === 0 && day.receivableCount === 0)) {
      return '';
    }
    const net = Number(day.receivableTotal || 0) - Number(day.payableTotal || 0);
    if (!net) {
      return this.compactMoney(0);
    }
    const prefix = net > 0 ? '+' : '−';
    return `${prefix}${this.compactMoney(Math.abs(net))}`;
  }

  private compactMoney(value: number): string {
    const abs = Math.abs(Number(value));
    const digits = Number.isInteger(abs) ? 0 : 2;
    return abs.toLocaleString('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: digits,
      maximumFractionDigits: 2,
    });
  }

  isToday(iso: string | undefined): boolean {
    return !!iso && iso === this.todayIso();
  }

  private maybeClearLaterDates(): void {
    const today = new Date();
    const isCurrentMonth = this.year === today.getFullYear() && this.month === today.getMonth() + 1;
    const alreadyCleared =
      typeof localStorage !== 'undefined' && localStorage.getItem(ManagementDuePanelComponent.CLEAR_LATER_KEY);
    if (!isCurrentMonth || alreadyCleared) {
      this.loadMonth();
      return;
    }
    this.loading = true;
    this.api.clearLaterDueItems(this.year, this.month).subscribe({
      next: (month) => {
        if (typeof localStorage !== 'undefined') {
          localStorage.setItem(ManagementDuePanelComponent.CLEAR_LATER_KEY, '1');
        }
        this.loading = false;
        this.applyMonth(month);
      },
      error: () => {
        if (typeof localStorage !== 'undefined') {
          localStorage.setItem(ManagementDuePanelComponent.CLEAR_LATER_KEY, '1');
        }
        this.loadMonth();
      },
    });
  }

  private loadMonth(year = this.year, month = this.month): void {
    this.loading = true;
    this.api.dueMonth(year, month).subscribe({
      next: (data) => {
        this.loading = false;
        this.applyMonth(data, year, month);
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load Due', undefined, { duration: 3200 });
      },
    });
  }

  private applyMonth(month: ManagementDueMonthDto | null, fallbackYear?: number, fallbackMonth?: number): void {
    const wantedYear = fallbackYear ?? this.year;
    const wantedMonth = fallbackMonth ?? this.month;
    if (!month || !Array.isArray(month.days)) {
      if (fallbackYear == null) {
        this.loadMonth(wantedYear, wantedMonth);
      }
      return;
    }
    if (month.year !== wantedYear || month.month !== wantedMonth) {
      if (fallbackYear == null) {
        this.loadMonth(wantedYear, wantedMonth);
      }
      return;
    }
    this.year = month.year;
    this.month = month.month;
    this.monthData = month;
    this.ensureSelectedInMonth();
    this.rebuildCalendar();
  }

  private monthDataMatchesView(): boolean {
    return !!this.monthData && this.monthData.year === this.year && this.monthData.month === this.month;
  }

  private rebuildCalendar(): void {
    const last = new Date(this.year, this.month, 0).getDate();
    const firstDow = new Date(this.year, this.month - 1, 1).getDay();
    const days = this.monthDataMatchesView() ? this.monthData!.days : [];
    const byDate = new Map(days.map((d) => [this.dayDate(d), d]));
    const flat: DueCalCell[] = [];
    let pad = 0;
    for (let i = 0; i < firstDow; i++) {
      pad += 1;
      flat.push({ type: 'pad', trackKey: `pad-${pad}` });
    }
    for (let d = 1; d <= last; d++) {
      const iso = this.isoFor(this.year, this.month, d);
      flat.push({
        type: 'day',
        iso,
        label: String(d),
        day: byDate.get(iso),
        trackKey: iso,
      });
    }
    const rows: DueCalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    while (rows.length && rows[rows.length - 1].length < 7) {
      pad += 1;
      rows[rows.length - 1].push({ type: 'pad', trackKey: `pad-tail-${pad}` });
    }
    this.calRows = rows;
  }

  private dayDate(day: ManagementDueDayDto): string {
    const raw = day?.date as unknown;
    return typeof raw === 'string' ? raw : raw != null ? String(raw) : '';
  }

  private ensureSelectedInMonth(): void {
    if (!this.selectedIso.startsWith(`${this.year}-${String(this.month).padStart(2, '0')}`)) {
      const today = this.todayIso();
      this.selectedIso = today.startsWith(`${this.year}-${String(this.month).padStart(2, '0')}`)
        ? today
        : this.isoFor(this.year, this.month, 1);
    }
  }

  private emptyDraft() {
    const iso = this.selectedIso || this.todayIso();
    return {
      side: 'PAYABLE' as ManagementDueSide,
      counterparty: '',
      recurring: true,
      dayOfMonth: Number(iso.slice(8, 10)) || 1,
      oneOffDate: iso,
      amount: '',
      notes: '',
    };
  }

  private parseAmount(raw: string): number | null {
    const text = (raw || '').trim();
    if (!text) {
      return null;
    }
    const n = Number(text.replace(/[$,]/g, ''));
    return Number.isFinite(n) ? n : null;
  }

  private isoFor(year: number, month: number, day: number): string {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }

  private todayIso(): string {
    const now = new Date();
    return this.isoFor(now.getFullYear(), now.getMonth() + 1, now.getDate());
  }
}
