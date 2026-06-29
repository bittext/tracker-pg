import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { RobinhoodRhDailyTrackerManualCaptureDto } from '../../../models/finance.models';
import {
  RobinhoodDailySnapshotDialogComponent,
  RobinhoodDailySnapshotDialogData,
} from './robinhood-daily-snapshot-dialog.component';

export interface RobinhoodDailyManualCaptureDialogData {
  dayLabel: string;
  capture: RobinhoodRhDailyTrackerManualCaptureDto;
}

@Component({
  selector: 'app-robinhood-daily-manual-capture-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, CurrencyPipe, DatePipe],
  templateUrl: './robinhood-daily-manual-capture-dialog.component.html',
  styleUrl: './robinhood-daily-manual-capture-dialog.component.scss',
})
export class RobinhoodDailyManualCaptureDialogComponent {
  readonly data = inject<RobinhoodDailyManualCaptureDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RobinhoodDailyManualCaptureDialogComponent>);
  private readonly dialog = inject(MatDialog);

  close(): void {
    this.ref.close();
  }

  openAccount(snapshotId: number, accountSuffix: string): void {
    this.dialog.open(RobinhoodDailySnapshotDialogComponent, {
      width: 'min(960px, 96vw)',
      maxHeight: '90vh',
      data: {
        snapshotId,
        dayLabel: this.data.dayLabel,
        accountSuffix,
      } satisfies RobinhoodDailySnapshotDialogData,
    });
  }
}
