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
  RobinhoodOwnershipContractDto,
  RobinhoodOwnershipContractSeriesDto,
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

interface OptionActivity {
  date: string;
  side: 'buy' | 'sell' | 'close';
  from: number;
  to: number;
  delta: number;
  /** Estimated realized $ on sells/closes; null for buys or legacy identity merges. */
  realizedPnl: number | null;
  realizedPnlPct: number | null;
  /** True when another same-chain series continues — not a real sale. */
  identityMerge: boolean;
  marketValue: number | null;
  costBasis: number | null;
}

interface OptionContractView {
  contract: RobinhoodOwnershipContractDto;
  series: RobinhoodOwnershipContractSeriesDto;
  activities: OptionActivity[];
  /** Open: unrealized on latest snapshot. Closed: sum of real sell/close realized P&L. */
  pnl: number;
  pnlPct: number | null;
  heat: number;
  open: boolean;
  groupKey: string;
  expirationDate: string | null;
}

interface OptionExpiryGroup {
  key: string;
  chainSymbol: string;
  expirationDate: string | null;
  openContracts: OptionContractView[];
  closedContracts: OptionContractView[];
  openQty: number;
  openPnl: number;
  openPnlPct: number | null;
  /** Sum of realized sell/close P&L (excludes identity merges). */
  realizedPnl: number;
  heat: number;
}

interface OptionDayHeldRow {
  groupKey: string;
  chainSymbol: string;
  expirationDate: string | null;
  label: string;
  contractKey: string;
  quantity: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPct: number | null;
}

interface OptionDaySoldRow {
  groupKey: string;
  chainSymbol: string;
  expirationDate: string | null;
  label: string;
  contractKey: string;
  soldQty: number;
  realizedPnl: number;
  realizedPnlPct: number | null;
  identityMerge: boolean;
}

interface OptionCalCell {
  type: 'pad' | 'day';
  trackKey: string;
  date: string;
  dayNumber: number | null;
  isToday: boolean;
  isSelected: boolean;
  heldCount: number;
  soldCount: number;
  activityCount: number;
  /** Realized sell/close P&L that day (excludes identity merges). */
  realizedPnl: number | null;
  realizedPnlPct: number | null;
  heat: number;
  tone: 'pos' | 'neg' | 'flat' | 'empty';
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
  readonly calendarMonth = signal(
    `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01`,
  );
  readonly selectedDate = signal<string | null>(null);

