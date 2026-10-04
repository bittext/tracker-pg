import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementDueReportDto,
  ManagementDueReportRowDto,
  ManagementDueSide,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type ReportScope = 'month' | 'year' | 'lifetime';
type ReportView = 'summary' | 'tree' | 'calendar' | 'who';
type TreeMode = 'type' | 'time';

interface PeriodView {
  paid: number;
  received: number;
  net: number;
  openPayable: number;
  openReceivable: number;
  openPayableCount: number;
  openReceivableCount: number;
  settledCount: number;
  biggestOutName: string | null;
  biggestInName: string | null;
}

interface WhoView {
  counterparty: string;
  side: ManagementDueSide;
  paid: number;
  received: number;
  openAmount: number;
  count: number;
  lastDate: string | null;
}

interface TreeLeaf {
  name: string;
  amount: number;
  count: number;
  date: string;
  side: ManagementDueSide;
  settled: boolean;
}

interface TreeNode {
  key: string;
  label: string;
  amount: number;
  count: number;
  tone: 'out' | 'in' | 'neutral';
  children: TreeNode[];
  leaves: TreeLeaf[];
}

interface HeatMonth {
  month: number;
  title: string;
  weeks: HeatCell[][];
}

interface HeatCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  paid: number;
  received: number;
  net: number;
  count: number;
}

interface MonthBarView {
  month: number;
  label: string;
  paid: number;
  received: number;
  paidPct: number;
  receivedPct: number;
  current: boolean;
}

