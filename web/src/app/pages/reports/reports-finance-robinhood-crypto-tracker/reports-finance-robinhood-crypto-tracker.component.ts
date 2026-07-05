import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { RobinhoodRhCryptoTrackerReportDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-crypto-tracker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
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
  tracker: RobinhoodRhCryptoTrackerReportDto | null = null;

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

  isWaitingForMcp(): boolean {
    return this.tracker?.status === 'WAITING_FOR_MCP';
  }

  connectionStatusLabel(): string {
    if (!this.tracker?.agenticServiceConfigured) {
      return 'Sidecar not configured';
    }
    if (!this.tracker.agenticConnected) {
      return 'Not connected';
    }
    return 'Agentic connected';
  }

  connectionStatusClass(): string {
    if (!this.tracker?.agenticServiceConfigured) {
      return 'rh-crypto__status--muted';
    }
    return this.tracker.agenticConnected ? 'rh-crypto__status--ok' : 'rh-crypto__status--warn';
  }

  private normalizedReportMonths(): number[] | undefined {
    if (!this.reportMonths.length) {
      return undefined;
    }
    return [...this.reportMonths].sort((a, b) => a - b);
  }
}
