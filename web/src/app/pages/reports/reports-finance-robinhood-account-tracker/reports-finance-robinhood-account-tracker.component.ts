import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, interval, of } from 'rxjs';
import { filter } from 'rxjs/operators';
import {
  RobinhoodRhDailySnapshotDetailDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerReportDto,
  RobinhoodRhPeriodAccountFigureDto,
  RobinhoodRhPeriodBalanceRowDto,
  RobinhoodRhPeriodBalancesDto,
} from '../../../models/finance.models';
import { RobinhoodAccountDataService } from '../../../services/robinhood-account-data.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  RH_SNAPSHOT_DIALOG_CONFIG,
  RobinhoodDailySnapshotDialogComponent,
  RobinhoodDailySnapshotDialogData,
} from '../reports-finance-robinhood-daily-tracker/robinhood-daily-snapshot-dialog.component';

interface AccountChangeRow {
  snapshotId: number;
  date: string;
  at: string | null;
  kind: 'CLOSE' | 'LIVE';
  value: number;
  change: number | null;
  added: number;
  removed: number;
  afterCash: number | null;
  tradeCount: number;
  positionsChanged: boolean;
}

interface AccountLatest {
  at: string;
  value: number;
  kind: 'CLOSE' | 'LIVE';
  priorClose: number | null;
  snapshotId: number;
}

