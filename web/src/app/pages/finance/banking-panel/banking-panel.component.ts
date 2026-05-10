import { CommonModule } from '@angular/common';
import { Component, Input, NgZone, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
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
  BankingInstitutionTypeDto,
  BankingLedgerDto,
  BankingLedgerRange,
  BankingPlaidStatusDto,
  BankingTransactionDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { MeMemberApiService } from '../../../services/me-member-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  BankingDeleteImportDialogComponent,
} from './banking-delete-import-dialog.component';
import { firstValueFrom } from 'rxjs';

interface BankingInstitutionEditRow {
  id: number;
  name: string;
  institutionTypeId: number | null;
}

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
    MatDatepickerModule,
    MatNativeDateModule,
    MatSelectModule,
    MatTableModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatCheckboxModule,
  ],
  templateUrl: './banking-panel.component.html',
  styleUrl: './banking-panel.component.scss',
})
export class BankingPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly meApi = inject(MeMemberApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly ngZone = inject(NgZone);
  private readonly dialog = inject(MatDialog);

  /**
   * {@code imports}: institutions + file upload (Admin → Finance → Banking).
   * {@code ledger}: period filter, transactions, uploaded files (Finance → Banking).
   */
  @Input() segment: 'imports' | 'ledger' = 'ledger';

  institutions: BankingInstitutionDto[] = [];
  institutionTypes: BankingInstitutionTypeDto[] = [];
  institutionTypesLoading = false;
  institutionEditRows: BankingInstitutionEditRow[] = [];
  savingInstitutionId: number | null = null;
  newTypeName = '';
  newTypeSortOrder: number | null = null;
  typeCreateBusy = false;
  deletingTypeId: number | null = null;
  institutionsLoading = false;
  uploadInstitutionId: number | null = null;
  newInstitutionName = '';
  /** Optional type when creating an institution (Admin imports). */
  newInstitutionTypeId: number | null = null;
  createBusy = false;
  selectedUploadFile: File | null = null;
  uploadBusy = false;

  ledgerRange: BankingLedgerRange = 'MONTH';
  ledgerYear = new Date().getFullYear();
  ledgerMonth = new Date().getMonth() + 1;
  ledgerQuarter = Math.floor(new Date().getMonth() / 3) + 1;
  /** Ledger filter: empty string = all institutions. */
  filterInstitutionId = '';
  /** Ledger filter: empty string = all types (mutually exclusive with {@link filterInstitutionId}). */
  filterInstitutionTypeId = '';

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
  /** Plaid sync range; Material datepicker — avoids clipped native `type="date"` and missing calendar in some browsers. */
  plaidSyncStartDate: Date | null = null;
  plaidSyncEndDate: Date | null = null;
  private plaidScriptPromise: Promise<void> | null = null;

  /** Server-recorded Privacy acknowledgment for Financial data & Plaid (ISO string). */
  plaidFinancialDataNoticeAcceptedAt: string | null = null;
  /** Checkbox before first Link when acknowledgment not yet recorded. */
  plaidPrivacyCheckboxAck = false;
  plaidUnlinkBusy = false;

  /** Admin imports: broader listing than ledger period. */
  importsHistoryFromYear = new Date().getFullYear() - 5;
  importsHistoryToYear = new Date().getFullYear();
  /** Import history filter; null = all institutions. */
  importsHistoryInstitutionId: number | null = null;
  importsHistoryLoading = false;
  importsHistoryRows: BankingImportFileDto[] = [];

  readonly txnColumns: string[] = [
    'txnDate',
    'institutionName',
    'institutionTypeName',
    'sourceFormat',
    'debitCredit',
    'amount',
    'description',
  ];
  readonly typeAdminColumns: string[] = ['name', 'sortOrder', 'actions'];
  readonly institutionEditColumns: string[] = ['editName', 'editType', 'editSave'];

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
    this.loadMemberPrivacyForPlaid();
    this.loadInstitutionTypes();
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
    this.plaidSyncEndDate = new Date(t.getFullYear(), t.getMonth(), t.getDate());
    this.plaidSyncStartDate = new Date(t.getFullYear(), t.getMonth(), 1);
  }

  /** Local calendar `yyyy-MM-dd` for Plaid API (no UTC shift). */
  private plaidIsoDate(d: Date | null): string {
    if (!d) {
      return '';
    }
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  loadInstitutionTypes(): void {
    this.institutionTypesLoading = true;
    this.api.listBankingInstitutionTypes().subscribe({
      next: (rows) => {
        this.institutionTypesLoading = false;
        this.institutionTypes = rows ?? [];
      },
      error: (e) => {
        this.institutionTypesLoading = false;
        this.snackBar.open(`Could not load institution types — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 5000,
        });
      },
    });
  }

  createInstitutionType(): void {
    const n = this.newTypeName.trim();
    if (!n) {
      return;
    }
    this.typeCreateBusy = true;
    const body: { name: string; sortOrder?: number | null } = { name: n };
    if (this.newTypeSortOrder != null && Number.isFinite(this.newTypeSortOrder)) {
      body.sortOrder = this.newTypeSortOrder;
    }
    this.api.createBankingInstitutionType(body).subscribe({
      next: (row) => {
        this.typeCreateBusy = false;
        this.newTypeName = '';
        this.newTypeSortOrder = null;
        this.institutionTypes = [...this.institutionTypes, row].sort((a, b) => {
          if (a.sortOrder !== b.sortOrder) {
            return a.sortOrder - b.sortOrder;
          }
          return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
        });
        this.snackBar.open('Institution type created', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.typeCreateBusy = false;
        this.snackBar.open(`Create type failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  deleteInstitutionType(id: number): void {
    this.deletingTypeId = id;
    this.api.deleteBankingInstitutionType(id).subscribe({
      next: () => {
        this.deletingTypeId = null;
        this.institutionTypes = this.institutionTypes.filter((t) => t.id !== id);
        for (const row of this.institutionEditRows) {
          if (row.institutionTypeId === id) {
            row.institutionTypeId = null;
          }
        }
        if (this.newInstitutionTypeId === id) {
          this.newInstitutionTypeId = null;
        }
        if (this.filterInstitutionTypeId === String(id)) {
          this.filterInstitutionTypeId = '';
          this.loadLedger();
        }
        this.reloadInstitutions();
        this.snackBar.open('Institution type removed', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.deletingTypeId = null;
        this.snackBar.open(`Delete type failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  saveInstitutionRow(row: BankingInstitutionEditRow): void {
    const name = row.name.trim();
    if (!name) {
      this.snackBar.open('Institution name is required', undefined, { duration: 3000 });
      return;
    }
    this.savingInstitutionId = row.id;
    this.api
      .updateBankingInstitution(row.id, { name, institutionTypeId: row.institutionTypeId })
      .subscribe({
        next: (updated) => {
          this.savingInstitutionId = null;
          this.mergeInstitutionRow(updated);
          this.snackBar.open('Institution saved', undefined, { duration: 2500 });
        },
        error: (e) => {
          this.savingInstitutionId = null;
          this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
        },
      });
  }

  reloadInstitutions(): void {
    this.institutionsLoading = true;
    this.api.listBankingInstitutions().subscribe({
      next: (rows) => {
        this.institutionsLoading = false;
        this.applyInstitutionListFromServer(rows);
      },
      error: (e) => {
        this.institutionsLoading = false;
        this.snackBar.open(`Could not load institutions — ${formatHttpErrorDetail(e)}`, undefined, { duration: 5000 });
      },
    });
  }

  /** Defensive: same id twice (race / merge) would duplicate mat-select options. */
  private dedupeInstitutionsById(rows: BankingInstitutionDto[]): BankingInstitutionDto[] {
    const seen = new Set<number>();
    const out: BankingInstitutionDto[] = [];
    for (const r of rows) {
      if (!seen.has(r.id)) {
        seen.add(r.id);
        out.push(r);
      }
    }
    return out;
  }

  /**
   * Applies a fresh institution list from the API: dedupe, sort, fix invalid mat-select targets, refresh Plaid status.
   */
  private applyInstitutionListFromServer(rows: BankingInstitutionDto[]): void {
    const sorted = this.dedupeInstitutionsById(rows).sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
    );
    this.institutions = sorted;
    const ids = new Set(sorted.map((r) => r.id));

    if (this.uploadInstitutionId != null && !ids.has(this.uploadInstitutionId)) {
      this.uploadInstitutionId = null;
    }
    if (this.uploadInstitutionId == null && sorted.length) {
      this.uploadInstitutionId = sorted[0].id;
    }

    if (this.segment === 'ledger') {
      if (this.plaidLedgerInstitutionId != null && !ids.has(this.plaidLedgerInstitutionId)) {
        this.plaidLedgerInstitutionId = null;
      }
      if (sorted.length) {
        if (this.plaidLedgerInstitutionId == null) {
          const fid = this.filterInstitutionId ? Number(this.filterInstitutionId) : NaN;
          this.plaidLedgerInstitutionId =
              Number.isFinite(fid) && fid > 0 && ids.has(fid) ? fid : sorted[0].id;
        }
      } else {
        this.plaidLedgerInstitutionId = null;
      }
    }

    this.refreshPlaidStatus();
    this.rebuildInstitutionEditRows();
  }

  private rebuildInstitutionEditRows(): void {
    if (this.segment !== 'imports') {
      this.institutionEditRows = [];
      return;
    }
    this.institutionEditRows = this.institutions.map((i) => ({
      id: i.id,
      name: i.name,
      institutionTypeId: i.institutionTypeId ?? null,
    }));
  }

  private mergeInstitutionRow(row: BankingInstitutionDto): void {
    const merged = [...this.institutions.filter((i) => i.id !== row.id), row];
    this.institutions = merged.sort((a, b) =>
      a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
    );
    this.rebuildInstitutionEditRows();
  }

  createInstitution(): void {
    const n = this.newInstitutionName.trim();
    if (!n) {
      return;
    }
    this.createBusy = true;
    this.api.createBankingInstitution(n, this.newInstitutionTypeId).subscribe({
      next: (row) => {
        this.createBusy = false;
        this.newInstitutionName = '';
        this.newInstitutionTypeId = null;
        this.mergeInstitutionRow(row);
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
    this.filterInstitutionTypeId = '';
    this.loadLedger();
    const fid = this.filterInstitutionId ? Number(this.filterInstitutionId) : NaN;
    if (Number.isFinite(fid) && fid > 0) {
      this.plaidLedgerInstitutionId = fid;
    }
    this.refreshPlaidStatus();
  }

  onLedgerTypeFilterChange(): void {
    this.filterInstitutionId = '';
    this.loadLedger();
    this.refreshPlaidStatus();
  }

  loadLedger(): void {
    this.ledgerLoading = true;
    const inst = this.filterInstitutionId ? Number(this.filterInstitutionId) : null;
    const typ = this.filterInstitutionTypeId ? Number(this.filterInstitutionTypeId) : null;
    this.api
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
    const inst = sel != null && typeof sel === 'number' && sel > 0 ? sel : null;
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
      (row.institutionTypeName ?? '').toLowerCase(),
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
        this.importsHistoryRows = this.importsHistoryRows.filter((r) => r.id !== row.id);
        if (this.ledger?.importFiles?.length) {
          this.ledger = {
            ...this.ledger,
            importFiles: this.ledger.importFiles.filter((r) => r.id !== row.id),
          };
        }
        if (this.ledger?.transactions?.length) {
          this.ledger = {
            ...this.ledger,
            transactions: this.ledger.transactions.filter((t) => t.importFileId !== row.id),
          };
        }
        this.snackBar.open('Import removed', undefined, { duration: 3000 });
        if (this.segment === 'imports') {
          this.importsHistoryInstitutionId = null;
          this.selectedUploadFile = null;
        }
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

  formatPlaidAcknowledgedAt(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  }

  unlinkPlaid(): void {
    const id = this.plaidTargetInstitutionId();
    if (id == null || !this.plaidStatus?.linked) {
      return;
    }
    const confirmed = confirm(
      'Disconnect Plaid for this institution? This removes stored connection credentials from the app. Imported transactions and import files are not deleted.',
    );
    if (!confirmed) {
      return;
    }
    this.plaidUnlinkBusy = true;
    this.api.bankingPlaidUnlink(id).subscribe({
      next: () => {
        this.plaidUnlinkBusy = false;
        this.snackBar.open('Plaid disconnected for this institution.', undefined, { duration: 5000 });
        this.refreshPlaidStatus();
      },
      error: (e) => {
        this.plaidUnlinkBusy = false;
        this.snackBar.open(`Disconnect failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  private loadMemberPrivacyForPlaid(): void {
    this.meApi.getMemberProfile().subscribe({
      next: (p) => {
        this.plaidFinancialDataNoticeAcceptedAt = p.plaidFinancialDataNoticeAcceptedAt ?? null;
      },
      error: () => {
        this.plaidFinancialDataNoticeAcceptedAt = null;
      },
    });
  }

  /**
   * Ensures server-side acknowledgment exists before Plaid Link (posts checkbox consent if needed).
   */
  private async ensurePlaidFinancialPrivacyAcknowledgment(): Promise<boolean> {
    if (this.plaidFinancialDataNoticeAcceptedAt) {
      return true;
    }
    if (!this.plaidPrivacyCheckboxAck) {
      this.snackBar.open(
        'Confirm you have read the Financial data & Plaid section of the Privacy policy before connecting.',
        undefined,
        { duration: 7500 },
      );
      return false;
    }
    try {
      const p = await firstValueFrom(this.meApi.acceptPlaidFinancialDataNotice());
      this.plaidFinancialDataNoticeAcceptedAt = p.plaidFinancialDataNoticeAcceptedAt ?? null;
      return true;
    } catch (e) {
      this.snackBar.open(`Could not record acknowledgment — ${formatHttpErrorDetail(e)}`, undefined, {
        duration: 8000,
      });
      return false;
    }
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
      const acked = await this.ensurePlaidFinancialPrivacyAcknowledgment();
      if (!acked) {
        this.plaidLinkOpening = false;
        return;
      }
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
                const linked = ex.linkedInstitutionIds?.filter((id) => Number.isFinite(id) && id > 0) ?? [];
                let msg = 'Bank linked with Plaid.';
                if (ex.institutionRenamedFromPlaid && ex.institutionName) {
                  msg += ` Institution name set to “${ex.institutionName}”.`;
                }
                if (linked.length > 1) {
                  msg += ` ${linked.length} institutions were added (one per account); pick one below to sync and import separately.`;
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
    let existing = this.plaidTargetInstitutionId();
    if (existing != null) {
      return existing;
    }

    // Avoid racing ngOnInit's list: opening Link before the first load finishes could append a new institution
    // while the in-flight GET returns the same rows again, producing duplicate mat-options. Always reconcile from server
    // before auto-creating.
    const rows = await firstValueFrom(this.api.listBankingInstitutions());
    this.applyInstitutionListFromServer(rows);

    existing = this.plaidTargetInstitutionId();
    if (existing != null) {
      return existing;
    }

    if (this.institutions.length > 0) {
      const pick = this.plaidTargetInstitutionId();
      if (pick != null) {
        return pick;
      }
      if (this.segment === 'imports') {
        this.uploadInstitutionId = this.institutions[0].id;
        return this.institutions[0].id;
      }
      this.plaidLedgerInstitutionId = this.institutions[0].id;
      return this.institutions[0].id;
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
    this.mergeInstitutionRow(created);
    this.uploadInstitutionId = this.segment === 'imports' ? created.id : this.uploadInstitutionId;
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
    const startDate = this.plaidIsoDate(this.plaidSyncStartDate);
    const endDate = this.plaidIsoDate(this.plaidSyncEndDate);
    if (!startDate || !endDate) {
      this.snackBar.open('Choose both From and To dates.', undefined, { duration: 4000 });
      return;
    }
    this.plaidSyncBusy = true;
    this.api
      .bankingPlaidSync({
        institutionId: id,
        startDate,
        endDate,
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
