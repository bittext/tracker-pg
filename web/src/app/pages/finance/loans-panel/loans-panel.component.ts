import { CommonModule } from '@angular/common';
import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import {
  FinanceLoanDto,
  FinanceLoanOptionDto,
  FinanceLoanRequestDto,
} from '../../../models/finance.models';
import { FinanceLoansApiService } from '../../../services/finance-loans-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-loans-panel',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './loans-panel.component.html',
  styleUrl: './loans-panel.component.scss',
})
export class LoansPanelComponent implements OnInit {
  private readonly api = inject(FinanceLoansApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** When true, show admin-oriented hint text. */
  @Input() adminContext = false;

  loans: FinanceLoanDto[] = [];
  loanNatures: FinanceLoanOptionDto[] = [];
  paymentFrequencies: FinanceLoanOptionDto[] = [];
  loading = false;
  saving = false;
  editingId: number | null = null;

  form: FinanceLoanRequestDto = this.emptyForm();

  readonly displayedColumns = [
    'institution',
    'nature',
    'dateAvailed',
    'dateToCommence',
    'currentBalance',
    'interestRate',
    'paidSoFar',
    'balanceToPay',
    'frequency',
    'actions',
  ] as const;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    forkJoin({ loans: this.api.list(), options: this.api.options() }).subscribe({
      next: ({ loans, options }) => {
        this.loans = loans;
        this.loanNatures = options.loanNatures;
        this.paymentFrequencies = options.paymentFrequencies;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load loans — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  startAdd(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  startEdit(row: FinanceLoanDto): void {
    this.editingId = row.id;
    this.form = {
      institution: row.institution,
      loanNature: row.loanNature,
      natureOther: row.natureOther,
      dateAvailed: row.dateAvailed,
      dateToCommence: row.dateToCommence,
      currentBalance: row.currentBalance,
      interestRate: row.interestRate,
      paidSoFar: row.paidSoFar,
      balanceToPay: row.balanceToPay,
      paymentFrequency: row.paymentFrequency,
      notes: row.notes,
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  save(): void {
    if (!this.form.institution?.trim()) {
      this.snackBar.open('Institution is required', undefined, { duration: 4500 });
      return;
    }
    if (this.form.loanNature === 'OTHER' && !this.form.natureOther?.trim()) {
      this.snackBar.open('Describe the loan type when Other is selected', undefined, { duration: 4500 });
      return;
    }
    this.saving = true;
    const body: FinanceLoanRequestDto = {
      ...this.form,
      institution: this.form.institution.trim(),
      natureOther: this.form.loanNature === 'OTHER' ? this.form.natureOther?.trim() : '',
      dateAvailed: this.form.dateAvailed || null,
      dateToCommence: this.form.dateToCommence || null,
      notes: this.form.notes?.trim() || '',
    };
    const req =
      this.editingId == null
        ? this.api.create(body)
        : this.api.update(this.editingId, body);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.snackBar.open(this.editingId == null ? 'Loan added' : 'Loan updated', undefined, { duration: 4500 });
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

  deleteRow(row: FinanceLoanDto): void {
    if (!window.confirm(`Delete loan from ${row.institution}?`)) {
      return;
    }
    this.api.delete(row.id).subscribe({
      next: () => {
        if (this.editingId === row.id) {
          this.cancelEdit();
        }
        this.snackBar.open('Loan deleted', undefined, { duration: 4500 });
        this.refresh();
      },
      error: (e) => {
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  money(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(v);
  }

  pct(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    return `${v}%`;
  }

  formatDate(d: string | null | undefined): string {
    if (!d) {
      return '—';
    }
    return new Date(d + 'T12:00:00').toLocaleDateString();
  }

  private emptyForm(): FinanceLoanRequestDto {
    return {
      institution: '',
      loanNature: 'PERSONAL',
      natureOther: '',
      dateAvailed: null,
      dateToCommence: null,
      currentBalance: null,
      interestRate: null,
      paidSoFar: null,
      balanceToPay: null,
      paymentFrequency: 'MONTHLY',
      notes: '',
    };
  }
}
