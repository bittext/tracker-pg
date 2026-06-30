import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { FinanceApiService } from '../../../services/finance-api.service';
import { rhHoldingCurrentUnitPrice, rhHoldingPnlPercent } from '../rh-holding-display.util';
import { RobinhoodRhAccountsTrackDto, RobinhoodRhCashFlowEventDto, RobinhoodRhHoldingDto } from '../../../models/finance.models';

@Component({
  selector: 'app-rh-accounts-track-panel',
  standalone: true,
  imports: [CommonModule, MatButtonModule, CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './rh-accounts-track-panel.component.html',
  styleUrl: './rh-accounts-track-panel.component.scss',
})
export class RhAccountsTrackPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);

  track: RobinhoodRhAccountsTrackDto | null = null;
  loading = false;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.financeApi.robinhoodRhAccountsTrack().subscribe({
      next: (t) => {
        this.track = t;
        this.loading = false;
      },
      error: () => {
        this.track = null;
        this.loading = false;
      },
    });
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  }

  pnlClass(positive: boolean): string {
    return positive ? 'rh-acct__pnl--pos' : 'rh-acct__pnl--neg';
  }

  flowRowClass(row: RobinhoodRhCashFlowEventDto): string {
    if (row.flowCategory === 'STARTING_BALANCE') {
      return 'rh-acct__row--starting';
    }
    if (row.internalTransfer) {
      return 'rh-acct__row--internal';
    }
    return '';
  }

  directionClass(row: RobinhoodRhCashFlowEventDto): string {
    if (row.flowCategory === 'STARTING_BALANCE') {
      return 'rh-acct__dir--starting';
    }
    return row.direction === 'IN' ? 'rh-acct__dir--in' : 'rh-acct__dir--out';
  }

  directionLabel(row: RobinhoodRhCashFlowEventDto): string {
    if (row.flowCategory === 'STARTING_BALANCE') {
      return 'Starting';
    }
    if (row.flowCategory === 'INTERNAL_OUT') {
      return 'Out (internal)';
    }
    if (row.flowCategory === 'INTERNAL_IN') {
      return 'In (internal)';
    }
    return row.direction === 'IN' ? 'In' : 'Out';
  }

  categoryLabel(category: string): string {
    switch (category) {
      case 'STARTING_BALANCE':
        return 'Starting balance';
      case 'INTERNAL_IN':
        return 'Internal in';
      case 'INTERNAL_OUT':
        return 'Internal out';
      case 'EXTERNAL_IN':
        return 'External in';
      case 'EXTERNAL_OUT':
        return 'External out';
      case 'INTEREST':
        return 'Interest';
      case 'FEE':
        return 'Fee';
      default:
        return category || '—';
    }
  }

  currentCost(h: RobinhoodRhHoldingDto): number | null {
    return rhHoldingCurrentUnitPrice(h);
  }

  pnlPercent(h: RobinhoodRhHoldingDto): number | null {
    return rhHoldingPnlPercent(h);
  }
}
