import { CommonModule, CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodExecutedTradeDto,
  RobinhoodExecutedTradesDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface TradeDayGroup {
  key: string;
  label: string;
  trades: RobinhoodExecutedTradeDto[];
}

@Component({
  selector: 'app-reports-finance-robinhood-executed-trades',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    CurrencyPipe,
  ],
  templateUrl: './reports-finance-robinhood-executed-trades.component.html',
  styleUrl: './reports-finance-robinhood-executed-trades.component.scss',
})
export class ReportsFinanceRobinhoodExecutedTradesComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  sideFilter: 'all' | 'buy' | 'sell' = 'all';
  accountFilter = '';
  loading = false;
  data: RobinhoodExecutedTradesDto | null = null;

  yearChoices(): number[] {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi.robinhoodExecutedTrades(this.reportYear).subscribe({
      next: (res) => {
        this.data = res;
        if (this.accountFilter && !res.accounts.some((a) => a.accountSuffix === this.accountFilter)) {
          this.accountFilter = '';
        }
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(err) || 'Could not load executed trades', 'Dismiss', {
          duration: 7000,
        });
      },
    });
  }

  visibleTrades(): RobinhoodExecutedTradeDto[] {
    const trades = this.data?.trades ?? [];
    return trades.filter((t) => {
      if (this.sideFilter !== 'all' && (t.side ?? '').toLowerCase() !== this.sideFilter) {
        return false;
      }
      if (this.accountFilter && t.accountSuffix !== this.accountFilter) {
        return false;
      }
      return true;
    });
  }

  dayGroups(): TradeDayGroup[] {
    const groups = new Map<string, RobinhoodExecutedTradeDto[]>();
    for (const trade of this.visibleTrades()) {
      const key = this.dayKey(trade.executedAt);
      const list = groups.get(key) ?? [];
      list.push(trade);
      groups.set(key, list);
    }
    return [...groups.entries()].map(([key, trades]) => ({
      key,
      label: this.dayLabel(key),
      trades,
    }));
  }

  buyCount(): number {
    return this.visibleTrades().filter((t) => (t.side ?? '').toLowerCase() === 'buy').length;
  }

  sellCount(): number {
    return this.visibleTrades().filter((t) => (t.side ?? '').toLowerCase() === 'sell').length;
  }

  isBuy(side: string | null | undefined): boolean {
    return (side ?? '').toLowerCase() === 'buy';
  }

  isSell(side: string | null | undefined): boolean {
    return (side ?? '').toLowerCase() === 'sell';
  }

  sideLabel(side: string | null | undefined): string {
    if (!side) {
      return 'Trade';
    }
    return side.charAt(0).toUpperCase() + side.slice(1).toLowerCase();
  }

  qtyLabel(trade: RobinhoodExecutedTradeDto): string {
    const qty = trade.quantity;
    if (qty == null) {
      return '';
    }
    const formatted = new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(qty);
    const option = / call | put /i.test(trade.symbol ?? '');
    const unit = option ? (qty === 1 ? 'contract' : 'contracts') : qty === 1 ? 'share' : 'shares';
    return `${formatted} ${unit}`;
  }

  timeLabel(iso: string | null): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return '';
    }
    return d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  }

  private dayKey(iso: string | null): string {
    if (!iso) {
      return 'unknown';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return 'unknown';
    }
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private dayLabel(key: string): string {
    if (key === 'unknown') {
      return 'Unknown date';
    }
    const [year, month, day] = key.split('-').map((p) => Number(p));
    const date = new Date(year, month - 1, day);
    const today = new Date();
    const todayKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const yest = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    const yestKey = `${yest.getFullYear()}-${String(yest.getMonth() + 1).padStart(2, '0')}-${String(yest.getDate()).padStart(2, '0')}`;
    if (key === todayKey) {
      return 'Today';
    }
    if (key === yestKey) {
      return 'Yesterday';
    }
    return date.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  }
}
