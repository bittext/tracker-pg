import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  FinanceCreditCardBankingInstitutionOptionDto,
  FinanceCreditCardDto,
  FinanceCreditCardRequestDto,
  FinanceCreditCardStatementDto,
  FinanceCreditCardStatementRequestDto,
  FinanceCreditCardSummaryDto,
  RobinhoodAgenticBankingStatusDto,
  RobinhoodAgenticBankingTransactionDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { FinanceCreditCardsApiService } from '../../../services/finance-credit-cards-api.service';
import { FinanceAgenticBankingApiService } from '../../../services/finance-agentic-banking-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { FinanceEntryDocumentsComponent } from '../finance-entry-documents/finance-entry-documents.component';

export interface CreditStatementWithCard extends FinanceCreditCardStatementDto {
  card: FinanceCreditCardDto;
}

export interface CreditStatementGroupSection {
  key: string;
  label: string;
  rows: CreditStatementWithCard[];
}

@Component({
  selector: 'app-credit-panel',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
    MatButtonToggleModule,
    FinanceEntryDocumentsComponent,
  ],
  templateUrl: './credit-panel.component.html',
  styleUrl: './credit-panel.component.scss',
})
export class CreditPanelComponent implements OnInit {
  private readonly api = inject(FinanceCreditCardsApiService);
  private readonly agenticBankingApi = inject(FinanceAgenticBankingApiService);
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  creditSubTabIndex = 0;

  cards: FinanceCreditCardDto[] = [];
  summary: FinanceCreditCardSummaryDto | null = null;
  bankingInstitutions: FinanceCreditCardBankingInstitutionOptionDto[] = [];
  loading = false;
  saving = false;
  editingId: number | null = null;

  form: FinanceCreditCardRequestDto = this.emptyCardForm();

  statementsCardId: number | null = null;
  statements: FinanceCreditCardStatementDto[] = [];
  statementsLoading = false;
  statementsAllLoading = false;
  allStatements: CreditStatementWithCard[] = [];
  statementsViewMode: 'card' | 'grouped' = 'grouped';
  statementsPeriodMode: 'month' | 'year' = 'month';
  statementsYear = new Date().getFullYear();
  statementsMonth = new Date().getMonth() + 1;
  statementSaving = false;
  editingStatementId: number | null = null;
  statementForm: FinanceCreditCardStatementRequestDto = this.emptyStatementForm();

  /** Month-to-date spending from Banking ledger for linked institutions (institutionId → total debits). */
  ledgerSpendingByInstitution = new Map<number, number>();

  agenticStatus: RobinhoodAgenticBankingStatusDto | null = null;
  agenticTransactions: RobinhoodAgenticBankingTransactionDto[] = [];
  agenticLoading = false;
  agenticSyncing = false;
  agenticSavingTokens = false;
  agenticTokenJson = '';

  readonly agenticTxnColumns = ['transactionAt', 'merchantName', 'amountUsd', 'transactionStatus'] as const;

  readonly cardColumns = [
    'institution',
    'cardName',
    'limit',
    'balance',
    'utilization',
    'health',
    'statementDate',
    'paymentDueDate',
    'ledgerSpending',
    'documents',
    'actions',
  ] as const;

  readonly statementColumns = [
    'statementDate',
    'statementBalance',
    'minimumPayment',
    'paymentDueDate',
    'actions',
  ] as const;

  readonly groupedStatementColumns = [
    'card',
    'statementDate',
    'statementBalance',
    'minimumPayment',
    'paymentDueDate',
    'actions',
  ] as const;

  ngOnInit(): void {
    this.refresh();
    this.refreshAgentic();
  }

  get selectedStatementsCard(): FinanceCreditCardDto | null {
    if (this.statementsCardId == null) {
      return null;
    }
    return this.cards.find((c) => c.id === this.statementsCardId) ?? null;
  }

  get statementsPeriodLabel(): string {
    if (this.statementsPeriodMode === 'month') {
      return new Date(this.statementsYear, this.statementsMonth - 1, 1).toLocaleDateString(undefined, {
        month: 'long',
        year: 'numeric',
      });
    }
    return String(this.statementsYear);
  }

