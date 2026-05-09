import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MarketOverviewDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-market-overview-panel',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './market-overview-panel.component.html',
  styleUrl: './market-overview-panel.component.scss',
})
export class MarketOverviewPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly displayedColumns = ['name', 'price', 'day', 'mtd', 'ytd', 'link'];

  overview: MarketOverviewDto | null = null;
  loading = false;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.financeMarketOverview().subscribe({
      next: (o) => {
        this.overview = o;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(`Market overview failed: ${formatHttpErrorDetail(err)}`, 'Dismiss', {
          duration: 12_000,
        });
      },
    });
  }

  chgClass(v: number | null | undefined): string {
    if (v == null || Number.isNaN(v)) {
      return '';
    }
    if (v > 0.0001) {
      return 'chg-pos';
    }
    if (v < -0.0001) {
      return 'chg-neg';
    }
    return '';
  }
}
