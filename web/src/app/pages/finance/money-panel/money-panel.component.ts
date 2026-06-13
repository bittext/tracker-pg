import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import {
  BankingInstitutionDto,
  BankingInstitutionTypeDto,
  BankingLedgerDto,
  BankingLedgerRange,
  BankingTransactionDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import {
  BankingFlowByDescriptionRow,
  BankingFlowByInstitutionRow,
  BankingFlowByMonthRow,
  BankingFlowByTypeRow,
  BankingFlowTotals,
  buildBankingFlowByInstitution,
  buildBankingFlowByMonth,
  buildBankingFlowByType,
  buildBankingSpendingByDescription,
  computeBankingFlowTotals,
  filterBankingCreditTransactions,
  filterBankingDebitTransactions,
} from '../../../util/banking-ledger-flow.util';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-money-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './money-panel.component.html',
  styleUrl: './money-panel.component.scss',
})
export class MoneyPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  moneySubTabIndex = 0;

  ledgerRange: BankingLedgerRange = 'MONTH';
  ledgerYear = new Date().getFullYear();
  ledgerMonth = new Date().getMonth() + 1;
  ledgerQuarter = Math.floor(new Date().getMonth() / 3) + 1;
  filterInstitutionId = '';
  filterInstitutionTypeId = '';

  institutions: BankingInstitutionDto[] = [];
  institutionTypes: BankingInstitutionTypeDto[] = [];
  ledger: BankingLedgerDto | null = null;
  loading = false;

  totals: BankingFlowTotals = { creditTotal: 0, debitTotal: 0, net: 0, txnCount: 0 };
  byInstitution: BankingFlowByInstitutionRow[] = [];
  byType: BankingFlowByTypeRow[] = [];
  byMonth: BankingFlowByMonthRow[] = [];
  incomeTxns: BankingTransactionDto[] = [];
  spendingTxns: BankingTransactionDto[] = [];
  spendingByDescription: BankingFlowByDescriptionRow[] = [];

  readonly monthChoices = [
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' },
  ];

  readonly instColumns = ['institutionName', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly typeColumns = ['typeLabel', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly monthColumns = ['monthLabel', 'creditTotal', 'debitTotal', 'net', 'savingsRatePct', 'txnCount'];
  readonly budgetColumns = ['monthLabel', 'creditTotal', 'debitTotal', 'net', 'savingsRatePct'];
  readonly incomeColumns = ['txnDate', 'institutionName', 'description', 'amount'];
  readonly spendingColumns = ['txnDate', 'institutionName', 'description', 'amount'];
  readonly spendDescColumns = ['description', 'debitTotal', 'txnCount'];

  ngOnInit(): void {
    this.financeApi.listBankingInstitutions().subscribe({
      next: (rows) => {
        this.institutions = rows;
      },
      error: (e) =>
        this.snackBar.open(`Could not load institutions — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 }),
    });
    this.financeApi.listBankingInstitutionTypes().subscribe({
      next: (rows) => {
        this.institutionTypes = rows ?? [];
      },
      error: () => {
        /* optional */
      },
    });
    this.loadLedger();
  }

  onInstitutionFilterChange(): void {
    this.filterInstitutionTypeId = '';
    this.loadLedger();
  }

  onTypeFilterChange(): void {
    this.filterInstitutionId = '';
    this.loadLedger();
  }

  loadLedger(): void {
    this.loading = true;
    const inst = this.filterInstitutionId ? Number(this.filterInstitutionId) : null;
    const typ = this.filterInstitutionTypeId ? Number(this.filterInstitutionTypeId) : null;
    this.financeApi
      .bankingLedger(
        this.ledgerRange,
        this.ledgerYear,
        this.ledgerRange === 'MONTH' ? this.ledgerMonth : null,
        this.ledgerRange === 'QUARTER' ? this.ledgerQuarter : null,
        inst,
        typ,
      )
      .subscribe({
        next: (dto) => {
          this.ledger = dto;
          this.loading = false;
          this.recompute(dto);
        },
        error: (e) => {
          this.loading = false;
          this.ledger = null;
          this.snackBar.open(`Could not load banking ledger — ${formatHttpErrorDetail(e)}`, undefined, {
            duration: 8000,
          });
        },
      });
  }

  get avgMonthlyIncome(): number | null {
    if (!this.byMonth.length) {
      return null;
    }
    const sum = this.byMonth.reduce((s, r) => s + r.creditTotal, 0);
    return sum / this.byMonth.length;
  }

  get avgMonthlySpending(): number | null {
    if (!this.byMonth.length) {
      return null;
    }
    const sum = this.byMonth.reduce((s, r) => s + r.debitTotal, 0);
    return sum / this.byMonth.length;
  }

  get avgSavingsRatePct(): number | null {
    if (!this.byMonth.length) {
      return null;
    }
    const rates = this.byMonth.map((r) => r.savingsRatePct).filter((v): v is number => v != null);
    if (!rates.length) {
      return null;
    }
    return rates.reduce((s, v) => s + v, 0) / rates.length;
  }

  money(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(v);
  }

  pct(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) {
      return '—';
    }
    return `${v.toFixed(1)}%`;
  }

  formatDate(d: string | null | undefined): string {
    if (!d) {
      return '—';
    }
    return new Date(d + 'T12:00:00').toLocaleDateString();
  }

  netClass(v: number): string {
    if (v > 0) {
      return 'money-net-pos';
    }
    if (v < 0) {
      return 'money-net-neg';
    }
    return '';
  }

  private recompute(dto: BankingLedgerDto): void {
    const txns = dto.transactions ?? [];
    this.totals = computeBankingFlowTotals(txns);
    this.byInstitution = buildBankingFlowByInstitution(txns);
    this.byType = buildBankingFlowByType(txns);
    this.byMonth = buildBankingFlowByMonth(txns);
    this.incomeTxns = filterBankingCreditTransactions(txns);
    this.spendingTxns = filterBankingDebitTransactions(txns);
    this.spendingByDescription = buildBankingSpendingByDescription(txns);
  }
}
