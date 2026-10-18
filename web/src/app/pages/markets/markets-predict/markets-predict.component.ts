import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RhPredictCloseDto, RhPredictDeskDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  predictInvested,
  predictPayback,
  PredictPeriodTotals,
  PredictYearRollup,
  rollupPredictCloses,
} from './rh-predict-rollups';

@Component({
  selector: 'app-markets-predict',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './markets-predict.component.html',
  styleUrl: './markets-predict.component.scss',
})
export class MarketsPredictComponent implements OnInit {
  private readonly api = inject(RhPredictApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly columns = ['closedAt', 'quantity', 'price', 'invested', 'payback', 'realized'] as const;

  readonly periodGrains = [
    { id: 'year', label: 'Year' },
    { id: 'month', label: 'Month' },
    { id: 'day', label: 'Day' },
    { id: 'hour', label: 'Hour' },
  ] as const;

  desk: RhPredictDeskDto | null = null;
  loading = false;
  refreshing = false;
  selectedYear: number | null = null;
  /** Null means every month in the selected year. */
  selectedMonth: number | null = null;
  periodGrain: 'year' | 'month' | 'day' | 'hour' = 'year';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.load().subscribe({
      next: (desk) => {
        this.apply(desk);
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastError(err);
      },
    });
  }

  refresh(): void {
    this.refreshing = true;
    this.api.refresh().subscribe({
      next: (desk) => {
        this.apply(desk);
        this.refreshing = false;
        this.snackBar.open('Predict book updated from Robinhood.', 'Dismiss', { duration: 3000 });
      },
      error: (err) => {
        this.refreshing = false;
        this.toastError(err);
      },
    });
  }

  get years(): PredictYearRollup[] {
    return rollupPredictCloses(this.desk?.closes ?? []);
  }

  get activeYear(): PredictYearRollup | null {
    const years = this.years;
    if (!years.length) {
      return null;
    }
    const wanted = this.selectedYear ?? new Date().getFullYear();
    return years.find((year) => year.year === wanted) ?? years[0];
  }

  selectYear(year: number): void {
    this.selectedYear = Number(year);
    this.selectedMonth = null;
  }

  selectMonth(month: number): void {
    const value = Number(month);
    this.selectedMonth = Number.isFinite(value) && value >= 0 ? value : null;
  }

  get scopedMonths() {
    const year = this.activeYear;
    if (!year) {
      return [];
    }
    if (this.selectedMonth == null) {
      return year.months;
    }
    return year.months.filter((month) => month.month === this.selectedMonth);
  }

  get scopedTotals(): PredictPeriodTotals {
    const year = this.activeYear;
    if (!year) {
      return { closes: 0, invested: 0, payback: 0, returned: 0 };
    }
    if (this.selectedMonth == null) {
      return year;
    }
    return this.scopedMonths[0] ?? { closes: 0, invested: 0, payback: 0, returned: 0 };
  }

  get periodRows(): { key: string; label: string; level: string; totals: PredictPeriodTotals }[] {
    const year = this.activeYear;
    if (!year) {
      return [];
    }
    const months = this.scopedMonths;
    if (this.periodGrain === 'year' && this.selectedMonth == null) {
      return [{ key: String(year.year), label: String(year.year), level: 'year', totals: year }];
    }
    if (this.periodGrain === 'year' || this.periodGrain === 'month') {
      return months.map((month) => ({ key: month.key, label: month.label, level: 'month', totals: month }));
    }
    if (this.periodGrain === 'day') {
      return months.flatMap((month) =>
        month.days.map((day) => ({ key: day.key, label: day.label, level: 'day', totals: day })),
      );
    }
    return months.flatMap((month) =>
      month.days.flatMap((day) =>
        day.hours.map((hour) => ({
          key: hour.key,
          label: `${day.label}, ${hour.label}`,
          level: 'hour',
          totals: hour,
        })),
      ),
    );
  }

  get accounts(): string[] {
    const seen = new Set<string>();
    const labels: string[] = [];
    for (const row of this.desk?.closes ?? []) {
      const label = `${row.accountLabel} ••••${row.accountSuffix}`;
      if (seen.has(label)) {
        continue;
      }
      seen.add(label);
      labels.push(label);
    }
    return labels;
  }

  invested(row: RhPredictCloseDto): number {
    return predictInvested(row);
  }

  payback(row: RhPredictCloseDto): number {
    return predictPayback(row);
  }

  moneyClass(value: number | null | undefined): string {
    const n = value ?? 0;
    if (n > 0) {
      return 'gain';
    }
    if (n < 0) {
      return 'loss';
    }
    return '';
  }

  private apply(desk: RhPredictDeskDto): void {
    this.desk = desk;
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
  }
}
