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

  readonly columns = ['closedAt', 'account', 'quantity', 'price', 'realized', 'label'] as const;

  desk: RhPredictDeskDto | null = null;
  loading = false;
  refreshing = false;
  draftLabels: Record<number, string> = {};

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

  saveLabel(row: RhPredictCloseDto): void {
    const label = (this.draftLabels[row.id] ?? '').trim();
    if (label === (row.label ?? '').trim()) {
      return;
    }
    this.api.rename(row.id, label).subscribe({
      next: (updated) => {
        if (!this.desk) {
          return;
        }
        this.desk = {
          ...this.desk,
          closes: this.desk.closes.map((item) => (item.id === updated.id ? updated : item)),
        };
        this.draftLabels[updated.id] = updated.label ?? '';
      },
      error: (err) => this.toastError(err),
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
