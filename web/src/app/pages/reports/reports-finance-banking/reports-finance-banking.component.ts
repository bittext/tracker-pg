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
  BankingImportFileDto,
  BankingInstitutionDto,
  BankingInstitutionTypeDto,
  BankingLedgerDto,
  BankingTransactionDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

export interface BankingFlowByInstitutionRow {
  institutionId: number;
  institutionName: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowByMonthRow {
  yearMonth: string;
  monthLabel: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowBySourceRow {
  sourceFormat: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowByTypeRow {
  institutionTypeId: number | null;
  typeLabel: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

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
    let yc = 0;
    let yd = 0;
    for (const t of txns) {
      const a = Number(t.amount);
      if (!Number.isFinite(a)) {
        continue;
      }
      if (a > 0) {
        yc += a;
      } else if (a < 0) {
        yd += -a;
      }
    }
    this.yearCreditTotal = yc;
    this.yearDebitTotal = yd;
    this.yearNet = yc - yd;
    this.yearTxnCount = txns.length;

    this.byInstitution = this.buildByInstitution(txns);
    this.byType = this.buildByType(txns);
    this.byMonth = this.buildByMonth(txns);
    this.bySource = this.buildBySource(txns);
  }

  private buildByInstitution(txns: BankingTransactionDto[]): BankingFlowByInstitutionRow[] {
    const m = new Map<number, { name: string; credit: number; debit: number; n: number }>();
    for (const t of txns) {
      const id = t.institutionId;
      const name = t.institutionName || `Institution ${id}`;
      const a = Number(t.amount);
      if (!Number.isFinite(a)) {
        continue;
      }
      const cur = m.get(id) ?? { name, credit: 0, debit: 0, n: 0 };
      cur.name = name;
      if (a > 0) {
        cur.credit += a;
      } else if (a < 0) {
        cur.debit += -a;
      }
      cur.n += 1;
      m.set(id, cur);
    }
    return [...m.entries()]
      .map(([institutionId, v]) => ({
        institutionId,
        institutionName: v.name,
        creditTotal: v.credit,
        debitTotal: v.debit,
        net: v.credit - v.debit,
        txnCount: v.n,
      }))
      .sort((a, b) => a.institutionName.localeCompare(b.institutionName, undefined, { sensitivity: 'base' }));
  }

  private buildByType(txns: BankingTransactionDto[]): BankingFlowByTypeRow[] {
    const m = new Map<number | null, { label: string; credit: number; debit: number; n: number }>();
    for (const t of txns) {
      const tid = t.institutionTypeId != null && Number.isFinite(t.institutionTypeId) ? t.institutionTypeId : null;
      const label =
          tid != null && (t.institutionTypeName ?? '').trim()
              ? (t.institutionTypeName as string).trim()
              : 'Untyped';
      const a = Number(t.amount);
      if (!Number.isFinite(a)) {
        continue;
      }
      const cur = m.get(tid) ?? { label, credit: 0, debit: 0, n: 0 };
      cur.label = label;
      if (a > 0) {
        cur.credit += a;
      } else if (a < 0) {
        cur.debit += -a;
      }
      cur.n += 1;
      m.set(tid, cur);
    }
    return [...m.entries()]
      .map(([institutionTypeId, v]) => ({
        institutionTypeId,
        typeLabel: v.label,
        creditTotal: v.credit,
        debitTotal: v.debit,
        net: v.credit - v.debit,
        txnCount: v.n,
      }))
      .sort((a, b) => {
        if (a.institutionTypeId == null) {
          return 1;
        }
        if (b.institutionTypeId == null) {
          return -1;
        }
        return a.typeLabel.localeCompare(b.typeLabel, undefined, { sensitivity: 'base' });
      });
  }

  private buildByMonth(txns: BankingTransactionDto[]): BankingFlowByMonthRow[] {
    const m = new Map<string, { credit: number; debit: number; n: number }>();
    for (const t of txns) {
      const ym = (t.txnDate ?? '').slice(0, 7);
      if (ym.length !== 7) {
        continue;
      }
      const a = Number(t.amount);
      if (!Number.isFinite(a)) {
        continue;
      }
      const cur = m.get(ym) ?? { credit: 0, debit: 0, n: 0 };
      if (a > 0) {
        cur.credit += a;
      } else if (a < 0) {
        cur.debit += -a;
      }
      cur.n += 1;
      m.set(ym, cur);
    }
    return [...m.entries()]
      .map(([yearMonth, v]) => ({
        yearMonth,
        monthLabel: this.formatYearMonthLabel(yearMonth),
        creditTotal: v.credit,
        debitTotal: v.debit,
        net: v.credit - v.debit,
        txnCount: v.n,
      }))
      .sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
  }

  private formatYearMonthLabel(ym: string): string {
    const y = Number(ym.slice(0, 4));
    const mo = Number(ym.slice(5, 7));
    if (!Number.isFinite(y) || !Number.isFinite(mo) || mo < 1 || mo > 12) {
      return ym;
    }
    return new Date(y, mo - 1, 1).toLocaleString(undefined, { month: 'short', year: 'numeric' });
  }

  private buildBySource(txns: BankingTransactionDto[]): BankingFlowBySourceRow[] {
    const m = new Map<string, { credit: number; debit: number; n: number }>();
    for (const t of txns) {
      const key = (t.sourceFormat ?? '').trim() || '—';
      const a = Number(t.amount);
      if (!Number.isFinite(a)) {
        continue;
      }
      const cur = m.get(key) ?? { credit: 0, debit: 0, n: 0 };
      if (a > 0) {
        cur.credit += a;
      } else if (a < 0) {
        cur.debit += -a;
      }
      cur.n += 1;
      m.set(key, cur);
    }
    return [...m.entries()]
      .map(([sourceFormat, v]) => ({
        sourceFormat,
        creditTotal: v.credit,
        debitTotal: v.debit,
        net: v.credit - v.debit,
        txnCount: v.n,
      }))
      .sort((a, b) => Math.abs(b.net) - Math.abs(a.net));
  }

}
