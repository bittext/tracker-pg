import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { filter, interval, switchMap } from 'rxjs';
import {
  RobinhoodRhDailyTrackerAccountCellDto,
  RobinhoodRhDailyTrackerAccountColumnDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerManualCaptureDto,
  RobinhoodRhDailyTrackerRefreshHintDto,
  RobinhoodRhDailyTrackerReportDto,
  RhDailyTrackerAccountAlertDto,
  RhDailyTrackerAccountAlertItemDto,
  RhDailyTrackerAccountAlertsDto,
  RhDailyTrackerAlertEventDto,
  RhDailyTrackerAiInsightDto,
  RhDailyTrackerAiInsightScope,
  RhDailyTrackerAiInsightStatusDto,
  RhDailyTrackerSnapshotAlertDto,
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
  spikeAlert: RhDailyTrackerSnapshotAlertDto;
}

export interface RhDailyAlertProgress {
  ratio: number;
  widthPct: number;
  level: 'idle' | 'warn' | 'hot' | 'fired';
  title: string;
}

export interface RhDailyMovementBar {
  key: string;
  label: string;
  change: number;
  heightPct: number;
  positive: boolean;
  hasAlert: boolean;
  title: string;
  /** Central-time market window for capture-strip shading. */
  marketSession: 'pre' | 'rth' | 'after' | 'other';
}

export interface RhDailyFocusPoint {
  key: string;
  label: string;
  value: number;
  /** Cumulative % return vs first scheduled 9 PM value in the series. */
  returnPercent: number;
  change: number;
  changePercent: number | null;
  periodAdded: number;
  periodRemoved: number;
  periodValueChange: number;
  tradeCount: number;
  positionsChanged: boolean;
  hasAlert: boolean;
  alert: RhDailyTrackerSnapshotAlertDto;
  x: number;
  y: number;
}

export interface RhDailyBenchmarkPoint {
  key: string;
  label: string;
  value: number;
  /** Cumulative % return vs launch-day S&P buy-and-hold stake. */
  returnPercent: number;
  indexClose: number;
  marketDate: string;
  launchInvestment: number;
  launchDate: string;
  x: number;
  y: number;
}

export interface RhDailyFocusMetrics {
  startValue: number;
  latestValue: number;
  latestChange: number;
  periodChange: number;
  periodChangePercent: number | null;
  totalAdded: number;
  totalRemoved: number;
  totalValueChange: number;
  high: number;
  low: number;
  alertCount: number;
  bestDay: RhDailyFocusPoint;
  worstDay: RhDailyFocusPoint;
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
    MatCheckboxModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-daily-tracker.component.html',
  styleUrl: './reports-finance-robinhood-daily-tracker.component.scss',
})
export class ReportsFinanceRobinhoodDailyTrackerComponent implements OnInit {
  readonly focusAccountSuffix = '3370';
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  reportYear = new Date().getFullYear();
  /** Empty = all months in the selected year. */
  reportMonths: number[] = [new Date().getMonth() + 1];
  loading = false;
  /** Background refresh after scheduled/manual capture (no full-page spinner). */
  softRefreshing = false;
  capturing = false;
  tracker: RobinhoodRhDailyTrackerReportDto | null = null;

  spikeAlertsExpanded = false;
  alertsLoading = false;
  alertsSaving = false;
  alertsTesting = false;
  alertSettings: RhDailyTrackerAccountAlertsDto | null = null;
  alertFormRows: RhDailyTrackerAccountAlertDto[] = [];
  alertEvents: RhDailyTrackerAlertEventDto[] = [];
  /** Expanded recent-alert rows showing destination/detail. */
  private readonly expandedAlertEventIds = new Set<number>();
  focusDetailsExpanded = false;
  selectedFocusDate: string | null = null;

  readonly aiScopes: RhDailyTrackerAiInsightScope[] = ['YEAR', 'MONTH', 'WEEK', 'DAY'];

  /** AI coaching panel (on-demand LLM over Daily Tracker facts). */
  aiStatus: RhDailyTrackerAiInsightStatusDto | null = null;
  aiScope: RhDailyTrackerAiInsightScope = 'MONTH';
  aiWeekStart = '';
  aiDay = '';
  aiInsight: RhDailyTrackerAiInsightDto | null = null;
  aiLoading = false;
  aiError: string | null = null;

  /** snapshotDate keys for expanded 9 PM day rows */
  private readonly expandedDays = new Set<string>();
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

