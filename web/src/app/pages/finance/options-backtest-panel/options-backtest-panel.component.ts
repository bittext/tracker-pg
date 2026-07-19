import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { OptionsBacktestResultDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-options-backtest-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './options-backtest-panel.component.html',
  styleUrl: './options-backtest-panel.component.scss',
})
export class OptionsBacktestPanelComponent {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  symbol = 'SPY';
  lookbackDays = 252;
  startingCapital = 100000;
  callOtmPercent = 5;
  daysToExpiration = 30;
  riskFreeRatePct = 4;

  loading = false;
  result: OptionsBacktestResultDto | null = null;

  readonly presets = [
    { label: 'SPY', symbol: 'SPY' },
    { label: 'QQQ', symbol: 'QQQ' },
    { label: 'IWM', symbol: 'IWM' },
    { label: 'AAPL', symbol: 'AAPL' },
    { label: 'NBIS', symbol: 'NBIS' },
  ];

  run(): void {
    const symbol = this.symbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Enter a symbol', 'Dismiss', { duration: 4000 });
      return;
    }
    this.loading = true;
    this.result = null;
    this.financeApi
      .optionsBacktest({
        symbol,
        lookbackDays: this.lookbackDays,
        startingCapital: this.startingCapital,
        callOtmPercent: this.callOtmPercent,
        daysToExpiration: this.daysToExpiration,
        riskFreeRate: this.riskFreeRatePct / 100,
      })
      .subscribe({
        next: (r) => {
          this.result = r;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  applyPreset(symbol: string): void {
    this.symbol = symbol;
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'oback__pnl--pos' : 'oback__pnl--neg';
  }

  equityPath(): string {
    const curve = this.result?.equityCurve ?? [];
    if (curve.length < 2) {
      return '';
    }
    const values = curve.map((p) => p.equity);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = Math.max(max - min, 1);
    return curve
      .map((point, index) => {
        const x = (index / (curve.length - 1)) * 100;
        const y = 36 - ((point.equity - min) / range) * 32;
        return `${index === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`;
      })
      .join(' ');
  }
}