  get cardModeStatements(): FinanceCreditCardStatementDto[] {
    return this.statements.filter((s) => this.statementMatchesPeriod(s.statementDate));
  }

  get groupedStatementSections(): CreditStatementGroupSection[] {
    const rows = this.allStatements.filter((s) => this.statementMatchesPeriod(s.statementDate));
    if (!rows.length) {
      return [];
    }
    if (this.statementsPeriodMode === 'month') {
      return [
        {
          key: `${this.statementsYear}-${String(this.statementsMonth).padStart(2, '0')}`,
          label: this.statementsPeriodLabel,
          rows: this.sortStatementsWithCard(rows),
        },
      ];
    }
    const byMonth = new Map<number, CreditStatementWithCard[]>();
    for (const row of rows) {
      const month = this.parseStatementMonth(row.statementDate);
      if (month == null) {
        continue;
      }
      const bucket = byMonth.get(month) ?? [];
      bucket.push(row);
      byMonth.set(month, bucket);
    }
    return [...byMonth.entries()]
      .sort(([a], [b]) => b - a)
      .map(([month, sectionRows]) => ({
        key: `${this.statementsYear}-${String(month).padStart(2, '0')}`,
        label: new Date(this.statementsYear, month - 1, 1).toLocaleDateString(undefined, {
          month: 'long',
          year: 'numeric',
        }),
        rows: this.sortStatementsWithCard(sectionRows),
      }));
  }

