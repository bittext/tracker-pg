import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementDueCategoryDto,
  ManagementDueReportDto,
  ManagementDueReportRowDto,
  ManagementDueSide,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type ReportScope = 'month' | 'year';
type ReportGroup = 'business' | 'category';

interface PeriodView {
  paid: number;
  received: number;
  net: number;
  openPayable: number;
  openReceivable: number;
  openPayableCount: number;
  openReceivableCount: number;
}

interface LedgerLine {
  date: string;
  label: string;
  amount: number;
  settled: boolean;
  recurring: boolean;
}

interface LedgerGroup {
  name: string;
  total: number;
  open: number;
  lines: LedgerLine[];
}

interface MonthLine {
  month: number;
  label: string;
  paid: number;
  received: number;
  net: number;
  current: boolean;
}

interface CategoryLine {
  name: string;
  paid: number;
  received: number;
  net: number;
}

@Component({
  selector: 'app-management-due-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
  ],
  templateUrl: './management-due-reports.component.html',
  styleUrl: './management-due-reports.component.scss',
})
export class ManagementDueReportsComponent implements OnChanges {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  @Input({ required: true }) year = 0;
  @Input({ required: true }) month = 0;
  @Input() revision = 0;
  /** Off keeps bill rows. With bills adds sale days. Sales only keeps sale days. */
  @Input() salesView: 'off' | 'with' | 'only' = 'off';
  @Input() salesRows: ManagementDueReportRowDto[] = [];
  /** Admin order, highest first. Category groups follow this list. */
  @Input() categories: ManagementDueCategoryDto[] = [];
  @Output() readonly selectDate = new EventEmitter<string>();

