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
  FinanceInvestmentDto,
  FinanceInvestmentOptionDto,
  FinanceInvestmentRequestDto,
} from '../../../models/finance.models';
import { FinanceInvestmentsApiService } from '../../../services/finance-investments-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-investments-panel',
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
  templateUrl: './investments-panel.component.html',
  styleUrl: './investments-panel.component.scss',
})
export class InvestmentsPanelComponent implements OnInit {
  private readonly api = inject(FinanceInvestmentsApiService);
  private readonly snackBar = inject(MatSnackBar);

  investments: FinanceInvestmentDto[] = [];
  investmentTypes: FinanceInvestmentOptionDto[] = [];
  loading = false;
  saving = false;
  editingId: number | null = null;

  form: FinanceInvestmentRequestDto = this.emptyForm();

  readonly displayedColumns = [
    'institution',
    'type',
    'symbol',
    'name',
    'dateAcquired',
    'quantity',
    'costBasis',
    'currentValue',
    'gainLoss',
    'actions',
  ] as const;

  ngOnInit(): void {
    this.refresh();
  }

  get totalCostBasis(): number | null {
    return this.sumField('costBasis');
  }

  get totalCurrentValue(): number | null {
    return this.sumField('currentValue');
  }

  get totalGainLoss(): number | null {
    if (this.totalCostBasis == null || this.totalCurrentValue == null) {
      return null;
    }
    return this.totalCurrentValue - this.totalCostBasis;
  }

  refresh(): void {
    this.loading = true;
    forkJoin({ investments: this.api.list(), options: this.api.options() }).subscribe({
      next: ({ investments, options }) => {
        this.investments = investments;
        this.investmentTypes = options.investmentTypes;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load investments — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  startAdd(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  startEdit(row: FinanceInvestmentDto): void {
    this.editingId = row.id;
    this.form = {
      institution: row.institution,
      investmentType: row.investmentType,
      typeOther: row.typeOther,
      symbol: row.symbol,
      name: row.name,
      dateAcquired: row.dateAcquired,
      quantity: row.quantity,
      costBasis: row.costBasis,
      currentValue: row.currentValue,
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
    if (!this.form.name?.trim()) {
      this.snackBar.open('Name is required', undefined, { duration: 4500 });
      return;
    }
    if (this.form.investmentType === 'OTHER' && !this.form.typeOther?.trim()) {
      this.snackBar.open('Describe the investment type when Other is selected', undefined, { duration: 4500 });
      return;
    }
    this.saving = true;
    const body: FinanceInvestmentRequestDto = {
      ...this.form,
      institution: this.form.institution.trim(),
      name: this.form.name.trim(),
      symbol: this.form.symbol?.trim().toUpperCase() || '',
      typeOther: this.form.investmentType === 'OTHER' ? this.form.typeOther?.trim() : '',
      dateAcquired: this.form.dateAcquired || null,
      notes: this.form.notes?.trim() || '',
    };
    const req =
      this.editingId == null ? this.api.create(body) : this.api.update(this.editingId, body);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.snackBar.open(this.editingId == null ? 'Investment added' : 'Investment updated', undefined, {
          duration: 4500,
        });
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

  deleteRow(row: FinanceInvestmentDto): void {
    if (!window.confirm(`Delete ${row.name} at ${row.institution}?`)) {
      return;
    }
    this.api.delete(row.id).subscribe({
      next: () => {
        if (this.editingId === row.id) {
          this.cancelEdit();
        }
        this.snackBar.open('Investment deleted', undefined, { duration: 4500 });
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

  qty(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 6 }).format(v);
  }

  formatDate(d: string | null | undefined): string {
    if (!d) {
      return '—';
    }
    return new Date(d + 'T12:00:00').toLocaleDateString();
  }

  gainClass(v: number | null | undefined): string {
    if (v == null || v === 0) {
      return '';
    }
    return v > 0 ? 'inv-gain-pos' : 'inv-gain-neg';
  }

  private sumField(field: 'costBasis' | 'currentValue'): number | null {
    if (!this.investments.length) {
      return null;
    }
    let hasAny = false;
    let sum = 0;
    for (const row of this.investments) {
      const v = row[field];
      if (v != null) {
        hasAny = true;
        sum += v;
      }
    }
    return hasAny ? sum : null;
  }

  private emptyForm(): FinanceInvestmentRequestDto {
    return {
      institution: '',
      investmentType: 'STOCK',
      typeOther: '',
      symbol: '',
      name: '',
      dateAcquired: null,
      quantity: null,
      costBasis: null,
      currentValue: null,
      notes: '',
    };
  }
}