  /** Options overview filters — empty = all underlyings / all months. */
  readonly optionsChainFilter = signal('');
  /** '' = all months in year for the grouped list; calendar still uses calendarMonth. */
  readonly optionsMonthFilter = signal('');
  readonly selectedOptionsContractKey = signal<string | null>(null);
  readonly selectedOptionsDate = signal<string | null>(null);

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
    return r?.assetKind === 'option' && !r.contractKey;
  });

  readonly optionContractViews = computed((): OptionContractView[] => {
    const series = this.report()?.contractSeries ?? [];
    const chain = this.optionsChainFilter().trim().toUpperCase();
    const month = this.optionsMonthFilter().trim();
    const views: OptionContractView[] = [];
    for (const s of series) {
      const c = s.contract;
      const chainSym = (c.chainSymbol || '').toUpperCase();
      if (chain && chainSym !== chain) {
        continue;
      }
      const activities = this.activitiesFromContract(c, s.points, series);
      if (month) {
        const inMonth =
          c.firstDate?.slice(0, 10).startsWith(month) ||
          c.lastDate?.slice(0, 10).startsWith(month) ||
          c.closedDate?.slice(0, 10).startsWith(month) ||
          c.expirationDate?.slice(0, 10).startsWith(month) ||
          activities.some((a) => a.date.startsWith(month)) ||
          s.points.some((p) => p.snapshotDate.slice(0, 10).startsWith(month) && (Number(p.quantity) || 0) > 0);
        if (!inMonth) {
          continue;
        }
      }
      const open = !!c.currentlyOpen;
      const realized = activities
        .filter((a) => (a.side === 'sell' || a.side === 'close') && !a.identityMerge && a.realizedPnl != null)
        .reduce((sum, a) => sum + (a.realizedPnl ?? 0), 0);
      const realizedCost = activities
        .filter((a) => (a.side === 'sell' || a.side === 'close') && !a.identityMerge && a.realizedPnl != null)
        .reduce((sum, a) => {
          const sold = Math.abs(a.delta);
          const from = a.from || 0;
          const cost = Number(a.costBasis) || 0;
          // costBasis on the activity is post-trade; approximate sold cost from prior avg via from qty.
          if (from > 0 && sold > 0 && a.side !== 'buy') {
            // Prefer pct back-solve when present.
            if (a.realizedPnlPct != null && Math.abs(a.realizedPnlPct) > 0.0001) {
              return sum + (a.realizedPnl ?? 0) / (a.realizedPnlPct / 100);
            }
          }
          return sum + cost * (sold / Math.max(from, sold));
        }, 0);
      const pnl = open ? Number(c.latestUnrealizedPnL) || 0 : realized;
      const pnlPct = open
        ? c.latestUnrealizedPnLPercent == null
          ? this.pnlPct(pnl, c.latestCostBasis)
          : Number(c.latestUnrealizedPnLPercent)
        : this.pnlPct(realized, realizedCost);
      const expirationDate = c.expirationDate?.slice(0, 10) || null;
      views.push({
        contract: c,
        series: s,
        activities,
        pnl,
        pnlPct,
        heat: this.pnlHeat(pnl, pnlPct),
        open,
        groupKey: this.optionGroupKey(chainSym || '—', expirationDate),
        expirationDate,
      });
    }
    views.sort((a, b) => {
      if (a.groupKey !== b.groupKey) {
        return a.groupKey.localeCompare(b.groupKey);
      }
      if (a.open !== b.open) {
        return a.open ? -1 : 1;
      }
      return a.contract.label.localeCompare(b.contract.label);
    });
    return views;
  });

  readonly optionExpiryGroups = computed((): OptionExpiryGroup[] => {
    const map = new Map<string, OptionContractView[]>();
    for (const v of this.optionContractViews()) {
      const list = map.get(v.groupKey) ?? [];
      list.push(v);
      map.set(v.groupKey, list);
    }
    const groups: OptionExpiryGroup[] = [];
    for (const [key, contracts] of map) {
      const openContracts = contracts.filter((c) => c.open);
      const closedContracts = contracts.filter((c) => !c.open);
      const openQty = openContracts.reduce((s, c) => s + (Number(c.contract.latestQuantity) || 0), 0);
      const openPnl = openContracts.reduce((s, c) => s + c.pnl, 0);
      const openCost = openContracts.reduce((s, c) => s + (Number(c.contract.latestCostBasis) || 0), 0);
      const realizedPnl = contracts.reduce(
        (s, c) =>
          s +
          c.activities
            .filter((a) => (a.side === 'sell' || a.side === 'close') && !a.identityMerge && a.realizedPnl != null)
            .reduce((x, a) => x + (a.realizedPnl ?? 0), 0),
        0,
      );
      const openPnlPct = this.pnlPct(openPnl, openCost);
      groups.push({
        key,
        chainSymbol: contracts[0]?.contract.chainSymbol?.toUpperCase() || '—',
        expirationDate: contracts[0]?.expirationDate ?? null,
        openContracts,
        closedContracts,
        openQty,
        openPnl,
        openPnlPct,
        realizedPnl,
        heat: this.pnlHeat(openContracts.length ? openPnl : realizedPnl, openPnlPct),
      });
    }
    groups.sort((a, b) => {
      if (a.chainSymbol !== b.chainSymbol) {
        return a.chainSymbol.localeCompare(b.chainSymbol);
      }
      const ae = a.expirationDate || '9999';
      const be = b.expirationDate || '9999';
      return ae < be ? -1 : ae > be ? 1 : 0;
    });
    return groups;
  });

  readonly optionCalCells = computed((): OptionCalCell[] => {
    const monthStart = this.calendarMonth().slice(0, 10);
    const [ys, ms] = monthStart.split('-').map(Number);
    if (!ys || !ms) {
      return [];
    }
    const views = this.optionContractViews();
    const held = new Map<string, number>();
    const sold = new Map<string, number>();
    const realizedByDay = new Map<string, number>();
    const realizedCostByDay = new Map<string, number>();
    for (const v of views) {
      for (const p of v.series.points) {
        const date = p.snapshotDate.slice(0, 10);
        if ((Number(p.quantity) || 0) > 0) {
          held.set(date, (held.get(date) ?? 0) + 1);
        }
      }
      for (const a of v.activities) {
        if ((a.side === 'sell' || a.side === 'close') && !a.identityMerge) {
          sold.set(a.date, (sold.get(a.date) ?? 0) + 1);
          if (a.realizedPnl != null) {
            realizedByDay.set(a.date, (realizedByDay.get(a.date) ?? 0) + a.realizedPnl);
            if (a.realizedPnlPct != null && Math.abs(a.realizedPnlPct) > 0.0001) {
              realizedCostByDay.set(
                a.date,
                (realizedCostByDay.get(a.date) ?? 0) + a.realizedPnl / (a.realizedPnlPct / 100),
              );
            }
          }
        }
      }
    }
    const first = new Date(ys, ms - 1, 1);
    const daysInMonth = new Date(ys, ms, 0).getDate();
    const startPad = first.getDay();
    const today = this.todayIso();
    const selected = this.selectedOptionsDate();
    const cells: OptionCalCell[] = [];
    for (let i = 0; i < startPad; i++) {
      cells.push({
        type: 'pad',
        trackKey: `opad-${monthStart}-${i}`,
        date: '',
        dayNumber: null,
        isToday: false,
        isSelected: false,
        heldCount: 0,
        soldCount: 0,
        activityCount: 0,
        realizedPnl: null,
        realizedPnlPct: null,
        heat: 0,
        tone: 'empty',
      });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${ys}-${String(ms).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const heldCount = held.get(date) ?? 0;
      const soldCount = sold.get(date) ?? 0;
      const realizedPnl = realizedByDay.has(date) ? (realizedByDay.get(date) ?? 0) : null;
      const cost = realizedCostByDay.get(date) ?? 0;
      const realizedPnlPct = realizedPnl != null ? this.pnlPct(realizedPnl, cost) : null;
      const activityCount = heldCount + soldCount;
      const heat = realizedPnl != null ? this.pnlHeat(realizedPnl, realizedPnlPct) : heldCount ? 0.12 : 0;
      let tone: OptionCalCell['tone'] = 'empty';
      if (realizedPnl != null) {
        tone = realizedPnl > 0.5 ? 'pos' : realizedPnl < -0.5 ? 'neg' : 'flat';
      } else if (heldCount || soldCount) {
        tone = 'flat';
      }
      cells.push({
        type: 'day',
        trackKey: date,
        date,
        dayNumber: d,
        isToday: date === today,
        isSelected: date === selected,
        heldCount,
        soldCount,
        activityCount,
        realizedPnl,
        realizedPnlPct,
        heat,
        tone,
      });
    }
    return cells;
  });

  readonly optionCalWeeks = computed(() => {
    const cells = this.optionCalCells();
    const weeks: OptionCalCell[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7));
    }
    return weeks;
  });

  readonly optionMonthsInYear = computed(() => {
    const months = new Set<string>();
    const chain = this.optionsChainFilter().trim().toUpperCase();
    const series = this.report()?.contractSeries ?? [];
    for (const s of series) {
      const c = s.contract;
      if (chain && (c.chainSymbol || '').toUpperCase() !== chain) {
        continue;
      }
      for (const a of this.activitiesFromContract(c, s.points, series)) {
        months.add(a.date.slice(0, 7));
      }
      for (const p of s.points) {
        if ((Number(p.quantity) || 0) > 0) {
          months.add(p.snapshotDate.slice(0, 7));
        }
      }
      if (c.expirationDate) {
        months.add(c.expirationDate.slice(0, 7));
      }
    }
    return [...months].sort();
  });

  readonly selectedOptionView = computed((): OptionContractView | null => {
    const key = this.selectedOptionsContractKey();
    if (!key) {
      return null;
    }
    return this.optionContractViews().find((v) => v.contract.contractKey === key) ?? null;
  });

  readonly selectedOptionDayDetail = computed(() => {
    const day = this.selectedOptionsDate();
    if (!day) {
      return null;
    }
    const held: OptionDayHeldRow[] = [];
    const sold: OptionDaySoldRow[] = [];
    for (const v of this.optionContractViews()) {
      const pt = v.series.points.find((p) => p.snapshotDate.slice(0, 10) === day);
      if (pt && (Number(pt.quantity) || 0) > 0) {
        const qty = Number(pt.quantity) || 0;
        const mv = Number(pt.marketValue) || 0;
        const upnl = Number(pt.unrealizedPnL) || 0;
        const cost = Number(pt.costBasis) || 0;
        held.push({
          groupKey: v.groupKey,
          chainSymbol: (v.contract.chainSymbol || '—').toUpperCase(),
          expirationDate: v.expirationDate,
          label: v.contract.label,
          contractKey: v.contract.contractKey,
          quantity: qty,
          marketValue: mv,
          unrealizedPnl: upnl,
          unrealizedPnlPct: this.pnlPct(upnl, cost),
        });
      }
      for (const a of v.activities) {
        if (a.date !== day || (a.side !== 'sell' && a.side !== 'close')) {
          continue;
        }
        sold.push({
          groupKey: v.groupKey,
          chainSymbol: (v.contract.chainSymbol || '—').toUpperCase(),
          expirationDate: v.expirationDate,
          label: v.contract.label,
          contractKey: v.contract.contractKey,
          soldQty: Math.abs(a.delta),
          realizedPnl: a.identityMerge ? 0 : (a.realizedPnl ?? 0),
          realizedPnlPct: a.identityMerge ? null : a.realizedPnlPct,
          identityMerge: a.identityMerge,
        });
      }
    }
    held.sort((a, b) => a.groupKey.localeCompare(b.groupKey) || a.label.localeCompare(b.label));
    sold.sort((a, b) => a.groupKey.localeCompare(b.groupKey) || a.label.localeCompare(b.label));
    const soldReal = sold.filter((s) => !s.identityMerge);
    const heldGroups = this.groupDayRows(held);
    const soldGroups = this.groupDaySoldRows(soldReal);
    const heldPnl = held.reduce((s, r) => s + r.unrealizedPnl, 0);
    const soldPnl = soldReal.reduce((s, r) => s + r.realizedPnl, 0);
    return {
      day,
      held,
      sold,
      soldRealCount: soldReal.length,
      heldGroups,
      soldGroups,
      heldPnl,
      soldPnl,
    };
  });

  readonly optionsTotals = computed(() => {
    const views = this.optionContractViews();
    const open = views.filter((v) => v.open);
    const pnl = open.reduce((s, v) => s + v.pnl, 0);
    const cost = open.reduce((s, v) => s + (Number(v.contract.latestCostBasis) || 0), 0);
    const mv = open.reduce((s, v) => s + (Number(v.contract.latestMarketValue) || 0), 0);
    const openQty = open.reduce((s, v) => s + (Number(v.contract.latestQuantity) || 0), 0);
    let realizedPnl = 0;
    let realizedCost = 0;
    for (const v of views) {
      for (const a of v.activities) {
        if ((a.side === 'sell' || a.side === 'close') && !a.identityMerge && a.realizedPnl != null) {
          realizedPnl += a.realizedPnl;
          if (a.realizedPnlPct != null && Math.abs(a.realizedPnlPct) > 0.0001) {
            realizedCost += a.realizedPnl / (a.realizedPnlPct / 100);
          }
        }
      }
    }
    const pnlPct = this.pnlPct(pnl, cost);
    const realizedPnlPct = this.pnlPct(realizedPnl, realizedCost);
    return {
      openQty,
      mv,
      cost,
      pnl,
      pnlPct,
      realizedPnl,
      realizedPnlPct,
      heat: this.pnlHeat(pnl, pnlPct),
    };
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
    const monthStart = this.calendarMonth().slice(0, 10);
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
    const selected = this.selectedDate();

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
    const d = this.selectedDate();
    if (!d) {
      return null;
    }
    return this.pointByDate().get(d) ?? null;
  });

  readonly selectedChange = computed(() => {
    const d = this.selectedDate();
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
    this.selectedDate.set(null);
    this.selectedOptionsContractKey.set(null);
    this.selectedOptionsDate.set(null);
    this.optionsChainFilter.set('');
    this.optionsMonthFilter.set('');
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
    if (r.assetKind === 'option' && !r.contractKey) {
      const months = this.optionMonthsInYear();
      const prefer =
        months.find((m) => m === this.todayIso().slice(0, 7)) ||
        months[months.length - 1] ||
        `${r.year}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
      this.calendarMonth.set(prefer.slice(0, 7) + '-01');
      return;
    }
    const changes = this.changeRows();
    const prefer =
      changes.length > 0
        ? changes[changes.length - 1].date
        : r.points.length
          ? r.points[r.points.length - 1].snapshotDate.slice(0, 10)
          : null;
    if (prefer) {
      this.calendarMonth.set(prefer.slice(0, 7) + '-01');
      const sel = this.selectedDate();
      if (!sel || sel.slice(0, 4) !== String(r.year)) {
        this.selectedDate.set(prefer);
      }
    } else {
      let month = `${r.year}-${String(new Date().getMonth() + 1).padStart(2, '0')}-01`;
      if (Number(month.slice(0, 4)) !== r.year) {
        month = `${r.year}-01-01`;
      }
      this.calendarMonth.set(month);
    }
  }

  onYearChange(): void {
    this.selectedDate.set(null);
    this.selectedOptionsDate.set(null);
    this.selectedOptionsContractKey.set(null);
    this.optionsMonthFilter.set('');
    this.calendarMonth.set(`${this.reportYear}-01-01`);
    this.load();
  }

  onSymbolChange(sym: string): void {
    this.symbol = sym;
    this.selectedDate.set(null);
    this.load();
  }

  onContractChange(key: string): void {
    this.contractKey = key;
    this.selectedDate.set(null);
    this.load();
  }

  selectContract(key: string): void {
    this.contractKey = key;
    this.selectedDate.set(null);
    this.load();
  }

  showAllContracts(): void {
    this.contractKey = '';
    this.selectedDate.set(null);
    this.selectedOptionsContractKey.set(null);
    this.load();
  }

  setOptionsChainFilter(chain: string): void {
    this.optionsChainFilter.set(chain);
    this.selectedOptionsContractKey.set(null);
  }

  setOptionsMonthFilter(ym: string): void {
    this.optionsMonthFilter.set(ym);
    if (ym) {
      this.calendarMonth.set(ym.slice(0, 7) + '-01');
    }
  }

  selectOptionsContract(key: string): void {
    this.selectedOptionsContractKey.set(key);
    const view = this.optionContractViews().find((v) => v.contract.contractKey === key);
    const focus =
      view?.activities[view.activities.length - 1]?.date ||
      view?.contract.closedDate?.slice(0, 10) ||
      view?.contract.firstDate?.slice(0, 10);
    if (focus) {
      this.calendarMonth.set(focus.slice(0, 7) + '-01');
      this.selectedOptionsDate.set(focus);
    }
  }

  selectOptionsDay(cell: OptionCalCell): void {
    if (cell.type !== 'day' || !cell.activityCount) {
      return;
    }
    this.selectedOptionsDate.set(cell.date);
    this.selectedOptionsContractKey.set(null);
  }

  shiftCalendarMonth(delta: number): void {
    const [y, m] = this.calendarMonth().slice(0, 7).split('-').map(Number);
    const d = new Date(y, m - 1 + delta, 1);
    const nextYear = d.getFullYear();
    if (nextYear !== this.reportYear) {
      return;
    }
    this.calendarMonth.set(`${nextYear}-${String(d.getMonth() + 1).padStart(2, '0')}-01`);
  }

  canShiftMonth(delta: number): boolean {
    const [y, m] = this.calendarMonth().slice(0, 7).split('-').map(Number);
    const d = new Date(y, m - 1 + delta, 1);
    return d.getFullYear() === this.reportYear;
  }

  jumpToMonth(ym: string): void {
    if (!ym || ym.length < 7) {
      return;
    }
    this.calendarMonth.set(ym.slice(0, 7) + '-01');
  }

  selectDay(cell: CalendarCell): void {
    if (cell.type !== 'day') {
      return;
    }
    this.selectedDate.set(cell.date);
  }

  focusDate(iso: string): void {
    const day = iso.slice(0, 10);
    this.jumpToMonth(day);
    this.selectedDate.set(day);
  }

  calendarMonthLabel(): string {
    const [y, m] = this.calendarMonth().slice(0, 7).split('-').map(Number);
    return new Date(y, m - 1, 1).toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  activitiesFromContract(
    contract: RobinhoodOwnershipContractDto,
    points: RobinhoodOwnershipHistoryPointDto[],
    allSeries: RobinhoodOwnershipContractSeriesDto[],
  ): OptionActivity[] {
    const out: OptionActivity[] = [];
    for (let i = 0; i < points.length; i++) {
      const cur = Number(points[i].quantity) || 0;
      const prev = i === 0 ? 0 : Number(points[i - 1].quantity) || 0;
      const delta = cur - prev;
      if (Math.abs(delta) < 0.0000005) {
        continue;
      }
      const side: OptionActivity['side'] = delta > 0 ? 'buy' : 'sell';
      const prior = i > 0 ? points[i - 1] : null;
      const realized =
        side === 'sell' && prior ? this.estimateRealizedSell(prior, Math.abs(delta)) : { pnl: null, pct: null };
      out.push({
        date: points[i].snapshotDate.slice(0, 10),
        side,
        from: prev,
        to: cur,
        delta,
        realizedPnl: realized.pnl,
        realizedPnlPct: realized.pct,
        identityMerge: false,
        marketValue: points[i].marketValue,
        costBasis: points[i].costBasis,
      });
    }
    // Lot vanishes without a zero-qty print — close, or identity merge when avg-price key reshuffles.
    if (!contract.currentlyOpen && points.length) {
      const last = points[points.length - 1];
      const lastQty = Number(last.quantity) || 0;
      const lastDate = (contract.closedDate || last.snapshotDate).slice(0, 10);
      const alreadyClosedToZero = out.some((a) => a.to === 0 && (a.side === 'sell' || a.side === 'close'));
      if (lastQty > 0 && !alreadyClosedToZero) {
        const merge = this.isIdentityMergeClose(contract, lastDate, allSeries);
        const realized = merge
          ? { pnl: null as number | null, pct: null as number | null }
          : this.estimateRealizedSell(last, lastQty);
        out.push({
          date: lastDate,
          side: 'close',
          from: lastQty,
          to: 0,
          delta: -lastQty,
          realizedPnl: realized.pnl,
          realizedPnlPct: realized.pct,
          identityMerge: merge,
          marketValue: last.marketValue,
          costBasis: last.costBasis,
        });
      }
    }
    return out;
  }

  /** Same underlying + expiry + strike + type — used to detect avg-price key reshuffles. */
  optionIdentityKey(c: RobinhoodOwnershipContractDto): string {
    const chain = (c.chainSymbol || '').toUpperCase();
    const exp = c.expirationDate?.slice(0, 10) || '';
    const typ = (c.optionType || '').toUpperCase();
    const strike = c.strikePrice == null ? '' : String(c.strikePrice);
    return `${chain}|${exp}|${typ}|${strike}`;
  }

  optionGroupKey(chainSymbol: string, expirationDate: string | null): string {
    return `${(chainSymbol || '—').toUpperCase()}|${expirationDate || 'open'}`;
  }

  /**
   * Estimate realized P&amp;L for sold contracts from the pre-trade snapshot:
   * sold share of that day's mark-to-market unrealized.
   */
  estimateRealizedSell(
    prior: RobinhoodOwnershipHistoryPointDto,
    soldQty: number,
  ): { pnl: number | null; pct: number | null } {
    const fromQty = Number(prior.quantity) || 0;
    if (soldQty <= 0 || fromQty <= 0) {
      return { pnl: null, pct: null };
    }
    const fraction = Math.min(1, soldQty / fromQty);
    const upnl = Number(prior.unrealizedPnL);
    const cost = Number(prior.costBasis) || 0;
    if (!Number.isFinite(upnl)) {
      const mv = Number(prior.marketValue) || 0;
      if (cost <= 0 && mv <= 0) {
        return { pnl: null, pct: null };
      }
      const pnl = (mv - cost) * fraction;
      const soldCost = cost * fraction;
      return { pnl, pct: this.pnlPct(pnl, soldCost) };
    }
    const pnl = upnl * fraction;
    const soldCost = cost * fraction;
    return { pnl, pct: this.pnlPct(pnl, soldCost) };
  }

  isIdentityMergeClose(
    contract: RobinhoodOwnershipContractDto,
    closedDate: string,
    allSeries: RobinhoodOwnershipContractSeriesDto[],
  ): boolean {
    const identity = this.optionIdentityKey(contract);
    if (!identity || identity.startsWith('|')) {
      return false;
    }
    for (const s of allSeries) {
      if (s.contract.contractKey === contract.contractKey) {
        continue;
      }
      if (this.optionIdentityKey(s.contract) !== identity) {
        continue;
      }
      if (s.contract.currentlyOpen) {
        return true;
      }
      const otherFirst = s.contract.firstDate?.slice(0, 10) || '';
      const otherLast = s.contract.lastDate?.slice(0, 10) || '';
      if (otherFirst && otherFirst <= closedDate && (!otherLast || otherLast >= closedDate)) {
        return true;
      }
      if (otherFirst && closedDate) {
        const gap = this.dayDiff(closedDate, otherFirst);
        if (gap >= 0 && gap <= 5) {
          return true;
        }
      }
    }
    return false;
  }

  dayDiff(fromIso: string, toIso: string): number {
    const a = new Date(fromIso + 'T12:00:00').getTime();
    const b = new Date(toIso + 'T12:00:00').getTime();
    if (!Number.isFinite(a) || !Number.isFinite(b)) {
      return 999;
    }
    return Math.round((b - a) / 86400000);
  }

  groupDayRows(rows: OptionDayHeldRow[]): Array<{
    key: string;
    chainSymbol: string;
    expirationDate: string | null;
    rows: OptionDayHeldRow[];
    quantity: number;
    unrealizedPnl: number;
  }> {
    const map = new Map<string, OptionDayHeldRow[]>();
    for (const r of rows) {
      const list = map.get(r.groupKey) ?? [];
      list.push(r);
      map.set(r.groupKey, list);
    }
    return [...map.entries()].map(([key, groupRows]) => ({
      key,
      chainSymbol: groupRows[0]?.chainSymbol || '—',
      expirationDate: groupRows[0]?.expirationDate ?? null,
      rows: groupRows,
      quantity: groupRows.reduce((s, r) => s + r.quantity, 0),
      unrealizedPnl: groupRows.reduce((s, r) => s + r.unrealizedPnl, 0),
    }));
  }

  groupDaySoldRows(rows: OptionDaySoldRow[]): Array<{
    key: string;
    chainSymbol: string;
    expirationDate: string | null;
    rows: OptionDaySoldRow[];
    soldQty: number;
    realizedPnl: number;
  }> {
    const map = new Map<string, OptionDaySoldRow[]>();
    for (const r of rows) {
      const list = map.get(r.groupKey) ?? [];
      list.push(r);
      map.set(r.groupKey, list);
    }
    return [...map.entries()].map(([key, groupRows]) => ({
      key,
      chainSymbol: groupRows[0]?.chainSymbol || '—',
      expirationDate: groupRows[0]?.expirationDate ?? null,
      rows: groupRows,
      soldQty: groupRows.reduce((s, r) => s + r.soldQty, 0),
      realizedPnl: groupRows.reduce((s, r) => s + r.realizedPnl, 0),
    }));
  }

  expiryLabel(iso: string | null | undefined): string {
    if (!iso) {
      return 'No expiry';
    }
    const d = iso.slice(0, 10);
    const dt = new Date(d + 'T12:00:00');
    if (Number.isNaN(dt.getTime())) {
      return d;
    }
    return dt.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }

  pnlPct(pnl: number, cost: number | null | undefined): number | null {
    const c = Number(cost) || 0;
    if (c <= 0) {
      return null;
    }
    return (pnl / c) * 100;
  }

  pnlHeat(pnl: number, pct: number | null): number {
    if (pct != null && Number.isFinite(pct)) {
      return Math.min(1, Math.abs(pct) / 40);
    }
    if (!Number.isFinite(pnl) || pnl === 0) {
      return 0.15;
    }
    return Math.min(1, Math.abs(pnl) / 2500);
  }

  heatStyle(heat: number, pnl: number): Record<string, string> {
    const h = Math.max(0, Math.min(1, heat));
    const tone = pnl >= 0 ? 'pos' : 'neg';
    return {
      '--own-heat': String(h),
      '--own-pnl-tone': tone,
    };
  }

  monthChipLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    if (!y || !m) {
      return ym;
    }
    return new Date(y, m - 1, 1).toLocaleString(undefined, { month: 'short' });
  }

  optionCellTooltip(cell: OptionCalCell): string {
    if (cell.type !== 'day') {
      return '';
    }
    const parts = [this.formatDay(cell.date)];
    if (cell.heldCount) {
      parts.push(`${cell.heldCount} held`);
    }
    if (cell.soldCount) {
      parts.push(`${cell.soldCount} sold`);
    }
    if (cell.realizedPnl != null && cell.soldCount) {
      const pct =
        cell.realizedPnlPct != null ? ` (${cell.realizedPnlPct.toFixed(1)}%)` : '';
      parts.push(
        `realized ${cell.realizedPnl.toLocaleString(undefined, { maximumFractionDigits: 0 })}${pct}`,
      );
    }
    return parts.join(' · ');
  }

  activitySideLabel(side: OptionActivity['side']): string {
    if (side === 'buy') {
      return 'Buy';
    }
    if (side === 'sell') {
      return 'Sell';
    }
    return 'Closed';
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
