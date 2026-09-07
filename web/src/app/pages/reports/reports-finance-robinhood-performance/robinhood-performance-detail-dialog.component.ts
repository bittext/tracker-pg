import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { RobinhoodExecutedTradeDto } from '../../../models/finance.models';

export interface RhPerfAccountLine {
  suffix: string;
  label: string;
  start: number | null;
  end: number | null;
  change: number | null;
}

export interface RhPerfDetailDialogData {
  title: string;
  subtitle: string;
  accounts: RhPerfAccountLine[];
  trades: RobinhoodExecutedTradeDto[];
}

@Component({
  selector: 'app-robinhood-performance-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, CurrencyPipe, DecimalPipe],
  templateUrl: './robinhood-performance-detail-dialog.component.html',
  styleUrl: './robinhood-performance-detail-dialog.component.scss',
})
export class RobinhoodPerformanceDetailDialogComponent {
  readonly data = inject<RhPerfDetailDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<RobinhoodPerformanceDetailDialogComponent>);

  close(): void {
    this.ref.close();
  }

  realizedTotal(): number {
    return this.data.trades.reduce((sum, t) => sum + (t.realizedPnl ?? 0), 0);
  }

  isGain(value: number | null | undefined): boolean {
    return value != null && value > 0;
  }

  isLoss(value: number | null | undefined): boolean {
    return value != null && value < 0;
  }

  formatWhen(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleString('en-US', {
      timeZone: 'America/Chicago',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  }
}
