import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import {
  ManagementSubscriptionBillingCycle,
  ManagementSubscriptionDto,
  ManagementSubscriptionStatus,
  ManagementSubscriptionWriteBody,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface SubscriptionDraft {
  name: string;
  vendor: string;
  category: string;
  plan: string;
  billingCycle: ManagementSubscriptionBillingCycle;
  amount: string;
  currency: string;
  enrolledOn: string;
  renewsOn: string;
  trialEndsOn: string;
  cancelledOn: string;
  status: ManagementSubscriptionStatus;
  autoRenew: boolean;
  website: string;
  accountEmail: string;
  notes: string;
}

@Component({
  selector: 'app-management-subscriptions-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './management-subscriptions-panel.component.html',
  styleUrl: './management-subscriptions-panel.component.scss',
})
export class ManagementSubscriptionsPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly statuses: ManagementSubscriptionStatus[] = ['ACTIVE', 'TRIAL', 'CANCELLED', 'EXPIRED'];
  readonly cycles: ManagementSubscriptionBillingCycle[] = ['WEEKLY', 'MONTHLY', 'ANNUAL', 'OTHER'];
  readonly tableColumns = ['name', 'enrolledOn', 'renews', 'status', 'amount', 'actions'];

  rows: ManagementSubscriptionDto[] = [];
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
    this.api.listSubscriptions().subscribe({
      next: (rows) => {
        this.rows = rows;
        this.loading = false;
        if (this.selectedId != null && !rows.some((r) => r.id === this.selectedId)) {
          this.selectedId = null;
        }
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load subscriptions', undefined, {
          duration: 3200,
        });
      },
    });
  }

  get filteredRows(): ManagementSubscriptionDto[] {
    const q = this.search.trim().toLowerCase();
    if (!q) {
      return this.rows;
    }
    return this.rows.filter((row) =>
      [row.name, row.vendor, row.category, row.plan, row.accountEmail, row.website, row.notes]
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  }

  get selected(): ManagementSubscriptionDto | null {
    if (this.selectedId == null) {
      return null;
    }
    return this.rows.find((r) => r.id === this.selectedId) ?? null;
  }

  get activeCount(): number {
    return this.rows.filter((r) => r.status === 'ACTIVE' || r.status === 'TRIAL').length;
  }

  get nextUp(): ManagementSubscriptionDto | null {
    return this.rows
      .filter((r) => (r.status === 'ACTIVE' || r.status === 'TRIAL') && r.nextRenewalOn)
      .slice()
      .sort((a, b) => (a.nextRenewalOn || '').localeCompare(b.nextRenewalOn || ''))[0] ?? null;
  }

  select(row: ManagementSubscriptionDto): void {
    this.selectedId = row.id;
  }

  startEdit(row: ManagementSubscriptionDto, ev?: Event): void {
    ev?.stopPropagation();
    this.selectedId = row.id;
    this.editingId = row.id;
    this.draft = {
      name: row.name,
      vendor: row.vendor || '',
      category: row.category || '',
      plan: row.plan || '',
      billingCycle: row.billingCycle || 'ANNUAL',
      amount: row.amount == null ? '' : String(row.amount),
      currency: row.currency || 'USD',
      enrolledOn: row.enrolledOn || '',
      renewsOn: row.renewsOn || '',
      trialEndsOn: row.trialEndsOn || '',
      cancelledOn: row.cancelledOn || '',
      status: row.status,
      autoRenew: row.autoRenew,
      website: row.website || '',
      accountEmail: row.accountEmail || '',
      notes: row.notes || '',
    };
  }

  applyFinvizSuggestion(): void {
    this.editingId = null;
    this.draft = {
      name: 'Finviz Elite',
      vendor: 'Finviz',
      category: 'Market data',
      plan: 'Annual',
      billingCycle: 'ANNUAL',
      amount: '299.50',
      currency: 'USD',
      enrolledOn: '2026-07-26',
      renewsOn: '2027-07-26',
      trialEndsOn: '',
      cancelledOn: '',
      status: 'ACTIVE',
      autoRenew: true,
      website: 'https://elite.finviz.com/subscription',
      accountEmail: '',
      notes:
        'Cancel on the Subscription page stops the next auto-renewal; Elite stays until the prepaid term ends. Finviz refunds only in the first 30 days after a written request to support@finviz.com.',
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
    const body: ManagementSubscriptionWriteBody = {
      name,
      vendor: this.draft.vendor.trim(),
      category: this.draft.category.trim(),
      plan: this.draft.plan.trim(),
      billingCycle: this.draft.billingCycle,
      amount,
      currency: this.draft.currency.trim() || 'USD',
      enrolledOn: this.draft.enrolledOn || null,
      renewsOn: this.draft.renewsOn || null,
      trialEndsOn: this.draft.trialEndsOn || null,
      cancelledOn: this.draft.cancelledOn || null,
      status: this.draft.status,
      autoRenew: this.draft.autoRenew,
      website: this.draft.website.trim(),
      accountEmail: this.draft.accountEmail.trim(),
      notes: this.draft.notes,
    };
    this.saving = true;
    const wasUpdate = this.editingId != null;
    const req = wasUpdate
      ? this.api.updateSubscription(this.editingId as number, body)
      : this.api.createSubscription(body);
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

  delete(row: ManagementSubscriptionDto, ev?: Event): void {
    ev?.stopPropagation();
    if (typeof window !== 'undefined' && !window.confirm(`Remove “${row.name}”?`)) {
      return;
    }
    this.saving = true;
    this.api.deleteSubscription(row.id).subscribe({
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

  formatMoney(row: ManagementSubscriptionDto): string {
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
      case 'TRIAL':
        return 'Trial';
      case 'CANCELLED':
        return 'Cancelled';
      case 'EXPIRED':
        return 'Expired';
      default:
        return status;
    }
  }

  cycleLabel(cycle: string): string {
    switch (cycle) {
      case 'WEEKLY':
        return 'Weekly';
      case 'MONTHLY':
        return 'Monthly';
      case 'ANNUAL':
        return 'Annual';
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

  private emptyDraft(): SubscriptionDraft {
    return {
      name: '',
      vendor: '',
      category: '',
      plan: '',
      billingCycle: 'ANNUAL',
      amount: '',
      currency: 'USD',
      enrolledOn: '',
      renewsOn: '',
      trialEndsOn: '',
      cancelledOn: '',
      status: 'ACTIVE',
      autoRenew: true,
      website: '',
      accountEmail: '',
      notes: '',
    };
  }
}