@Component({
  selector: 'app-reports-finance-robinhood-account-tracker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-account-tracker.component.html',
  styleUrl: './reports-finance-robinhood-account-tracker.component.scss',
})
export class ReportsFinanceRobinhoodAccountTrackerComponent implements OnInit {
  private readonly data = inject(RobinhoodAccountDataService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  private static readonly AUTO_REFRESH_MS = 60_000;

  /** Fixed account suffix (for example the Roth IRA ••••2835). Wins over {@link accountKind}. */
  @Input() suffix = '';
  /** Resolve the account from the Daily Tracker columns by kind (INDIVIDUAL) when no fixed suffix is given. */
  @Input() accountKind = '';
  /** Used until the Daily Tracker columns say which suffix holds {@link accountKind}. */
  @Input() fallbackSuffix = '';
  @Input() title = 'Account';
  @Input() blurb = '';
  reportYear = new Date().getFullYear();
  loading = false;
  error: string | null = null;

  daily: RobinhoodRhDailyTrackerReportDto | null = null;
  balances: RobinhoodRhPeriodBalancesDto | null = null;
  detail: RobinhoodRhDailySnapshotDetailDto | null = null;

  ngOnInit(): void {
    // Paint the last known numbers straight away, then refresh quietly behind them.
    this.hydrateFromCache();
    this.load();
    interval(ReportsFinanceRobinhoodAccountTrackerComponent.AUTO_REFRESH_MS)
      .pipe(
        filter(() => !document.hidden && !this.loading),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.load({ silent: true }));
  }

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  onYearChange(): void {
    this.daily = null;
    this.balances = null;
    this.detail = null;
    this.hydrateFromCache();
    this.load();
  }

  private hydrateFromCache(): void {
    this.daily = this.data.dailyCached(this.reportYear);
    this.balances = this.data.balancesCached(this.reportYear);
    if (this.daily) {
      this.showCachedDetail();
    }
  }

  /** Daily report and balances load independently; each is applied as soon as it arrives. */
  load(opts?: { silent?: boolean }): void {
    const year = this.reportYear;
    const silent = (opts?.silent ?? false) || !!this.daily || !!this.balances;
    if (!silent) {
      this.loading = true;
      this.error = null;
    }
    const failures: string[] = [];
    const settle = (): void => {
      pending -= 1;
      if (pending > 0 || year !== this.reportYear) {
        return;
      }
      this.loading = false;
      if (!failures.length) {
        this.error = null;
      } else if (!this.daily && !this.balances) {
        this.error = failures[0];
      }
      if (failures.length && !silent) {
        this.snackBar.open(failures[0], 'Dismiss', { duration: 7000 });
      }
    };
    let pending = 2;
    this.data.refreshDaily(year).subscribe({
      next: (daily) => {
        if (year === this.reportYear) {
          this.daily = daily;
          this.loadDetail();
        }
      },
      error: (err) => {
        failures.push(formatHttpErrorDetail(err) || `Could not load ${this.title}`);
        settle();
      },
      complete: settle,
    });
    this.data.refreshBalances(year).subscribe({
      next: (balances) => {
        if (year === this.reportYear) {
          this.balances = balances;
        }
      },
      error: (err) => {
        failures.push(formatHttpErrorDetail(err) || `Could not load ${this.title}`);
        settle();
      },
      complete: settle,
    });
  }

  /** The ••••suffix this tab follows. */
  activeSuffix(): string {
    if (this.suffix) {
      return this.suffix;
    }
    const byKind = this.accountKind
      ? this.daily?.accounts.find((a) => (a.accountKind || '').toUpperCase() === this.accountKind.toUpperCase())
      : undefined;
    return byKind?.accountSuffix ?? this.fallbackSuffix;
  }

  openSnapshot(row: AccountChangeRow): void {
    if (!row.snapshotId) {
      return;
    }
    const data: RobinhoodDailySnapshotDialogData = {
      snapshotId: row.snapshotId,
      dayLabel: row.date,
      accountSuffix: this.activeSuffix(),
    };
    this.dialog.open(RobinhoodDailySnapshotDialogComponent, { ...RH_SNAPSHOT_DIALOG_CONFIG, data });
  }

  hasData(): boolean {
    return this.latest() != null;
  }

  now(): number | null {
    return this.latest()?.value ?? this.yearFigure()?.end ?? null;
  }

  nowAtLabel(): string {
    const latest = this.latest();
    if (!latest) {
      return `No ${this.title} captures yet`;
    }
    return `${latest.kind === 'CLOSE' ? 'Official close' : 'Live'} · ${this.formatWhen(latest.at)}`;
  }

  sinceLastClose(): number | null {
    const latest = this.latest();
    if (!latest || latest.priorClose == null) {
      return null;
    }
    return latest.value - latest.priorClose;
  }

  yearFigure(): RobinhoodRhPeriodAccountFigureDto | undefined {
    return this.figureFor(this.balances?.yearBalance);
  }

  monthRows(): { label: string; figure: RobinhoodRhPeriodAccountFigureDto; current: boolean }[] {
    const out: { label: string; figure: RobinhoodRhPeriodAccountFigureDto; current: boolean }[] = [];
    for (const row of this.balances?.months ?? []) {
      const figure = this.figureFor(row);
      // A month stays in the list when only deposits / withdrawals are known, so history before the first
      // captured close still shows what was added or taken out.
      if (figure && (figure.start != null || figure.end != null || !!figure.added || !!figure.removed)) {
        out.push({ label: row.label.replace(/\s+\d{4}$/, ''), figure, current: row.currentPeriod });
      }
    }
    return out;
  }

  /** Date of the first official close saved for this account, or null when none exists yet. */
  firstCloseDate(): string | null {
    let first: string | null = null;
    for (const day of this.daily?.days ?? []) {
      const cell = day.accounts.find((a) => a.accountSuffix === this.activeSuffix());
      if (cell && day.hasScheduledSnapshot && (first == null || day.snapshotDate < first)) {
        first = day.snapshotDate;
      }
    }
    return first;
  }

  /** Newest-first log of every close (plus the latest live capture) with what changed. */
  changeRows(): AccountChangeRow[] {
    const rows: AccountChangeRow[] = [];
    for (const day of this.daily?.days ?? []) {
      const cell = day.accounts.find((a) => a.accountSuffix === this.activeSuffix());
      if (!cell || !day.hasScheduledSnapshot) {
        continue;
      }
      rows.push({
        snapshotId: cell.snapshotId,
        date: day.snapshotDate,
        at: day.snapshotAt,
        kind: 'CLOSE',
        value: cell.totalAccountValue,
        change: day.hasPreviousScheduledSnapshot ? cell.totalChangeFromPrevious : null,
        added: cell.periodAdded ?? 0,
        removed: cell.periodRemoved ?? 0,
        afterCash: day.hasPreviousScheduledSnapshot ? cell.periodValueChange : null,
        tradeCount: cell.tradeCount ?? 0,
        positionsChanged: !!cell.positionsChangedFromPrior,
      });
    }
    const live = this.latest();
    if (live && live.kind === 'LIVE') {
      rows.push({
        snapshotId: live.snapshotId,
        date: this.centralDate(live.at),
        at: live.at,
        kind: 'LIVE',
        value: live.value,
        change: live.priorClose != null ? live.value - live.priorClose : null,
        added: 0,
        removed: 0,
        afterCash: null,
        tradeCount: 0,
        positionsChanged: false,
      });
    }
    return rows.sort((a, b) => (b.at ?? b.date).localeCompare(a.at ?? a.date));
  }

  holdings(): { symbol: string; type: string; quantity: number; value: number; change: number | null; exited: boolean }[] {
    return (this.detail?.holdings ?? [])
      .map((h) => ({
        symbol: h.holding.symbol,
        type: h.holding.positionType,
        quantity: h.holding.quantity,
        value: h.holding.marketValue,
        change: h.marketValueChange,
        exited: h.exited,
      }))
      .sort((a, b) => b.value - a.value);
  }

  isGain(value: number | null | undefined): boolean {
    return value != null && value > 0;
  }

  isLoss(value: number | null | undefined): boolean {
    return value != null && value < 0;
  }

  pct(start: number | null | undefined, change: number | null | undefined): number | null {
    if (start == null || change == null || !Number.isFinite(start) || start === 0) {
      return null;
    }
    return (change / start) * 100;
  }

  formatWhen(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return `${new Date(iso).toLocaleString('en-US', {
      timeZone: 'America/Chicago',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    })} CT`;
  }

  private figureFor(
    row: RobinhoodRhPeriodBalanceRowDto | null | undefined,
  ): RobinhoodRhPeriodAccountFigureDto | undefined {
    return row?.accounts.find((a) => a.accountSuffix === this.activeSuffix());
  }

  private showCachedDetail(): void {
    const id = this.latest()?.snapshotId ?? 0;
    this.detail = id > 0 ? this.data.detailCached(id) : null;
  }

  private loadDetail(): void {
    const id = this.latest()?.snapshotId ?? 0;
    if (id <= 0) {
      this.detail = null;
      return;
    }
    const cached = this.data.detailCached(id);
    if (cached) {
      this.detail = cached;
      return;
    }
    this.data
      .snapshotDetail(id)
      .pipe(catchError(() => of(null)))
      .subscribe((row) => {
        if (row && this.latest()?.snapshotId === id) {
          this.detail = row;
        }
      });
  }

  private latest(): AccountLatest | null {
    let best: AccountLatest | null = null;
    const consider = (candidate: AccountLatest): void => {
      if (!best || new Date(candidate.at).getTime() >= new Date(best.at).getTime()) {
        best = candidate;
      }
    };
    for (const day of this.daily?.days ?? []) {
      const priorClose = this.priorFor(day);
      const cell = day.accounts.find((a) => a.accountSuffix === this.activeSuffix());
      if (cell && day.hasScheduledSnapshot && day.snapshotAt) {
        consider({
          at: day.snapshotAt,
          value: cell.totalAccountValue,
          kind: 'CLOSE',
          priorClose: day.hasPreviousScheduledSnapshot
            ? cell.totalAccountValue - cell.totalChangeFromPrevious
            : priorClose,
          snapshotId: cell.snapshotId,
        });
      }
      for (const cap of [...(day.intradayCaptures ?? []), ...(day.manualCaptures ?? [])]) {
        const acct = cap.accounts.find((a) => a.accountSuffix === this.activeSuffix());
        if (!acct) {
          continue;
        }
        const afterClose =
          !!cell && day.hasScheduledSnapshot && !!day.snapshotAt && new Date(cap.capturedAt) > new Date(day.snapshotAt);
        consider({
          at: cap.capturedAt,
          value: acct.totalAccountValue,
          kind: 'LIVE',
          priorClose: afterClose && cell ? cell.totalAccountValue : priorClose,
          snapshotId: acct.snapshotId,
        });
      }
    }
    return best;
  }

  private priorFor(day: RobinhoodRhDailyTrackerDayDto): number | null {
    return day.priorPull?.accounts.find((a) => a.accountSuffix === this.activeSuffix())?.totalAccountValue ?? null;
  }

  private centralDate(iso: string): string {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Chicago',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date(iso));
  }
}
