import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RobinhoodYtdCheckDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorMessage } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-ytd-check',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DatePipe,
  ],
  templateUrl: './reports-finance-robinhood-ytd-check.component.html',
  styleUrl: './reports-finance-robinhood-ytd-check.component.scss',
})
export class ReportsFinanceRobinhoodYtdCheckComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly journalNav = inject(TradingJournalNavService);

  reportYear = new Date().getFullYear();
  loading = false;
  data: RobinhoodYtdCheckDto | null = null;
  readonly yearChoices = [this.reportYear, this.reportYear - 1, this.reportYear - 2];

  ngOnInit(): void {
    this.reportYear = this.journalNav.insightsYear() || this.reportYear;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.journalNav.insightsYear.set(this.reportYear);
    this.financeApi.robinhoodYtdCheck(this.reportYear).subscribe({
      next: (data) => {
        this.data = data;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorMessage(err) || 'Could not load YTD check', 'Dismiss', {
          duration: 7000,
        });
      },
    });
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
      timeZone: 'America/New_York',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    });
  }
}
