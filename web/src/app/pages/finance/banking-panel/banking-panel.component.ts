import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
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
  BankingLedgerDto,
  BankingLedgerRange,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-banking-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './banking-panel.component.html',
  styleUrl: './banking-panel.component.scss',
})
export class BankingPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  institutions: BankingInstitutionDto[] = [];
  institutionsLoading = false;
  uploadInstitutionId: number | null = null;
  newInstitutionName = '';
  createBusy = false;
  selectedUploadFile: File | null = null;
  uploadBusy = false;

  ledgerRange: BankingLedgerRange = 'MONTH';
  ledgerYear = new Date().getFullYear();
  ledgerMonth = new Date().getMonth() + 1;
  ledgerQuarter = Math.floor(new Date().getMonth() / 3) + 1;
  /** Ledger filter: empty string = all institutions. */
  filterInstitutionId = '';

  ledger: BankingLedgerDto | null = null;
  ledgerLoading = false;

  readonly txnColumns: string[] = ['txnDate', 'institutionName', 'amount', 'description'];
  readonly fileColumns: string[] = [
    'createdAt',
    'institutionName',
    'fileKind',
    'originalFilename',
    'sizeBytes',
    'rowsInserted',
    'rowsSkippedDuplicate',
    'actions',
  ];

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

  ngOnInit(): void {
    this.reloadInstitutions();
    this.loadLedger();
  }

  reloadInstitutions(): void {
    this.institutionsLoading = true;
    this.api.listBankingInstitutions().subscribe({
      next: (rows) => {
        this.institutions = rows;
        this.institutionsLoading = false;
        if (this.uploadInstitutionId == null && rows.length) {
          this.uploadInstitutionId = rows[0].id;
        }
      },
      error: (e) => {
        this.institutionsLoading = false;
        this.snackBar.open(`Could not load institutions — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  createInstitution(): void {
    const n = this.newInstitutionName.trim();
    if (!n) {
      return;
    }
    this.createBusy = true;
    this.api.createBankingInstitution(n).subscribe({
      next: (row) => {
        this.createBusy = false;
        this.newInstitutionName = '';
        this.institutions = [...this.institutions, row].sort((a, b) =>
          a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
        );
        this.uploadInstitutionId = row.id;
        this.snackBar.open('Institution created', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.createBusy = false;
        this.snackBar.open(`Create failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  onUploadFilePicked(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    this.selectedUploadFile = f ?? null;
    input.value = '';
  }

  upload(): void {
    if (this.uploadInstitutionId == null || !this.selectedUploadFile) {
      return;
    }
    this.uploadBusy = true;
    this.api.importBankingFile(this.uploadInstitutionId, this.selectedUploadFile).subscribe({
      next: (r) => {
        this.uploadBusy = false;
        this.selectedUploadFile = null;
        const msg = r.message + (r.skippedDuplicateFile ? ' (duplicate file skipped.)' : '');
        this.snackBar.open(msg, undefined, { duration: 5000 });
        this.loadLedger();
      },
      error: (e) => {
        this.uploadBusy = false;
        this.snackBar.open(`Upload failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  loadLedger(): void {
    this.ledgerLoading = true;
    const inst = this.filterInstitutionId ? Number(this.filterInstitutionId) : null;
    this.api
      .bankingLedger(
        this.ledgerRange,
        this.ledgerYear,
        this.ledgerRange === 'MONTH' ? this.ledgerMonth : null,
        this.ledgerRange === 'QUARTER' ? this.ledgerQuarter : null,
        inst,
      )
      .subscribe({
        next: (dto) => {
          this.ledger = dto;
          this.ledgerLoading = false;
        },
        error: (e) => {
          this.ledgerLoading = false;
          this.snackBar.open(`Could not load banking ledger — ${formatHttpErrorDetail(e)}`, undefined, {
            duration: 5000,
          });
        },
      });
  }

  download(row: BankingImportFileDto): void {
    this.api.downloadBankingFile(row.id).subscribe({
      next: (resp) => {
        const blob = resp.body;
        if (!blob) {
          return;
        }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = row.originalFilename || 'download';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => {
        this.snackBar.open(`Download failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  formatBytes(n: number): string {
    if (n < 1024) {
      return `${n} B`;
    }
    if (n < 1024 * 1024) {
      return `${(n / 1024).toFixed(1)} KB`;
    }
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  }
}
