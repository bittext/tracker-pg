import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import {
  RobinhoodRhDailyTrackerAccountCellDto,
  RobinhoodRhDailyTrackerAccountColumnDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerManualCaptureAccountDto,
  RobinhoodRhDailyTrackerManualCaptureDto,
  RobinhoodRhDailyTrackerReportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  RobinhoodDailySnapshotDialogComponent,
  RobinhoodDailySnapshotDialogData,
  RH_SNAPSHOT_DIALOG_CONFIG,
} from './robinhood-daily-snapshot-dialog.component';

/** One row in the intraday capture comparison table. */
export interface RhDailyCaptureTimelineRow {
  capturedAt: string;
  kind: 'scheduled' | 'intraday' | 'manual';
  timeLabel: string;
  combinedTotal: number;
  changeFromPrior: number | null;
  accounts: RhDailyCaptureTimelineAccountCell[];
}

export interface RhDailyCaptureTimelineAccountCell {
  snapshotId: number;
  accountSuffix: string;
  label: string;
  totalAccountValue: number;
  changeFromPrior: number | null;
  positionsChangedFromPrior: boolean;
}

@Component({
  selector: 'app-reports-finance-robinhood-daily-tracker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-daily-tracker.component.html',
  styleUrl: './reports-finance-robinhood-daily-tracker.component.scss',
})
export class ReportsFinanceRobinhoodDailyTrackerComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  reportYear = new Date().getFullYear();
  /** Empty = all months in the selected year. */
  reportMonths: number[] = [new Date().getMonth() + 1];
  loading = false;
  capturing = false;
  tracker: RobinhoodRhDailyTrackerReportDto | null = null;

  /** snapshotDate keys for expanded 9 PM day rows */
  private readonly expandedDays = new Set<string>();
  /** dayDate keys where the manual-captures section is collapsed */
  private readonly collapsedManualSections = new Set<string>();
  /** dayDate|capturedAt keys for expanded manual capture rows */
  private readonly expandedManuals = new Set<string>();
  /** dayDate|capturedAt key currently being deleted */
  deletingManualKey: string | null = null;
  /** Editable call-summary note drafts keyed by snapshotDate */
  readonly noteDrafts = new Map<string, string>();
  /** snapshotDate keys with note save in flight */
  private readonly savingNoteDays = new Set<string>();
  /** snapshotDate keys where call-summary notes section is collapsed */
  private readonly collapsedSummaryNotes = new Set<string>();
  /** snapshotDate keys where collapsible sections are collapsed (default expanded) */
  private readonly collapsedTrades = new Set<string>();
  private readonly collapsedTimeline = new Set<string>();
  private readonly collapsedFlows = new Set<string>();
  private readonly collapsedAccounts = new Set<string>();

  /** Classic expandable cards vs side-by-side capture timeline. */
  viewMode: 'classic' | 'timeline' = this.loadViewMode();

  private static readonly VIEW_MODE_STORAGE_KEY = 'rh-daily-tracker-view-mode';
  private static readonly EXPANSION_STORAGE_PREFIX = 'rh-daily-tracker-expansion';

  readonly monthChoices = [
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

  ngOnInit(): void {
    this.load();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  load(): void {
    this.loading = true;
    const months = this.normalizedReportMonths();
    this.financeApi.robinhoodDailyTracker(this.reportYear, months).subscribe({
      next: (t) => {
        this.tracker = t;
        const validDates = new Set(t.days.map((d) => d.snapshotDate));
        this.mergeExpansionStateFromStorage(validDates);
        this.pruneExpansionState(validDates);
        this.syncNoteDrafts(t.days);
        this.loading = false;
      },
      error: (err) => {
        this.tracker = null;
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  captureNow(): void {
    this.capturing = true;
    this.financeApi.robinhoodDailyTrackerCapture(true).subscribe({
      next: (r) => {
        this.capturing = false;
        this.snackBar.open(r.message, 'OK', { duration: 6000 });
        this.load();
      },
      error: (err) => {
        this.capturing = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  isDayExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return this.expandedDays.has(day.snapshotDate);
  }

  toggleDay(day: RobinhoodRhDailyTrackerDayDto): void {
    if (this.expandedDays.has(day.snapshotDate)) {
      this.expandedDays.delete(day.snapshotDate);
      this.collapsedManualSections.delete(day.snapshotDate);
      for (const key of [...this.expandedManuals]) {
        if (key.startsWith(day.snapshotDate + '|')) {
          this.expandedManuals.delete(key);
        }
      }
    } else {
      this.expandedDays.add(day.snapshotDate);
    }
    this.persistExpansionState();
  }

  isManualSectionExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedManualSections.has(day.snapshotDate);
  }

  toggleManualSection(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    if (this.collapsedManualSections.has(day.snapshotDate)) {
      this.collapsedManualSections.delete(day.snapshotDate);
    } else {
      this.collapsedManualSections.add(day.snapshotDate);
      for (const key of [...this.expandedManuals]) {
        if (key.startsWith(day.snapshotDate + '|')) {
          this.expandedManuals.delete(key);
        }
      }
    }
    this.persistExpansionState();
  }

  isManualExpanded(day: RobinhoodRhDailyTrackerDayDto, capture: RobinhoodRhDailyTrackerManualCaptureDto): boolean {
    return this.expandedManuals.has(this.manualKey(day, capture));
  }

  toggleManual(day: RobinhoodRhDailyTrackerDayDto, capture: RobinhoodRhDailyTrackerManualCaptureDto, event: Event): void {
    event.stopPropagation();
    const key = this.manualKey(day, capture);
    if (this.expandedManuals.has(key)) {
      this.expandedManuals.delete(key);
    } else {
      this.expandedManuals.add(key);
    }
    this.persistExpansionState();
  }

  deleteManualCapture(
    day: RobinhoodRhDailyTrackerDayDto,
    capture: RobinhoodRhDailyTrackerManualCaptureDto,
    event: Event,
  ): void {
    event.stopPropagation();
    const timeLabel = new Date(capture.capturedAt).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit',
    });
    if (
      !window.confirm(
        `Delete the manual capture from ${timeLabel} (${capture.accounts.length} account${capture.accounts.length === 1 ? '' : 's'})? This cannot be undone.`,
      )
    ) {
      return;
    }
    const key = this.manualKey(day, capture);
    this.deletingManualKey = key;
    this.financeApi.robinhoodDailyTrackerDeleteManualCapture(capture.capturedAt).subscribe({
      next: (r) => {
        this.deletingManualKey = null;
        this.expandedManuals.delete(key);
        this.snackBar.open(r.message, 'OK', { duration: 5000 });
        this.load();
      },
      error: (err) => {
        this.deletingManualKey = null;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  isDeletingManual(day: RobinhoodRhDailyTrackerDayDto, capture: RobinhoodRhDailyTrackerManualCaptureDto): boolean {
    return this.deletingManualKey === this.manualKey(day, capture);
  }

  openManualAccountSnapshot(
    acct: RobinhoodRhDailyTrackerManualCaptureAccountDto,
    day: RobinhoodRhDailyTrackerDayDto,
    event: Event,
  ): void {
    event.stopPropagation();
    this.openSnapshot(
      {
        snapshotId: acct.snapshotId,
        accountSuffix: acct.accountSuffix,
        totalAccountValue: acct.totalAccountValue,
        totalChangeFromPrevious: 0,
        periodAdded: 0,
        periodRemoved: 0,
        periodValueChange: 0,
        hasFlowActivity: false,
        tradeCount: 0,
      },
      day,
    );
  }

  openSnapshot(cell: RobinhoodRhDailyTrackerAccountCellDto, day: RobinhoodRhDailyTrackerDayDto): void {
    if (!cell.snapshotId) {
      return;
    }
    this.dialog.open(RobinhoodDailySnapshotDialogComponent, {
      ...RH_SNAPSHOT_DIALOG_CONFIG,
      data: {
        snapshotId: cell.snapshotId,
        dayLabel: day.snapshotDate,
        accountSuffix: cell.accountSuffix,
        scheduledCaptureEnabled: this.tracker?.autoCaptureScheduled ?? false,
      } satisfies RobinhoodDailySnapshotDialogData,
    });
  }

  cellForDay(day: RobinhoodRhDailyTrackerDayDto, suffix: string): RobinhoodRhDailyTrackerAccountCellDto | undefined {
    return day.accounts.find((a) => a.accountSuffix === suffix);
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'rh-daily__pnl--pos' : 'rh-daily__pnl--neg';
  }

  trendIcon(value: number): string {
    return value >= 0 ? 'trending_up' : 'trending_down';
  }

  /** Percent change vs prior total; null when prior is zero or change is missing. */
  changePercent(change: number | null | undefined, priorTotal: number | null | undefined): number | null {
    if (change == null || priorTotal == null || priorTotal === 0 || !Number.isFinite(change) || !Number.isFinite(priorTotal)) {
      return null;
    }
    return (change / priorTotal) * 100;
  }

  /** Prior total from current value and absolute change. */
  priorFromChange(currentTotal: number, change: number | null | undefined): number | null {
    if (change == null || !Number.isFinite(currentTotal) || !Number.isFinite(change)) {
      return null;
    }
    return currentTotal - change;
  }

  deltaPercentForCurrent(currentTotal: number, change: number | null | undefined): number | null {
    return this.changePercent(change, this.priorFromChange(currentTotal, change));
  }

  setViewMode(mode: 'classic' | 'timeline'): void {
    this.viewMode = mode;
    try {
      localStorage.setItem(ReportsFinanceRobinhoodDailyTrackerComponent.VIEW_MODE_STORAGE_KEY, mode);
    } catch {
      /* ignore storage errors */
    }
  }

  isClassicView(): boolean {
    return this.viewMode === 'classic';
  }

  isTimelineView(): boolean {
    return this.viewMode === 'timeline';
  }

  hasCaptureTimeline(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return day.hasScheduledSnapshot || day.intradayCaptures.length > 0 || day.manualCaptures.length > 0;
  }

  captureTimeline(day: RobinhoodRhDailyTrackerDayDto): RhDailyCaptureTimelineRow[] {
    const entries: Array<{ at: number; row: RhDailyCaptureTimelineRow }> = [];

    if (day.hasScheduledSnapshot && day.snapshotAt) {
      entries.push({
        at: new Date(day.snapshotAt).getTime(),
        row: {
          capturedAt: day.snapshotAt,
          kind: 'scheduled',
          timeLabel: this.tracker?.autoCaptureScheduled ? '9 PM CST' : 'Scheduled',
          combinedTotal: day.combinedTotal,
          changeFromPrior: null,
          accounts: day.accounts.map((cell) => ({
            snapshotId: cell.snapshotId,
            accountSuffix: cell.accountSuffix,
            label: this.accountLabel(cell.accountSuffix),
            totalAccountValue: cell.totalAccountValue,
            changeFromPrior: null,
            positionsChangedFromPrior: cell.positionsChangedFromPrior ?? false,
          })),
        },
      });
    }

    for (const capture of day.intradayCaptures ?? []) {
      entries.push({
        at: new Date(capture.capturedAt).getTime(),
        row: {
          capturedAt: capture.capturedAt,
          kind: 'intraday',
          timeLabel: this.formatCaptureTime(capture.capturedAt),
          combinedTotal: capture.combinedTotal,
          changeFromPrior: null,
          accounts: capture.accounts.map((acct) => ({
            snapshotId: acct.snapshotId,
            accountSuffix: acct.accountSuffix,
            label: acct.label,
            totalAccountValue: acct.totalAccountValue,
            changeFromPrior: null,
            positionsChangedFromPrior: acct.positionsChangedFromPrior ?? false,
          })),
        },
      });
    }

    for (const capture of day.manualCaptures) {
      entries.push({
        at: new Date(capture.capturedAt).getTime(),
        row: {
          capturedAt: capture.capturedAt,
          kind: 'manual',
          timeLabel: this.formatCaptureTime(capture.capturedAt),
          combinedTotal: capture.combinedTotal,
          changeFromPrior: null,
          accounts: capture.accounts.map((acct) => ({
            snapshotId: acct.snapshotId,
            accountSuffix: acct.accountSuffix,
            label: acct.label,
            totalAccountValue: acct.totalAccountValue,
            changeFromPrior: null,
            positionsChangedFromPrior: acct.positionsChangedFromPrior ?? false,
          })),
        },
      });
    }

    entries.sort((a, b) => a.at - b.at);
    const rows = entries.map((e) => e.row);

    let priorCombined: number | null = null;
    const priorBySuffix = new Map<string, number>();
    if (day.hasPriorPull && day.priorPull) {
      priorCombined = day.priorPull.combinedTotal;
      for (const acct of day.priorPull.accounts) {
        priorBySuffix.set(acct.accountSuffix, acct.totalAccountValue);
      }
    }

    for (const row of rows) {
      if (priorCombined != null) {
        row.changeFromPrior = row.combinedTotal - priorCombined;
      }
      for (const acct of row.accounts) {
        if (priorCombined != null) {
          const prior = priorBySuffix.get(acct.accountSuffix) ?? 0;
          acct.changeFromPrior = acct.totalAccountValue - prior;
        }
      }
      priorCombined = row.combinedTotal;
      for (const acct of row.accounts) {
        priorBySuffix.set(acct.accountSuffix, acct.totalAccountValue);
      }
    }

    return rows.reverse();
  }

  showTimelineDelta(value: number | null): boolean {
    return value != null;
  }

  timelineDeltaClass(value: number | null): string {
    if (value == null || value === 0) {
      return 'muted';
    }
    return this.pnlClass(value);
  }

  priorPullHint(day: RobinhoodRhDailyTrackerDayDto): string {
    if (!day.hasPriorPull || !day.priorPull) {
      return 'Newest pull at top. First pull on record — no prior pull to compare.';
    }
    const when = new Date(day.priorPull.snapshotAt);
    const kind = day.priorPull.captureKind === 'MANUAL' ? 'manual' : 'scheduled';
    const whenLabel = when.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
    return `Newest pull at top. The earliest pull today compares to the prior ${kind} pull (${whenLabel}); each row above compares to the pull before it.`;
  }

  openTimelineAccountSnapshot(
    acct: RhDailyCaptureTimelineAccountCell,
    day: RobinhoodRhDailyTrackerDayDto,
    event: Event,
  ): void {
    event.stopPropagation();
    this.openSnapshot(
      {
        snapshotId: acct.snapshotId,
        accountSuffix: acct.accountSuffix,
        totalAccountValue: acct.totalAccountValue,
        totalChangeFromPrevious: 0,
        periodAdded: 0,
        periodRemoved: 0,
        periodValueChange: 0,
        hasFlowActivity: false,
        tradeCount: 0,
      },
      day,
    );
  }

  accountLabel(suffix: string): string {
    return this.tracker?.accounts.find((a) => a.accountSuffix === suffix)?.label ?? `••••${suffix}`;
  }

  formatCaptureTime(capturedAt: string): string {
    return new Date(capturedAt).toLocaleTimeString(undefined, {
      hour: 'numeric',
      minute: '2-digit',
    });
  }

  accountColumns(): RobinhoodRhDailyTrackerAccountColumnDto[] {
    return this.tracker?.accounts ?? [];
  }

  timelineAccountTotal(row: RhDailyCaptureTimelineRow, suffix: string): RhDailyCaptureTimelineAccountCell | undefined {
    return row.accounts.find((a) => a.accountSuffix === suffix);
  }

  timelineRowHasPositionChanges(row: RhDailyCaptureTimelineRow): boolean {
    return row.accounts.some((a) => a.positionsChangedFromPrior);
  }

  hasFlowBlock(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return day.hasScheduledSnapshot && (day.combinedPeriodAdded !== 0 || day.combinedPeriodRemoved !== 0);
  }

  isTradesExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedTrades.has(day.snapshotDate);
  }

  toggleTrades(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    this.toggleCollapsed(this.collapsedTrades, day.snapshotDate);
  }

  isTimelineExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedTimeline.has(day.snapshotDate);
  }

  toggleTimeline(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    this.toggleCollapsed(this.collapsedTimeline, day.snapshotDate);
  }

  isFlowsExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedFlows.has(day.snapshotDate);
  }

  toggleFlows(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    this.toggleCollapsed(this.collapsedFlows, day.snapshotDate);
  }

  isAccountsExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedAccounts.has(day.snapshotDate);
  }

  toggleAccounts(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    this.toggleCollapsed(this.collapsedAccounts, day.snapshotDate);
  }

  sideLabel(side: string | null): string {
    if (!side) {
      return '—';
    }
    return side.charAt(0).toUpperCase() + side.slice(1).toLowerCase();
  }

  sideClass(side: string | null): string {
    const s = (side ?? '').toLowerCase();
    if (s === 'buy') {
      return 'rh-daily__pnl--pos';
    }
    if (s === 'sell') {
      return 'rh-daily__pnl--neg';
    }
    return '';
  }

  hasSummaryNote(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !!(day.summaryNote && day.summaryNote.trim());
  }

  noteDraft(day: RobinhoodRhDailyTrackerDayDto): string {
    return this.noteDrafts.get(day.snapshotDate) ?? day.summaryNote ?? '';
  }

  onNoteDraftChange(day: RobinhoodRhDailyTrackerDayDto, value: string): void {
    this.noteDrafts.set(day.snapshotDate, value);
  }

  isNoteDirty(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return this.noteDraft(day).trim() !== (day.summaryNote ?? '').trim();
  }

  isSavingNote(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return this.savingNoteDays.has(day.snapshotDate);
  }

  isSummaryNotesExpanded(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return !this.collapsedSummaryNotes.has(day.snapshotDate);
  }

  toggleSummaryNotes(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    if (this.collapsedSummaryNotes.has(day.snapshotDate)) {
      this.collapsedSummaryNotes.delete(day.snapshotDate);
    } else {
      this.collapsedSummaryNotes.add(day.snapshotDate);
    }
    this.persistExpansionState();
  }

  saveSummaryNote(day: RobinhoodRhDailyTrackerDayDto, event: Event): void {
    event.stopPropagation();
    if (this.isSavingNote(day)) {
      return;
    }
    const draft = this.noteDraft(day);
    this.savingNoteDays.add(day.snapshotDate);
    this.financeApi.robinhoodDailyTrackerSaveDayNote(day.snapshotDate, draft).subscribe({
      next: (r) => {
        this.savingNoteDays.delete(day.snapshotDate);
        day.summaryNote = r.noteText;
        this.noteDrafts.set(day.snapshotDate, r.noteText);
        this.snackBar.open(r.message, 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.savingNoteDays.delete(day.snapshotDate);
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  private manualKey(day: RobinhoodRhDailyTrackerDayDto, capture: RobinhoodRhDailyTrackerManualCaptureDto): string {
    return `${day.snapshotDate}|${capture.capturedAt}`;
  }

  onMonthsChange(): void {
    this.load();
  }

  selectAllMonths(): void {
    this.reportMonths = this.monthChoices.map((m) => m.value);
    this.load();
  }

  clearMonthSelection(): void {
    this.reportMonths = [];
    this.load();
  }

  monthsFilterLabel(): string {
    const months = this.tracker?.months ?? this.normalizedReportMonths();
    if (!months.length) {
      return 'All months';
    }
    if (months.length === 1) {
      return this.monthChoices.find((m) => m.value === months[0])?.label ?? `Month ${months[0]}`;
    }
    return `${months.length} months selected`;
  }

  periodKpiLabel(): string {
    const months = this.tracker?.months ?? this.normalizedReportMonths();
    if (!months.length) {
      return 'All months combined total';
    }
    if (months.length === 1) {
      return 'Month combined total';
    }
    return 'Selected months combined total';
  }

  periodChangeHint(): string {
    const months = this.tracker?.months ?? this.normalizedReportMonths();
    if (!months.length) {
      return 'No change in period';
    }
    if (months.length === 1) {
      return 'No change this month';
    }
    return 'No change in selected months';
  }

  private normalizedReportMonths(): number[] {
    return [...this.reportMonths].sort((a, b) => a - b);
  }

  private expansionStorageKey(): string {
    const months = this.normalizedReportMonths();
    const monthKey = months.length ? months.join('-') : 'all';
    return `${ReportsFinanceRobinhoodDailyTrackerComponent.EXPANSION_STORAGE_PREFIX}-${this.reportYear}-${monthKey}`;
  }

  private persistExpansionState(): void {
    try {
      sessionStorage.setItem(
        this.expansionStorageKey(),
        JSON.stringify({
          expandedDays: [...this.expandedDays],
          collapsedManualSections: [...this.collapsedManualSections],
          expandedManuals: [...this.expandedManuals],
          collapsedSummaryNotes: [...this.collapsedSummaryNotes],
          collapsedTrades: [...this.collapsedTrades],
          collapsedTimeline: [...this.collapsedTimeline],
          collapsedFlows: [...this.collapsedFlows],
          collapsedAccounts: [...this.collapsedAccounts],
        }),
      );
    } catch {
      /* ignore storage errors */
    }
  }

  private mergeExpansionStateFromStorage(validDates: Set<string>): void {
    try {
      const raw = sessionStorage.getItem(this.expansionStorageKey());
      if (!raw) {
        return;
      }
      const stored = JSON.parse(raw) as {
        expandedDays?: string[];
        collapsedManualSections?: string[];
        expandedManuals?: string[];
        collapsedSummaryNotes?: string[];
        expandedTrades?: string[];
        collapsedTrades?: string[];
        collapsedTimeline?: string[];
        collapsedFlows?: string[];
        collapsedAccounts?: string[];
      };
      for (const date of stored.expandedDays ?? []) {
        if (validDates.has(date)) {
          this.expandedDays.add(date);
        }
      }
      for (const date of stored.collapsedManualSections ?? []) {
        if (validDates.has(date)) {
          this.collapsedManualSections.add(date);
        }
      }
      for (const key of stored.expandedManuals ?? []) {
        if (validDates.has(key.split('|')[0] ?? '')) {
          this.expandedManuals.add(key);
        }
      }
      for (const date of stored.collapsedSummaryNotes ?? []) {
        if (validDates.has(date)) {
          this.collapsedSummaryNotes.add(date);
        }
      }
      for (const date of stored.collapsedTrades ?? []) {
        if (validDates.has(date)) {
          this.collapsedTrades.add(date);
        }
      }
      if (stored.expandedTrades && !stored.collapsedTrades) {
        for (const date of validDates) {
          if (!stored.expandedTrades.includes(date)) {
            this.collapsedTrades.add(date);
          }
        }
      }
      for (const date of stored.collapsedTimeline ?? []) {
        if (validDates.has(date)) {
          this.collapsedTimeline.add(date);
        }
      }
      for (const date of stored.collapsedFlows ?? []) {
        if (validDates.has(date)) {
          this.collapsedFlows.add(date);
        }
      }
      for (const date of stored.collapsedAccounts ?? []) {
        if (validDates.has(date)) {
          this.collapsedAccounts.add(date);
        }
      }
    } catch {
      /* ignore parse/storage errors */
    }
  }

  private pruneExpansionState(validDates: Set<string>): void {
    for (const date of [...this.expandedDays]) {
      if (!validDates.has(date)) {
        this.expandedDays.delete(date);
      }
    }
    for (const date of [...this.collapsedManualSections]) {
      if (!validDates.has(date)) {
        this.collapsedManualSections.delete(date);
      }
    }
    for (const key of [...this.expandedManuals]) {
      if (!validDates.has(key.split('|')[0] ?? '')) {
        this.expandedManuals.delete(key);
      }
    }
    for (const date of [...this.collapsedSummaryNotes]) {
      if (!validDates.has(date)) {
        this.collapsedSummaryNotes.delete(date);
      }
    }
    for (const date of [...this.collapsedTrades]) {
      if (!validDates.has(date)) {
        this.collapsedTrades.delete(date);
      }
    }
    for (const date of [...this.collapsedTimeline]) {
      if (!validDates.has(date)) {
        this.collapsedTimeline.delete(date);
      }
    }
    for (const date of [...this.collapsedFlows]) {
      if (!validDates.has(date)) {
        this.collapsedFlows.delete(date);
      }
    }
    for (const date of [...this.collapsedAccounts]) {
      if (!validDates.has(date)) {
        this.collapsedAccounts.delete(date);
      }
    }
  }

  private toggleCollapsed(collapsed: Set<string>, snapshotDate: string): void {
    if (collapsed.has(snapshotDate)) {
      collapsed.delete(snapshotDate);
    } else {
      collapsed.add(snapshotDate);
    }
    this.persistExpansionState();
  }

  private syncNoteDrafts(days: RobinhoodRhDailyTrackerDayDto[]): void {
    const validDates = new Set(days.map((d) => d.snapshotDate));
    for (const key of [...this.noteDrafts.keys()]) {
      if (!validDates.has(key)) {
        this.noteDrafts.delete(key);
      }
    }
    for (const day of days) {
      if (!this.isNoteDirty(day)) {
        this.noteDrafts.set(day.snapshotDate, day.summaryNote ?? '');
      }
    }
  }

  private loadViewMode(): 'classic' | 'timeline' {
    try {
      const stored = localStorage.getItem(ReportsFinanceRobinhoodDailyTrackerComponent.VIEW_MODE_STORAGE_KEY);
      return stored === 'timeline' ? 'timeline' : 'classic';
    } catch {
      return 'classic';
    }
  }
}
