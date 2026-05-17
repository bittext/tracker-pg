import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import {
  RobinhoodNotebookConfigDto,
  RobinhoodPerformanceReportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface ChartPoint {
  label: string;
  value: number;
  x: number;
  y: number;
}

interface DailyBar {
  day: string;
  pnl: number;
  heightPct: number;
  positive: boolean;
}

@Component({
  selector: 'app-reports-finance-robinhood',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatTableModule,
  ],
  templateUrl: './reports-finance-robinhood.component.html',
  styleUrl: './reports-finance-robinhood.component.scss',
})
export class ReportsFinanceRobinhoodComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sanitizer = inject(DomSanitizer);

  reportYear = new Date().getFullYear();
  filterSymbol = '';
  symbols: string[] = [];

  readonly loading = signal(false);
  readonly report = signal<RobinhoodPerformanceReportDto | null>(null);
  readonly notebookConfig = signal<RobinhoodNotebookConfigDto | null>(null);
  readonly notebookRendering = signal(false);
  readonly notebookHtml = signal<SafeHtml | null>(null);
  readonly notebookRenderNote = signal('');

  readonly dailyColumns = ['date', 'realizedPnL', 'closedLots'];
  readonly bestStockColumns = ['instrument', 'totalRealizedPnL', 'closedLots', 'winCount', 'lossCount'];
  readonly worstTradeColumns = ['instrument', 'contract', 'strategy', 'sellDate', 'holdDays', 'realizedPnL'];
  readonly strategyColumns = ['strategy', 'totalRealizedPnL', 'closedLots', 'winRate'];
  readonly quarterlyColumns = ['quarterLabel', 'realizedGain', 'estimatedTax'];

  readonly dailyBars = computed<DailyBar[]>(() => {
    const r = this.report();
    if (!r?.dailyPnL.length) {
      return [];
    }
    const max = r.dailyPnL.reduce((m, d) => Math.max(m, Math.abs(Number(d.realizedPnL) || 0)), 0);
    const scale = max > 0 ? max : 1;
    return r.dailyPnL.map((d) => {
      const pnl = Number(d.realizedPnL) || 0;
      return {
        day: d.date,
        pnl,
        heightPct: Math.max(4, (Math.abs(pnl) / scale) * 100),
        positive: pnl >= 0,
      };
    });
  });

  readonly equityLine = computed<ChartPoint[]>(() => {
    const curve = this.report()?.equityCurve ?? [];
    if (!curve.length) {
      return [];
    }
    const values = curve.map((p) => Number(p.cumulativePnL) || 0);
    const min = Math.min(...values, 0);
    const max = Math.max(...values, 0);
    const span = Math.max(max - min, 1);
    const n = curve.length;
    const idxSpan = Math.max(n - 1, 1);
    return curve.map((p, i) => {
      const v = Number(p.cumulativePnL) || 0;
      return {
        label: p.date,
        value: v,
        x: (i / idxSpan) * 100,
        y: 100 - ((v - min) / span) * 92,
      };
    });
  });

  readonly equityLinePath = computed(() => {
    const pts = this.equityLine();
    if (pts.length < 2) {
      return pts.length === 1 ? `M ${pts[0].x} ${pts[0].y} L ${pts[0].x} ${pts[0].y}` : '';
    }
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
  });

  readonly monthlyBars = computed<DailyBar[]>(() => {
    const r = this.report();
    if (!r?.monthlyPnL.length) {
      return [];
    }
    const max = r.monthlyPnL.reduce((m, d) => Math.max(m, Math.abs(Number(d.realizedPnL) || 0)), 0);
    const scale = max > 0 ? max : 1;
    return r.monthlyPnL.map((d) => {
      const pnl = Number(d.realizedPnL) || 0;
      return {
        day: d.monthLabel,
        pnl,
        heightPct: Math.max(6, (Math.abs(pnl) / scale) * 100),
        positive: pnl >= 0,
      };
    });
  });

  readonly quarterlyBars = computed<DailyBar[]>(() => {
    const q = this.report()?.tax?.quarterlyGains ?? [];
    if (!q.length) {
      return [];
    }
    const max = q.reduce((m, d) => Math.max(m, Math.abs(Number(d.realizedGain) || 0)), 0);
    const scale = max > 0 ? max : 1;
    return q.map((d) => {
      const pnl = Number(d.realizedGain) || 0;
      return {
        day: d.quarterLabel,
        pnl,
        heightPct: Math.max(8, (Math.abs(pnl) / scale) * 100),
        positive: pnl >= 0,
      };
    });
  });

  readonly winLossTotal = computed(() => {
    const s = this.report()?.summary;
    if (!s) {
      return 0;
    }
    return s.winCount + s.lossCount + s.breakevenCount;
  });

  ngOnInit(): void {
    this.financeApi.robinhoodNotebookConfig().subscribe({
      next: (cfg) => this.notebookConfig.set(cfg),
      error: () => this.notebookConfig.set(null),
    });
    this.financeApi.robinhoodStockSymbols().subscribe({
      next: (rows) => {
        this.symbols = rows ?? [];
      },
      error: () => {
        this.symbols = [];
      },
    });
    this.loadReport();
  }

  loadReport(): void {
    this.loading.set(true);
    this.notebookHtml.set(null);
    this.notebookRenderNote.set('');
    const sym = this.filterSymbol.trim() || undefined;
    this.financeApi.robinhoodPerformanceReport(this.reportYear, sym).subscribe({
      next: (dto) => {
        this.report.set(dto);
        this.loading.set(false);
      },
      error: (e) => {
        this.report.set(null);
        this.loading.set(false);
        this.snackBar.open(`Could not load Robinhood report — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  downloadNotebookBundle(): void {
    const sym = this.filterSymbol.trim() || undefined;
    this.financeApi.robinhoodNotebookBundle(this.reportYear, sym).subscribe({
      next: (dto) => {
        const blob = new Blob([JSON.stringify(dto, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `robinhood-bundle-${this.reportYear}${sym ? `-${sym}` : ''}.json`;
        a.click();
        URL.revokeObjectURL(url);
        this.snackBar.open('Notebook bundle downloaded', undefined, { duration: 3000 });
      },
      error: (e) => {
        this.snackBar.open(`Bundle download failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
      },
    });
  }

  renderNotebook(): void {
    const cfg = this.notebookConfig();
    if (!cfg?.notebookServiceConfigured) {
      this.snackBar.open('Notebook service is not enabled on the server', undefined, { duration: 5000 });
      return;
    }
    this.notebookRendering.set(true);
    this.notebookHtml.set(null);
    const sym = this.filterSymbol.trim() || undefined;
    this.financeApi.robinhoodNotebookRender(this.reportYear, sym).subscribe({
      next: (dto) => {
        this.notebookRendering.set(false);
        this.notebookRenderNote.set(dto.note || '');
        if (dto.html?.trim()) {
          this.notebookHtml.set(this.sanitizer.bypassSecurityTrustHtml(dto.html));
        } else {
          this.snackBar.open(dto.note || 'No HTML returned from notebook service', undefined, { duration: 6000 });
        }
      },
      error: (e) => {
        this.notebookRendering.set(false);
        this.snackBar.open(`Notebook render failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
      },
    });
  }

  openJupyterLab(): void {
    const url = this.notebookConfig()?.jupyterLabUrl?.trim();
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return Array.from({ length: 8 }, (_, i) => y - i);
  }

  formatMoney(n: number | null | undefined): string {
    const v = Number(n);
    if (!Number.isFinite(v)) {
      return '—';
    }
    const sign = v < 0 ? '-' : '';
    return `${sign}$${Math.abs(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  formatHoldDays(days: number | null | undefined): string {
    const d = Number(days);
    if (!Number.isFinite(d)) {
      return '—';
    }
    if (d === 0) {
      return '0d';
    }
    if (d === 1) {
      return '1 day';
    }
    return `${Math.round(d)} days`;
  }

  formatPct(rate: number | null | undefined): string {
    const v = Number(rate);
    if (!Number.isFinite(v)) {
      return '—';
    }
    return `${(v * 100).toFixed(1)}%`;
  }

  formatDayLabel(iso: string): string {
    const d = new Date(iso + 'T12:00:00');
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  equityTickIndices(): number[] {
    const n = this.equityLine().length;
    if (n <= 1) {
      return n === 1 ? [0] : [];
    }
    const step = Math.max(1, Math.floor(n / 6));
    const out: number[] = [];
    for (let i = 0; i < n; i += step) {
      out.push(i);
    }
    if (out[out.length - 1] !== n - 1) {
      out.push(n - 1);
    }
    return out;
  }
}