  private static readonly EXPANSION_STORAGE_PREFIX = 'rh-daily-tracker-expansion';
  /** Poll for new snapshots from hourly cron or admin "Run now". */
  private static readonly AUTO_REFRESH_MS = 25_000;

  private lastKnownSnapshotId = 0;
  private refreshPollReady = false;

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

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  ngOnInit(): void {
    this.aiWeekStart = this.mondayOf(this.todayIso());
    this.aiDay = this.todayIso();
    this.load();
    this.loadSpikeAlerts({ silent: true });
    this.loadAiStatus();
    this.startAutoRefresh();
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    });
  }

  private readonly onVisibilityChange = (): void => {
    if (document.hidden || !this.refreshPollReady || this.loading || this.capturing) {
      return;
    }
    this.financeApi.robinhoodDailyTrackerRefreshHint().subscribe({
      next: (hint) => this.onRefreshHint(hint, true),
    });
  };

  private startAutoRefresh(): void {
    interval(ReportsFinanceRobinhoodDailyTrackerComponent.AUTO_REFRESH_MS)
      .pipe(
        filter(
          () =>
            this.refreshPollReady &&
            !document.hidden &&
            !this.loading &&
            !this.capturing &&
            !this.softRefreshing,
        ),
        switchMap(() => this.financeApi.robinhoodDailyTrackerRefreshHint()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (hint) => this.onRefreshHint(hint, true),
        error: () => {
          /* ignore transient poll failures */
        },
      });
  }

  private onRefreshHint(hint: RobinhoodRhDailyTrackerRefreshHintDto, fromPoll: boolean): void {
    const id = hint.latestSnapshotId ?? 0;
    if (fromPoll && id === this.lastKnownSnapshotId) {
      return;
    }
    this.lastKnownSnapshotId = id;
    if (!fromPoll || !this.tracker) {
      return;
    }
    this.load({ silent: true });
    if (this.spikeAlertsExpanded) {
      this.loadSpikeAlerts();
    }
  }

  private syncRefreshHint(markPollReady: boolean): void {
    this.financeApi.robinhoodDailyTrackerRefreshHint().subscribe({
      next: (hint) => {
        this.onRefreshHint(hint, false);
        if (markPollReady) {
          this.refreshPollReady = true;
        }
      },
      error: () => {
        if (markPollReady) {
          this.refreshPollReady = true;
        }
      },
    });
  }

  load(opts?: { silent?: boolean }): void {
    const silent = opts?.silent ?? false;
    if (silent) {
      this.softRefreshing = true;
    } else {
      this.loading = true;
    }
    const months = this.normalizedReportMonths();
    this.financeApi.robinhoodDailyTracker(this.reportYear, months).subscribe({
      next: (t) => {
        this.tracker = t;
        const validDates = new Set(t.days.map((d) => d.snapshotDate));
        this.mergeExpansionStateFromStorage(validDates);
        this.pruneExpansionState(validDates);
        this.syncNoteDrafts(t.days);
        this.loading = false;
        this.softRefreshing = false;
        if (!silent) {
          this.syncRefreshHint(true);
        }
      },
      error: (err) => {
        this.tracker = null;
        this.loading = false;
        this.softRefreshing = false;
        if (!silent) {
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        }
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

  hasAnyDayExpanded(): boolean {
    return this.expandedDays.size > 0;
  }

  allDaysExpanded(): boolean {
    const days = this.tracker?.days ?? [];
    return days.length > 0 && days.every((day) => this.expandedDays.has(day.snapshotDate));
  }

  expandAllDays(): void {
    if (!this.tracker?.days.length) {
      return;
    }
    for (const day of this.tracker.days) {
      this.expandedDays.add(day.snapshotDate);
    }
    this.persistExpansionState();
  }

  collapseAllDays(): void {
    this.expandedDays.clear();
    this.persistExpansionState();
  }

  toggleDay(day: RobinhoodRhDailyTrackerDayDto): void {
    if (this.expandedDays.has(day.snapshotDate)) {
      this.expandedDays.delete(day.snapshotDate);
    } else {
      this.expandedDays.add(day.snapshotDate);
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
        this.snackBar.open(r.message, 'OK', { duration: 5000 });
        this.load();
      },
      error: (err) => {
        this.deletingManualKey = null;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  deleteManualTimelineRow(day: RobinhoodRhDailyTrackerDayDto, row: RhDailyCaptureTimelineRow, event: Event): void {
    const capture = day.manualCaptures.find((mc) => mc.capturedAt === row.capturedAt);
    if (!capture) {
      return;
    }
    this.deleteManualCapture(day, capture, event);
  }

  isDeletingManualTimelineRow(day: RobinhoodRhDailyTrackerDayDto, row: RhDailyCaptureTimelineRow): boolean {
    return this.deletingManualKey === `${day.snapshotDate}|${row.capturedAt}`;
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
            spikeAlert: cell.spikeAlert,
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
            spikeAlert: acct.spikeAlert,
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
            spikeAlert: acct.spikeAlert,
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
        positionsChangedFromPrior: acct.positionsChangedFromPrior,
        spikeAlert: acct.spikeAlert,
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

  timelineRowHasSpikeAlerts(row: RhDailyCaptureTimelineRow): boolean {
    const acct = row.accounts.find((a) => a.accountSuffix === this.focusAccountSuffix);
    return this.hasSpikeAlert(acct?.spikeAlert);
  }

  hasSpikeAlert(alert: RhDailyTrackerSnapshotAlertDto | null | undefined): boolean {
    return alert?.fired === true;
  }

  /** True when the fired spike was an up move (green styling). */
  isSpikeAlertPositive(alert: RhDailyTrackerSnapshotAlertDto | null | undefined): boolean {
    if (!this.hasSpikeAlert(alert)) {
      return false;
    }
    if (alert!.deltaDollars != null && Number.isFinite(alert!.deltaDollars)) {
      return alert!.deltaDollars > 0;
    }
    if (alert!.deltaPercent != null && Number.isFinite(alert!.deltaPercent)) {
      return alert!.deltaPercent > 0;
    }
    return false;
  }

  daySpikeAlertCount(day: RobinhoodRhDailyTrackerDayDto): number {
    return this.collectDaySpikeAlerts(day).length;
  }

  dayHasSpikeAlerts(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return this.daySpikeAlertCount(day) > 0;
  }

  captureMovementBars(day: RobinhoodRhDailyTrackerDayDto): RhDailyMovementBar[] {
    const timeline = [...this.captureTimeline(day)].reverse();
    const candidates = timeline
      .map((row) => ({
        row,
        acct: row.accounts.find((a) => a.accountSuffix === this.focusAccountSuffix),
      }))
      .filter(
        (
          item,
        ): item is {
          row: RhDailyCaptureTimelineRow;
          acct: RhDailyCaptureTimelineAccountCell;
        } => !!item.acct && item.acct.changeFromPrior != null,
      )
      .map(({ row, acct }) => {
        const marketSession = this.marketSessionForCapture(row.capturedAt);
        const sessionHint =
          marketSession === 'pre'
            ? ' · pre-market (4–8 AM CT)'
            : marketSession === 'rth'
              ? ' · regular hours (8 AM–4 PM CT)'
              : marketSession === 'after'
                ? ' · after-hours (4–7 PM CT)'
                : '';
        return {
          key: row.capturedAt,
          label: row.timeLabel,
          change: acct.changeFromPrior!,
          hasAlert: this.hasSpikeAlert(acct.spikeAlert),
          marketSession,
          title: `${row.timeLabel} · ••••${this.focusAccountSuffix}: ${this.formatMoney(acct.changeFromPrior!)}${
            this.hasSpikeAlert(acct.spikeAlert) ? ' · spike alert' : ''
          }${sessionHint}`,
        };
      });
    return this.toMovementBars(candidates);
  }

  alertProgressForAccount(acct: RhDailyCaptureTimelineAccountCell): RhDailyAlertProgress | null {
    if (acct.accountSuffix !== this.focusAccountSuffix) {
      return null;
    }
    if (this.hasSpikeAlert(acct.spikeAlert)) {
      const parts = ['Alert fired'];
      if (acct.spikeAlert.triggerReasons) {
        parts.push(this.alertTriggerLabel(acct.spikeAlert.triggerReasons));
      }
      if (acct.spikeAlert.deltaDollars != null) {
        parts.push(this.formatMoney(acct.spikeAlert.deltaDollars));
      }
      if (acct.spikeAlert.deltaPercent != null) {
        parts.push(`${acct.spikeAlert.deltaPercent.toFixed(2)}%`);
      }
      return {
        ratio: 1,
        widthPct: 100,
        level: 'fired',
        title: parts.join(' · '),
      };
    }
    if (acct.changeFromPrior == null) {
      return null;
    }
    const cfg = this.alertSettings?.accounts.find((a) => a.accountSuffix === acct.accountSuffix);
    if (!cfg?.enabled) {
      return null;
    }
    const absDelta = Math.abs(acct.changeFromPrior);
    const pct = Math.abs(this.deltaPercentForCurrent(acct.totalAccountValue, acct.changeFromPrior) ?? 0);
    let ratio = 0;
    const parts: string[] = [];
    if (cfg.valueDollarsEnabled && cfg.minValueChangeDollars != null && cfg.minValueChangeDollars > 0) {
      const r = absDelta / cfg.minValueChangeDollars;
      ratio = Math.max(ratio, r);
      parts.push(`$${absDelta.toFixed(0)} / $${cfg.minValueChangeDollars.toFixed(0)}`);
    }
    if (cfg.valuePercentEnabled && cfg.minValueChangePercent != null && cfg.minValueChangePercent > 0) {
      const r = pct / cfg.minValueChangePercent;
      ratio = Math.max(ratio, r);
      parts.push(`${pct.toFixed(2)}% / ${cfg.minValueChangePercent}%`);
    }
    if (ratio <= 0 && !cfg.positionChangeEnabled) {
      return null;
    }
    if (ratio <= 0 && cfg.positionChangeEnabled) {
      if (!acct.positionsChangedFromPrior) {
        return null;
      }
      return {
        ratio: 1,
        widthPct: 100,
        level: 'hot',
        title: 'Position change trigger ready',
      };
    }
    const level: RhDailyAlertProgress['level'] = ratio >= 1 ? 'hot' : ratio >= 0.7 ? 'warn' : 'idle';
    return {
      ratio,
      widthPct: Math.min(100, Math.round(ratio * 100)),
      level,
      title: `Alert progress ${Math.round(ratio * 100)}% · ${parts.join(' · ')}`,
    };
  }

  isAlertEventExpanded(id: number): boolean {
    return this.expandedAlertEventIds.has(id);
  }

  focusAlertEvents(): RhDailyTrackerAlertEventDto[] {
    return this.alertEvents.filter((event) => event.accountSuffix === this.focusAccountSuffix);
  }

  focusAccountLabel(): string {
    return (
      this.tracker?.accounts.find((account) => account.accountSuffix === this.focusAccountSuffix)?.label ??
      `Account ••••${this.focusAccountSuffix}`
    );
  }

  focusPoints(): RhDailyFocusPoint[] {
    return this.focusChartSeries().account;
  }

  sp500BenchmarkPoints(): RhDailyBenchmarkPoint[] {
    return this.focusChartSeries().benchmark;
  }

  private focusChartSeries(): { account: RhDailyFocusPoint[]; benchmark: RhDailyBenchmarkPoint[] } {
    const raw = (this.tracker?.days ?? [])
      .map((day) => ({
        day,
        cell: day.accounts.find((account) => account.accountSuffix === this.focusAccountSuffix),
      }))
      .filter(
        (
          item,
        ): item is {
          day: RobinhoodRhDailyTrackerDayDto;
          cell: RobinhoodRhDailyTrackerAccountCellDto;
        } => !!item.cell && item.day.hasScheduledSnapshot,
      )
      .reverse();
    if (!raw.length) {
      return { account: [], benchmark: [] };
    }

    const benchmarkByDate = new Map(
      (this.tracker?.sp500Benchmark ?? []).map((point) => [point.snapshotDate, point]),
    );

    // Launch day = first 9 PM snapshot that also has an S&P close. Invest 100% of that
    // account value in the index and hold; later points = launch × (close_t / close_launch).
    let launchIndex = -1;
    let launchInvestment = 0;
    let launchClose = 0;
    let launchDate = '';
    for (let i = 0; i < raw.length; i++) {
      const benchmark = benchmarkByDate.get(raw[i].day.snapshotDate);
      const accountValue = raw[i].cell.totalAccountValue;
      if (benchmark && benchmark.close > 0 && accountValue > 0) {
        launchIndex = i;
        launchInvestment = accountValue;
        launchClose = benchmark.close;
        launchDate = raw[i].day.snapshotDate;
        break;
      }
    }

    const benchmarkValues =
      launchIndex < 0
        ? []
        : raw
            .map(({ day }, index) => {
              if (index < launchIndex) {
                return null;
              }
              const benchmark = benchmarkByDate.get(day.snapshotDate);
              if (!benchmark || launchClose <= 0) {
                return null;
              }
              const value = launchInvestment * (benchmark.close / launchClose);
              return {
                key: day.snapshotDate,
                label: day.snapshotDate.slice(5),
                value,
                returnPercent: launchInvestment === 0 ? 0 : ((value - launchInvestment) / launchInvestment) * 100,
                indexClose: benchmark.close,
                marketDate: benchmark.marketDate,
                launchInvestment,
                launchDate,
                index,
              };
            })
            .filter((point): point is NonNullable<typeof point> => point != null);

    // Plot both series as % return from launch so S&P moves stay visible beside large
    // account dollar swings. Prefer the S&P launch stake as the account base when present.
    const accountBase =
      launchIndex >= 0 && launchInvestment > 0 ? launchInvestment : raw[0].cell.totalAccountValue;
    const accountReturns = raw.map(({ cell }) =>
      accountBase === 0 ? 0 : ((cell.totalAccountValue - accountBase) / accountBase) * 100,
    );
    const returns = [...accountReturns, ...benchmarkValues.map((point) => point.returnPercent), 0];
    const min = Math.min(...returns);
    const max = Math.max(...returns);
    const range = Math.max(max - min, 0.01);
    const xDenominator = Math.max(raw.length - 1, 1);
    const yForReturn = (returnPercent: number) => 36 - ((returnPercent - min) / range) * 32;
    return {
      account: raw.map(({ day, cell }, index) => ({
        key: day.snapshotDate,
        label: day.snapshotDate.slice(5),
        value: cell.totalAccountValue,
        returnPercent: accountReturns[index],
        change: cell.totalChangeFromPrevious,
        changePercent: this.deltaPercentForCurrent(cell.totalAccountValue, cell.totalChangeFromPrevious),
        periodAdded: cell.periodAdded,
        periodRemoved: cell.periodRemoved,
        periodValueChange: cell.periodValueChange,
        tradeCount: cell.tradeCount,
        positionsChanged: cell.positionsChangedFromPrior,
        hasAlert: this.hasSpikeAlert(cell.spikeAlert),
        alert: cell.spikeAlert,
        x: (index / xDenominator) * 100,
        y: yForReturn(accountReturns[index]),
      })),
      benchmark: benchmarkValues.map((point) => ({
        key: point.key,
        label: point.label,
        value: point.value,
        returnPercent: point.returnPercent,
        indexClose: point.indexClose,
        marketDate: point.marketDate,
        launchInvestment: point.launchInvestment,
        launchDate: point.launchDate,
        x: (point.index / xDenominator) * 100,
        y: yForReturn(point.returnPercent),
      })),
    };
  }

  focusTrendPath(): string {
    const points = this.focusPoints();
    if (!points.length) {
      return '';
    }
    return points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`).join(' ');
  }

  sp500TrendPath(): string {
    const points = this.sp500BenchmarkPoints();
    return points
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
      .join(' ');
  }

  sp500BenchmarkSummary(): {
    latestValue: number;
    change: number;
    changePercent: number;
    launchInvestment: number;
    launchDate: string;
    vsAccount: number;
    vsAccountPercent: number | null;
  } | null {
    const points = this.sp500BenchmarkPoints();
    const account = this.focusPoints();
    if (!points.length || !account.length) {
      return null;
    }
    const start = points[0];
    const latest = points[points.length - 1];
    const change = latest.value - start.value;
    const accountLatest = account[account.length - 1].value;
    const vsAccount = accountLatest - latest.value;
    return {
      latestValue: latest.value,
      change,
      changePercent: start.value === 0 ? 0 : (change / start.value) * 100,
      launchInvestment: start.launchInvestment,
      launchDate: start.launchDate,
      vsAccount,
      vsAccountPercent: latest.value === 0 ? null : (vsAccount / latest.value) * 100,
    };
  }

  focusAreaPath(): string {
    const points = this.focusPoints();
    if (!points.length) {
      return '';
    }
    const line = this.focusTrendPath();
    const first = points[0];
    const last = points[points.length - 1];
    return `${line} L ${last.x.toFixed(2)} 36 L ${first.x.toFixed(2)} 36 Z`;
  }

  focusYAxisLabels(): Array<{ y: number; value: number }> {
    const series = this.focusChartSeries();
    const values = [
      ...series.account.map((point) => point.returnPercent),
      ...series.benchmark.map((point) => point.returnPercent),
      0,
    ];
    if (!series.account.length) {
      return [];
    }
    const high = Math.max(...values);
    const low = Math.min(...values);
    return [
      { y: 4, value: high },
      { y: 20, value: low + (high - low) / 2 },
      { y: 36, value: low },
    ];
  }

  /** SVG y for the 0% return baseline, or null when launch is outside the plot. */
  focusZeroLineY(): number | null {
    const series = this.focusChartSeries();
    if (!series.account.length) {
      return null;
    }
    const values = [
      ...series.account.map((point) => point.returnPercent),
      ...series.benchmark.map((point) => point.returnPercent),
      0,
    ];
    const high = Math.max(...values);
    const low = Math.min(...values);
    if (0 < low || 0 > high) {
      return null;
    }
    const range = Math.max(high - low, 0.01);
    return 36 - ((0 - low) / range) * 32;
  }

  formatFocusReturn(value: number): string {
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(1)}%`;
  }

  focusXAxisLabels(): RhDailyFocusPoint[] {
    const points = this.focusPoints();
    if (points.length <= 3) {
      return points;
    }
    const middle = points[Math.floor((points.length - 1) / 2)];
    return [points[0], middle, points[points.length - 1]];
  }

  focusDetailRows(): Array<{
    day: RobinhoodRhDailyTrackerDayDto;
    cell: RobinhoodRhDailyTrackerAccountCellDto;
    changePercent: number | null;
  }> {
    return (this.tracker?.days ?? [])
      .map((day) => ({
        day,
        cell: day.accounts.find((account) => account.accountSuffix === this.focusAccountSuffix),
      }))
      .filter(
        (
          item,
        ): item is {
          day: RobinhoodRhDailyTrackerDayDto;
          cell: RobinhoodRhDailyTrackerAccountCellDto;
        } => !!item.cell,
      )
      .map(({ day, cell }) => ({
        day,
        cell,
        changePercent: this.deltaPercentForCurrent(cell.totalAccountValue, cell.totalChangeFromPrevious),
      }));
  }

  toggleFocusDetails(): void {
    this.focusDetailsExpanded = !this.focusDetailsExpanded;
    if (this.focusDetailsExpanded) {
      this.scrollToFocusDetails();
    }
  }

  selectFocusPoint(snapshotDate: string, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.selectedFocusDate = snapshotDate;
    this.focusDetailsExpanded = true;
    this.scrollToFocusRow(snapshotDate);
  }

  isFocusPointSelected(snapshotDate: string): boolean {
    return this.selectedFocusDate === snapshotDate;
  }

  private scrollToFocusDetails(): void {
    setTimeout(() => {
      document.getElementById('rh-daily-focus-details')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
    });
  }

  private scrollToFocusRow(snapshotDate: string): void {
    setTimeout(() => {
      document.getElementById(`rh-daily-focus-row-${snapshotDate}`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    });
  }

  focusMetrics(): RhDailyFocusMetrics | null {
    const points = this.focusPoints();
    if (!points.length) {
      return null;
    }
    const first = points[0];
    const latest = points[points.length - 1];
    const periodChange = latest.value - first.value;
    const byChange = [...points].sort((a, b) => b.change - a.change);
    return {
      startValue: first.value,
      latestValue: latest.value,
      latestChange: latest.change,
      periodChange,
      periodChangePercent: first.value === 0 ? null : (periodChange / first.value) * 100,
      totalAdded: points.reduce((total, point) => total + point.periodAdded, 0),
      totalRemoved: points.reduce((total, point) => total + point.periodRemoved, 0),
      totalValueChange: points.reduce((total, point) => total + point.periodValueChange, 0),
      high: Math.max(...points.map((point) => point.value)),
      low: Math.min(...points.map((point) => point.value)),
      alertCount: (this.tracker?.days ?? []).reduce((count, day) => count + this.daySpikeAlertCount(day), 0),
      bestDay: byChange[0],
      worstDay: byChange[byChange.length - 1],
    };
  }

  toggleAlertEventDetail(id: number, event?: Event): void {
    event?.stopPropagation();
    if (this.expandedAlertEventIds.has(id)) {
      this.expandedAlertEventIds.delete(id);
    } else {
      this.expandedAlertEventIds.add(id);
    }
  }

  spikeAlertTitle(alert: RhDailyTrackerSnapshotAlertDto): string {
    const parts = ['Spike alert'];
    if (alert.triggerReasons) {
      parts.push(this.alertTriggerLabel(alert.triggerReasons));
    }
    if (alert.deltaDollars != null) {
      parts.push(`Δ ${alert.deltaDollars.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}`);
    }
    if (alert.deltaPercent != null) {
      parts.push(`${alert.deltaPercent.toFixed(2)}%`);
    }
    if (alert.emailStatus) {
      parts.push(`Email ${alert.emailStatus}`);
    }
    return parts.join(' · ');
  }

  private collectDaySpikeAlerts(day: RobinhoodRhDailyTrackerDayDto): RhDailyTrackerSnapshotAlertDto[] {
    const alerts: RhDailyTrackerSnapshotAlertDto[] = [];
    for (const cell of day.accounts ?? []) {
      if (cell.accountSuffix === this.focusAccountSuffix && this.hasSpikeAlert(cell.spikeAlert)) {
        alerts.push(cell.spikeAlert);
      }
    }
    for (const capture of [...(day.intradayCaptures ?? []), ...(day.manualCaptures ?? [])]) {
      for (const acct of capture.accounts ?? []) {
        if (acct.accountSuffix === this.focusAccountSuffix && this.hasSpikeAlert(acct.spikeAlert)) {
          alerts.push(acct.spikeAlert);
        }
      }
    }
    return alerts;
  }

  private toMovementBars(
    candidates: Array<{
      key: string;
      label: string;
      change: number;
      hasAlert: boolean;
      title: string;
      marketSession: RhDailyMovementBar['marketSession'];
    }>,
  ): RhDailyMovementBar[] {
    if (!candidates.length) {
      return [];
    }
    const maxAbs = Math.max(...candidates.map((c) => Math.abs(c.change)), 1);
    return candidates.map((c) => ({
      key: c.key,
      label: c.label,
      change: c.change,
      heightPct: Math.max(8, Math.round((Math.abs(c.change) / maxAbs) * 100)),
      positive: c.change >= 0,
      hasAlert: c.hasAlert,
      title: c.title,
      marketSession: c.marketSession,
    }));
  }

  /** Pre-market 4–8 AM, RTH 8 AM–4 PM, after-hours 4–7 PM CT. */
  private marketSessionForCapture(capturedAt: string): RhDailyMovementBar['marketSession'] {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Chicago',
      hour: 'numeric',
      minute: 'numeric',
      hourCycle: 'h23',
    }).formatToParts(new Date(capturedAt));
    const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? Number.NaN);
    const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? Number.NaN);
    if (!Number.isFinite(hour) || !Number.isFinite(minute)) {
      return 'other';
    }
    const mins = hour * 60 + minute;
    if (mins >= 4 * 60 && mins < 8 * 60) {
      return 'pre';
    }
    if (mins >= 8 * 60 && mins < 16 * 60) {
      return 'rth';
    }
    if (mins >= 16 * 60 && mins < 19 * 60) {
      return 'after';
    }
    return 'other';
  }

  private formatMoney(v: number): string {
    return v.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
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

  /** Latest scheduled combined total in the loaded period (matches top day row). */
  periodCombinedTotal(): number {
    const t = this.tracker;
    if (!t) {
      return 0;
    }
    const scheduled = this.scheduledDaysNewestFirst(t.days);
    if (scheduled.length) {
      return scheduled[0].combinedTotal;
    }
    return t.monthCombinedTotal ?? 0;
  }

  /** Combined change from first to last scheduled day in the loaded period. */
  periodCombinedChange(): number {
    const t = this.tracker;
    if (!t) {
      return 0;
    }
    const scheduled = this.scheduledDaysNewestFirst(t.days);
    if (scheduled.length >= 2) {
      return scheduled[0].combinedTotal - scheduled[scheduled.length - 1].combinedTotal;
    }
    return t.monthCombinedChange ?? 0;
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

  private scheduledDaysNewestFirst(days: RobinhoodRhDailyTrackerDayDto[]): RobinhoodRhDailyTrackerDayDto[] {
    return days.filter((day) => day.hasScheduledSnapshot);
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

  toggleSpikeAlertsPanel(): void {
    this.spikeAlertsExpanded = !this.spikeAlertsExpanded;
    if (this.spikeAlertsExpanded && !this.alertSettings && !this.alertsLoading) {
      this.loadSpikeAlerts();
    }
  }

  loadSpikeAlerts(opts?: { silent?: boolean }): void {
    const silent = opts?.silent ?? false;
    if (!silent) {
      this.alertsLoading = true;
    }
    this.financeApi.robinhoodDailyTrackerAlerts().subscribe({
      next: (settings) => {
        this.alertSettings = settings;
        this.alertFormRows = settings.accounts.map((a) => ({ ...a }));
        this.alertsLoading = false;
      },
      error: (err) => {
        if (!silent) {
          this.alertSettings = null;
          this.alertFormRows = [];
          this.alertsLoading = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        } else {
          this.alertsLoading = false;
        }
      },
    });
    this.financeApi.robinhoodDailyTrackerAlertEvents(10).subscribe({
      next: (events) => {
        this.alertEvents = events;
      },
      error: () => {
        if (!silent) {
          this.alertEvents = [];
        }
      },
    });
  }

  saveSpikeAlerts(): void {
    this.alertsSaving = true;
    const body = {
      accounts: this.alertFormRows.map(
        (row): RhDailyTrackerAccountAlertItemDto => ({
          accountSuffix: row.accountSuffix,
          enabled: row.enabled,
          valueDollarsEnabled: row.valueDollarsEnabled,
          minValueChangeDollars: row.minValueChangeDollars,
          valuePercentEnabled: row.valuePercentEnabled,
          minValueChangePercent: row.minValueChangePercent,
          positionChangeEnabled: row.positionChangeEnabled,
          cooldownMinutes: row.cooldownMinutes,
        }),
      ),
    };
    this.financeApi.robinhoodDailyTrackerSaveAlerts(body).subscribe({
      next: (settings) => {
        this.alertSettings = settings;
        this.alertFormRows = settings.accounts.map((a) => ({ ...a }));
        this.alertsSaving = false;
        this.snackBar.open('Spike alert settings saved', undefined, { duration: 2500 });
      },
      error: (err) => {
        this.alertsSaving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  testSpikeAlertEmail(): void {
    this.alertsTesting = true;
    this.financeApi.robinhoodDailyTrackerAlertTest().subscribe({
      next: (result) => {
        this.alertsTesting = false;
        this.snackBar.open(result.message, undefined, { duration: 5000 });
        this.loadSpikeAlerts();
      },
      error: (err) => {
        this.alertsTesting = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  alertTriggerLabel(reasons: string): string {
    return reasons
      .split(',')
      .map((r) => {
        switch (r.trim()) {
          case 'VALUE_DOLLARS':
            return '$ change';
          case 'VALUE_PERCENT':
            return '% change';
          case 'POSITIONS':
            return 'positions';
          case 'TEST':
            return 'test';
          default:
            return r.trim();
        }
      })
      .join(', ');
  }

  alertStatusClass(status: string): string {
    switch (status) {
      case 'SENT':
        return 'rh-daily__alert-status--sent';
      case 'FAILED':
        return 'rh-daily__alert-status--failed';
      default:
        return 'rh-daily__alert-status--skipped';
    }
  }

  loadAiStatus(): void {
    this.financeApi.robinhoodDailyTrackerAiInsightStatus().subscribe({
      next: (s) => {
        this.aiStatus = s;
      },
      error: () => {
        this.aiStatus = { enabled: false, configured: false };
      },
    });
  }

  setAiScope(scope: RhDailyTrackerAiInsightScope): void {
    this.aiScope = scope;
    if (scope === 'WEEK' && !this.aiWeekStart) {
      this.aiWeekStart = this.mondayOf(this.todayIso());
    }
    if (scope === 'DAY' && !this.aiDay) {
      this.aiDay = this.latestTrackerDayOrToday();
    }
  }

  aiMonthForRequest(): number {
    const months = this.normalizedReportMonths();
    if (months.length === 1) {
      return months[0];
    }
    if (months.length > 1) {
      return Math.max(...months);
    }
    return new Date().getMonth() + 1;
  }

  generateAiInsight(forceRefresh = false): void {
    if (this.aiLoading) {
      return;
    }
    this.aiLoading = true;
    this.aiError = null;
    const body = {
      scope: this.aiScope,
      year: this.reportYear,
      month: this.aiScope === 'MONTH' ? this.aiMonthForRequest() : null,
      weekStart: this.aiScope === 'WEEK' ? this.aiWeekStart || this.mondayOf(this.todayIso()) : null,
      day: this.aiScope === 'DAY' ? this.aiDay || this.latestTrackerDayOrToday() : null,
      forceRefresh,
    };
    this.financeApi.robinhoodDailyTrackerAiInsights(body).subscribe({
      next: (insight) => {
        this.aiInsight = insight;
        this.aiLoading = false;
      },
      error: (err) => {
        this.aiLoading = false;
        this.aiError = formatHttpErrorDetail(err);
        this.snackBar.open(this.aiError, 'Dismiss', { duration: 10_000 });
      },
    });
  }

  private latestTrackerDayOrToday(): string {
    const days = this.tracker?.days ?? [];
    if (days.length) {
      return days[0].snapshotDate;
    }
    return this.todayIso();
  }

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private mondayOf(iso: string): string {
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    const dt = new Date(y, m - 1, d);
    const dow = dt.getDay(); // 0 Sun
    const diff = dow === 0 ? -6 : 1 - dow;
    dt.setDate(dt.getDate() + diff);
    return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
  }
}