@Component({
  selector: 'app-management-due-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
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
  @Output() readonly selectDate = new EventEmitter<string>();

  readonly weekDays = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  report: ManagementDueReportDto | null = null;
  loading = false;
  query = '';
  scope: ReportScope = 'month';
  view: ReportView = 'summary';
  treeMode: TreeMode = 'type';
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
    return (this.report?.rows ?? []).filter((row) => {
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
      return `${row.counterparty} ${row.notes}`.toLowerCase().includes(q);
    });
  }

  get hasFilters(): boolean {
    return !!(this.query.trim() || this.sideFilter || this.statusFilter || this.cadenceFilter);
  }

  get period(): PeriodView {
    return this.toPeriod(this.filteredRows);
  }

  get summarySentence(): string {
    const rows = this.filteredRows;
    if (!this.report) {
      return '';
    }
    if (!rows.length) {
      return this.hasFilters ? 'Nothing matches this search.' : 'Nothing to summarize for this range yet.';
    }
    const p = this.period;
    const open = p.openPayableCount + p.openReceivableCount;
    const parts = [
      `${this.scopeLabel}: paid ${this.money(p.paid)}, received ${this.money(p.received)}, net ${this.signed(p.net)}.`,
    ];
    if (open) {
      parts.push(`${open} still open.`);
    } else if (p.settledCount) {
      parts.push('All marked paid or received.');
    }
    if (p.biggestOutName) {
      parts.push(`Biggest out: ${p.biggestOutName}.`);
    }
    if (p.biggestInName) {
      parts.push(`Biggest in: ${p.biggestInName}.`);
    }
    return parts.join(' ');
  }

  get scopeLabel(): string {
    if (this.scope === 'lifetime') {
      return 'Till date';
    }
    if (this.scope === 'year') {
      return String(this.year);
    }
    return new Date(this.year, this.month - 1, 1).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  get monthBars(): MonthBarView[] {
    const byMonth = new Map<number, { paid: number; received: number }>();
    for (let m = 1; m <= 12; m++) {
      byMonth.set(m, { paid: 0, received: 0 });
    }
    for (const row of this.filteredRows) {
      if (row.year !== this.year || !row.settled) {
        continue;
      }
      const slot = byMonth.get(row.month);
      if (!slot) {
        continue;
      }
      const amount = Number(row.amount || 0);
      if (row.side === 'PAYABLE') {
        slot.paid += amount;
      } else {
        slot.received += amount;
      }
    }
    const peak = Math.max(
      1,
      ...[...byMonth.values()].flatMap((slot) => [slot.paid, slot.received])
    );
    return [...byMonth.entries()].map(([month, slot]) => ({
      month,
      label: new Date(this.year, month - 1, 1).toLocaleDateString(undefined, { month: 'short' }),
      paid: slot.paid,
      received: slot.received,
      paidPct: Math.round((slot.paid / peak) * 100),
      receivedPct: Math.round((slot.received / peak) * 100),
      current: month === this.month,
    }));
  }

  get tree(): TreeNode[] {
    return this.treeMode === 'type' ? this.treeByType() : this.treeByTime();
  }

  get heatMonths(): HeatMonth[] {
    const byDate = new Map<string, { paid: number; received: number; count: number }>();
    for (const row of this.filteredRows) {
      if (row.year !== this.year) {
        continue;
      }
      const iso = this.rowDate(row);
      const slot = byDate.get(iso) ?? { paid: 0, received: 0, count: 0 };
      const amount = Number(row.amount || 0);
      if (row.side === 'PAYABLE') {
        slot.paid += amount;
      } else {
        slot.received += amount;
      }
      slot.count += 1;
      byDate.set(iso, slot);
    }
    const months: HeatMonth[] = [];
    for (let month = 1; month <= 12; month++) {
      const last = new Date(this.year, month, 0).getDate();
      const firstDow = new Date(this.year, month - 1, 1).getDay();
      const cells: HeatCell[] = [];
      for (let i = 0; i < firstDow; i++) {
        cells.push({ type: 'pad', paid: 0, received: 0, net: 0, count: 0 });
      }
      for (let d = 1; d <= last; d++) {
        const iso = this.isoFor(this.year, month, d);
        const slot = byDate.get(iso);
        const paid = slot?.paid ?? 0;
        const received = slot?.received ?? 0;
        cells.push({
          type: 'day',
          iso,
          label: String(d),
          paid,
          received,
          net: received - paid,
          count: slot?.count ?? 0,
        });
      }
      const weeks: HeatCell[][] = [];
      for (let i = 0; i < cells.length; i += 7) {
        weeks.push(cells.slice(i, i + 7));
      }
      months.push({
        month,
        title: new Date(this.year, month - 1, 1).toLocaleDateString(undefined, { month: 'short' }),
        weeks,
      });
    }
    return months;
  }

  get heatPeak(): number {
    let peak = 0;
    for (const month of this.heatMonths) {
      for (const week of month.weeks) {
        for (const cell of week) {
          peak = Math.max(peak, Math.abs(cell.net));
        }
      }
    }
    return peak;
  }

  get whoPaid(): WhoView[] {
    return this.whoRows().filter((row) => row.side === 'PAYABLE');
  }

  get whoReceived(): WhoView[] {
    return this.whoRows().filter((row) => row.side === 'RECEIVABLE');
  }

  heatTone(cell: HeatCell): string {
    if (cell.type !== 'day' || !cell.count) {
      return '';
    }
    const peak = this.heatPeak || 1;
    const rank = Math.min(4, Math.max(1, Math.ceil((Math.abs(cell.net) / peak) * 4) || 1));
    const side = cell.net > 0 ? 'in' : cell.net < 0 ? 'out' : 'flat';
    return `rpt-heat-day--${side}-${rank}`;
  }

  whoShare(row: WhoView, side: ManagementDueSide): string {
    const total = this.whoRows()
      .filter((item) => item.side === side)
      .reduce((sum, item) => sum + item.paid + item.received + item.openAmount, 0);
    const value = row.paid + row.received + row.openAmount;
    if (!total) {
      return '0%';
    }
    return `${Math.round((value / total) * 100)}%`;
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

  openLeaf(leaf: TreeLeaf): void {
    this.selectDate.emit(leaf.date);
  }

  clearFilters(): void {
    this.query = '';
    this.sideFilter = '';
    this.statusFilter = '';
    this.cadenceFilter = '';
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

  private treeByType(): TreeNode[] {
    const groups = new Map<string, ManagementDueReportRowDto[]>();
    for (const row of this.filteredRows) {
      const key = `${row.side}|${row.recurring ? 'recurring' : 'once'}`;
      const list = groups.get(key) ?? [];
      list.push(row);
      groups.set(key, list);
    }
    const paid = this.branch(
      'paid',
      'Paid',
      'out',
      [
        this.cadenceBranch('Paid recurring', groups.get('PAYABLE|recurring') ?? [], 'out'),
        this.cadenceBranch('Paid once', groups.get('PAYABLE|once') ?? [], 'out'),
      ].filter((node) => node.count)
    );
    const received = this.branch(
      'received',
      'Received',
      'in',
      [
        this.cadenceBranch('Received recurring', groups.get('RECEIVABLE|recurring') ?? [], 'in'),
        this.cadenceBranch('Received once', groups.get('RECEIVABLE|once') ?? [], 'in'),
      ].filter((node) => node.count)
    );
    return [paid, received].filter((node) => node.count);
  }

  private treeByTime(): TreeNode[] {
    const years = new Map<number, Map<number, ManagementDueReportRowDto[]>>();
    for (const row of this.filteredRows) {
      const months = years.get(row.year) ?? new Map<number, ManagementDueReportRowDto[]>();
      const list = months.get(row.month) ?? [];
      list.push(row);
      months.set(row.month, list);
      years.set(row.year, months);
    }
    return [...years.entries()]
      .sort((a, b) => b[0] - a[0])
      .map(([year, months]) => {
        const monthNodes = [...months.entries()]
          .sort((a, b) => b[0] - a[0])
          .map(([month, rows]) =>
            this.leafBranch(
              `${year}-${month}`,
              new Date(year, month - 1, 1).toLocaleDateString(undefined, {
                month: 'long',
                year: 'numeric',
              }),
              rows,
              'neutral'
            )
          );
        return this.branch(String(year), String(year), 'neutral', monthNodes);
      });
  }

  private cadenceBranch(label: string, rows: ManagementDueReportRowDto[], tone: TreeNode['tone']): TreeNode {
    return this.leafBranch(label, label, rows, tone);
  }

  private leafBranch(
    key: string,
    label: string,
    rows: ManagementDueReportRowDto[],
    tone: TreeNode['tone']
  ): TreeNode {
    const byName = new Map<string, ManagementDueReportRowDto[]>();
    for (const row of rows) {
      const list = byName.get(row.counterparty) ?? [];
      list.push(row);
      byName.set(row.counterparty, list);
    }
    const leaves: TreeLeaf[] = [...byName.entries()]
      .map(([name, group]) => {
        const amount = group.reduce((sum, row) => {
          const n = Number(row.amount || 0);
          return sum + (row.side === 'PAYABLE' ? -n : n);
        }, 0);
        const latest = group.reduce((best, row) => (row.date > best.date ? row : best), group[0]);
        return {
          name,
          amount,
          count: group.length,
          date: this.rowDate(latest),
          side: latest.side,
          settled: group.every((row) => row.settled),
        };
      })
      .sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount) || a.name.localeCompare(b.name));
    const amount = leaves.reduce((sum, leaf) => sum + leaf.amount, 0);
    return { key, label, amount, count: rows.length, tone, children: [], leaves };
  }

  private branch(key: string, label: string, tone: TreeNode['tone'], children: TreeNode[]): TreeNode {
    return {
      key,
      label,
      amount: children.reduce((sum, child) => sum + child.amount, 0),
      count: children.reduce((sum, child) => sum + child.count, 0),
      tone,
      children,
      leaves: [],
    };
  }

  private whoRows(): WhoView[] {
    const byKey = new Map<string, WhoView>();
    for (const row of this.filteredRows) {
      const key = `${row.side}|${row.counterparty.toLowerCase()}`;
      const acc = byKey.get(key) ?? {
        counterparty: row.counterparty,
        side: row.side,
        paid: 0,
        received: 0,
        openAmount: 0,
        count: 0,
        lastDate: null as string | null,
      };
      const amount = Number(row.amount || 0);
      if (row.settled) {
        if (row.side === 'PAYABLE') {
          acc.paid += amount;
        } else {
          acc.received += amount;
        }
      } else {
        acc.openAmount += amount;
      }
      acc.count += 1;
      const iso = this.rowDate(row);
      if (!acc.lastDate || iso > acc.lastDate) {
        acc.lastDate = iso;
      }
      byKey.set(key, acc);
    }
    return [...byKey.values()].sort(
      (a, b) => b.paid + b.received + b.openAmount - (a.paid + a.received + a.openAmount)
    );
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
      settledCount: 0,
      biggestOutName: null,
      biggestInName: null,
    };
    let outAmt = -1;
    let inAmt = -1;
    let schedOut = -1;
    let schedIn = -1;
    let schedOutName: string | null = null;
    let schedInName: string | null = null;
    for (const row of rows) {
      const amount = Number(row.amount || 0);
      if (row.settled) {
        view.settledCount += 1;
        if (row.side === 'PAYABLE') {
          view.paid += amount;
          if (amount > outAmt) {
            outAmt = amount;
            view.biggestOutName = row.counterparty;
          }
        } else {
          view.received += amount;
          if (amount > inAmt) {
            inAmt = amount;
            view.biggestInName = row.counterparty;
          }
        }
      } else if (row.side === 'PAYABLE') {
        view.openPayable += amount;
        view.openPayableCount += 1;
        if (amount > schedOut) {
          schedOut = amount;
          schedOutName = row.counterparty;
        }
      } else {
        view.openReceivable += amount;
        view.openReceivableCount += 1;
        if (amount > schedIn) {
          schedIn = amount;
          schedInName = row.counterparty;
        }
      }
    }
    if (!view.biggestOutName) {
      view.biggestOutName = schedOutName;
    }
    if (!view.biggestInName) {
      view.biggestInName = schedInName;
    }
    view.net = view.received - view.paid;
    return view;
  }

  private rowDate(row: ManagementDueReportRowDto): string {
    const raw = row.date as unknown;
    return typeof raw === 'string' ? raw : raw != null ? String(raw) : '';
  }

  private isoFor(year: number, month: number, day: number): string {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }
}
