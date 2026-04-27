import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { FinanceTax1040ReturnDto, Form1040ParsedSummary } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-finance-tax-1040-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './finance-tax-1040-panel.component.html',
  styleUrl: './finance-tax-1040-panel.component.scss',
})
export class FinanceTax1040PanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snack = inject(MatSnackBar);

  returns: FinanceTax1040ReturnDto[] = [];
  loading = false;
  uploadBusy = false;
  uploadYear = new Date().getFullYear();
  selected: FinanceTax1040ReturnDto | null = null;
  detailLoading = false;

  displayedColumns = ['taxYear', 'filename', 'agi', 'tax', 'refund', 'owed', 'actions'] as const;
  trackById = (_: number, r: FinanceTax1040ReturnDto) => r.id;

  ngOnInit() {
    this.loadList();
  }

  loadList() {
    this.loading = true;
    this.api.listTax1040Returns(false).subscribe({
      next: (rows) => {
        this.returns = rows;
        this.loading = false;
        if (this.selected) {
          const m = rows.find((r) => r.id === this.selected!.id);
          this.selected = m ?? null;
        }
      },
      error: (e) => {
        this.loading = false;
        this.snack.open('Could not load 1040 records: ' + formatHttpErrorDetail(e), 'Dismiss', { duration: 8_000 });
      },
    });
  }

  openDetail(row: FinanceTax1040ReturnDto) {
    this.detailLoading = true;
    this.selected = row;
    this.api.getTax1040Return(row.id, true).subscribe({
      next: (r) => {
        this.selected = r;
        this.detailLoading = false;
      },
      error: (e) => {
        this.detailLoading = false;
        this.snack.open('Could not load details: ' + formatHttpErrorDetail(e), 'Dismiss', { duration: 8_000 });
      },
    });
  }

  closeDetail() {
    this.selected = null;
  }

  onFileSelected(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const y = Math.floor(Number(this.uploadYear));
    if (!Number.isFinite(y) || y < 1990 || y > 2100) {
      this.snack.open('Choose a tax year between 1990 and 2100.', 'OK', { duration: 4_000 });
      return;
    }
    this.uploadBusy = true;
    this.api.uploadTax1040Return(y, file).subscribe({
      next: () => {
        this.uploadBusy = false;
        this.snack.open('Form 1040 saved and parsed.', 'OK', { duration: 3_000 });
        this.loadList();
      },
      error: (e) => {
        this.uploadBusy = false;
        this.snack.open('Upload failed: ' + formatHttpErrorDetail(e), 'Dismiss', { duration: 10_000 });
      },
    });
  }

  deleteRow(row: FinanceTax1040ReturnDto) {
    if (!window.confirm(`Delete the saved 1040 for tax year ${row.taxYear}?`)) {
      return;
    }
    this.api.deleteTax1040Return(row.id).subscribe({
      next: () => {
        if (this.selected?.id === row.id) {
          this.selected = null;
        }
        this.snack.open('Deleted.', 'OK', { duration: 2_000 });
        this.loadList();
      },
      error: (e) => {
        this.snack.open('Delete failed: ' + formatHttpErrorDetail(e), 'Dismiss', { duration: 8_000 });
      },
    });
  }

  download(row: FinanceTax1040ReturnDto) {
    this.api.downloadTax1040Blob(row.downloadPath).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = row.originalFilename;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => {
        this.snack.open('Download failed: ' + formatHttpErrorDetail(e), 'Dismiss', { duration: 8_000 });
      },
    });
  }

  money(v: number | null | undefined): string {
    if (v == null) {
      return '—';
    }
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(v);
  }

  summaryCols(s: Form1040ParsedSummary | null | undefined): { label: string; value: string }[] {
    if (!s) {
      return [];
    }
    const rows: { label: string; value: string }[] = [
      { label: 'Wages, salaries, tips (line 1a, best effort)', value: this.money(s.wagesSalariesTips) },
      { label: 'Taxable interest (2b)', value: this.money(s.taxableInterest) },
      { label: 'Ordinary dividends (3b)', value: this.money(s.ordinaryDividends) },
      { label: 'Adjusted gross income (11)', value: this.money(s.adjustedGrossIncome) },
      { label: 'Taxable income (15)', value: this.money(s.taxableIncome) },
      { label: 'Total tax (16)', value: this.money(s.totalTax) },
      { label: 'Federal income tax withheld (25d)', value: this.money(s.federalIncomeTaxWithheld) },
      { label: 'Estimated tax payments (26)', value: this.money(s.estimatedTaxPayments) },
      { label: 'Refund (from return)', value: this.money(s.refund) },
      { label: 'Amount you owe (from return)', value: this.money(s.amountOwed) },
    ];
    return rows;
  }
}