  report: ManagementDueReportDto | null = null;
  loading = false;
  query = '';
  scope: ReportScope = 'month';
  groupBy: ReportGroup = 'business';
  sideFilter: '' | ManagementDueSide = '';
  statusFilter: '' | 'open' | 'settled' = '';
  cadenceFilter: '' | 'recurring' | 'once' = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.year || !this.month) {
      return;
    }
    if (changes['year'] || changes['month'] || changes['revision']) {
      this.load();
    }
  }

  get filteredRows(): ManagementDueReportRowDto[] {
    const q = this.query.trim().toLowerCase();
    return this.sourceRows.filter((row) => {
      if (this.scope === 'month' && (row.year !== this.year || row.month !== this.month)) {
        return false;
      }
      if (this.scope === 'year' && row.year !== this.year) {
        return false;
      }
      if (this.sideFilter && row.side !== this.sideFilter) {
        return false;
      }
      if (this.statusFilter === 'open' && row.settled) {
        return false;
      }
      if (this.statusFilter === 'settled' && !row.settled) {
        return false;
      }
      if (this.cadenceFilter === 'recurring' && !row.recurring) {
        return false;
      }
      if (this.cadenceFilter === 'once' && row.recurring) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${row.counterparty} ${row.category || ''} ${row.notes}`.toLowerCase().includes(q);
    });
  }

  get hasFilters(): boolean {
    return !!(this.query.trim() || this.sideFilter || this.statusFilter || this.cadenceFilter);
  }

  get period(): PeriodView {
    return this.toPeriod(this.filteredRows);
  }

  get scopeLabel(): string {
    if (this.scope === 'year') {
      return String(this.year);
    }
    return new Date(this.year, this.month - 1, 1).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  get showPaid(): boolean {
    return this.sideFilter !== 'RECEIVABLE';
  }

  get showReceived(): boolean {
    return this.sideFilter !== 'PAYABLE';
  }

  get paidGroups(): LedgerGroup[] {
    return this.groupsFor('PAYABLE');
  }

  get receivedGroups(): LedgerGroup[] {
    return this.groupsFor('RECEIVABLE');
  }

  get categoryLines(): CategoryLine[] {
    const byName = new Map<string, { paid: number; received: number }>();
    for (const row of this.filteredRows) {
      if (!row.settled) {
        continue;
      }
      const name = this.categoryName(row);
      const slot = byName.get(name) ?? { paid: 0, received: 0 };
      const amount = Number(row.amount || 0);
      if (row.side === 'PAYABLE') {
        slot.paid += amount;
      } else {
        slot.received += amount;
      }
      byName.set(name, slot);
    }
    const lines = [...byName.entries()].map(([name, slot]) => ({
      name,
      paid: slot.paid,
      received: slot.received,
      net: slot.received - slot.paid,
    }));
    if (!lines.some((line) => line.name !== 'Uncategorized')) {
      return [];
    }
    return lines.sort((a, b) => this.categoryRank(b.name) - this.categoryRank(a.name) || a.name.localeCompare(b.name));
  }

  get monthLines(): MonthLine[] {
    const byMonth = new Map<number, { paid: number; received: number }>();
    for (const row of this.filteredRows) {
      if (row.year !== this.year || !row.settled) {
        continue;
      }
      const slot = byMonth.get(row.month) ?? { paid: 0, received: 0 };
      const amount = Number(row.amount || 0);
      if (row.side === 'PAYABLE') {
        slot.paid += amount;
      } else {
        slot.received += amount;
      }
      byMonth.set(row.month, slot);
    }
    return [...byMonth.entries()]
      .sort((a, b) => a[0] - b[0])
      .map(([month, slot]) => ({
        month,
        label: new Date(this.year, month - 1, 1).toLocaleDateString(undefined, { month: 'long' }),
        paid: slot.paid,
        received: slot.received,
        net: slot.received - slot.paid,
        current: month === this.month,
      }));
  }

  money(value: number | null | undefined): string {
    if (value == null || Number.isNaN(Number(value))) {
      return '—';
    }
    return Number(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' });
  }

  signed(value: number): string {
    if (!value) {
      return this.money(0);
    }
    return `${value > 0 ? '+' : '−'}${this.money(Math.abs(value))}`;
  }

  openDay(iso: string | undefined): void {
    if (iso) {
      this.selectDate.emit(iso);
    }
  }

  clearFilters(): void {
    this.query = '';
    this.sideFilter = '';
    this.statusFilter = '';
    this.cadenceFilter = '';
  }

  private get sourceRows(): ManagementDueReportRowDto[] {
    const bills = this.salesView === 'only' ? [] : (this.report?.rows ?? []);
    if (this.salesView === 'off') {
      return bills;
    }
    return [...bills, ...this.salesRows];
  }

  private load(): void {
    this.loading = true;
    this.api.dueReports(this.year, this.month).subscribe({
      next: (report) => {
        this.loading = false;
        this.report = report;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load Due reports', undefined, {
          duration: 3200,
        });
      },
    });
  }

  private groupsFor(side: ManagementDueSide): LedgerGroup[] {
    const byName = new Map<string, ManagementDueReportRowDto[]>();
    for (const row of this.filteredRows) {
      if (row.side !== side) {
        continue;
      }
      const key = this.groupBy === 'category' ? this.categoryName(row) : row.counterparty;
      const list = byName.get(key) ?? [];
      list.push(row);
      byName.set(key, list);
    }
    const groups: LedgerGroup[] = [];
    for (const [name, rows] of byName) {
      const lines = rows
        .map((row) => ({
          date: this.rowDate(row),
          label: this.lineLabel(row),
          amount: Number(row.amount || 0),
          settled: row.settled,
          recurring: row.recurring,
        }))
        .sort((a, b) => a.date.localeCompare(b.date) || a.label.localeCompare(b.label));
      const total = lines.filter((line) => line.settled).reduce((sum, line) => sum + line.amount, 0);
      const open = lines.filter((line) => !line.settled).reduce((sum, line) => sum + line.amount, 0);
      groups.push({ name, total, open, lines });
    }
    if (this.groupBy === 'category') {
      return groups.sort((a, b) => this.categoryRank(b.name) - this.categoryRank(a.name) || a.name.localeCompare(b.name));
    }
    return groups.sort(
      (a, b) => b.total + b.open - (a.total + a.open) || a.name.localeCompare(b.name)
    );
  }

  private categoryName(row: ManagementDueReportRowDto): string {
    const name = (row.category || '').trim();
    return name || 'Uncategorized';
  }

  private categoryRank(name: string): number {
    if (name === 'Uncategorized') {
      return Number.NEGATIVE_INFINITY;
    }
    const match = this.categories.find((category) => category.name === name);
    return match?.sortOrder ?? 0;
  }

  private toPeriod(rows: ManagementDueReportRowDto[]): PeriodView {
    const view: PeriodView = {
      paid: 0,
      received: 0,
      net: 0,
      openPayable: 0,
      openReceivable: 0,
      openPayableCount: 0,
      openReceivableCount: 0,
    };
    for (const row of rows) {
      const amount = Number(row.amount || 0);
      if (row.settled) {
        if (row.side === 'PAYABLE') {
          view.paid += amount;
        } else {
          view.received += amount;
        }
      } else if (row.side === 'PAYABLE') {
        view.openPayable += amount;
        view.openPayableCount += 1;
      } else {
        view.openReceivable += amount;
        view.openReceivableCount += 1;
      }
    }
    view.net = view.received - view.paid;
    return view;
  }

  private lineLabel(row: ManagementDueReportRowDto): string {
    const iso = this.rowDate(row);
    const date = this.parseIso(iso);
    const when = date
      ? date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
      : iso;
    const cadence = row.recurring ? 'Recurring' : 'Once';
    const state = row.settled ? (row.side === 'PAYABLE' ? 'Paid' : 'Received') : 'Open';
    const detail = this.groupBy === 'category' ? row.counterparty : (row.category || '').trim();
    const middle = detail ? `${detail} · ` : '';
    return `${when} · ${middle}${cadence} · ${state}`;
  }

  private rowDate(row: ManagementDueReportRowDto): string {
    const raw = row.date as unknown;
    if (Array.isArray(raw) && raw.length >= 3) {
      return this.isoFor(Number(raw[0]), Number(raw[1]), Number(raw[2]));
    }
    return typeof raw === 'string' ? raw.slice(0, 10) : raw != null ? String(raw).slice(0, 10) : '';
  }

  private parseIso(iso: string): Date | null {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
    if (!match) {
      return null;
    }
    return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  }

  private isoFor(year: number, month: number, day: number): string {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }
}
