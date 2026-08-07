import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogConfig, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import {
  RobinhoodRhDailyTrackerAccountCellDto,
  RobinhoodRhDailyTrackerDayDto,
} from '../../../models/finance.models';
import { robinhoodAccountDisplayLabel } from '../../../util/robinhood-account-display';
import {
  RobinhoodDailySnapshotDialogComponent,
  RobinhoodDailySnapshotDialogData,
  RH_SNAPSHOT_DIALOG_CONFIG,
} from './robinhood-daily-snapshot-dialog.component';

export interface RobinhoodDailyDayDialogAccountRow {
  cell: RobinhoodRhDailyTrackerAccountCellDto;
  label: string;
  change: number | null;
}

export interface RobinhoodDailyDayDialogData {
  day: RobinhoodRhDailyTrackerDayDto;
  total: number | null;
  delta: number | null;
  liveBadge: string | null;
  accounts: RobinhoodDailyDayDialogAccountRow[];
  autoCaptureScheduled: boolean;
}

export const RH_DAY_DIALOG_CONFIG: Pick<MatDialogConfig, 'width' | 'maxWidth' | 'maxHeight' | 'panelClass'> = {
  width: 'min(720px, 96vw)',
  maxWidth: '96vw',
  maxHeight: '90vh',
  panelClass: 'rh-day-dialog-panel',
};

@Component({
  selector: 'app-robinhood-daily-day-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
  ],
  templateUrl: './robinhood-daily-day-dialog.component.html',
  styleUrl: './robinhood-daily-day-dialog.component.scss',
})
export class RobinhoodDailyDayDialogComponent {
  readonly data = inject<RobinhoodDailyDayDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RobinhoodDailyDayDialogComponent>);
  private readonly dialog = inject(MatDialog);

  close(): void {
    this.ref.close();
  }

  /** Expand this day in the classic timeline (parent listens). */
  openInTimeline(): void {
    this.ref.close({ expandTimeline: true, snapshotDate: this.data.day.snapshotDate });
  }

  openAccount(row: RobinhoodDailyDayDialogAccountRow): void {
    if (!row.cell.snapshotId) {
      return;
    }
    this.dialog.open(RobinhoodDailySnapshotDialogComponent, {
      ...RH_SNAPSHOT_DIALOG_CONFIG,
      data: {
        snapshotId: row.cell.snapshotId,
        dayLabel: this.data.day.snapshotDate,
        accountSuffix: row.cell.accountSuffix,
        scheduledCaptureEnabled: this.data.autoCaptureScheduled,
      } satisfies RobinhoodDailySnapshotDialogData,
    });
  }

  pnlClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '';
    }
    return value > 0 ? 'rh-day-dialog__pnl--pos' : 'rh-day-dialog__pnl--neg';
  }

  trendIcon(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return 'remove';
    }
    return value > 0 ? 'trending_up' : 'trending_down';
  }

  accountFallbackLabel(suffix: string): string {
    return robinhoodAccountDisplayLabel(suffix);
  }

  deltaPercent(current: number | null, change: number | null): number | null {
    if (current == null || change == null || !Number.isFinite(current) || !Number.isFinite(change)) {
      return null;
    }
    const prior = current - change;
    if (prior === 0) {
      return null;
    }
    return (change / prior) * 100;
  }
}
