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
import { formatHttpErrorDetail, formatHttpErrorMessage } from '../../../util/http-error';

type FeedbackKind = 'ok' | 'error' | 'info';

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
  connectError: string | null = null;
  captureFeedback: { kind: FeedbackKind; message: string } | null = null;

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
      this.connectError = 'API key and private key are both required.';
      return;
    }
    this.connectError = null;
    this.savingCredentials = true;
    this.financeApi.robinhoodCryptoTradingSaveCredentials({ apiKey, privateKeyBase64 }).subscribe({
      next: (s) => {
        this.cryptoStatus = s;
        this.savingCredentials = false;
        this.apiKey = '';
        this.privateKeyBase64 = '';
        this.connectExpanded = false;
        this.showCaptureFeedback('ok', 'Crypto Trading API credentials saved.');
        this.load();
      },
      error: (err) => {
        this.savingCredentials = false;
        this.connectError = formatHttpErrorMessage(err);
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
    this.captureFeedback = null;
    this.financeApi.robinhoodCryptoTrackerCapture(true).subscribe({
      next: (r) => {
        this.capturing = false;
        this.showCaptureFeedback(r.ok ? 'ok' : 'error', r.message);
        this.loadCryptoStatus();
        this.load();
      },
      error: (err) => {
        this.capturing = false;
        this.showCaptureFeedback('error', formatHttpErrorMessage(err));
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

  formatDelta(value: number): string {
    const abs = Math.abs(value);
    const formatted = new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(abs);
    return value >= 0 ? `+${formatted}` : `−${formatted}`;
  }

  syncStatusClass(): string {
    const status = this.cryptoStatus?.lastSyncStatus?.toLowerCase() ?? '';
    if (status === 'ok') {
      return 'rh-crypto__sync rh-crypto__sync--ok';
    }
    if (status === 'error') {
      return 'rh-crypto__sync rh-crypto__sync--error';
    }
    return 'rh-crypto__sync rh-crypto__sync--muted';
  }

  syncStatusIcon(): string {
    const status = this.cryptoStatus?.lastSyncStatus?.toLowerCase() ?? '';
    if (status === 'ok') {
      return 'check_circle';
    }
    if (status === 'error') {
      return 'error_outline';
    }
    return 'sync';
  }

  bannerClass(kind: FeedbackKind): string {
    return `rh-crypto__banner rh-crypto__banner--${kind}`;
  }

  bannerIcon(kind: FeedbackKind): string {
    switch (kind) {
      case 'ok':
        return 'check_circle';
      case 'error':
        return 'error_outline';
      default:
        return 'info';
    }
  }

  dismissCaptureFeedback(): void {
    this.captureFeedback = null;
  }

  private showCaptureFeedback(kind: FeedbackKind, message: string): void {
    this.captureFeedback = { kind, message };
  }

  private normalizedReportMonths(): number[] | undefined {
    if (!this.reportMonths.length) {
      return undefined;
    }
    return [...this.reportMonths].sort((a, b) => a - b);
  }
}
