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
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import {
  RobinhoodRhDailyTrackerAccountCellDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTrackerManualCaptureDto,
  RobinhoodRhDailyTrackerReportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  RobinhoodDailyManualCaptureDialogComponent,
  RobinhoodDailyManualCaptureDialogData,
} from './robinhood-daily-manual-capture-dialog.component';
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
    MatTableModule,
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

  openManualCapture(capture: RobinhoodRhDailyTrackerManualCaptureDto, day: RobinhoodRhDailyTrackerDayDto): void {
    this.dialog.open(RobinhoodDailyManualCaptureDialogComponent, {
      width: 'min(520px, 96vw)',
      maxHeight: '90vh',
      data: {
        dayLabel: day.snapshotDate,
        capture,
      } satisfies RobinhoodDailyManualCaptureDialogData,
    });
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
}
