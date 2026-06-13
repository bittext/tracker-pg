import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import {
  FinanceInsuranceOptionDto,
  FinanceInsurancePolicyDto,
  FinanceInsurancePolicyRequestDto,
  FinanceInsuranceSummaryDto,
} from '../../../models/finance.models';
import { FinanceInsuranceApiService } from '../../../services/finance-insurance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-insurance-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './insurance-panel.component.html',
  styleUrl: './insurance-panel.component.scss',
})
export class InsurancePanelComponent implements OnInit {
  private readonly api = inject(FinanceInsuranceApiService);
  private readonly snackBar = inject(MatSnackBar);

  policies: FinanceInsurancePolicyDto[] = [];
  summary: FinanceInsuranceSummaryDto | null = null;
  policyTypes: FinanceInsuranceOptionDto[] = [];
  premiumFrequencies: FinanceInsuranceOptionDto[] = [];
  loading = false;
  saving = false;
  editingId: number | null = null;

  form: FinanceInsurancePolicyRequestDto = this.emptyForm();

  readonly displayedColumns = [
    'carrier',
    'type',
    'coverage',
    'premium',
    'frequency',
    'annualPremium',
    'coverageStart',
    'coverageEnd',
    'renewal',
    'actions',
  ] as const;

  readonly reminderColumns = ['carrier', 'type', 'coverageEnd', 'daysUntilRenewal', 'renewalStatusLabel', 'actions'] as const;

  ngOnInit(): void {
    this.refresh();
  }

  get renewalReminders(): FinanceInsurancePolicyDto[] {
    return this.policies.filter((p) => p.renewalStatus === 'DUE_SOON' || p.renewalStatus === 'EXPIRED');
  }

  refresh(): void {
    this.loading = true;
    forkJoin({
      policies: this.api.list(),
      summary: this.api.summary(),
      options: this.api.options(),
    }).subscribe({
      next: ({ policies, summary, options }) => {
        this.policies = policies;
        this.summary = summary;
        this.policyTypes = options.policyTypes;
        this.premiumFrequencies = options.premiumFrequencies;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load insurance policies — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 8000,
        });
      },
    });
  }

  startAdd(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  startEdit(row: FinanceInsurancePolicyDto): void {
    this.editingId = row.id;
    this.form = {
      carrier: row.carrier,
      policyType: row.policyType,
      typeOther: row.typeOther,
      policyNumber: row.policyNumber,
      coverageDescription: row.coverageDescription,
      premiumAmount: row.premiumAmount,
      premiumFrequency: row.premiumFrequency,
      coverageStartDate: row.coverageStartDate,
      coverageEndDate: row.coverageEndDate,
      renewalReminderDays: row.renewalReminderDays,
      notes: row.notes,
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  save(): void {
    if (!this.form.carrier?.trim()) {
      this.snackBar.open('Carrier is required', undefined, { duration: 4500 });
      return;
    }
    if (!this.form.coverageDescription?.trim()) {
      this.snackBar.open('Coverage description is required', undefined, { duration: 4500 });
      return;
    }
    if (this.form.policyType === 'OTHER' && !this.form.typeOther?.trim()) {
      this.snackBar.open('Describe the policy type when Other is selected', undefined, { duration: 4500 });
      return;
    }
    this.saving = true;
    const body: FinanceInsurancePolicyRequestDto = {
      ...this.form,
      carrier: this.form.carrier.trim(),
      coverageDescription: this.form.coverageDescription.trim(),
      policyNumber: this.form.policyNumber?.trim() || '',
      typeOther: this.form.policyType === 'OTHER' ? this.form.typeOther?.trim() : '',
      coverageStartDate: this.form.coverageStartDate || null,
      coverageEndDate: this.form.coverageEndDate || null,
      renewalReminderDays: this.form.renewalReminderDays ?? 30,
      notes: this.form.notes?.trim() || '',
    };
    const req = this.editingId == null ? this.api.create(body) : this.api.update(this.editingId, body);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.snackBar.open(this.editingId == null ? 'Policy added' : 'Policy updated', undefined, { duration: 4500 });
        this.editingId = null;
        this.form = this.emptyForm();
        this.refresh();
      },
      error: (e) => {
        this.saving = false;
        this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  deleteRow(row: FinanceInsurancePolicyDto): void {
    if (!window.confirm(`Delete ${row.policyTypeLabel} policy with ${row.carrier}?`)) {
      return;
    }
    this.api.delete(row.id).subscribe({
      next: () => {
        if (this.editingId === row.id) {
          this.cancelEdit();
        }
        this.snackBar.open('Policy deleted', undefined, { duration: 4500 });
        this.refresh();
      },
      error: (e) => {
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  money(v: number | null | undefined): string {
    if (v == null || !Number.isFinite(v)) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(v);
  }

  formatDate(d: string | null | undefined): string {
    if (!d) {
      return '—';
    }
    return new Date(d + 'T12:00:00').toLocaleDateString();
  }

  daysLabel(days: number | null | undefined): string {
    if (days == null || !Number.isFinite(days)) {
      return '—';
    }
    if (days < 0) {
      return `${Math.abs(days)} day${Math.abs(days) === 1 ? '' : 's'} ago`;
    }
    if (days === 0) {
      return 'Today';
    }
    return `In ${days} day${days === 1 ? '' : 's'}`;
  }

  renewalClass(status: string): string {
    switch (status) {
      case 'EXPIRED':
        return 'ins-renewal-expired';
      case 'DUE_SOON':
        return 'ins-renewal-soon';
      case 'OK':
        return 'ins-renewal-ok';
      default:
        return '';
    }
  }

  private emptyForm(): FinanceInsurancePolicyRequestDto {
    return {
      carrier: '',
      policyType: 'AUTO',
      typeOther: '',
      policyNumber: '',
      coverageDescription: '',
      premiumAmount: null,
      premiumFrequency: 'ANNUAL',
      coverageStartDate: null,
      coverageEndDate: null,
      renewalReminderDays: 30,
      notes: '',
    };
  }
}
