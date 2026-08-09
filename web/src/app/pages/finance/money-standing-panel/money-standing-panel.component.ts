import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  BankingLedgerDto,
  BankingLedgerRange,
  FinanceCreditCardDto,
  FinanceCreditCardSummaryDto,
  FinanceCreditStandingDto,
  FinanceLoanDto,
  RobinhoodAgenticBankingStatusDto,
} from '../../../models/finance.models';
import { FinanceAgenticBankingApiService } from '../../../services/finance-agentic-banking-api.service';
import { FinanceApiService } from '../../../services/finance-api.service';
import { FinanceCreditCardsApiService } from '../../../services/finance-credit-cards-api.service';
import { FinanceCreditStandingApiService } from '../../../services/finance-credit-standing-api.service';
import { FinanceLoansApiService } from '../../../services/finance-loans-api.service';
import { computeBankingFlowTotals } from '../../../util/banking-ledger-flow.util';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { FinanceEntryDocumentsComponent } from '../finance-entry-documents/finance-entry-documents.component';
import { MarketsRoadmapSummaryComponent } from '../../markets/markets-roadmap-summary/markets-roadmap-summary.component';

type PeriodMode = 'WEEK' | 'BIWEEK';

@Component({
  selector: 'app-money-standing-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
    DecimalPipe,
    FinanceEntryDocumentsComponent,
    MarketsRoadmapSummaryComponent,
  ],
  templateUrl: './money-standing-panel.component.html',
  styleUrl: './money-standing-panel.component.scss',
})
export class MoneyStandingPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly creditApi = inject(FinanceCreditCardsApiService);
  private readonly loansApi = inject(FinanceLoansApiService);
  private readonly standingApi = inject(FinanceCreditStandingApiService);
  private readonly agenticApi = inject(FinanceAgenticBankingApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly annualCreditReportUrl = 'https://www.annualcreditreport.com/';

  loading = false;
  savingStanding = false;
  period: PeriodMode = 'WEEK';
  weekStart = this.mondayOf(new Date());

  ledger: BankingLedgerDto | null = null;
  creditSummary: FinanceCreditCardSummaryDto | null = null;
  cards: FinanceCreditCardDto[] = [];
  loans: FinanceLoanDto[] = [];
  agentic: RobinhoodAgenticBankingStatusDto | null = null;
  standing: FinanceCreditStandingDto | null = null;

  standingForm = {
    score: null as number | null,
    bureau: '',
    reportedAsOf: '',
    notes: '',
    annualReportPulledAt: '',
  };

  earnings = 0;
  expenses = 0;
  netCash = 0;
  txnCount = 0;

  ngOnInit(): void {
    this.refresh();
  }

  get rangeLabel(): string {
    return this.ledger?.rangeLabel || this.formatPeriodLabel();
  }

  get loanTotalBalance(): number {
    return this.loans.reduce((s, l) => s + (Number(l.balanceToPay ?? l.currentBalance) || 0), 0);
  }

  get loanTotalPaid(): number {
    return this.loans.reduce((s, l) => s + (Number(l.paidSoFar) || 0), 0);
  }

  get loanProgressPct(): number | null {
    const paid = this.loanTotalPaid;
    const remaining = this.loanTotalBalance;
    const total = paid + remaining;
    if (total <= 0) {
      return null;
    }
    return (paid / total) * 100;
  }

  get cardsDueSoon(): FinanceCreditCardDto[] {
    const today = this.todayIso();
    return this.cards
      .filter((c) => c.paymentDueDate)
      .filter((c) => {
        const due = (c.paymentDueDate || '').slice(0, 10);
        return due >= today && due <= this.addDays(today, 14);
      })
      .sort((a, b) => (a.paymentDueDate || '').localeCompare(b.paymentDueDate || ''));
  }

  get standingNarrative(): string {
    const parts: string[] = [];
    const period = this.period === 'WEEK' ? 'This week' : 'These two weeks';

    if (this.netCash > 50) {
      parts.push(`${period} you earned more than you spent (net ${this.formatMoney(this.netCash)}).`);
    } else if (this.netCash < -50) {
      parts.push(`${period} spending outpaced earnings by ${this.formatMoney(Math.abs(this.netCash))}.`);
    } else if (this.txnCount) {
      parts.push(`${period} cash flow is roughly break-even.`);
    } else {
      parts.push(
        `${period} has no banking ledger activity yet — sync Plaid or import statements under Banking.`,
      );
    }

    if (this.creditSummary) {
      const util = this.creditSummary.overallUtilizationPct;
      const health = this.creditSummary.healthLabel || 'n/a';
      if (util != null) {
        parts.push(
          `Credit cards: ${this.creditSummary.cardCount} card(s), ${util.toFixed(0)}% utilization (${health}).`,
        );
      } else {
        parts.push(`Credit cards: ${this.creditSummary.cardCount} on file.`);
      }
    }

    if (this.loans.length) {
      const pct = this.loanProgressPct;
      parts.push(
        `Loans: ${this.loans.length} active · ${this.formatMoney(this.loanTotalBalance)} remaining` +
          (pct != null ? ` · ${pct.toFixed(0)}% paid down` : '') +
          '.',
      );
    } else {
      parts.push('No loans recorded yet — add them under the Loans tab.');
    }

    if (this.standing?.score != null) {
      parts.push(
        `Bureau score on file: ${this.standing.score}` +
          (this.standing.bureau ? ` (${this.standing.bureau})` : '') +
          '.',
      );
    } else {
      parts.push(
        'No bureau score saved yet — pull your free Annual Credit Report and record the score below.',
      );
    }

    if (this.agentic?.connected) {
      const avail = this.agentic.availableBalanceUsd;
      parts.push(
        `Robinhood Agentic card` +
          (avail != null ? ` available ${this.formatMoney(avail)}` : '') +
          (this.agentic.totalSpendUsd != null
            ? ` · spent ${this.formatMoney(this.agentic.totalSpendUsd)} this cycle`
            : '') +
          '.',
      );
    }

    return parts.join(' ');
  }

  shiftPeriod(delta: number): void {
    const days = this.period === 'WEEK' ? 7 : 14;
    this.weekStart = this.addDays(this.weekStart, delta * days);
    this.refreshCashFlow();
  }

  onPeriodChange(): void {
    this.weekStart = this.mondayOf(new Date());
    this.refreshCashFlow();
  }

  refresh(): void {
    this.loading = true;
    forkJoin({
      ledger: this.financeApi
        .bankingLedger(this.period as BankingLedgerRange, this.yearOf(this.weekStart), null, null, null, null, this.weekStart)
        .pipe(catchError(() => of(null))),
      creditSummary: this.creditApi.summary().pipe(catchError(() => of(null))),
      cards: this.creditApi.list().pipe(catchError(() => of([] as FinanceCreditCardDto[]))),
      loans: this.loansApi.list().pipe(catchError(() => of([] as FinanceLoanDto[]))),
      standing: this.standingApi.get().pipe(catchError(() => of(null))),
      agentic: this.agenticApi.status().pipe(catchError(() => of(null))),
    }).subscribe({
      next: (bundle) => {
        this.applyLedger(bundle.ledger);
        this.creditSummary = bundle.creditSummary;
        this.cards = bundle.cards ?? [];
        this.loans = bundle.loans ?? [];
        this.standing = bundle.standing;
        this.agentic = bundle.agentic;
        this.hydrateStandingForm();
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load standing — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  refreshCashFlow(): void {
    this.financeApi
      .bankingLedger(this.period as BankingLedgerRange, this.yearOf(this.weekStart), null, null, null, null, this.weekStart)
      .subscribe({
        next: (ledger) => this.applyLedger(ledger),
        error: (e) =>
          this.snackBar.open(`Could not load cash flow — ${formatHttpErrorDetail(e)}`, undefined, {
            duration: 5000,
          }),
      });
  }

  saveStanding(): void {
    this.savingStanding = true;
    this.standingApi
      .upsert({
        score: this.standingForm.score,
        bureau: this.standingForm.bureau || null,
        reportedAsOf: this.standingForm.reportedAsOf || null,
        notes: this.standingForm.notes || null,
        annualReportPulledAt: this.standingForm.annualReportPulledAt || null,
      })
      .subscribe({
        next: (row) => {
          this.standing = row;
          this.hydrateStandingForm();
          this.savingStanding = false;
          this.snackBar.open('Credit standing saved', undefined, { duration: 2500 });
        },
        error: (e) => {
          this.savingStanding = false;
          this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
        },
      });
  }

  markAnnualReportPulled(): void {
    this.standingForm.annualReportPulledAt = this.todayIso();
    this.saveStanding();
  }

  loanPaidPct(loan: FinanceLoanDto): number | null {
    const paid = Number(loan.paidSoFar) || 0;
    const remain = Number(loan.balanceToPay ?? loan.currentBalance) || 0;
    const total = paid + remain;
    if (total <= 0) {
      return null;
    }
    return (paid / total) * 100;
  }

  private applyLedger(ledger: BankingLedgerDto | null): void {
    this.ledger = ledger;
    if (!ledger) {
      this.earnings = 0;
      this.expenses = 0;
      this.netCash = 0;
      this.txnCount = 0;
      return;
    }
    const t = computeBankingFlowTotals(ledger.transactions ?? []);
    this.earnings = t.creditTotal;
    this.expenses = t.debitTotal;
    this.netCash = t.net;
    this.txnCount = t.txnCount;
  }

  private hydrateStandingForm(): void {
    const s = this.standing;
    this.standingForm = {
      score: s?.score ?? null,
      bureau: s?.bureau ?? '',
      reportedAsOf: s?.reportedAsOf?.slice(0, 10) ?? '',
      notes: s?.notes ?? '',
      annualReportPulledAt: s?.annualReportPulledAt?.slice(0, 10) ?? '',
    };
  }

  private mondayOf(d: Date): string {
    const x = new Date(d);
    const day = x.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    x.setDate(x.getDate() + diff);
    return this.toIso(x);
  }

  private yearOf(iso: string): number {
    return Number(iso.slice(0, 4)) || new Date().getFullYear();
  }

  private todayIso(): string {
    return this.toIso(new Date());
  }

  private toIso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private addDays(iso: string, days: number): string {
    const d = new Date(iso + 'T12:00:00');
    d.setDate(d.getDate() + days);
    return this.toIso(d);
  }

  private formatPeriodLabel(): string {
    const end = this.addDays(this.weekStart, this.period === 'WEEK' ? 6 : 13);
    return `${this.weekStart} → ${end}`;
  }

  private formatMoney(n: number): string {
    return n.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
  }
}
