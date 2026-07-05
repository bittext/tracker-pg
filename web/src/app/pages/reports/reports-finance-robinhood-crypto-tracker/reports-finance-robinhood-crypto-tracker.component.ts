import { CommonModule, CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
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
  RobinhoodCryptoTradingStatusDto,
  RobinhoodRhCryptoTrackerDayDto,
  RobinhoodRhCryptoTrackerReportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-crypto-tracker',
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
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-crypto-tracker.component.html',
  styleUrl: './reports-finance-robinhood-crypto-tracker.component.scss',
})
export class ReportsFinanceRobinhoodCryptoTrackerComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  reportMonths: number[] = [new Date().getMonth() + 1];
  loading = false;
  capturing = false;
  savingCredentials = false;
  connectExpanded = false;
  tracker: RobinhoodRhCryptoTrackerReportDto | null = null;
  cryptoStatus: RobinhoodCryptoTradingStatusDto | null = null;

  apiKey = '';
  privateKeyBase64 = '';

  readonly expandedDays = new Set<string>();

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
    this.load();
    this.loadCryptoStatus();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  load(): void {
    this.loading = true;
    const months = this.normalizedReportMonths();
    this.financeApi.robinhoodCryptoTracker(this.reportYear, months).subscribe({
      next: (t) => {
        this.tracker = t;
        this.loading = false;
      },
      error: (err) => {
        this.tracker = null;
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  loadCryptoStatus(): void {
    this.financeApi.robinhoodCryptoTradingStatus().subscribe({
      next: (s) => {
        this.cryptoStatus = s;
        if (!s.connected) {
          this.connectExpanded = true;
        }
      },
      error: () => {
        this.cryptoStatus = null;
      },
    });
  }

  onMonthsChange(): void {
    this.load();
  }

  selectAllMonths(): void {
    this.reportMonths = this.monthChoices.map((m) => m.value);
    this.load();
  }

  clearMonthSelection(): void {
    this.reportMonths = [];
    this.load();
  }

  monthsFilterLabel(): string {
    if (!this.reportMonths.length) {
      return 'All months';
    }
    if (this.reportMonths.length === 1) {
      const m = this.monthChoices.find((c) => c.value === this.reportMonths[0]);
      return m?.label ?? '1 month';
    }
    return `${this.reportMonths.length} months`;
  }

  toggleConnect(): void {
    this.connectExpanded = !this.connectExpanded;
  }

  saveCredentials(): void {
    const apiKey = this.apiKey.trim();
    const privateKeyBase64 = this.privateKeyBase64.trim();
    if (!apiKey || !privateKeyBase64) {
      this.snackBar.open('API key and private key are required.', 'Dismiss', { duration: 5000 });
      return;
    }
    this.savingCredentials = true;
    this.financeApi.robinhoodCryptoTradingSaveCredentials({ apiKey, privateKeyBase64 }).subscribe({
      next: (s) => {
        this.cryptoStatus = s;
        this.savingCredentials = false;
        this.apiKey = '';
        this.privateKeyBase64 = '';
        this.connectExpanded = false;
        this.snackBar.open('Crypto Trading API credentials saved.', 'Dismiss', { duration: 4000 });
        this.load();
      },
      error: (err) => {
        this.savingCredentials = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  disconnect(): void {
    this.financeApi.robinhoodCryptoTradingDisconnect().subscribe({
      next: () => {
        this.cryptoStatus = null;
        this.loadCryptoStatus();
        this.load();
        this.snackBar.open('Crypto Trading API disconnected.', 'Dismiss', { duration: 4000 });
      },
      error: (err) => {
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  captureNow(): void {
    this.capturing = true;
    this.financeApi.robinhoodCryptoTrackerCapture(true).subscribe({
      next: (r) => {
        this.capturing = false;
        this.snackBar.open(r.message, 'Dismiss', { duration: 6000 });
        this.loadCryptoStatus();
        this.load();
      },
      error: (err) => {
        this.capturing = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  isConnected(): boolean {
    return this.tracker?.cryptoConnected === true || this.cryptoStatus?.connected === true;
  }

  connectionStatusLabel(): string {
    if (!this.tracker?.sidecarConfigured && !this.cryptoStatus?.sidecarConfigured) {
      return 'Sidecar not configured';
    }
    if (!this.isConnected()) {
      return 'Crypto API not connected';
    }
    return 'Crypto API connected';
  }

  connectionStatusClass(): string {
    if (!this.tracker?.sidecarConfigured && !this.cryptoStatus?.sidecarConfigured) {
      return 'rh-crypto__status--muted';
    }
    return this.isConnected() ? 'rh-crypto__status--ok' : 'rh-crypto__status--warn';
  }

  dayKey(day: RobinhoodRhCryptoTrackerDayDto): string {
    return day.snapshotAt;
  }

  isDayExpanded(day: RobinhoodRhCryptoTrackerDayDto): boolean {
    return this.expandedDays.has(this.dayKey(day));
  }

  toggleDay(day: RobinhoodRhCryptoTrackerDayDto): void {
    const key = this.dayKey(day);
    if (this.expandedDays.has(key)) {
      this.expandedDays.delete(key);
    } else {
      this.expandedDays.add(key);
    }
  }

  captureKindLabel(kind: string): string {
    switch (kind) {
      case 'MANUAL':
        return 'manual';
      case 'SCHEDULED':
        return 'scheduled';
      default:
        return kind.toLowerCase();
    }
  }

  pnlClass(value: number): string {
    return value >= 0 ? 'rh-crypto__pnl--pos' : 'rh-crypto__pnl--neg';
  }

  private normalizedReportMonths(): number[] | undefined {
    if (!this.reportMonths.length) {
      return undefined;
    }
    return [...this.reportMonths].sort((a, b) => a - b);
  }
}
