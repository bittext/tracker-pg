import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  FinanceTaxDeskIncomeItemDto,
  FinanceTaxDeskPageDto,
  FinanceTaxDeskPaymentDto,
  FinanceTaxDeskQuarterDto,
  FinanceTaxDeskSettingsDto,
  FinanceTaxDeskWorkbookDto,
  TaxDeskRiskLevel,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-tax-desk',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    CurrencyPipe,
    DatePipe,
  ],
  templateUrl: './reports-finance-robinhood-tax-desk.component.html',
  styleUrl: './reports-finance-robinhood-tax-desk.component.scss',
})
export class ReportsFinanceRobinhoodTaxDeskComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snack = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  asOf = '';
  loading = false;
  saving = false;
  page: FinanceTaxDeskPageDto | null = null;
  settingsDraft: FinanceTaxDeskSettingsDto | null = null;
  newIncome: Partial<FinanceTaxDeskIncomeItemDto> = this.emptyIncome();
  newPayment: Partial<FinanceTaxDeskPaymentDto> = this.emptyPayment();

  ngOnInit(): void {
    this.load();
  }

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current + 1; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  load(asOf?: string | null): void {
    this.loading = true;
    this.api.taxDesk(this.reportYear, asOf || undefined).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not load tax desk', 'Dismiss', { duration: 8000 });
      },
    });
  }

  onYearChange(): void {
    this.asOf = '';
    this.load();
  }

  selectDay(asOf: string): void {
    this.load(asOf);
  }

  saveSettings(): void {
    if (!this.settingsDraft) {
      return;
    }
    this.saving = true;
    this.api.saveTaxDeskSettings(this.reportYear, this.settingsDraft).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.saving = false;
        this.snack.open('Working papers updated', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not save settings', 'Dismiss', { duration: 8000 });
      },
    });
  }

  saveIncomeRow(row: FinanceTaxDeskIncomeItemDto): void {
    this.saving = true;
    this.api.updateTaxDeskIncome(this.reportYear, row.id, row).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not save income', 'Dismiss', { duration: 8000 });
      },
    });
  }

  addIncome(): void {
    if (!this.newIncome.payer?.trim()) {
      this.snack.open('Enter a payer or description', 'Dismiss', { duration: 4000 });
      return;
    }
    this.saving = true;
    this.api.addTaxDeskIncome(this.reportYear, this.newIncome).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.newIncome = this.emptyIncome();
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not add income', 'Dismiss', { duration: 8000 });
      },
    });
  }

  removeIncome(row: FinanceTaxDeskIncomeItemDto): void {
    this.saving = true;
    this.api.deleteTaxDeskIncome(this.reportYear, row.id).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not remove income', 'Dismiss', { duration: 8000 });
      },
    });
  }

  addPayment(): void {
    if (!this.newPayment.paidOn || this.newPayment.amount == null || this.newPayment.amount <= 0) {
      this.snack.open('Enter a payment date and amount', 'Dismiss', { duration: 4000 });
      return;
    }
    this.saving = true;
    this.api.addTaxDeskPayment(this.reportYear, this.newPayment).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.newPayment = this.emptyPayment();
        this.saving = false;
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not add payment', 'Dismiss', { duration: 8000 });
      },
    });
  }

  removePayment(row: FinanceTaxDeskPaymentDto): void {
    if (row?.id == null) {
      this.snack.open('This payment cannot be removed', 'Dismiss', { duration: 4000 });
      return;
    }
    this.saving = true;
    this.api.deleteTaxDeskPayment(this.reportYear, row.id).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.saving = false;
        this.snack.open('Payment removed', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.saving = false;
        this.snack.open(formatHttpErrorDetail(err) || 'Could not remove payment', 'Dismiss', { duration: 8000 });
      },
    });
  }

  wb(): FinanceTaxDeskWorkbookDto | null {
    return this.page?.workbook ?? null;
  }

  riskClass(level?: TaxDeskRiskLevel | null): string {
    switch (level) {
      case 'REFUND_TRACK':
        return 'risk-refund';
      case 'SAFE_HARBOR':
        return 'risk-harbor';
      case 'CATCH_UP':
        return 'risk-catch';
      case 'PENALTY_RISK':
        return 'risk-penalty';
      case 'LIABILITY_SPIKE':
        return 'risk-spike';
      default:
        return 'risk-catch';
    }
  }

  riskLabel(level?: TaxDeskRiskLevel | null): string {
    switch (level) {
      case 'REFUND_TRACK':
        return 'Refund track';
      case 'SAFE_HARBOR':
        return 'Safe harbor only';
      case 'CATCH_UP':
        return 'Catch up remaining quarters';
      case 'PENALTY_RISK':
        return 'Penalty exposure';
      case 'LIABILITY_SPIKE':
        return 'Liability rising';
      default:
        return 'Review';
    }
  }

  quarterStatus(q: FinanceTaxDeskQuarterDto): string {
    return (q.status || '').replaceAll('_', ' ');
  }

  quarterClass(q: FinanceTaxDeskQuarterDto): string {
    switch (q.status) {
      case 'SHORT':
      case 'DUE_TODAY':
        return 'q-hot';
      case 'DUE_SOON':
        return 'q-warn';
      case 'MET':
      case 'ON_TRACK':
        return 'q-ok';
      default:
        return 'q-next';
    }
  }

  filingOutcome(wb: FinanceTaxDeskWorkbookDto): string {
    if (wb.filingDayBalance > 0) {
      return 'Balance due at filing';
    }
    if (wb.filingDayBalance < 0) {
      return 'Refund at filing';
    }
    return 'Even at filing';
  }

  filingAmount(wb: FinanceTaxDeskWorkbookDto): number {
    return Math.abs(wb.filingDayBalance ?? 0);
  }

  deltaUp(n: number | null | undefined): boolean {
    return (n ?? 0) > 0;
  }

  isBuy(side: string | null | undefined): boolean {
    return (side ?? '').toLowerCase() === 'buy';
  }

  isSell(side: string | null | undefined): boolean {
    return (side ?? '').toLowerCase() === 'sell';
  }

  private applyPage(page: FinanceTaxDeskPageDto): void {
    this.page = page;
    this.asOf = page.asOf;
    this.settingsDraft = page.workbook?.settings ? { ...page.workbook.settings } : null;
  }

  private emptyIncome(): Partial<FinanceTaxDeskIncomeItemDto> {
    return {
      kind: 'EXTERNAL',
      payer: '',
      ytdAmount: 0,
      annualProjected: 0,
      withholdingYtd: 0,
      withholdingAnnualProjected: 0,
      notes: '',
    };
  }

  private emptyPayment(): Partial<FinanceTaxDeskPaymentDto> {
    return { paidOn: '', amount: undefined, method: 'IRS Direct Pay', source: 'MANUAL', notes: '' };
  }
}
