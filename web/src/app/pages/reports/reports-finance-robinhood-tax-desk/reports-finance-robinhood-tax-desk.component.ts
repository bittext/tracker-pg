import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
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
  FinanceTaxDeskTradeRowDto,
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

  @ViewChild('dayDetail') dayDetail?: ElementRef<HTMLElement>;

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

  load(asOf?: string | null, opts?: { scrollToDay?: boolean }): void {
    this.loading = true;
    const day = asOf ? this.isoDate(asOf) : undefined;
    this.api.taxDesk(this.reportYear, day).subscribe({
      next: (page) => {
        this.applyPage(page);
        this.loading = false;
        if (opts?.scrollToDay) {
          setTimeout(() => this.scrollToDay(), 40);
        }
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

  refreshCurrent(): void {
    this.load(this.asOf || undefined);
  }

  selectDay(asOf: string | null | undefined): void {
    const day = this.isoDate(asOf);
    if (!day) {
      return;
    }
    this.load(day, { scrollToDay: true });
  }

  backToToday(): void {
    this.asOf = '';
    this.load(undefined, { scrollToDay: true });
  }

  openPriorDay(): void {
    const prior = this.priorHistoryAsOf();
    if (prior) {
      this.selectDay(prior);
    }
  }

  isHistorical(): boolean {
    return this.page?.live === false;
  }

  sameDay(a?: string | null, b?: string | null): boolean {
    return this.isoDate(a) === this.isoDate(b) && !!this.isoDate(a);
  }

  priorHistoryAsOf(): string | null {
    const hist = this.page?.history ?? [];
    const cur = this.isoDate(this.asOf);
    const idx = hist.findIndex((h) => this.sameDay(h.asOf, cur));
    if (idx >= 0 && idx + 1 < hist.length) {
      return hist[idx + 1].asOf;
    }
    return hist.length > 1 ? hist[1].asOf : null;
  }

  private scrollToDay(): void {
    this.dayDetail?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  isoDate(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    return value.length >= 10 ? value.slice(0, 10) : value.trim();
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
      return 'Still owe at April filing';
    }
    if (wb.filingDayBalance < 0) {
      return 'Refund at April filing';
    }
    return 'Even at April filing';
  }

  filingAmount(wb: FinanceTaxDeskWorkbookDto): number {
    return Math.abs(wb.filingDayBalance ?? 0);
  }

  carryoverPool(wb: FinanceTaxDeskWorkbookDto): number {
    const st = wb.settings?.shortTermLossCarryover ?? 0;
    const lt = wb.settings?.longTermLossCarryover ?? 0;
    const fromSettings = st + lt;
    return fromSettings > 0 ? fromSettings : (wb.capitalLossCarryoverApplied ?? 0);
  }

  capitalInAgi(wb: FinanceTaxDeskWorkbookDto): number {
    return (wb.agi ?? 0) - (wb.wagesProjected ?? 0) - (wb.externalProjected ?? 0);
  }

  ordinaryLossUsed(wb: FinanceTaxDeskWorkbookDto): number {
    const net = (wb.realizedYtd ?? 0) - this.carryoverPool(wb);
    if (net >= 0) {
      return 0;
    }
    return Math.min(3000, Math.abs(net));
  }

  unusedCarryover(wb: FinanceTaxDeskWorkbookDto): number {
    const net = (wb.realizedYtd ?? 0) - this.carryoverPool(wb);
    if (net >= 0) {
      return 0;
    }
    return Math.abs(net) - this.ordinaryLossUsed(wb);
  }

  wageSource(wb: FinanceTaxDeskWorkbookDto): string {
    const w2 = (wb.incomeItems || []).find((row) => (row.kind || '').toUpperCase() === 'W2');
    return w2?.payer?.trim() || 'W-2 annual projection (last-year pattern until you edit it)';
  }

  usesBrokerYtd(wb: FinanceTaxDeskWorkbookDto): boolean {
    return (wb.realizedYtdSource || '').toUpperCase() === 'ROBINHOOD';
  }

  realizedLineLabel(wb: FinanceTaxDeskWorkbookDto): string {
    return this.usesBrokerYtd(wb) ? 'Robinhood YTD realized (broker)' : 'This year’s realized trades (app FIFO)';
  }

  realizedKpiLabel(wb: FinanceTaxDeskWorkbookDto): string {
    return this.usesBrokerYtd(wb) ? 'Broker YTD realized' : 'FIFO realized trades';
  }

  realizedSourceNote(wb: FinanceTaxDeskWorkbookDto): string {
    const asOf = wb.asOf;
    if (this.usesBrokerYtd(wb)) {
      const parts = (wb.robinhoodRealizedAccounts || [])
        .map((row) => `${row.label} ••••${row.suffix}`)
        .filter(Boolean);
      const who = parts.length ? parts.join(', ') : 'Individual, Agentic, and Ammu';
      let note = `Calendar YTD from Robinhood on ${who} through ${asOf}. Not Form 1099-B. Open lots are out.`;
      const fifo = wb.fifoTapeRealizedYtd;
      if (fifo != null && Math.abs(fifo - (wb.realizedYtd ?? 0)) > 0.005) {
        note += ` App FIFO tape was different because unmatched sells contribute $0 there.`;
      }
      return note;
    }
    return `FIFO on Individual, Agentic, and Ammu through ${asOf}. Broker YTD was unavailable, so this is the in-app tape. Open lots are out.`;
  }

  taxAfterCredits(wb: FinanceTaxDeskWorkbookDto): number {
    return (wb.estimatedIncomeTax ?? 0) - (wb.childCredit ?? 0);
  }

  takeawayTitle(wb: FinanceTaxDeskWorkbookDto): string {
    if ((wb.recommendedAdditionalPrepay ?? 0) > 0) {
      return 'Send a 1040-ES by the next due date below.';
    }
    if ((wb.filingDayBalance ?? 0) > 0) {
      return 'No extra 1040-ES on the refund plan, but April filing still shows a balance due.';
    }
    if ((wb.filingDayBalance ?? 0) < 0) {
      return 'Nothing more to send. April filing is a refund on these papers.';
    }
    return 'Nothing more to send. April filing is even.';
  }

  takeawayBody(wb: FinanceTaxDeskWorkbookDto): string {
    const year = wb.taxYear;
    const next = this.nextOpenQuarter(wb);
    const extra = wb.recommendedAdditionalPrepay ?? 0;
    if (extra > 0 && next) {
      return (
        `Full-year tax is the first tile — not a check due today. Send the extra 1040-ES on Q${next.quarter} ` +
        `(due ${this.formatDay(next.dueDate)}). Withholding is counted as paid evenly across the four dates.`
      );
    }
    if ((wb.filingDayBalance ?? 0) > 0) {
      return `Full-year tax is already computed from wages, this year’s closed trades, and loss carryover. Credits are short of that tax, so Form 1040 in April ${year + 1} would still owe unless you add withholding or another estimate.`;
    }
    return (
      `The $${this.roundDollars(wb.estimatedFederalTax)} figure is full-year tax after carryover. ` +
      `Withholding plus logged 1040-ES already cover it` +
      ((wb.filingDayBalance ?? 0) < 0 ? ` and leave a refund` : '') +
      `. Remaining 1040-ES dates are on the calendar below — send $0 unless income jumps. ` +
      `Form 1040 is due mid-April ${year + 1}.`
    );
  }

  nextOpenQuarter(wb: FinanceTaxDeskWorkbookDto): FinanceTaxDeskQuarterDto | null {
    const asOf = wb.asOf;
    return (wb.quarters || []).find((q) => !asOf || q.dueDate >= asOf) ?? null;
  }

  quarterCounted(q: FinanceTaxDeskQuarterDto): number {
    return (q.withholdingCredit ?? 0) + (q.estimateCredit ?? 0);
  }

  quarterAction(q: FinanceTaxDeskQuarterDto): string {
    const asOf = this.page?.workbook?.asOf;
    const past = !!asOf && q.dueDate < asOf;
    if ((q.suggestedPayment ?? 0) > 0) {
      return past ? 'Was the catch-up slot' : 'Send this 1040-ES';
    }
    if (past && (q.shortfall ?? 0) > 0) {
      return 'Date passed · installment short';
    }
    if (past) {
      return 'Date passed · no extra check';
    }
    return 'No extra 1040-ES needed';
  }

  formatDay(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso.length === 10 ? `${iso}T12:00:00` : iso);
    if (Number.isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  private roundDollars(n: number | null | undefined): string {
    return Math.round(n ?? 0).toLocaleString('en-US');
  }

  deltaUp(n: number | null | undefined): boolean {
    return (n ?? 0) > 0;
  }

  isBuy(side: string | null | undefined): boolean {
    return this.normalizedSide(side).startsWith('buy');
  }

  isSell(side: string | null | undefined): boolean {
    return this.normalizedSide(side).startsWith('sell');
  }

  sideLabel(side: string | null | undefined): string {
    const s = this.normalizedSide(side);
    if (!s) {
      return 'Fill';
    }
    const labels: Record<string, string> = {
      buy: 'Buy',
      sell: 'Sell',
      buy_to_open: 'BTO',
      buy_to_close: 'BTC',
      sell_to_open: 'STO',
      sell_to_close: 'STC',
      buy_to_o: 'BTO',
      buy_to_c: 'BTC',
      sell_to_o: 'STO',
      sell_to_c: 'STC',
    };
    if (labels[s]) {
      return labels[s];
    }
    if (s.startsWith('buy_to_open') || s === 'bto') {
      return 'BTO';
    }
    if (s.startsWith('buy_to_close') || s === 'btc') {
      return 'BTC';
    }
    if (s.startsWith('sell_to_open') || s === 'sto') {
      return 'STO';
    }
    if (s.startsWith('sell_to_close') || s === 'stc') {
      return 'STC';
    }
    if (s.startsWith('buy')) {
      return 'Buy';
    }
    if (s.startsWith('sell')) {
      return 'Sell';
    }
    return (side ?? 'Fill').trim() || 'Fill';
  }

  cashSign(side: string | null | undefined): string {
    if (this.isSell(side)) {
      return '+';
    }
    if (this.isBuy(side)) {
      return '−';
    }
    return '';
  }

  tradeMeta(tr: FinanceTaxDeskTradeRowDto): string {
    const bits: string[] = [];
    if (tr.quantity != null) {
      const formatted = new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(tr.quantity);
      const option = / call | put /i.test(tr.symbol ?? '');
      const unit = option
        ? tr.quantity === 1
          ? 'contract'
          : 'contracts'
        : tr.quantity === 1
          ? 'share'
          : 'shares';
      bits.push(`${formatted} ${unit}`);
    }
    if (tr.averagePrice != null) {
      bits.push(
        `@ ${new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(tr.averagePrice)}`,
      );
    }
    if (tr.accountLabel) {
      bits.push(tr.accountLabel);
    }
    if (tr.executedAt) {
      const d = new Date(tr.executedAt);
      if (!Number.isNaN(d.getTime())) {
        bits.push(d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' }));
      }
    }
    return bits.join(' · ');
  }

  private normalizedSide(side: string | null | undefined): string {
    return (side ?? '').trim().toLowerCase().replace(/[\s-]+/g, '_');
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
