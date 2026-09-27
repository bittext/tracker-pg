import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodIndividualMarginDayDto,
  RobinhoodIndividualMarginPeekDto,
  RobinhoodIndividualMarginWatchDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorMessage } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-margin-watch',
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
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-margin-watch.component.html',
  styleUrl: './reports-finance-robinhood-margin-watch.component.scss',
})
export class ReportsFinanceRobinhoodMarginWatchComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  readonly journalNav = inject(TradingJournalNavService);

  reportYear = new Date().getFullYear();
  loading = false;
  peeking = false;
  watch: RobinhoodIndividualMarginWatchDto | null = null;

  readonly yearChoices = [this.reportYear, this.reportYear - 1, this.reportYear - 2];

  ngOnInit(): void {
    this.reportYear = this.journalNav.insightsYear() || this.reportYear;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi.robinhoodMarginWatch(this.reportYear, false).subscribe({
      next: (watch) => {
        this.watch = watch;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorMessage(err) || 'Could not load margin watch', 'Dismiss', { duration: 6000 });
      },
    });
  }

  peekNow(): void {
    this.peeking = true;
    this.financeApi.robinhoodMarginWatchPeek().subscribe({
      next: (result) => {
        this.watch = result.watch;
        this.peeking = false;
        this.snackBar.open(result.message, 'Dismiss', { duration: 4000 });
      },
      error: (err) => {
        this.peeking = false;
        this.snackBar.open(formatHttpErrorMessage(err) || 'Live peek failed', 'Dismiss', { duration: 6000 });
      },
    });
  }

  onYearChange(): void {
    this.journalNav.insightsYear.set(this.reportYear);
    this.load();
  }

  latest(): RobinhoodIndividualMarginPeekDto | null {
    return this.watch?.latest ?? null;
  }

  /** Call-risk color for banners and the maintenance meter — not the borrow %. */
  riskTone(): string {
    const status = this.watch?.standing?.riskStatus || this.latest()?.riskStatus || 'UNKNOWN';
    if (status === 'CALL' || status === 'NEAR_CALL') {
      return 'call';
    }
    if (status === 'HIGH') {
      return 'hot';
    }
    if (status === 'ELEVATED') {
      return 'warn';
    }
    if (status === 'LOW') {
      return 'ok';
    }
    return 'unknown';
  }

  /** 0 = pleasant green, ~70 at 50% borrow = red footprint. Text stays dark. */
  borrowRedMix(): number {
    const pct = this.latest()?.borrowPercent ?? 0;
    return Math.max(0, Math.min(72, ((pct - 5) / 50) * 72));
  }

  borrowTone(pct: number | null | undefined): string {
    if (pct == null) {
      return 'unknown';
    }
    if (pct >= 50) {
      return 'deep';
    }
    if (pct >= 40) {
      return 'hot';
    }
    if (pct >= 25) {
      return 'warn';
    }
    return 'ok';
  }

  gaugeOffset(): number {
    const pct = Math.max(0, Math.min(100, this.watch?.latest?.borrowPercent ?? 0));
    const circ = 2 * Math.PI * 54;
    return circ * (1 - pct / 100);
  }

  maintWidth(): number {
    return Math.max(0, Math.min(100, this.watch?.standing?.maintenanceSharePercent ?? 0));
  }

  bufferWidth(): number {
    return Math.max(0, Math.min(100, this.watch?.standing?.bufferPercent ?? 0));
  }

  tapeHeight(day: RobinhoodIndividualMarginDayDto): number {
    return Math.max(8, Math.min(100, day.close.borrowPercent));
  }

  pulseHeight(peek: RobinhoodIndividualMarginPeekDto): number {
    return Math.max(8, Math.min(100, peek.borrowPercent));
  }

  captureLabel(kind: string | null | undefined): string {
    if (kind === 'MANUAL') {
      return 'peek';
    }
    if (kind === 'INTRADAY') {
      return 'hourly';
    }
    if (kind === 'SCHEDULED') {
      return 'close';
    }
    return kind?.toLowerCase() || '';
  }

  alertLabel(kind: string): string {
    if (kind === 'NEAR_CALL') {
      return 'Entered 5% call band';
    }
    if (kind === 'LEFT_NEAR_CALL') {
      return 'Left 5% call band';
    }
    return kind;
  }

  signed(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    const abs = Math.abs(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return `${v > 0 ? '+' : v < 0 ? '−' : ''}$${abs}`;
  }

  signedPct(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    const sign = v > 0 ? '+' : v < 0 ? '−' : '';
    return `${sign}${Math.abs(v).toFixed(2)}%`;
  }
}
