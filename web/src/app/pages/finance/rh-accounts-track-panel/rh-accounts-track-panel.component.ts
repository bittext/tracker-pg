import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { FinanceApiService } from '../../../services/finance-api.service';
import { RobinhoodRhAccountsTrackDto } from '../../../models/finance.models';

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
}
