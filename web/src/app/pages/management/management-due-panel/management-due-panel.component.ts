import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  BankingPlaidOpeningBalancesDto,
  RobinhoodExecutedTradeDto,
  RobinhoodRhAccountsTrackDto,
  RobinhoodRhPeriodBalancesDto,
} from '../../../models/finance.models';
import {
  ManagementDueCategoryDto,
  ManagementDueDayDto,
  ManagementDueItemWriteBody,
  ManagementDueReportRowDto,
  ManagementDueMonthDto,
  ManagementDueOccurrenceDto,
  ManagementDueSide,
} from '../../../models/management.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  DueOpeningGroup,
  DueOpeningMonth,
  ManagementDueReportsComponent,
} from '../management-due-reports/management-due-reports.component';

type SalesView = 'off' | 'with' | 'only';


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
    MatButtonToggleModule,
    MatSelectModule,
    MatSnackBarModule,
    ManagementDueReportsComponent,
  ],
  templateUrl: './management-due-panel.component.html',
  styleUrl: './management-due-panel.component.scss',
})
export class ManagementDuePanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** First sale day that may appear on Due. Earlier Robinhood sales stay off this calendar. */
  private static readonly SALES_START = '2026-09-01';

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  selectedIso = '';
  monthData: ManagementDueMonthDto | null = null;
  calRows: DueCalCell[][] = [];
  reportRevision = 0;
  loading = false;
  saving = false;
  editingItemId: number | null = null;
  /** Off keeps the calendar as bills only. With bills adds sale days. Sales only hides bills. */
  salesView: SalesView = 'off';
  /** Off hides month-open balances. Show puts them on the 1st and in reports. */
  showOpenings = false;
  dueCategories: ManagementDueCategoryDto[] = [];
  private salesByDate = new Map<string, ManagementDueOccurrenceDto>();
  /** Settled Robinhood (Sales) rows for the loaded year. Reports include these when sales are on. */
  salesReportRows: ManagementDueReportRowDto[] = [];
  private salesYearLoaded: number | null = null;
  private readonly openingByYear = new Map<number, Map<string, DueOpeningGroup[]>>();
  private openingYearLoading: number | null = null;
  private salesYearLoading: number | null = null;

  draft = this.emptyDraft();

  private static readonly CLEAR_LATER_KEY = 'tracker.due.clearedLater.v1';

  ngOnInit(): void {
    const today = this.todayIso();
    this.selectedIso = today;
    this.rebuildCalendar();
    this.loadCategories();
    this.refreshAll();
  }

  private loadCategories(): void {
    this.api.listDueCategories().subscribe({
      next: (rows) => {
        this.dueCategories = [...rows].sort((a, b) => b.sortOrder - a.sortOrder || a.name.localeCompare(b.name));
      },
      error: () => {
        this.dueCategories = [];
      },
    });
  }

  refreshAll(): void {
    this.maybeClearLaterDates();
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

  onReportSelect(iso: string): void {
    const year = Number(iso.slice(0, 4));
    const month = Number(iso.slice(5, 7));
    this.selectedIso = iso;
    if (year && month && (year !== this.year || month !== this.month)) {
      this.loadMonth(year, month);
    }
  }

  setSalesView(view: SalesView): void {
    this.salesView = view;
    if (view !== 'off') {
      this.ensureSales();
    }
  }

  setOpenings(on: boolean): void {
    this.showOpenings = on;
    if (on) {
      this.ensureOpenings();
    }
  }

  get openingReportMonths(): DueOpeningMonth[] {
    if (!this.showOpenings) {
      return [];
    }
    const months = this.openingByYear.get(this.year);
    if (!months) {
      return [];
    }
    return [...months.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, groups]) => {
        const year = Number(key.slice(0, 4));
        const month = Number(key.slice(5, 7));
        return {
          key,
          iso: `${key}-01`,
          label: new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' }),
          groups,
        };
      });
  }

  visibleItems(iso: string | undefined, day: ManagementDueDayDto | undefined): ManagementDueOccurrenceDto[] {
    const bills = day?.items ?? [];
    if (this.salesView === 'off' || !iso) {
      return bills;
    }
    const sale = this.salesByDate.get(iso);
    if (this.salesView === 'only') {
      return sale ? [sale] : [];
    }
    return sale ? [...bills, sale] : bills;
  }

  isSale(row: ManagementDueOccurrenceDto): boolean {
    return row.amountSource === 'market-sale';
  }

  get summaryPaid(): number {
    return this.summaryAmount('PAYABLE', this.month);
  }

  get summaryReceived(): number {
    return this.summaryAmount('RECEIVABLE', this.month);
  }

  get summaryNet(): number {
    return Math.round((this.summaryReceived - this.summaryPaid) * 100) / 100;
  }

  get summaryYearPaid(): number {
    return this.summaryAmount('PAYABLE', null);
  }

  get summaryYearReceived(): number {
    return this.summaryAmount('RECEIVABLE', null);
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
      categoryId: row.categoryId,
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
      categoryId: this.draft.categoryId,
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

  openingGroups(iso: string | undefined): DueOpeningGroup[] {
    if (!this.showOpenings || !iso || !iso.endsWith('-01') || iso < '2026-09-01') {
      return [];
    }
    const year = Number(iso.slice(0, 4));
    return this.openingByYear.get(year)?.get(iso.slice(0, 7)) ?? [];
  }

  openingLabel(iso: string | undefined): string {
    const groups = this.openingGroups(iso);
    if (!groups.length) {
      return '';
    }
    const total = groups.reduce((sum, group) => sum + group.total, 0);
    return this.compactMoney(total);
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
    this.reportRevision += 1;
    this.ensureSelectedInMonth();
    this.rebuildCalendar();
    if (this.showOpenings) {
      this.ensureOpenings();
    }
    if (this.salesView !== 'off') {
      this.ensureSales();
    }
  }

  private ensureOpenings(): void {
    const year = this.year;
    if (year < 2026 || this.openingByYear.has(year) || this.openingYearLoading === year) {
      return;
    }
    this.openingYearLoading = year;
    forkJoin({
      periods: this.financeApi.robinhoodDailyTrackerPeriodBalances(year).pipe(catchError(() => of(null))),
      track: this.financeApi.robinhoodRhAccountsTrack(false).pipe(catchError(() => of(null))),
      plaid: this.financeApi.bankingPlaidOpeningBalances(year).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ periods, track, plaid }) => {
        if (this.openingYearLoading === year) {
          this.openingYearLoading = null;
        }
        this.openingByYear.set(year, this.withPlaidOpenings(this.groupsFromBalances(periods, track), plaid));
      },
      error: () => {
        if (this.openingYearLoading === year) {
          this.openingYearLoading = null;
        }
        this.openingByYear.set(year, new Map());
      },
    });
  }

  private groupsFromBalances(
    report: RobinhoodRhPeriodBalancesDto | null,
    track: RobinhoodRhAccountsTrackDto | null,
  ): Map<string, DueOpeningGroup[]> {
    const out = new Map<string, DueOpeningGroup[]>();
    const roth = this.rothOpening(track);
    const labels = new Map((report?.accounts ?? []).map((account) => [account.accountSuffix, account.label]));
    for (const month of report?.months ?? []) {
      if (!month.key || month.key < '2026-09') {
        continue;
      }
      const accounts = (month.accounts ?? [])
        .filter((account) => account.start != null && Number.isFinite(Number(account.start)))
        .map((account) => ({
          label: labels.get(account.accountSuffix) || `Account (...${account.accountSuffix})`,
          amount: Math.round(Number(account.start) * 100) / 100,
        }));
      if (roth && !accounts.some((account) => account.label.startsWith('Roth IRA'))) {
        accounts.push(roth);
      }
      accounts.sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label));
      if (!accounts.length) {
        continue;
      }
      const total = Math.round(accounts.reduce((sum, account) => sum + account.amount, 0) * 100) / 100;
      out.set(month.key, [{ institution: 'Robinhood', total, accounts }]);
    }
    return out;
  }

  /** Latest Robinhood portfolio for the Roth IRA. Daily Tracker does not close this account. */
  private rothOpening(track: RobinhoodRhAccountsTrackDto | null): { label: string; amount: number } | null {
    const row = (track?.accounts ?? []).find((account) => account.accountSuffix === '2835');
    const amount = row?.totalAccountValue;
    if (amount == null || !Number.isFinite(Number(amount))) {
      return null;
    }
    return { label: 'Roth IRA (...2835)', amount: Math.round(Number(amount) * 100) / 100 };
  }

  private withPlaidOpenings(
    base: Map<string, DueOpeningGroup[]>,
    plaid: BankingPlaidOpeningBalancesDto | null,
  ): Map<string, DueOpeningGroup[]> {
    const out = new Map(base);
    for (const month of plaid?.months ?? []) {
      if (!month.key || month.key < '2026-09') {
        continue;
      }
      const incoming = (month.groups ?? [])
        .map((group) => ({
          institution: group.institution,
          total: Math.round(Number(group.total) * 100) / 100,
          accounts: (group.accounts ?? [])
            .filter((account) => account.amount != null && Number.isFinite(Number(account.amount)))
            .map((account) => ({
              label: account.label,
              amount: Math.round(Number(account.amount) * 100) / 100,
            })),
        }))
        .filter((group) => group.accounts.length);
      if (!incoming.length) {
        continue;
      }
      const names = new Set(incoming.map((group) => group.institution));
      const kept = (out.get(month.key) ?? []).filter((group) => !names.has(group.institution));
      out.set(
        month.key,
        [...kept, ...incoming].sort((a, b) => a.institution.localeCompare(b.institution)),
      );
    }
    return out;
  }

  private ensureSales(): void {
    if (this.year < 2026) {
      return;
    }
    if (this.salesYearLoaded === this.year || this.salesYearLoading === this.year) {
      return;
    }
    const year = this.year;
    this.salesYearLoading = year;
    this.financeApi.robinhoodExecutedTrades(year).subscribe({
      next: (report) => {
        if (this.salesYearLoading === year) {
          this.salesYearLoading = null;
        }
        if (this.year !== year) {
          this.ensureSales();
          return;
        }
        this.salesByDate = this.salesForYear(report?.trades ?? []);
        this.salesReportRows = this.toSalesReportRows(this.salesByDate);
        this.salesYearLoaded = year;
      },
      error: (err) => {
        if (this.salesYearLoading === year) {
          this.salesYearLoading = null;
        }
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load Robinhood sales', undefined, {
          duration: 3200,
        });
      },
    });
  }

  private salesForYear(trades: RobinhoodExecutedTradeDto[]): Map<string, ManagementDueOccurrenceDto> {
    const byDate = new Map<string, { net: number; symbols: string[] }>();
    for (const trade of trades) {
      if (!(trade.side ?? '').trim().toLowerCase().startsWith('sell') || !trade.executedAt) {
        continue;
      }
      const date = this.centralIsoDate(trade.executedAt);
      if (!date || date < ManagementDuePanelComponent.SALES_START) {
        continue;
      }
      const row = byDate.get(date) ?? { net: 0, symbols: [] };
      if (trade.realizedPnl != null && Number.isFinite(Number(trade.realizedPnl))) {
        row.net += Number(trade.realizedPnl);
      }
      const symbol = (trade.symbol ?? '').trim().toUpperCase();
      if (symbol && !row.symbols.includes(symbol)) {
        row.symbols.push(symbol);
      }
      byDate.set(date, row);
    }
    const out = new Map<string, ManagementDueOccurrenceDto>();
    for (const [date, row] of byDate) {
      const net = Math.round(row.net * 100) / 100;
      const side: ManagementDueSide = net < 0 ? 'PAYABLE' : 'RECEIVABLE';
      const amount = Math.abs(net);
      out.set(date, {
        itemId: -Number(date.replaceAll('-', '')),
        occurrenceId: null,
        side,
        counterparty: 'Robinhood (Sales)',
        recurring: false,
        dayOfMonth: null,
        oneOffDate: date,
        occurrenceDate: date,
        amountOverride: amount,
        estimatedAmount: null,
        displayAmount: amount,
        amountSource: 'market-sale',
        notes: row.symbols.length ? `Sold ${row.symbols.join(', ')}` : 'Sale',
        settled: true,
        settledAmount: amount,
        categoryId: null,
        category: null,
        categorySort: null,
      });
    }
    return out;
  }

  /** Bill totals, plus sale days when that view is on. Sales only drops the bills. */
  private summaryAmount(side: ManagementDueSide, month: number | null): number {
    const bill =
      this.salesView === 'only'
        ? 0
        : Number(
            (month == null
              ? side === 'PAYABLE'
                ? this.monthData?.yearPaidTotal
                : this.monthData?.yearReceivedTotal
              : side === 'PAYABLE'
                ? this.monthData?.paidTotal
                : this.monthData?.receivedTotal) || 0
          );
    if (this.salesView === 'off') {
      return bill;
    }
    const prefix =
      month == null ? `${this.year}-` : `${this.year}-${String(month).padStart(2, '0')}-`;
    let sales = 0;
    for (const [date, row] of this.salesByDate) {
      if (date.startsWith(prefix) && row.side === side) {
        sales += Number(row.displayAmount || 0);
      }
    }
    return Math.round((bill + sales) * 100) / 100;
  }

  private toSalesReportRows(byDate: Map<string, ManagementDueOccurrenceDto>): ManagementDueReportRowDto[] {
    const rows: ManagementDueReportRowDto[] = [];
    for (const [date, sale] of byDate) {
      const year = Number(date.slice(0, 4));
      const month = Number(date.slice(5, 7));
      rows.push({
        itemId: sale.itemId,
        occurrenceId: null,
        year,
        month,
        date,
        side: sale.side,
        counterparty: sale.counterparty,
        recurring: false,
        notes: sale.notes || '',
        settled: true,
        amount: sale.displayAmount,
        amountSource: 'market-sale',
        category: null,
      });
    }
    return rows;
  }

  private centralIsoDate(iso: string): string | null {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return null;
    }
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Chicago',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
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
      categoryId: null as number | null,
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
