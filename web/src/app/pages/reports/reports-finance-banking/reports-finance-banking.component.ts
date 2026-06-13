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
import {
  BankingInstitutionDto,
  BankingInstitutionTypeDto,
  BankingLedgerDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import {
  BankingFlowByInstitutionRow,
  BankingFlowByMonthRow,
  BankingFlowBySourceRow,
  BankingFlowByTypeRow,
  buildBankingFlowByInstitution,
  buildBankingFlowByMonth,
  buildBankingFlowBySource,
  buildBankingFlowByType,
  computeBankingFlowTotals,
} from '../../../util/banking-ledger-flow.util';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-banking',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
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
  templateUrl: './reports-finance-banking.component.html',
  styleUrl: './reports-finance-banking.component.scss',
})
export class ReportsFinanceBankingComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  filterInstitutionId = '';
  /** Mutually exclusive with {@link filterInstitutionId} for the ledger API. */
  filterInstitutionTypeId = '';
  institutions: BankingInstitutionDto[] = [];
  institutionTypes: BankingInstitutionTypeDto[] = [];

  ledger: BankingLedgerDto | null = null;
  loading = false;

  byInstitution: BankingFlowByInstitutionRow[] = [];
  byType: BankingFlowByTypeRow[] = [];
  byMonth: BankingFlowByMonthRow[] = [];
  bySource: BankingFlowBySourceRow[] = [];

  /** Whole-year totals from {@link ledger}. */
  yearCreditTotal = 0;
  yearDebitTotal = 0;
  yearNet = 0;
  yearTxnCount = 0;

  readonly instColumns = ['institutionName', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly typeColumns = ['typeLabel', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly monthColumns = ['monthLabel', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly sourceColumns = ['sourceFormat', 'creditTotal', 'debitTotal', 'net', 'txnCount'];
  readonly fileColumns = ['createdAt', 'institutionName', 'fileKind', 'originalFilename', 'rowsInserted', 'rowsSkippedDuplicate'];

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
        /* optional for report; types table may be empty */
      },
    });
    this.loadReport();
  }

  onReportInstitutionChange(): void {
    this.filterInstitutionTypeId = '';
    this.loadReport();
  }

  onReportTypeChange(): void {
    this.filterInstitutionId = '';
    this.loadReport();
  }

  loadReport(): void {
    this.loading = true;
    const inst = this.filterInstitutionId ? Number(this.filterInstitutionId) : null;
    const typ = this.filterInstitutionTypeId ? Number(this.filterInstitutionTypeId) : null;
    this.financeApi.bankingLedger('YEAR', this.reportYear, null, null, inst, typ).subscribe({
      next: (dto) => {
        this.ledger = dto;
        this.loading = false;
        this.recomputeFromLedger(dto);
      },
      error: (e) => {
        this.loading = false;
        this.ledger = null;
        this.snackBar.open(`Could not load banking report — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
      },
    });
  }

  private recomputeFromLedger(dto: BankingLedgerDto): void {
    const txns = dto.transactions ?? [];
    const totals = computeBankingFlowTotals(txns);
    this.yearCreditTotal = totals.creditTotal;
    this.yearDebitTotal = totals.debitTotal;
    this.yearNet = totals.net;
    this.yearTxnCount = totals.txnCount;

    this.byInstitution = buildBankingFlowByInstitution(txns);
    this.byType = buildBankingFlowByType(txns);
    this.byMonth = buildBankingFlowByMonth(txns);
    this.bySource = buildBankingFlowBySource(txns);
  }
}
