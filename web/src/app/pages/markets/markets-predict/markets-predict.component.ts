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
  PredictYearRollup,
  rollupPredictCloses,
  sumPredictCloses,
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

  readonly columns = ['closedAt', 'label', 'quantity', 'price', 'invested', 'payback', 'realized'] as const;

  desk: RhPredictDeskDto | null = null;
  loading = false;
  refreshing = false;
  draftLabels: Record<number, string> = {};
  savingLabelIds = new Set<number>();
  selectedYear: number | null = null;
  monthsOpen = false;
  selectedMonthKey: string | null = null;
  selectedDayKey: string | null = null;

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
    this.monthsOpen = false;
    this.selectedMonthKey = null;
    this.selectedDayKey = null;
  }

  toggleMonths(): void {
    this.monthsOpen = !this.monthsOpen;
    if (!this.monthsOpen) {
      this.selectedMonthKey = null;
      this.selectedDayKey = null;
    }
  }

  toggleMonth(key: string): void {
    this.selectedMonthKey = this.selectedMonthKey === key ? null : key;
    this.selectedDayKey = null;
  }

  toggleDay(key: string): void {
    this.selectedDayKey = this.selectedDayKey === key ? null : key;
  }

  get closedTotals() {
    return sumPredictCloses(this.desk?.closes ?? []);
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

  get unnamedCount(): number {
    return (this.desk?.closes ?? []).filter((row) => !(row.label ?? '').trim()).length;
  }

  invested(row: RhPredictCloseDto): number {
    return predictInvested(row);
  }

  payback(row: RhPredictCloseDto): number {
    return predictPayback(row);
  }

  labelDirty(row: RhPredictCloseDto): boolean {
    return (this.draftLabels[row.id] ?? '').trim() !== (row.label ?? '').trim();
  }

  saveLabel(row: RhPredictCloseDto): void {
    const label = (this.draftLabels[row.id] ?? '').trim();
    if (!this.labelDirty(row) || this.savingLabelIds.has(row.id)) {
      return;
    }
    this.savingLabelIds.add(row.id);
    this.api.rename(row.id, label).subscribe({
      next: (updated) => {
        this.savingLabelIds.delete(row.id);
        if (!this.desk) {
          return;
        }
        this.desk = {
          ...this.desk,
          closes: this.desk.closes.map((item) => (item.id === updated.id ? updated : item)),
        };
        this.draftLabels[updated.id] = updated.label ?? '';
      },
      error: (err) => {
        this.savingLabelIds.delete(row.id);
        this.toastError(err);
      },
    });
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
    this.draftLabels = {};
    for (const row of desk.closes) {
      this.draftLabels[row.id] = row.label ?? '';
    }
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
  }
}
