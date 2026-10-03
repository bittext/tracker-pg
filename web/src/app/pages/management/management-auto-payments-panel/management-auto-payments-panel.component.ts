import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import {
  ManagementAutoPaymentDto,
  ManagementAutoPaymentFrequency,
  ManagementAutoPaymentMethod,
  ManagementAutoPaymentStatus,
  ManagementAutoPaymentWriteBody,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface AutoPaymentDraft {
  name: string;
  payee: string;
  category: string;
  paymentMethod: ManagementAutoPaymentMethod;
  frequency: ManagementAutoPaymentFrequency;
  amount: string;
  currency: string;
  startedOn: string;
  nextPaymentOn: string;
  dayOfMonth: string;
  endedOn: string;
  status: ManagementAutoPaymentStatus;
  fundingAccount: string;
  confirmationRef: string;
  website: string;
  notes: string;
}

@Component({
  selector: 'app-management-auto-payments-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './management-auto-payments-panel.component.html',
  styleUrl: './management-auto-payments-panel.component.scss',
})
export class ManagementAutoPaymentsPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly statuses: ManagementAutoPaymentStatus[] = ['ACTIVE', 'PAUSED', 'CANCELLED'];
  readonly frequencies: ManagementAutoPaymentFrequency[] = ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'ANNUAL', 'OTHER'];
  readonly methods: ManagementAutoPaymentMethod[] = ['ACH', 'CARD', 'BILL_PAY', 'OTHER'];
  readonly tableColumns = ['name', 'nextDebit', 'status', 'amount', 'funding', 'actions'];

  rows: ManagementAutoPaymentDto[] = [];
  selectedId: number | null = null;
  editingId: number | null = null;
  search = '';
  loading = false;
  saving = false;
  draft = this.emptyDraft();

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.loading = true;
    this.api.listAutoPayments().subscribe({
      next: (rows) => {
        this.rows = rows;
        this.loading = false;
        if (this.selectedId != null && !rows.some((r) => r.id === this.selectedId)) {
          this.selectedId = null;
        }
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load auto payments', undefined, {
          duration: 3200,
        });
      },
    });
  }

  get filteredRows(): ManagementAutoPaymentDto[] {
    const q = this.search.trim().toLowerCase();
    if (!q) {
      return this.rows;
    }
    return this.rows.filter((row) =>
      [row.name, row.payee, row.category, row.fundingAccount, row.confirmationRef, row.website, row.notes]
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  }

  get selected(): ManagementAutoPaymentDto | null {
    if (this.selectedId == null) {
      return null;
    }
    return this.rows.find((r) => r.id === this.selectedId) ?? null;
  }

  get activeCount(): number {
    return this.rows.filter((r) => r.status === 'ACTIVE').length;
  }

  get nextUp(): ManagementAutoPaymentDto | null {
    return (
      this.rows
        .filter((r) => r.status === 'ACTIVE' && r.nextDebitOn)
        .slice()
        .sort((a, b) => (a.nextDebitOn || '').localeCompare(b.nextDebitOn || ''))[0] ?? null
    );
  }

  select(row: ManagementAutoPaymentDto): void {
    this.selectedId = row.id;
  }

  startEdit(row: ManagementAutoPaymentDto, ev?: Event): void {
    ev?.stopPropagation();
    this.selectedId = row.id;
    this.editingId = row.id;
    this.draft = {
      name: row.name,
      payee: row.payee || '',
      category: row.category || '',
      paymentMethod: row.paymentMethod || 'ACH',
      frequency: row.frequency || 'MONTHLY',
      amount: row.amount == null ? '' : String(row.amount),
      currency: row.currency || 'USD',
      startedOn: row.startedOn || '',
      nextPaymentOn: row.nextPaymentOn || '',
      dayOfMonth: row.dayOfMonth == null ? '' : String(row.dayOfMonth),
      endedOn: row.endedOn || '',
      status: row.status,
      fundingAccount: row.fundingAccount || '',
      confirmationRef: row.confirmationRef || '',
      website: row.website || '',
      notes: row.notes || '',
    };
  }

  save(): void {
    const name = this.draft.name.trim();
    if (!name) {
      this.snackBar.open('Name is required', undefined, { duration: 2000 });
      return;
    }
    const amountRaw = this.draft.amount.trim();
    const amount = amountRaw === '' ? null : Number(amountRaw);
    if (amountRaw && Number.isNaN(amount)) {
      this.snackBar.open('Amount must be a number', undefined, { duration: 2200 });
      return;
    }
    const dayRaw = this.draft.dayOfMonth.trim();
    let dayOfMonth: number | null = null;
    if (dayRaw) {
      dayOfMonth = Number(dayRaw);
      if (!Number.isInteger(dayOfMonth) || dayOfMonth < 1 || dayOfMonth > 31) {
        this.snackBar.open('Day of month must be 1–31', undefined, { duration: 2200 });
        return;
      }
    }
    const body: ManagementAutoPaymentWriteBody = {
      name,
      payee: this.draft.payee.trim(),
      category: this.draft.category.trim(),
      paymentMethod: this.draft.paymentMethod,
      frequency: this.draft.frequency,
      amount,
      currency: this.draft.currency.trim() || 'USD',
      startedOn: this.draft.startedOn || null,
      nextPaymentOn: this.draft.nextPaymentOn || null,
      dayOfMonth,
      endedOn: this.draft.endedOn || null,
      status: this.draft.status,
      fundingAccount: this.draft.fundingAccount.trim(),
      confirmationRef: this.draft.confirmationRef.trim(),
      website: this.draft.website.trim(),
      notes: this.draft.notes,
    };
    this.saving = true;
    const wasUpdate = this.editingId != null;
    const req = wasUpdate
      ? this.api.updateAutoPayment(this.editingId as number, body)
      : this.api.createAutoPayment(body);
    req.subscribe({
      next: (saved) => {
        this.saving = false;
        this.editingId = null;
        this.draft = this.emptyDraft();
        this.selectedId = saved.id;
        this.refreshAll();
        this.snackBar.open(wasUpdate ? 'Updated' : 'Saved', undefined, { duration: 1800 });
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not save', undefined, { duration: 3200 });
      },
    });
  }

  delete(row: ManagementAutoPaymentDto, ev?: Event): void {
    ev?.stopPropagation();
    if (typeof window !== 'undefined' && !window.confirm(`Remove “${row.name}”?`)) {
      return;
    }
    this.saving = true;
    this.api.deleteAutoPayment(row.id).subscribe({
      next: () => {
        this.saving = false;
        if (this.selectedId === row.id) {
          this.selectedId = null;
        }
        if (this.editingId === row.id) {
          this.resetForm();
        }
        this.refreshAll();
        this.snackBar.open('Removed', undefined, { duration: 1600 });
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not delete', undefined, { duration: 3200 });
      },
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.draft = this.emptyDraft();
  }

  openWebsite(raw: string, ev?: Event): void {
    ev?.stopPropagation();
    const url = this.normalizeUrl(raw);
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  formatMoney(row: ManagementAutoPaymentDto): string {
    if (row.amount == null) {
      return '—';
    }
    const n = Number(row.amount);
    if (Number.isNaN(n)) {
      return String(row.amount);
    }
    return `${row.currency || 'USD'} ${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    if (!y || !m || !d) {
      return iso;
    }
    return new Date(y, m - 1, d).toLocaleDateString();
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'Active';
      case 'PAUSED':
        return 'Paused';
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return status;
    }
  }

  frequencyLabel(frequency: string): string {
    switch (frequency) {
      case 'WEEKLY':
        return 'Weekly';
      case 'BIWEEKLY':
        return 'Every two weeks';
      case 'MONTHLY':
        return 'Monthly';
      case 'ANNUAL':
        return 'Annual';
      default:
        return 'Other';
    }
  }

  methodLabel(method: string): string {
    switch (method) {
      case 'ACH':
        return 'ACH / bank';
      case 'CARD':
        return 'Card';
      case 'BILL_PAY':
        return 'Bill pay';
      default:
        return 'Other';
    }
  }

  private normalizeUrl(raw: string): string | null {
    const t = (raw || '').trim();
    if (!t) {
      return null;
    }
    if (/^https?:\/\//i.test(t)) {
      return t;
    }
    return `https://${t}`;
  }

  private emptyDraft(): AutoPaymentDraft {
    return {
      name: '',
      payee: '',
      category: '',
      paymentMethod: 'ACH',
      frequency: 'MONTHLY',
      amount: '',
      currency: 'USD',
      startedOn: '',
      nextPaymentOn: '',
      dayOfMonth: '',
      endedOn: '',
      status: 'ACTIVE',
      fundingAccount: '',
      confirmationRef: '',
      website: '',
      notes: '',
    };
  }
}