  refresh(): void {
    this.loading = true;
    forkJoin({
      cards: this.api.list(),
      summary: this.api.summary(),
      options: this.api.options(),
    }).subscribe({
      next: ({ cards, summary, options }) => {
        this.cards = cards;
        this.summary = summary;
        this.bankingInstitutions = options.bankingInstitutions;
        this.loading = false;
        this.loadLedgerSpending(cards);
        if (this.statementsCardId != null && !cards.some((c) => c.id === this.statementsCardId)) {
          this.statementsCardId = cards[0]?.id ?? null;
          this.statements = [];
        }
        if (this.creditSubTabIndex === 1) {
          this.ensureStatementsCardSelected();
          if (this.statementsCardId != null) {
            this.loadStatements(this.statementsCardId);
          }
          this.loadAllStatements();
        }
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load credit cards — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  startAdd(): void {
    this.editingId = null;
    this.form = this.emptyCardForm();
    this.creditSubTabIndex = 0;
  }

  startEdit(row: FinanceCreditCardDto): void {
    this.editingId = row.id;
    this.form = {
      institution: row.institution,
      cardName: row.cardName,
      lastFour: row.lastFour,
      creditLimit: row.creditLimit,
      currentBalance: row.currentBalance,
      apr: row.apr,
      statementBalance: row.statementBalance,
      statementDate: row.statementDate,
      paymentDueDate: row.paymentDueDate,
      bankingInstitutionId: row.bankingInstitutionId,
      notes: row.notes,
    };
    this.creditSubTabIndex = 0;
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.emptyCardForm();
  }

  saveCard(): void {
    if (!this.form.institution?.trim()) {
      this.snackBar.open('Institution is required', undefined, { duration: 4500 });
      return;
    }
    if (!this.form.cardName?.trim()) {
      this.snackBar.open('Card name is required', undefined, { duration: 4500 });
      return;
    }
    if (this.form.lastFour?.trim() && !/^\d{4}$/.test(this.form.lastFour.trim())) {
      this.snackBar.open('Last four must be exactly 4 digits', undefined, { duration: 4500 });
      return;
    }
    this.saving = true;
    const body: FinanceCreditCardRequestDto = {
      ...this.form,
      institution: this.form.institution.trim(),
      cardName: this.form.cardName.trim(),
      lastFour: this.form.lastFour?.trim() || '',
      statementDate: this.form.statementDate || null,
      paymentDueDate: this.form.paymentDueDate || null,
      bankingInstitutionId: this.form.bankingInstitutionId || null,
      notes: this.form.notes?.trim() || '',
    };
    const req = this.editingId == null ? this.api.create(body) : this.api.update(this.editingId, body);
    req.subscribe({
      next: (saved) => {
        this.saving = false;
        const isNew = this.editingId == null;
        this.snackBar.open(isNew ? 'Credit card added' : 'Credit card updated', undefined, {
          duration: 4500,
        });
        if (isNew) {
          this.editingId = saved.id;
        } else {
          this.editingId = null;
          this.form = this.emptyCardForm();
        }
        this.refresh();
      },
      error: (e) => {
        this.saving = false;
        this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  deleteCard(row: FinanceCreditCardDto): void {
    const label = row.lastFour ? `${row.cardName} •••• ${row.lastFour}` : row.cardName;
    if (!window.confirm(`Delete ${label} at ${row.institution}?`)) {
      return;
    }
    this.api.delete(row.id).subscribe({
      next: () => {
        if (this.editingId === row.id) {
          this.cancelEdit();
        }
        if (this.statementsCardId === row.id) {
          this.statementsCardId = null;
          this.statements = [];
        }
        this.snackBar.open('Credit card deleted', undefined, { duration: 4500 });
        this.refresh();
      },
      error: (e) => {
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  onCreditSubTabChange(index: number): void {
    if (index !== 1) {
      return;
    }
    this.ensureStatementsCardSelected();
    if (this.statementsCardId != null) {
      this.loadStatements(this.statementsCardId);
    }
    this.loadAllStatements();
  }

  openStatements(row: FinanceCreditCardDto): void {
    this.statementsViewMode = 'card';
    this.selectStatementsCard(row);
    this.creditSubTabIndex = 1;
  }

  selectStatementsCard(row: FinanceCreditCardDto): void {
    this.statementsCardId = row.id;
    this.editingStatementId = null;
    this.statementForm = this.emptyStatementForm();
    this.loadStatements(row.id);
  }

  setStatementsViewMode(mode: 'card' | 'grouped'): void {
    this.statementsViewMode = mode;
    if (mode === 'grouped') {
      this.loadAllStatements();
    }
  }

  prevStatementsPeriod(): void {
    if (this.statementsPeriodMode === 'month') {
      if (this.statementsMonth === 1) {
        this.statementsMonth = 12;
        this.statementsYear -= 1;
      } else {
        this.statementsMonth -= 1;
      }
      return;
    }
    this.statementsYear -= 1;
  }

  nextStatementsPeriod(): void {
    if (this.statementsPeriodMode === 'month') {
      if (this.statementsMonth === 12) {
        this.statementsMonth = 1;
        this.statementsYear += 1;
      } else {
        this.statementsMonth += 1;
      }
      return;
    }
    this.statementsYear += 1;
  }

  loadStatements(cardId: number): void {
    this.statementsLoading = true;
    this.api.listStatements(cardId).subscribe({
      next: (rows) => {
        this.statements = rows;
        this.statementsLoading = false;
      },
      error: (e) => {
        this.statementsLoading = false;
        this.snackBar.open(`Could not load statements — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  loadAllStatements(): void {
    if (!this.cards.length) {
      this.allStatements = [];
      return;
    }
    this.statementsAllLoading = true;
    forkJoin(
      this.cards.map((card) =>
        this.api.listStatements(card.id).pipe(
          map((rows) => rows.map((row) => ({ ...row, card }))),
          catchError(() => of([] as CreditStatementWithCard[])),
        ),
      ),
    ).subscribe({
      next: (results) => {
        this.allStatements = this.sortStatementsWithCard(results.flat());
        this.statementsAllLoading = false;
      },
      error: (e) => {
        this.statementsAllLoading = false;
        this.snackBar.open(`Could not load statements — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  startAddStatement(): void {
    this.editingStatementId = null;
    this.statementForm = this.emptyStatementForm();
  }

  startEditStatement(row: FinanceCreditCardStatementDto): void {
    this.editingStatementId = row.id;
    this.statementForm = {
      statementDate: row.statementDate,
      statementBalance: row.statementBalance,
      minimumPayment: row.minimumPayment,
      paymentDueDate: row.paymentDueDate,
      notes: row.notes,
    };
  }

  cancelStatementEdit(): void {
    this.editingStatementId = null;
    this.statementForm = this.emptyStatementForm();
  }

  saveStatement(): void {
    if (!this.statementsCardId) {
      return;
    }
    if (!this.statementForm.statementDate) {
      this.snackBar.open('Statement date is required', undefined, { duration: 4500 });
      return;
    }
    this.statementSaving = true;
    const body: FinanceCreditCardStatementRequestDto = {
      ...this.statementForm,
      paymentDueDate: this.statementForm.paymentDueDate || null,
      notes: this.statementForm.notes?.trim() || '',
    };
    const req =
      this.editingStatementId == null
        ? this.api.createStatement(this.statementsCardId, body)
        : this.api.updateStatement(this.statementsCardId, this.editingStatementId, body);
    req.subscribe({
      next: () => {
        this.statementSaving = false;
        this.snackBar.open(this.editingStatementId == null ? 'Statement added' : 'Statement updated', undefined, {
          duration: 4500,
        });
        this.editingStatementId = null;
        this.statementForm = this.emptyStatementForm();
        this.loadStatements(this.statementsCardId!);
        this.loadAllStatements();
        this.refresh();
      },
      error: (e) => {
        this.statementSaving = false;
        this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  startEditStatementFromGrouped(row: CreditStatementWithCard): void {
    this.statementsViewMode = 'card';
    this.selectStatementsCard(row.card);
    this.startEditStatement(row);
  }

  deleteStatement(row: FinanceCreditCardStatementDto, cardId?: number): void {
    const targetCardId = cardId ?? this.statementsCardId;
    if (targetCardId == null) {
      return;
    }
    if (!window.confirm(`Delete statement dated ${this.formatDate(row.statementDate)}?`)) {
      return;
    }
    this.api.deleteStatement(targetCardId, row.id).subscribe({
      next: () => {
        if (this.editingStatementId === row.id) {
          this.cancelStatementEdit();
        }
        this.snackBar.open('Statement deleted', undefined, { duration: 4500 });
        if (this.statementsCardId === targetCardId) {
          this.loadStatements(targetCardId);
        }
        this.loadAllStatements();
      },
      error: (e) => {
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  ledgerSpending(row: FinanceCreditCardDto): number | null {
    if (row.bankingInstitutionId == null) {
      return null;
    }
    const v = this.ledgerSpendingByInstitution.get(row.bankingInstitutionId);
    return v == null ? 0 : v;
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

  healthClass(label: string): string {
    switch (label) {
      case 'Excellent':
        return 'credit-health-excellent';
      case 'Good':
        return 'credit-health-good';
      case 'Fair':
        return 'credit-health-fair';
      case 'High':
        return 'credit-health-high';
      default:
        return '';
    }
  }

  cardLabel(row: FinanceCreditCardDto): string {
    return row.lastFour ? `${row.cardName} •••• ${row.lastFour}` : row.cardName;
  }

  refreshAgentic(): void {
    this.agenticLoading = true;
    this.agenticBankingApi.status().subscribe({
      next: (status) => {
        this.agenticStatus = status;
        this.agenticLoading = false;
        if (status.connected) {
          this.loadAgenticTransactions();
        } else {
          this.agenticTransactions = [];
        }
      },
      error: (e) => {
        this.agenticLoading = false;
        this.snackBar.open(`Agentic card status failed — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 8000,
        });
      },
    });
  }

  saveAgenticTokens(): void {
    const raw = this.agenticTokenJson.trim();
    if (!raw) {
      this.snackBar.open('Paste .tokens-banking.json contents or access_token', undefined, { duration: 4500 });
      return;
    }
    let accessToken = raw;
    let refreshToken = '';
    try {
      const parsed = JSON.parse(raw) as { access_token?: string; refresh_token?: string };
      if (parsed.access_token) {
        accessToken = parsed.access_token;
        refreshToken = parsed.refresh_token ?? '';
      }
    } catch {
      // bare access token
    }
    this.agenticSavingTokens = true;
    this.agenticBankingApi.saveTokens(accessToken, refreshToken).subscribe({
      next: (status) => {
        this.agenticSavingTokens = false;
        this.agenticTokenJson = '';
        this.agenticStatus = status;
        this.snackBar.open('Robinhood Agentic Banking tokens saved', undefined, { duration: 4500 });
        this.syncAgentic();
      },
      error: (e) => {
        this.agenticSavingTokens = false;
        this.snackBar.open(`Save tokens failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  syncAgentic(): void {
    this.agenticSyncing = true;
    this.agenticBankingApi.sync().subscribe({
      next: (r) => {
        this.agenticSyncing = false;
        this.snackBar.open(r.message || 'Agentic card sync complete', undefined, { duration: 5000 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.agenticSyncing = false;
        this.snackBar.open(`Agentic sync failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        this.refreshAgentic();
      },
    });
  }

  disconnectAgentic(): void {
    if (!window.confirm('Disconnect Robinhood Agentic Credit Card from tracker-pg?')) {
      return;
    }
    this.agenticBankingApi.disconnect().subscribe({
      next: () => {
        this.agenticStatus = null;
        this.agenticTransactions = [];
        this.snackBar.open('Agentic card disconnected', undefined, { duration: 4500 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.snackBar.open(`Disconnect failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  agenticCardLabel(): string {
    const last4 = this.agenticStatus?.cardLastFour?.trim();
    return last4 ? `Robinhood Agentic •••• ${last4}` : 'Robinhood Agentic Credit Card';
  }

  formatInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleString();
  }

  private loadAgenticTransactions(): void {
    this.agenticBankingApi.transactions().subscribe({
      next: (r) => {
        this.agenticTransactions = r.transactions ?? [];
      },
      error: () => {
        this.agenticTransactions = [];
      },
    });
  }

  private loadLedgerSpending(cards: FinanceCreditCardDto[]): void {
    const linkedIds = cards.map((c) => c.bankingInstitutionId).filter((id): id is number => id != null && id > 0);
    if (!linkedIds.length) {
      this.ledgerSpendingByInstitution = new Map();
      return;
    }
    const now = new Date();
    this.financeApi.bankingLedger('MONTH', now.getFullYear(), now.getMonth() + 1).subscribe({
      next: (ledger) => {
        const map = new Map<number, number>();
        for (const t of ledger.transactions ?? []) {
          if (!linkedIds.includes(t.institutionId)) {
            continue;
          }
          const a = Number(t.amount);
          if (!Number.isFinite(a) || a >= 0) {
            continue;
          }
          map.set(t.institutionId, (map.get(t.institutionId) ?? 0) + -a);
        }
        this.ledgerSpendingByInstitution = map;
      },
      error: () => {
        this.ledgerSpendingByInstitution = new Map();
      },
    });
  }

  private ensureStatementsCardSelected(): void {
    if (this.statementsCardId != null && this.cards.some((c) => c.id === this.statementsCardId)) {
      return;
    }
    this.statementsCardId = this.cards[0]?.id ?? null;
  }

  private statementMatchesPeriod(date: string | null | undefined): boolean {
    if (!date) {
      return false;
    }
    const [year, month] = date.split('-').map(Number);
    if (year !== this.statementsYear) {
      return false;
    }
    if (this.statementsPeriodMode === 'year') {
      return true;
    }
    return month === this.statementsMonth;
  }

  private parseStatementMonth(date: string): number | null {
    const parts = date.split('-');
    if (parts.length < 2) {
      return null;
    }
    const month = Number(parts[1]);
    return Number.isFinite(month) ? month : null;
  }

  private sortStatementsWithCard(rows: CreditStatementWithCard[]): CreditStatementWithCard[] {
    return [...rows].sort((a, b) => {
      const byDate = b.statementDate.localeCompare(a.statementDate);
      if (byDate !== 0) {
        return byDate;
      }
      return this.cardLabel(a.card).localeCompare(this.cardLabel(b.card));
    });
  }

  private emptyCardForm(): FinanceCreditCardRequestDto {
    return {
      institution: '',
      cardName: '',
      lastFour: '',
      creditLimit: null,
      currentBalance: null,
      apr: null,
      statementBalance: null,
      statementDate: null,
      paymentDueDate: null,
      bankingInstitutionId: null,
      notes: '',
    };
  }

  private emptyStatementForm(): FinanceCreditCardStatementRequestDto {
    return {
      statementDate: '',
      statementBalance: null,
      minimumPayment: null,
      paymentDueDate: null,
      notes: '',
    };
  }
}
