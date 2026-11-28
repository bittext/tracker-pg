import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogConfig, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { RobinhoodIndividualMarginDayDto, RobinhoodIndividualMarginPeekDto } from '../../../models/finance.models';

export interface MarginWatchDayDialogData {
  day: RobinhoodIndividualMarginDayDto;
}

export const MG_DAY_DIALOG_CONFIG: Pick<MatDialogConfig, 'width' | 'maxWidth' | 'maxHeight' | 'panelClass'> = {
  width: 'min(1100px, 96vw)',
  maxWidth: '96vw',
  maxHeight: '90vh',
  panelClass: 'mg-day-dialog-panel',
};

@Component({
  selector: 'app-margin-watch-day-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './margin-watch-day-dialog.component.html',
  styleUrl: './margin-watch-day-dialog.component.scss',
})
export class MarginWatchDayDialogComponent {
  readonly data = inject<MarginWatchDayDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<MarginWatchDayDialogComponent>);

  peeks(): RobinhoodIndividualMarginPeekDto[] {
    return [...(this.data.day.peeks ?? [])].reverse();
  }

  captureLabel(kind: string | null | undefined): string {
    if (kind === 'MANUAL') {
      return 'peek';
    }
    if (kind === 'INTRADAY') {
      return 'hourly';
    }
    if (kind === 'SCHEDULED') {
      return 'close';
    }
    return kind?.toLowerCase() || '';
  }

  borrowTone(pct: number | null | undefined): string {
    if (pct == null) {
      return 'unknown';
    }
    if (pct >= 50) {
      return 'deep';
    }
    if (pct >= 40) {
      return 'hot';
    }
    if (pct >= 25) {
      return 'warn';
    }
    return 'ok';
  }

  signed(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    const abs = Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${v > 0 ? '+' : v < 0 ? '−' : ''}$${abs}`;
  }

  close(): void {
    this.ref.close();
  }
}
