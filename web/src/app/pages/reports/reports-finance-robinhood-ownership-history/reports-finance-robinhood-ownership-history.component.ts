import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import {
  RobinhoodOwnershipAssetKind,
  RobinhoodOwnershipHistoryDto,
  RobinhoodOwnershipHistoryPointDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorMessage } from '../../../util/http-error';

@Component({
  selector: 'app-reports-finance-robinhood-ownership-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './reports-finance-robinhood-ownership-history.component.html',
  styleUrl: './reports-finance-robinhood-ownership-history.component.scss',
})
export class ReportsFinanceRobinhoodOwnershipHistoryComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Aligns with Daily Tracker monkey / Then-Now capital start. */
  readonly optionsHistoryStart = '2026-06-28';

  reportYear = new Date().getFullYear();
  assetKind: RobinhoodOwnershipAssetKind = 'equity';
  symbol = 'NBIS';
  /** Empty string = all contracts overview. */
  contractKey = '';
  accountSuffix = '';
  captureKind = 'SCHEDULED';
  loading = false;

  readonly report = signal<RobinhoodOwnershipHistoryDto | null>(null);

  readonly equityTableColumns: string[] = [
    'snapshotDate',
    'quantity',
    'own',
    'margin',
    'loan',
    'price',
    'marketValue',
    'costBasis',
  ];

  readonly optionTableColumns: string[] = [
    'snapshotDate',
    'quantity',
    'price',
    'marketValue',
    'costBasis',
  ];

  readonly captureKinds = [
    { value: 'SCHEDULED', label: 'Daily close (SCHEDULED)' },
    { value: 'INTRADAY', label: 'Hourly (INTRADAY)' },
    { value: 'MANUAL', label: 'Manual captures' },
  ] as const;

  readonly showingAllContracts = computed(() => {
    const r = this.report();
    return r?.assetKind === 'option' && !r.contractKey && (r.contractSeries?.length ?? 0) > 0;
  });

  readonly chartBars = computed(() => {
    const pts = this.report()?.points ?? [];
    if (!pts.length) {
      return [];
    }
    const max = Math.max(...pts.map((p) => Number(p.quantity) || 0), 0.0001);
    const unit = this.report()?.assetKind === 'option' ? 'contracts' : 'shares';
    return pts.map((p) => {
      const qty = Number(p.quantity) || 0;
      return {
        date: p.snapshotDate,
        qty,
        heightPct: Math.max(2, (qty / max) * 100),
        title: `${p.snapshotDate}: ${qty} ${unit}`,
      };
    });
  });

  readonly changeRows = computed(() => {
    const pts = this.report()?.points ?? [];
    const out: Array<{
      date: string;
      from: number;
      to: number;
      delta: number;
    }> = [];
    for (let i = 1; i < pts.length; i++) {
      const prev = Number(pts[i - 1].quantity) || 0;
      const cur = Number(pts[i].quantity) || 0;
      const delta = cur - prev;
      if (Math.abs(delta) < 0.0000005) {
        continue;
      }
      out.push({ date: pts[i].snapshotDate, from: prev, to: cur, delta });
    }
    return out;
  });

  readonly allContractDayRows = computed(() => {
    const series = this.report()?.contractSeries ?? [];
    const rows: Array<{
      date: string;
      label: string;
      contractKey: string;
      quantity: number;
      marketValue: number;
      costBasis: number | null;
      price: number | null;
      legacy: boolean;
    }> = [];
    for (const s of series) {
      for (const p of s.points) {
        rows.push({
          date: p.snapshotDate,
          label: s.contract.label,
          contractKey: s.contract.contractKey,
          quantity: Number(p.quantity) || 0,
          marketValue: Number(p.marketValue) || 0,
          costBasis: p.costBasis,
          price: p.currentUnitPrice,
          legacy: s.contract.legacyIdentity,
        });
      }
    }
    rows.sort((a, b) => (a.date === b.date ? a.label.localeCompare(b.label) : a.date < b.date ? 1 : -1));
    return rows;
  });

  ngOnInit(): void {
    this.load();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  onAssetKindChange(kind: RobinhoodOwnershipAssetKind): void {
    this.assetKind = kind;
    this.contractKey = '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.financeApi
      .robinhoodOwnershipHistory({
        year: this.reportYear,
        assetKind: this.assetKind,
        symbol: this.assetKind === 'equity' ? this.symbol : null,
        contractKey: this.assetKind === 'option' && this.contractKey ? this.contractKey : null,
        accountSuffix: this.accountSuffix || null,
        captureKind: this.captureKind,
      })
      .subscribe({
        next: (r) => {
          this.report.set(r);
          this.assetKind = r.assetKind;
          if (r.symbol) {
            this.symbol = r.symbol;
          }
          this.contractKey = r.contractKey ?? '';
          this.accountSuffix = r.accountSuffix;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          this.report.set(null);
          this.snackBar.open(formatHttpErrorMessage(err) || 'Failed to load ownership history', 'Dismiss', {
            duration: 6000,
          });
        },
      });
  }

  onSymbolChange(sym: string): void {
    this.symbol = sym;
    this.load();
  }

  onContractChange(key: string): void {
    this.contractKey = key;
    this.load();
  }

  selectContract(key: string): void {
    this.contractKey = key;
    this.load();
  }

  showAllContracts(): void {
    this.contractKey = '';
    this.load();
  }

  formatDay(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = iso.length >= 10 ? iso.slice(0, 10) : iso;
    return d;
  }

  trackPoint(_i: number, p: RobinhoodOwnershipHistoryPointDto): number {
    return p.snapshotId;
  }
}
