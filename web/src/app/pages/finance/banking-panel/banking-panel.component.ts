import { CommonModule } from '@angular/common';
import { Component, Input, NgZone, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
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
import { MatDialog } from '@angular/material/dialog';
import {
  BankingImportFileDto,
  BankingInstitutionDto,
  BankingLedgerDto,
  BankingLedgerRange,
  BankingPlaidStatusDto,
  BankingTransactionDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  BankingDeleteImportDialogComponent,
} from './banking-delete-import-dialog.component';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-banking-panel',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
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
  private readonly ngZone = inject(NgZone);
  private readonly dialog = inject(MatDialog);

  /**
   * {@code imports}: institutions + file upload (Admin → Finance → Banking).
   * {@code ledger}: period filter, transactions, uploaded files (Finance → Banking).
   */
  @Input() segment: 'imports' | 'ledger' = 'ledger';

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
  /** While DELETE /banking/files/:id is in flight. */
  deletingFileId: number | null = null;

  /** Single text box: matches any displayed transaction column (whitespace = multiple terms, all must match). */
  txnSearchText = '';

  /** Plaid Link + sync (same APIs as documented in README). */
  plaidStatus: BankingPlaidStatusDto | null = null;
  plaidStatusLoading = false;
  plaidLinkOpening = false;
  plaidSyncBusy = false;
  /** Ledger tab: institution for Plaid status + sync. */
  plaidLedgerInstitutionId: number | null = null;
  plaidSyncStart = '';
  plaidSyncEnd = '';
  private plaidScriptPromise: Promise<void> | null = null;

  /** Admin imports: broader listing than ledger period. */
  importsHistoryFromYear = new Date().getFullYear() - 5;
  importsHistoryToYear = new Date().getFullYear();
  importsHistoryInstitutionId: number | '' = '';
  importsHistoryLoading = false;
  importsHistoryRows: BankingImportFileDto[] = [];

  readonly txnColumns: string[] = [
    'txnDate',
    'institutionName',
    'sourceFormat',
    'debitCredit',
    'amount',
    'description',
  ];
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
    this.initPlaidDefaultDates();
    this.reloadInstitutions();
    if (this.segment === 'ledger') {
      this.loadLedger();
    }
    if (this.segment === 'imports') {
      this.loadImportsHistory();
    }
  }

  private initPlaidDefaultDates(): void {
    const t = new Date();
    const y = t.getFullYear();
    const m = String(t.getMonth() + 1).padStart(2, '0');
    const d = String(t.getDate()).padStart(2, '0');
    this.plaidSyncEnd = `${y}-${m}-${d}`;
    const start = new Date(t.getFullYear(), t.getMonth(), 1);
    const sy = start.getFullYear();
    const sm = String(start.getMonth() + 1).padStart(2, '0');
    const sd = String(start.getDate()).padStart(2, '0');
    this.plaidSyncStart = `${sy}-${sm}-${sd}`;
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
        if (this.segment === 'ledger' && rows.length) {
          if (this.plaidLedgerInstitutionId == null) {
            const fid = this.filterInstitutionId ? Number(this.filterInstitutionId) : NaN;
            this.plaidLedgerInstitutionId = Number.isFinite(fid) && fid > 0 ? fid : rows[0].id;
          }
        }
        this.refreshPlaidStatus();
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
        if (this.segment === 'ledger') {
          this.loadLedger();
        }
        if (this.segment === 'imports') {
          this.loadImportsHistory();
        }
      },
      error: (e) => {
        this.uploadBusy = false;
        this.snackBar.open(`Upload failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  onLedgerFilterChange(): void {
    this.loadLedger();
    const fid = this.filterInstitutionId ? Number(this.filterInstitutionId) : NaN;
    if (Number.isFinite(fid) && fid > 0) {
      this.plaidLedgerInstitutionId = fid;
    }
    this.refreshPlaidStatus();
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

  loadImportsHistory(): void {
    if (this.segment !== 'imports') {
      return;
    }
    const fy = Math.min(this.importsHistoryFromYear, this.importsHistoryToYear);
    const ty = Math.max(this.importsHistoryFromYear, this.importsHistoryToYear);
    const from = `${fy}-01-01`;
    const to = `${ty}-12-31`;
    const sel = this.importsHistoryInstitutionId;
    const oid = sel === '' ? NaN : typeof sel === 'number' ? sel : Number(sel);
    const inst = Number.isFinite(oid) && oid > 0 ? oid : null;
    this.importsHistoryLoading = true;
    this.api.bankingImportFiles(from, to, inst).subscribe({
      next: (rows) => {
        this.importsHistoryRows = rows;
        this.importsHistoryLoading = false;
      },
      error: (e) => {
        this.importsHistoryLoading = false;
        this.snackBar.open(`Could not load import history — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  /** Rows in the current ledger range after {@link txnSearchText} is applied. */
  get filteredLedgerTransactions(): BankingTransactionDto[] {
    const all = this.ledger?.transactions;
    if (!all?.length) {
      return [];
    }
    const raw = this.txnSearchText.trim().toLowerCase();
    if (!raw) {
      return all;
    }
    const tokens = raw.split(/\s+/).filter(Boolean);
    return all.filter((row) => this.transactionMatchesSearchTokens(row, tokens));
  }

  private transactionMatchesSearchTokens(row: BankingTransactionDto, tokens: string[]): boolean {
    const fields = this.transactionSearchFields(row);
    return tokens.every((tok) => fields.some((f) => f.includes(tok)));
  }

  private transactionSearchFields(row: BankingTransactionDto): string[] {
    const amt = Number(row.amount);
    const amtFixed = Number.isFinite(amt) ? amt.toFixed(2) : String(row.amount ?? '');
    const dc = (row.debitCredit ?? '').toString();
    const dcLabel = this.debitCreditLabel(row.debitCredit).toLowerCase();
    return [
      (row.txnDate ?? '').toString().toLowerCase(),
      (row.institutionName ?? '').toLowerCase(),
      (row.sourceFormat ?? '').toLowerCase(),
      dc.toLowerCase(),
      dcLabel,
      String(row.amount ?? '').toLowerCase(),
      amtFixed.toLowerCase(),
      (row.description ?? '').toLowerCase(),
    ];
  }

  confirmRemoveAdminImport(row: BankingImportFileDto): void {
    const ref = this.dialog.open(BankingDeleteImportDialogComponent, {
      width: '560px',
      data: {
        filename: row.originalFilename,
        rowsInserted: row.rowsInserted ?? 0,
        fileKind: row.fileKind,
      },
    });
    ref.afterClosed().subscribe((ok: unknown) => {
      if (ok === true) {
        this.performDeleteImportFile(row);
      }
    });
  }

  private performDeleteImportFile(row: BankingImportFileDto): void {
    this.deletingFileId = row.id;
    this.api.deleteBankingImportFile(row.id).subscribe({
      next: () => {
        this.deletingFileId = null;
        this.snackBar.open('Import removed', undefined, { duration: 3000 });
        if (this.segment === 'ledger') {
          this.loadLedger();
        }
        if (this.segment === 'imports') {
          this.loadImportsHistory();
        }
      },
      error: (e) => {
        this.deletingFileId = null;
        this.snackBar.open(`Remove failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
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

  /** Human label for API {@code debitCredit} (CREDIT / DEBIT / ZERO). */
  debitCreditLabel(code: string | null | undefined): string {
    switch (code) {
      case 'CREDIT':
        return 'Credit';
      case 'DEBIT':
        return 'Debit';
      case 'ZERO':
        return '—';
      default:
        return code?.trim() ? code : '—';
    }
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

  institutionName(id: number | null): string {
    if (id == null) {
      return '';
    }
    return this.institutions.find((i) => i.id === id)?.name ?? '';
  }

  refreshPlaidStatus(): void {
    const id = this.plaidTargetInstitutionId();
    if (id == null) {
      this.plaidStatus = null;
      return;
    }
    this.plaidStatusLoading = true;
    this.api.bankingPlaidStatus(id).subscribe({
      next: (s) => {
        this.plaidStatus = s;
        this.plaidStatusLoading = false;
      },
      error: () => {
        this.plaidStatus = null;
        this.plaidStatusLoading = false;
      },
    });
  }

  private plaidTargetInstitutionId(): number | null {
    if (this.segment === 'imports') {
      return this.uploadInstitutionId;
    }
    return this.plaidLedgerInstitutionId;
  }

  private ensurePlaidScript(): Promise<void> {
    if (typeof window === 'undefined') {
      return Promise.reject(new Error('No window'));
    }
    if (window.Plaid) {
      return Promise.resolve();
    }
    if (this.plaidScriptPromise) {
      return this.plaidScriptPromise;
    }
    this.plaidScriptPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://cdn.plaid.com/link/v2/stable/link-initialize.js';
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('Could not load Plaid Link script'));
      document.head.appendChild(s);
    });
    return this.plaidScriptPromise;
  }

  async openPlaidLink(): Promise<void> {
    this.plaidLinkOpening = true;
    try {
      const inst = await this.ensurePlaidInstitutionForConnect();
      await this.ensurePlaidScript();
      if (!window.Plaid) {
        throw new Error('Plaid global missing after script load');
      }
      const { linkToken } = await firstValueFrom(this.api.bankingPlaidLinkToken(inst));
      const handler = window.Plaid.create({
        token: linkToken,
        onSuccess: (publicToken) => {
          this.ngZone.run(() => {
            this.api.bankingPlaidExchange(inst, publicToken).subscribe({
              next: (ex) => {
                this.plaidLinkOpening = false;
                let msg = 'Bank linked with Plaid.';
                if (ex.institutionRenamedFromPlaid && ex.institutionName) {
                  msg += ` Institution name set to “${ex.institutionName}”.`;
                }
                this.snackBar.open(msg, undefined, { duration: 7000 });
                this.uploadInstitutionId = ex.institutionId;
                if (this.segment === 'ledger') {
                  this.plaidLedgerInstitutionId = ex.institutionId;
                }
                this.reloadInstitutions();
                this.refreshPlaidStatus();
                if (this.segment === 'imports') {
                  this.loadImportsHistory();
                }
              },
              error: (e) => {
                this.plaidLinkOpening = false;
                this.snackBar.open(`Plaid exchange failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
              },
            });
          });
        },
        onExit: () => {
          this.ngZone.run(() => {
            this.plaidLinkOpening = false;
          });
        },
      });
      handler.open();
    } catch (e) {
      this.plaidLinkOpening = false;
      const msg = e instanceof Error ? e.message : String(e);
      this.snackBar.open(`Plaid Link could not start — ${msg}`, undefined, { duration: 8000 });
    }
  }

  /**
   * Plaid connect should be available by default. If no institution exists yet, create one now so
   * Link can proceed; exchange may then rename it to the bank/account-derived name.
   */
  private async ensurePlaidInstitutionForConnect(): Promise<number> {
    const existing = this.plaidTargetInstitutionId();
    if (existing != null) {
      return existing;
    }
    const seedBase = 'Plaid connection';
    let created: BankingInstitutionDto;
    try {
      created = await firstValueFrom(this.api.createBankingInstitution(seedBase));
    } catch {
      // In case that seed already exists (or race), retry with timestamp suffix.
      const suffix = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
      created = await firstValueFrom(this.api.createBankingInstitution(`${seedBase} ${suffix}`));
    }
    this.institutions = [...this.institutions, created].sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
    );
    this.uploadInstitutionId = created.id;
    if (this.segment === 'ledger') {
      this.plaidLedgerInstitutionId = created.id;
    }
    return created.id;
  }

  runPlaidSync(): void {
    const id = this.plaidTargetInstitutionId();
    if (id == null) {
      this.snackBar.open('Select a banking institution first.', undefined, { duration: 4000 });
      return;
    }
    if (!this.plaidSyncStart || !this.plaidSyncEnd) {
      return;
    }
    this.plaidSyncBusy = true;
    this.api
      .bankingPlaidSync({
        institutionId: id,
        startDate: this.plaidSyncStart,
        endDate: this.plaidSyncEnd,
        accountIds: [],
      })
      .subscribe({
        next: (r) => {
          this.plaidSyncBusy = false;
          const f = r.importResult.file;
          const parts = [
            `Plaid: ${r.transactionsFetchedFromPlaid} transaction(s)`,
            r.importResult.message,
          ];
          if (f) {
            parts.push(`inserted ${f.rowsInserted}, skipped dupes ${f.rowsSkippedDuplicate}`);
          }
          this.snackBar.open(parts.join(' · '), undefined, { duration: 9000 });
          if (this.segment === 'ledger') {
            this.loadLedger();
          }
          if (this.segment === 'imports') {
            this.loadImportsHistory();
          }
        },
        error: (e) => {
          this.plaidSyncBusy = false;
          this.snackBar.open(`Plaid sync failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }
}
