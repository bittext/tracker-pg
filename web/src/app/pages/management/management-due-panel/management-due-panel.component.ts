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
  loading = false;
  saving = false;
  editingItemId: number | null = null;

  draft = this.emptyDraft();

  ngOnInit(): void {
    const today = this.todayIso();
    this.selectedIso = today;
    this.refreshAll();
  }

  refreshAll(): void {
    this.loadMonth();
  }

  get calendarTitle(): string {
    return new Date(this.year, this.month - 1, 1).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  get selectedDay(): ManagementDueDayDto | null {
    if (!this.monthData || !this.selectedIso) {
      return null;
    }
    return this.monthData.days.find((d) => d.date === this.selectedIso) ?? null;
  }

  get suggestions(): ManagementDueSuggestionDto[] {
    return this.monthData?.suggestions ?? [];
  }

  calendarRows(): DueCalCell[][] {
    const last = new Date(this.year, this.month, 0).getDate();
    const firstDow = new Date(this.year, this.month - 1, 1).getDay();
    const byDate = new Map((this.monthData?.days ?? []).map((d) => [d.date, d]));
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
    return rows;
  }

  prevMonth(): void {
    if (this.month === 1) {
      this.year -= 1;
      this.month = 12;
    } else {
      this.month -= 1;
    }
    this.selectedIso = this.isoFor(this.year, this.month, 1);
    this.loadMonth();
  }

  nextMonth(): void {
    if (this.month === 12) {
      this.year += 1;
      this.month = 1;
    } else {
      this.month += 1;
    }
    this.selectedIso = this.isoFor(this.year, this.month, 1);
    this.loadMonth();
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
      ...(this.editingItemId
        ? {}
        : {
            startYear: this.year,
            startMonth: this.month,
          }),
    };
    this.saving = true;
    const wasUpdate = this.editingItemId != null;
    const req = wasUpdate
      ? this.api.updateDueItem(this.editingItemId as number, body)
      : this.api.createDueItem(body);
    req.subscribe({
      next: (month) => {
        this.monthData = month;
        this.saving = false;
        this.editingItemId = null;
        this.draft = this.emptyDraft();
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
    this.draft = {
      side: row.side,
      counterparty: row.counterparty,
      recurring: true,
      dayOfMonth: row.typicalDay,
      oneOffDate: this.isoFor(this.year, this.month, row.typicalDay),
      amount: row.estimatedAmount == null ? '' : String(row.estimatedAmount),
      notes: '',
    };
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
          this.monthData = month;
          this.saving = false;
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
        this.monthData = month;
        this.saving = false;
        if (this.editingItemId === row.itemId) {
          this.cancelEdit();
        }
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

  dayNet(day: ManagementDueDayDto | undefined): string {
    if (!day || (day.payableCount === 0 && day.receivableCount === 0)) {
      return '';
    }
    const net = Number(day.receivableTotal || 0) - Number(day.payableTotal || 0);
    if (!net) {
      return day.payableCount || day.receivableCount ? '·' : '';
    }
    const prefix = net > 0 ? '+' : '−';
    return `${prefix}${this.money(Math.abs(net))}`;
  }

  isToday(iso: string | undefined): boolean {
    return !!iso && iso === this.todayIso();
  }

  private loadMonth(): void {
    this.loading = true;
    this.api.dueMonth(this.year, this.month).subscribe({
      next: (month) => {
        this.monthData = month;
        this.loading = false;
        if (!this.selectedIso.startsWith(`${this.year}-${String(this.month).padStart(2, '0')}`)) {
          this.selectedIso = this.isoFor(this.year, this.month, 1);
        }
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load Due', undefined, { duration: 3200 });
      },
    });
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
