import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  RobinhoodOwnershipAssetKind,
  RobinhoodOwnershipHistoryDto,
  RobinhoodOwnershipHistoryPointDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorMessage } from '../../../util/http-error';
import { robinhoodAccountDisplayLabel } from '../../../util/robinhood-account-display';

interface QtyChangeRow {
  date: string;
  from: number;
  to: number;
  delta: number;
}

interface CalendarCell {
  type: 'pad' | 'day';
  trackKey: string;
  date: string;
  dayNumber: number | null;
  quantity: number | null;
  delta: number | null;
  hasSnapshot: boolean;
  isToday: boolean;
  isSelected: boolean;
  point: RobinhoodOwnershipHistoryPointDto | null;
}

@Component({
  selector: 'app-reports-finance-robinhood-ownership-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-ownership-history.component.html',
  styleUrl: './reports-finance-robinhood-ownership-history.component.scss',
})
export class ReportsFinanceRobinhoodOwnershipHistoryComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Aligns with Daily Tracker monkey / Then-Now capital start. */
  readonly optionsHistoryStart = '2026-06-28';

  reportYear = new Date().getFullYear();
  assetKind: RobinhoodOwnershipAssetKind = 'equity';
  /** Empty until first load; server picks first available equity symbol. */
  symbol = '';
  /** Empty string = all contracts overview. */
  contractKey = '';
  accountSuffix = '';
  captureKind = 'SCHEDULED';
  loading = false;
  detailsExpanded = false;

  /** Calendar month as YYYY-MM-01 */
  calendarMonth = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01`;
  selectedDate: string | null = null;

  readonly report = signal<RobinhoodOwnershipHistoryDto | null>(null);

  readonly marginUsedWarnPercent = 33;

  readonly captureKinds = [
    { value: 'SCHEDULED', label: 'Daily close' },
    { value: 'INTRADAY', label: 'Hourly' },
    { value: 'MANUAL', label: 'Manual' },
  ] as const;

  readonly weekdayLabels = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const;

  readonly showingAllContracts = computed(() => {
    const r = this.report();
    return r?.assetKind === 'option' && !r.contractKey && (r.contractSeries?.length ?? 0) > 0;
  });

  readonly changeRows = computed((): QtyChangeRow[] => {
    const pts = this.report()?.points ?? [];
    const out: QtyChangeRow[] = [];
    for (let i = 1; i < pts.length; i++) {
      const prev = Number(pts[i - 1].quantity) || 0;
      const cur = Number(pts[i].quantity) || 0;
      const delta = cur - prev;
      if (Math.abs(delta) < 0.0000005) {
        continue;
      }
      out.push({ date: pts[i].snapshotDate.slice(0, 10), from: prev, to: cur, delta });
    }
    return out;
  });

  readonly changeByDate = computed(() => {
    const map = new Map<string, QtyChangeRow>();
    for (const c of this.changeRows()) {
      map.set(c.date, c);
    }
    return map;
  });

  readonly pointByDate = computed(() => {
    const map = new Map<string, RobinhoodOwnershipHistoryPointDto>();
    for (const p of this.report()?.points ?? []) {
      map.set(p.snapshotDate.slice(0, 10), p);
    }
    return map;
  });

  readonly calendarCells = computed((): CalendarCell[] => {
    const monthStart = this.calendarMonth.slice(0, 10);
    const [ys, ms] = monthStart.split('-').map(Number);
    if (!ys || !ms) {
      return [];
    }
    const first = new Date(ys, ms - 1, 1);
    const daysInMonth = new Date(ys, ms, 0).getDate();
    const startPad = first.getDay();
    const today = this.todayIso();
    const changeMap = this.changeByDate();
    const pointMap = this.pointByDate();
    const selected = this.selectedDate;

    const cells: CalendarCell[] = [];
    for (let i = 0; i < startPad; i++) {
      cells.push({
        type: 'pad',
        trackKey: `pad-${monthStart}-${i}`,
        date: '',
        dayNumber: null,
        quantity: null,
        delta: null,
        hasSnapshot: false,
        isToday: false,
        isSelected: false,
        point: null,
      });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${ys}-${String(ms).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const point = pointMap.get(date) ?? null;
      const change = changeMap.get(date) ?? null;
      cells.push({
        type: 'day',
        trackKey: date,
        date,
        dayNumber: d,
        quantity: point != null ? Number(point.quantity) || 0 : null,
        delta: change?.delta ?? null,
        hasSnapshot: point != null,
        isToday: date === today,
        isSelected: date === selected,
        point,
      });
    }
    return cells;
  });

  readonly calendarWeeks = computed(() => {
    const cells = this.calendarCells();
    const weeks: CalendarCell[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7));
    }
    return weeks;
  });

  readonly selectedPoint = computed(() => {
    const d = this.selectedDate;
    if (!d) {
      return null;
    }
    return this.pointByDate().get(d) ?? null;
  });

  readonly selectedChange = computed(() => {
    const d = this.selectedDate;
    if (!d) {
      return null;
    }
    return this.changeByDate().get(d) ?? null;
  });

  readonly chartBars = computed(() => {
    const pts = this.report()?.points ?? [];
    if (!pts.length) {
      return [];
    }
    const max = Math.max(...pts.map((p) => Number(p.quantity) || 0), 0.0001);
    const unit = this.report()?.assetKind === 'option' ? 'contracts' : 'shares';
    const changeMap = this.changeByDate();
    return pts.map((p) => {
      const qty = Number(p.quantity) || 0;
      const date = p.snapshotDate.slice(0, 10);
      const delta = changeMap.get(date)?.delta ?? null;
      return {
        date,
        qty,
        heightPct: Math.max(2, (qty / max) * 100),
        changed: delta != null,
        delta,
        title:
          delta != null
            ? `${date}: ${qty} ${unit} (${delta > 0 ? '+' : ''}${delta})`
            : `${date}: ${qty} ${unit}`,
      };
    });
  });

  readonly changeMonthsInYear = computed(() => {
    const months = new Set<string>();
    for (const c of this.changeRows()) {
      months.add(c.date.slice(0, 7));
    }
    for (const p of this.report()?.points ?? []) {
      months.add(p.snapshotDate.slice(0, 7));
    }
    return [...months].sort();
  });

  ngOnInit(): void {
    this.load();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  onAssetKindChange(kind: RobinhoodOwnershipAssetKind): void {
    this.assetKind = kind;
    this.contractKey = '';
    if (kind === 'option') {
      this.symbol = '';
    }
    this.selectedDate = null;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi
      .robinhoodOwnershipHistory({
        year: this.reportYear,
        assetKind: this.assetKind,
        symbol: this.assetKind === 'equity' && this.symbol ? this.symbol : null,
        contractKey: this.assetKind === 'option' && this.contractKey ? this.contractKey : null,
        accountSuffix: this.accountSuffix || null,
        captureKind: this.captureKind,
      })
      .subscribe({
        next: (r) => {
          this.report.set(r);
          this.assetKind = r.assetKind;
          if (r.symbol) {
            this.symbol = r.symbol;
          }
          this.contractKey = r.contractKey ?? '';
          this.accountSuffix = r.accountSuffix;
          this.alignCalendarToData(r);
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.report.set(null);
          this.snackBar.open(formatHttpErrorMessage(err) || 'Failed to load ownership history', 'Dismiss', {
            duration: 6000,
          });
        },
      });
  }

  private alignCalendarToData(r: RobinhoodOwnershipHistoryDto): void {
    const changes = this.changeRows();
    const prefer =
      changes.length > 0
        ? changes[changes.length - 1].date
        : r.points.length
          ? r.points[r.points.length - 1].snapshotDate.slice(0, 10)
          : null;
    if (prefer) {
      this.calendarMonth = prefer.slice(0, 7) + '-01';
      if (!this.selectedDate || this.selectedDate.slice(0, 4) !== String(r.year)) {
        this.selectedDate = prefer;
      }
    } else {
      this.calendarMonth = `${r.year}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01`;
      if (Number(this.calendarMonth.slice(0, 4)) !== r.year) {
        this.calendarMonth = `${r.year}-01-01`;
      }
    }
  }

  onYearChange(): void {
    this.selectedDate = null;
    this.calendarMonth = `${this.reportYear}-01-01`;
    this.load();
  }

  onSymbolChange(sym: string): void {
    this.symbol = sym;
    this.selectedDate = null;
    this.load();
  }

  onContractChange(key: string): void {
    this.contractKey = key;
    this.selectedDate = null;
    this.load();
  }

  selectContract(key: string): void {
    this.contractKey = key;
    this.selectedDate = null;
    this.load();
  }

  showAllContracts(): void {
    this.contractKey = '';
    this.selectedDate = null;
    this.load();
  }

  shiftCalendarMonth(delta: number): void {
    const [y, m] = this.calendarMonth.slice(0, 7).split('-').map(Number);
    const d = new Date(y, m - 1 + delta, 1);
    const nextYear = d.getFullYear();
    if (nextYear !== this.reportYear) {
      return;
    }
    this.calendarMonth = `${nextYear}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }

  canShiftMonth(delta: number): boolean {
    const [y, m] = this.calendarMonth.slice(0, 7).split('-').map(Number);
    const d = new Date(y, m - 1 + delta, 1);
    return d.getFullYear() === this.reportYear;
  }

  jumpToMonth(ym: string): void {
    if (!ym || ym.length < 7) {
      return;
    }
    this.calendarMonth = ym.slice(0, 7) + '-01';
  }

  selectDay(cell: CalendarCell): void {
    if (cell.type !== 'day') {
      return;
    }
    this.selectedDate = cell.date;
  }

  focusDate(iso: string): void {
    const day = iso.slice(0, 10);
    this.jumpToMonth(day);
    this.selectedDate = day;
  }

  calendarMonthLabel(): string {
    const [y, m] = this.calendarMonth.slice(0, 7).split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  cellTone(cell: CalendarCell): 'pos' | 'neg' | 'hold' | 'empty' {
    if (cell.delta != null && cell.delta > 0) {
      return 'pos';
    }
    if (cell.delta != null && cell.delta < 0) {
      return 'neg';
    }
    if (cell.hasSnapshot) {
      return 'hold';
    }
    return 'empty';
  }

  formatDay(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return iso.length >= 10 ? iso.slice(0, 10) : iso;
  }

  formatDayFriendly(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = iso.slice(0, 10);
    const dt = new Date(d + 'T12:00:00');
    if (Number.isNaN(dt.getTime())) {
      return d;
    }
    return dt.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
  }

  qtyUnit(r: RobinhoodOwnershipHistoryDto | null = this.report()): string {
    return r?.assetKind === 'option' ? 'contracts' : 'shares';
  }

  positionTitle(r: RobinhoodOwnershipHistoryDto): string {
    if (r.assetKind === 'option') {
      return r.contractLabel || 'All option contracts';
    }
    return r.symbol || 'Stock';
  }

  marginUsedHigh(row: RobinhoodOwnershipHistoryPointDto | null | undefined): boolean {
    const pct = row?.marginUsedPercent;
    return pct != null && Number(pct) >= this.marginUsedWarnPercent;
  }

  accountOptionLabel(suffix: string): string {
    return robinhoodAccountDisplayLabel(suffix);
  }

  latestMarginUsedPercent(): number | null {
    const pts = this.report()?.points ?? [];
    if (!pts.length) {
      return null;
    }
    const pct = pts[pts.length - 1].marginUsedPercent;
    return pct == null ? null : Number(pct);
  }

  private todayIso(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }
}
