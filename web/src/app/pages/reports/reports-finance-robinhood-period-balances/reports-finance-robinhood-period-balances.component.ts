import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodRhPeriodAccountFigureDto,
  RobinhoodRhPeriodBalanceRowDto,
  RobinhoodRhPeriodBalancesDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-period-balances',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-period-balances.component.html',
  styleUrl: './reports-finance-robinhood-period-balances.component.scss',
})
export class ReportsFinanceRobinhoodPeriodBalancesComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  loading = false;
  data: RobinhoodRhPeriodBalancesDto | null = null;

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi.robinhoodDailyTrackerPeriodBalances(this.reportYear).subscribe({
      next: (res) => {
        this.data = res;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load period balances', 'Dismiss', {
          duration: 7000,
        });
      },
    });
  }

  figureFor(
    row: RobinhoodRhPeriodBalanceRowDto,
    suffix: string,
  ): RobinhoodRhPeriodAccountFigureDto | undefined {
    return row.accounts.find((a) => a.accountSuffix === suffix);
  }

  pnlClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return '';
    }
    return value > 0 ? 'rh-bal__pnl--pos' : 'rh-bal__pnl--neg';
  }

  changePercent(start: number | null | undefined, change: number | null | undefined): number | null {
    if (start == null || change == null || !Number.isFinite(start) || start === 0) {
      return null;
    }
    return (change / start) * 100;
  }
}
