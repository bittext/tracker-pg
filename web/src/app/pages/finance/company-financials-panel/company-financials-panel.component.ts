import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  CompanyFinancialsResponseDto,
  SymbolSearchMatchDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

/** A bare ticker, e.g. AAPL, BRK.B, RDS-A — no spaces, short. */
const TICKER_PATTERN = /^[A-Za-z][A-Za-z.\-]{0,6}$/;

@Component({
  selector: 'app-company-financials-panel',
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
    DecimalPipe,
  ],
  templateUrl: './company-financials-panel.component.html',
  styleUrl: './company-financials-panel.component.scss',
})
export class CompanyFinancialsPanelComponent {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  query = '';
  loading = false;
  searching = false;
  result: CompanyFinancialsResponseDto | null = null;
  searchMatches: SymbolSearchMatchDto[] = [];
  searchedQuery = '';

  readonly presets = ['AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL'];

  applyPreset(symbol: string): void {
    this.query = symbol;
    this.load();
  }

  load(): void {
    const raw = this.query.trim();
    if (!raw) {
      this.snackBar.open('Enter a company name or symbol', 'Dismiss', { duration: 4000 });
      return;
    }
    this.searchMatches = [];
    if (TICKER_PATTERN.test(raw)) {
      this.loadQuarters(raw.toUpperCase());
      return;
    }
    this.resolveSymbol(raw);
  }

  private resolveSymbol(query: string): void {
    this.searching = true;
    this.result = null;
    this.financeApi.companyFinancialsSymbolSearch(query).subscribe({
      next: (r) => {
        this.searching = false;
        if (r.autoSelected && r.matches.length === 1) {
          this.loadQuarters(r.matches[0].symbol);
          return;
        }
        if (!r.matches.length) {
          this.snackBar.open(`No matching company found for "${query}"`, 'Dismiss', { duration: 5000 });
          return;
        }
        this.searchedQuery = query;
        this.searchMatches = r.matches;
      },
      error: (err) => {
        this.searching = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  selectMatch(m: SymbolSearchMatchDto): void {
    this.searchMatches = [];
    this.query = m.symbol;
    this.loadQuarters(m.symbol);
  }

  private loadQuarters(symbol: string): void {
    this.loading = true;
    this.result = null;
    this.financeApi.companyFinancialsQuarters(symbol).subscribe({
      next: (r) => {
        this.loading = false;
        this.result = r;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  /** Newest quarter first for the table. */
  get quartersDesc() {
    return this.result ? [...this.result.quarters].reverse() : [];
  }

  verdictClass(verdict: string | undefined): string {
    switch (verdict) {
      case 'Improving':
        return 'cf__verdict--up';
      case 'Declining':
        return 'cf__verdict--down';
      case 'Mixed':
        return 'cf__verdict--mixed';
      default:
        return 'cf__verdict--unknown';
    }
  }
}
