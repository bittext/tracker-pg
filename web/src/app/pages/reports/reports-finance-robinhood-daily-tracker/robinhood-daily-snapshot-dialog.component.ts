import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RobinhoodRhDailySnapshotDetailDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';

export interface RobinhoodDailySnapshotDialogData {
  snapshotId: number;
  dayLabel: string;
  accountSuffix: string;
}

@Component({
  selector: 'app-robinhood-daily-snapshot-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatProgressSpinnerModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './robinhood-daily-snapshot-dialog.component.html',
  styleUrl: './robinhood-daily-snapshot-dialog.component.scss',
})
export class RobinhoodDailySnapshotDialogComponent implements OnInit {
  readonly data = inject<RobinhoodDailySnapshotDialogData>(MAT_DIALOG_DATA);
  private readonly financeApi = inject(FinanceApiService);
  private readonly ref = inject(MatDialogRef<RobinhoodDailySnapshotDialogComponent>);

  loading = true;
  detail: RobinhoodRhDailySnapshotDetailDto | null = null;

  ngOnInit(): void {
    this.financeApi.robinhoodDailyTrackerSnapshot(this.data.snapshotId).subscribe({
      next: (d) => {
        this.detail = d;
        this.loading = false;
      },
      error: () => {
        this.detail = null;
        this.loading = false;
      },
    });
  }

  close(): void {
    this.ref.close();
  }

  flowCategoryLabel(category: string): string {
    switch (category) {
      case 'EXTERNAL_IN':
        return 'External in';
      case 'EXTERNAL_OUT':
        return 'External out';
      case 'INTERNAL_IN':
        return 'Internal in';
      case 'INTERNAL_OUT':
        return 'Internal out';
      case 'INTEREST':
        return 'Interest';
      case 'FEE':
        return 'Fee';
      default:
        return category || '—';
    }
  }

  costBasis(h: { marketValue: number; unrealizedPnL: number; costBasis?: number }): number {
    if (h.costBasis != null && h.costBasis !== 0) {
      return h.costBasis;
    }
    return h.marketValue - h.unrealizedPnL;
  }

  pnlClass(positive: boolean): string {
    return positive ? 'rh-snap-dialog__pnl--pos' : 'rh-snap-dialog__pnl--neg';
  }
}
