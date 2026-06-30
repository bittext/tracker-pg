import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import {
  RobinhoodRhDailyTrackerAccountCellDto,
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
} from './robinhood-daily-snapshot-dialog.component';

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
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DatePipe,
  ],
  templateUrl: './reports-finance-robinhood-daily-tracker.component.html',
  styleUrl: './reports-finance-robinhood-daily-tracker.component.scss',
})
export class ReportsFinanceRobinhoodDailyTrackerComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  reportYear = new Date().getFullYear();
  reportMonth: number | null = new Date().getMonth() + 1;
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

  readonly monthChoices = [
    { value: null, label: 'All months' },
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
    this.expandedDays.clear();
    this.collapsedManualSections.clear();
    this.expandedManuals.clear();
    this.financeApi.robinhoodDailyTracker(this.reportYear, this.reportMonth).subscribe({
      next: (t) => {
        this.tracker = t;
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
        periodAdded: 0,
        periodRemoved: 0,
        periodValueChange: 0,
        hasFlowActivity: false,
      },
      day,
    );
  }

  openSnapshot(cell: RobinhoodRhDailyTrackerAccountCellDto, day: RobinhoodRhDailyTrackerDayDto): void {
    if (!cell.snapshotId) {
      return;
    }
    this.dialog.open(RobinhoodDailySnapshotDialogComponent, {
      width: 'min(960px, 96vw)',
      maxHeight: '90vh',
      data: {
        snapshotId: cell.snapshotId,
        dayLabel: day.snapshotDate,
        accountSuffix: cell.accountSuffix,
      } satisfies RobinhoodDailySnapshotDialogData,
    });
  }

  cellForDay(day: RobinhoodRhDailyTrackerDayDto, suffix: string): RobinhoodRhDailyTrackerAccountCellDto | undefined {
    return day.accounts.find((a) => a.accountSuffix === suffix);
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'rh-daily__pnl--pos' : 'rh-daily__pnl--neg';
  }

  hasFlowBlock(day: RobinhoodRhDailyTrackerDayDto): boolean {
    return day.hasScheduledSnapshot && (day.combinedPeriodAdded !== 0 || day.combinedPeriodRemoved !== 0);
  }

  private manualKey(day: RobinhoodRhDailyTrackerDayDto, capture: RobinhoodRhDailyTrackerManualCaptureDto): string {
    return `${day.snapshotDate}|${capture.capturedAt}`;
  }
}
