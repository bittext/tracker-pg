import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { InvestmentThenNowResultDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-investment-then-now-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './investment-then-now-panel.component.html',
  styleUrl: './investment-then-now-panel.component.scss',
})
export class InvestmentThenNowPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  symbol = '';
  investedAmount = 78198.72;
  asOfDate = '2026-06-28';

  loading = false;
  saving = false;
  loadingList = false;
  result: InvestmentThenNowResultDto | null = null;
  saved: InvestmentThenNowResultDto[] = [];

  readonly presets = [
    { label: 'AAPL', symbol: 'AAPL' },
    { label: 'MSFT', symbol: 'MSFT' },
    { label: 'NVDA', symbol: 'NVDA' },
    { label: 'AMZN', symbol: 'AMZN' },
    { label: 'GOOGL', symbol: 'GOOGL' },
    { label: 'SPY', symbol: 'SPY' },
    { label: 'QQQ', symbol: 'QQQ' },
  ];

  ngOnInit(): void {
    this.reloadSaved();
  }

  applyPreset(symbol: string): void {
    this.symbol = symbol;
  }

  compute(save: boolean): void {
    const symbol = this.symbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Enter a company symbol', 'Dismiss', { duration: 4000 });
      return;
    }
    if (!(this.investedAmount > 0)) {
      this.snackBar.open('Invested amount must be positive', 'Dismiss', { duration: 4000 });
      return;
    }
    if (!this.asOfDate) {
      this.snackBar.open('Pick an as-of date', 'Dismiss', { duration: 4000 });
      return;
    }
    if (save) {
      this.saving = true;
    } else {
      this.loading = true;
    }
    this.financeApi
      .computeInvestmentThenNow({
        symbol,
        investedAmount: this.investedAmount,
        asOfDate: this.asOfDate,
        save,
      })
      .subscribe({
        next: (r) => {
          this.result = r;
          this.loading = false;
          this.saving = false;
          if (save) {
            this.snackBar.open('Saved for reference', 'Dismiss', { duration: 2500 });
            this.reloadSaved();
          }
        },
        error: (err) => {
          this.loading = false;
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  selectSaved(row: InvestmentThenNowResultDto): void {
    this.result = row;
    this.symbol = row.symbol;
    this.investedAmount = row.investedAmount;
    this.asOfDate = row.asOfDate;
  }

  deleteSaved(row: InvestmentThenNowResultDto, event?: Event): void {
    event?.stopPropagation();
    if (row.id == null) {
      return;
    }
    this.financeApi.deleteInvestmentThenNow(row.id).subscribe({
      next: () => {
        if (this.result?.id === row.id) {
          this.result = null;
        }
        this.reloadSaved();
        this.snackBar.open('Removed', 'Dismiss', { duration: 2000 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  reloadSaved(): void {
    this.loadingList = true;
    this.financeApi.listInvestmentThenNow().subscribe({
      next: (rows) => {
        this.saved = rows;
        this.loadingList = false;
      },
      error: (err) => {
        this.loadingList = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'itn__pnl--pos' : 'itn__pnl--neg';
  }

  questionPreview(): string {
    const name = this.symbol.trim() || '«stock name»';
    const amt = this.investedAmount?.toLocaleString('en-US', {
      style: 'currency',
      currency: 'USD',
    });
    return `${amt} invested on ${this.asOfDate} in ${name} stocks — how much would it be worth now?`;
  }
}
