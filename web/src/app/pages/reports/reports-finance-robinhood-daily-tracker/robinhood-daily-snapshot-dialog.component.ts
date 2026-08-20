import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogConfig, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FinanceApiService } from '../../../services/finance-api.service';
import { rhHoldingAverageBuyPrice, rhHoldingCurrentUnitPrice, rhHoldingPnlPercent } from '../../finance/rh-holding-display.util';
import {
  RobinhoodRhDailySnapshotDetailDto,
  RobinhoodRhDailySnapshotHoldingDto,
  RobinhoodRhHoldingDto,
} from '../../../models/finance.models';

export interface RobinhoodDailySnapshotDialogData {
  snapshotId: number;
  dayLabel: string;
  accountSuffix: string;
  scheduledCaptureEnabled?: boolean;
}

/** Wide enough for holdings (8 cols) + cash-flow tables without clipping. */
export const RH_SNAPSHOT_DIALOG_CONFIG: Pick<MatDialogConfig, 'width' | 'maxWidth' | 'maxHeight' | 'panelClass'> = {
  width: 'min(1500px, 98vw)',
  maxWidth: '98vw',
  maxHeight: '92vh',
  panelClass: 'rh-snap-dialog-panel',
};

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

  pnlClass(positive: boolean): string {
    return positive ? 'rh-snap-dialog__pnl--pos' : 'rh-snap-dialog__pnl--neg';
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
      return 'rh-snap-dialog__pnl--pos';
    }
    if (s === 'sell') {
      return 'rh-snap-dialog__pnl--neg';
    }
    return '';
  }

  currentCost(h: RobinhoodRhHoldingDto): number | null {
    return rhHoldingCurrentUnitPrice(h);
  }

  averageCost(h: RobinhoodRhHoldingDto): number {
    return rhHoldingAverageBuyPrice(h);
  }

  pnlPercent(h: RobinhoodRhHoldingDto): number | null {
    return rhHoldingPnlPercent(h);
  }

  holdingTrack(row: RobinhoodRhDailySnapshotHoldingDto): string {
    const h = row.holding;
    return `${h.symbol}|${h.positionType}|${h.positionKey ?? ''}|${row.exited ? 'out' : 'in'}`;
  }

  showDelta(value: number | null | undefined): boolean {
    return value != null && value !== 0;
  }

  deltaClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '';
    }
    return this.pnlClass(value > 0);
  }

  signedMoney(value: number | null | undefined): string {
    if (value == null) {
      return '';
    }
    const abs = Math.abs(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' });
    if (value > 0) {
      return `+${abs}`;
    }
    if (value < 0) {
      return `-${abs}`;
    }
    return abs;
  }

  signedQty(value: number | null | undefined): string {
    if (value == null) {
      return '';
    }
    const abs = Math.abs(value).toLocaleString('en-US', { maximumFractionDigits: 4 });
    if (value > 0) {
      return `+${abs}`;
    }
    if (value < 0) {
      return `-${abs}`;
    }
    return abs;
  }
}
