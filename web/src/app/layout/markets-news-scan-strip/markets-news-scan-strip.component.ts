import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FinanceNewsScanDto, FinanceNewsScanHitDto } from '../../models/finance.models';
import { FinanceApiService } from '../../services/finance-api.service';
import { MarketsNewsScanTickersDialogComponent } from './markets-news-scan-tickers-dialog.component';

@Component({
  selector: 'app-markets-news-scan-strip',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './markets-news-scan-strip.component.html',
  styleUrl: './markets-news-scan-strip.component.scss',
})
export class MarketsNewsScanStripComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly dialog = inject(MatDialog);

  scan: FinanceNewsScanDto | null = null;
  loading = false;
  loadError: string | null = null;

  ngOnInit(): void {
    this.load(false);
  }

  hitFor(symbol: string): FinanceNewsScanHitDto | undefined {
    return this.scan?.hits.find((h) => h.symbol === symbol);
  }

  load(force: boolean): void {
    this.loading = true;
    this.loadError = null;
    this.financeApi.financeNewsScan(force).subscribe({
      next: (scan) => {
        this.scan = scan;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'News unavailable';
      },
    });
  }

  editList(): void {
    this.dialog
      .open(MarketsNewsScanTickersDialogComponent, {
        width: '28rem',
        maxWidth: '94vw',
        data: { tickers: this.scan?.tickers ?? [] },
      })
      .afterClosed()
      .subscribe((updated: FinanceNewsScanDto | undefined) => {
        if (updated) {
          this.scan = updated;
          this.loadError = null;
        }
      });
  }
}
