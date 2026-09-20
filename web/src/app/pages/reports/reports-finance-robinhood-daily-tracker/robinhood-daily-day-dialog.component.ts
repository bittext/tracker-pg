import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogConfig, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import {
  RobinhoodRhCashFlowEventDto,
  RobinhoodRhDailyTrackerAccountCellDto,
  RobinhoodRhDailyTrackerDayDto,
  RobinhoodRhDailyTradeDto,
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
  width: 'min(880px, 96vw)',
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

  tradeSymbol(tr: RobinhoodRhDailyTradeDto): {
    ticker: string;
    contract: string | null;
    option: boolean;
  } {
    return this.parseSymbol(tr.symbol);
  }

  isBuy(side: string | null | undefined): boolean {
    return (side || '').toLowerCase().startsWith('buy');
  }

  isSell(side: string | null | undefined): boolean {
    return (side || '').toLowerCase().startsWith('sell');
  }

  sideLabel(side: string | null | undefined): string {
    if (this.isBuy(side)) {
      return 'Buy';
    }
    if (this.isSell(side)) {
      return 'Sell';
    }
    const raw = (side || '').trim();
    return raw || '—';
  }

  /** Fill price when the trade executed; limit is only a fallback. */
  tradeUnitPrice(tr: RobinhoodRhDailyTradeDto): number | null {
    if (tr.averagePrice != null && Number.isFinite(tr.averagePrice)) {
      return tr.averagePrice;
    }
    if (tr.limitPrice != null && Number.isFinite(tr.limitPrice)) {
      return tr.limitPrice;
    }
    return null;
  }

  /** Cash of the fill. Option premium is per share, so × 100. */
  tradeCost(tr: RobinhoodRhDailyTradeDto): number | null {
    const px = this.tradeUnitPrice(tr);
    if (px == null || tr.quantity == null || !Number.isFinite(tr.quantity)) {
      return null;
    }
    const multiplier = this.tradeSymbol(tr).option ? 100 : 1;
    return Math.abs(tr.quantity) * Math.abs(px) * multiplier;
  }

  private parseSymbol(raw: string | null | undefined): {
    ticker: string;
    contract: string | null;
    option: boolean;
  } {
    const symbol = (raw ?? '').trim();
    if (!symbol) {
      return { ticker: '—', contract: null, option: false };
    }
    const spaced = symbol.match(
      /^([A-Z][A-Z0-9.]{0,5})\s+\$?([\d.]+)\s+(CALL|PUT)\s+(\d{4}-\d{2}-\d{2})\b/i,
    );
    if (spaced) {
      return {
        ticker: spaced[1].toUpperCase(),
        contract: `$${this.trimStrike(spaced[2])} ${spaced[3].toUpperCase()} · ${this.formatExpiry(spaced[4])}`,
        option: true,
      };
    }
    const occ = symbol.replace(/\s+/g, '').match(/^([A-Z]{1,6})(\d{6})([CP])(\d{8})$/i);
    if (occ) {
      const yy = occ[2].slice(0, 2);
      const mm = occ[2].slice(2, 4);
      const dd = occ[2].slice(4, 6);
      const strike = (Number(occ[4]) / 1000).toString();
      return {
        ticker: occ[1].toUpperCase(),
        contract: `$${this.trimStrike(strike)} ${occ[3].toUpperCase() === 'P' ? 'PUT' : 'CALL'} · ${this.formatExpiry(`20${yy}-${mm}-${dd}`)}`,
        option: true,
      };
    }
    if (/\b(CALL|PUT)\b/i.test(symbol) || /\s\$\d/.test(symbol)) {
      const ticker = symbol.split(/\s+/)[0] || symbol;
      const rest = symbol.slice(ticker.length).trim();
      return { ticker: ticker.toUpperCase(), contract: rest || null, option: true };
    }
    return { ticker: symbol, contract: null, option: false };
  }

  private trimStrike(value: string): string {
    const n = Number(value);
    if (!Number.isFinite(n)) {
      return value;
    }
    return n.toLocaleString('en-US', { maximumFractionDigits: 2 });
  }

  private formatExpiry(iso: string): string {
    const [year, month, day] = iso.split('-').map((p) => Number(p));
    if (!year || !month || !day) {
      return iso;
    }
    return new Date(year, month - 1, day).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }

  flowDirectionLabel(f: RobinhoodRhCashFlowEventDto): string {
    const dir = (f.direction || '').toLowerCase();
    if (dir === 'in' || (f.flowCategory || '').includes('_IN')) {
      return 'In';
    }
    if (dir === 'out' || (f.flowCategory || '').includes('_OUT')) {
      return 'Out';
    }
    return f.direction || 'Flow';
  }

  flowSigned(f: RobinhoodRhCashFlowEventDto): number {
    return this.flowDirectionLabel(f) === 'Out' ? -1 : 1;
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
