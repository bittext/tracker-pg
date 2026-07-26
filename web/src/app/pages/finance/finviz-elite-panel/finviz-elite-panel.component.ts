import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  FinvizElitePresetDto,
  FinvizEliteStatusDto,
  FinvizEliteTableDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-finviz-elite-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  templateUrl: './finviz-elite-panel.component.html',
  styleUrl: './finviz-elite-panel.component.scss',
})
export class FinvizElitePanelComponent implements OnInit, OnChanges {
  private static readonly DEFAULT_LIMIT = 80;

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** Optional symbol from query (`?t=`) to prefill Options. */
  @Input() initialSymbol = '';

  status: FinvizEliteStatusDto | null = null;
  statusLoading = false;
  presets: FinvizElitePresetDto[] = [];

  selectedPresetId = '';
  customUrl = '';
  optionsSymbol = '';
  groupsBy: 'sector' | 'industry' = 'sector';

  table: FinvizEliteTableDto | null = null;
  loading = false;
  selected = new Set<string>();

  ngOnInit(): void {
    this.applyInitialSymbol();
    this.loadStatus();
    this.loadPresets();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialSymbol'] && !changes['initialSymbol'].firstChange) {
      this.applyInitialSymbol();
    }
  }

  get ready(): boolean {
    return !!this.status?.enabled && !!this.status?.configured;
  }

  get signalPresets(): FinvizElitePresetDto[] {
    return this.presets.filter((p) => (p.category || '').toLowerCase() === 'signal');
  }

  loadStatus(): void {
    this.statusLoading = true;
    this.financeApi.finvizStatus().subscribe({
      next: (s) => {
        this.status = s;
        this.statusLoading = false;
      },
      error: (e) => {
        this.status = null;
        this.statusLoading = false;
        this.err('Could not load Finviz Elite status', e);
      },
    });
  }

  loadPresets(): void {
    this.financeApi.finvizPresets().subscribe({
      next: (list) => {
        this.presets = list ?? [];
        if (!this.selectedPresetId && this.presets.length > 0) {
          this.selectedPresetId = this.presets[0].id;
        }
      },
      error: (e) => this.err('Could not load Finviz presets', e),
    });
  }

  runSignal(preset: FinvizElitePresetDto, force = false): void {
    if (!this.ready) {
      return;
    }
    const name = (preset.signal || preset.id || '').trim();
    if (!name) {
      return;
    }
    this.loading = true;
    this.financeApi.finvizSignal(name, FinvizElitePanelComponent.DEFAULT_LIMIT, force).subscribe({
      next: (t) => this.applyTable(t),
      error: (e) => {
        this.loading = false;
        this.err(`Signal ${preset.label}`, e);
      },
    });
  }

  runPreset(force = false): void {
    if (!this.ready || !this.selectedPresetId) {
      return;
    }
    this.loading = true;
    this.financeApi
      .finvizScreener({
        preset: this.selectedPresetId,
        limit: FinvizElitePanelComponent.DEFAULT_LIMIT,
        force,
      })
      .subscribe({
        next: (t) => this.applyTable(t),
        error: (e) => {
          this.loading = false;
          this.err('Preset screener', e);
        },
      });
  }

  runCustomUrl(force = false): void {
    const url = this.customUrl.trim();
    if (!this.ready || !url) {
      this.snackBar.open('Paste an Elite screener URL first.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.loading = true;
    this.financeApi
      .finvizScreener({ url, limit: FinvizElitePanelComponent.DEFAULT_LIMIT, force })
      .subscribe({
        next: (t) => this.applyTable(t),
        error: (e) => {
          this.loading = false;
          this.err('Custom screener', e);
        },
      });
  }

  loadGroups(force = false): void {
    if (!this.ready) {
      return;
    }
    this.loading = true;
    this.financeApi.finvizGroups(this.groupsBy, FinvizElitePanelComponent.DEFAULT_LIMIT, force).subscribe({
      next: (t) => this.applyTable(t),
      error: (e) => {
        this.loading = false;
        this.err('Groups export', e);
      },
    });
  }

  loadNews(force = false): void {
    if (!this.ready) {
      return;
    }
    this.loading = true;
    this.financeApi.finvizNews(FinvizElitePanelComponent.DEFAULT_LIMIT, force).subscribe({
      next: (t) => this.applyTable(t),
      error: (e) => {
        this.loading = false;
        this.err('News export', e);
      },
    });
  }

  loadOptions(force = false): void {
    const t = this.optionsSymbol.trim().toUpperCase();
    if (!this.ready || !t) {
      this.snackBar.open('Enter a symbol for the options chain.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.loading = true;
    this.financeApi.finvizOptions(t, FinvizElitePanelComponent.DEFAULT_LIMIT, force).subscribe({
      next: (table) => this.applyTable(table),
      error: (e) => {
        this.loading = false;
        this.err('Options export', e);
      },
    });
  }

  loadPortfolio(force = false): void {
    if (!this.ready) {
      return;
    }
    this.loading = true;
    this.financeApi.finvizPortfolio(FinvizElitePanelComponent.DEFAULT_LIMIT, force).subscribe({
      next: (t) => this.applyTable(t),
      error: (e) => {
        this.loading = false;
        this.err('Portfolio export', e);
      },
    });
  }

  toggleRow(symbol: string, checked: boolean): void {
    const s = symbol.trim().toUpperCase();
    if (!s) {
      return;
    }
    if (checked) {
      this.selected.add(s);
    } else {
      this.selected.delete(s);
    }
  }

  isSelected(symbol: string): boolean {
    return this.selected.has(symbol.trim().toUpperCase());
  }

  selectAllVisible(): void {
    for (const row of this.table?.rows ?? []) {
      const s = this.rowSymbol(row);
      if (s) {
        this.selected.add(s);
      }
    }
  }

  clearSelection(): void {
    this.selected.clear();
  }

  addSelectedToWatch(): void {
    const symbols = [...this.selected];
    if (symbols.length === 0) {
      this.snackBar.open('Select one or more rows with a ticker first.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.financeApi.finvizWatch({ symbols, thesisTag: 'finviz-elite' }).subscribe({
      next: (r) => {
        this.snackBar.open(`Added/updated ${r.addedOrUpdated} on Your Watch.`, 'Dismiss', { duration: 4500 });
        this.clearSelection();
      },
      error: (e) => this.err('Add to Watch', e),
    });
  }

  importPortfolioToWatch(): void {
    if (!this.table?.rows?.length) {
      this.snackBar.open('Load the Elite portfolio first.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.selectAllVisible();
    this.addSelectedToWatch();
  }

  downloadCsv(): void {
    if (!this.table?.columns?.length) {
      return;
    }
    const cols = this.table.columns;
    const escape = (v: string) => {
      const s = v ?? '';
      if (/[",\n\r]/.test(s)) {
        return `"${s.replace(/"/g, '""')}"`;
      }
      return s;
    };
    const lines = [cols.map(escape).join(',')];
    for (const row of this.table.rows) {
      lines.push(cols.map((c) => escape(row[c] ?? '')).join(','));
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `finviz-elite-${Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  openOnFinviz(): void {
    const first = [...this.selected][0] || this.optionsSymbol.trim().toUpperCase();
    const url = first
      ? `https://finviz.com/quote.ashx?t=${encodeURIComponent(first)}`
      : 'https://elite.finviz.com/screener.ashx';
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  rowSymbol(row: Record<string, string>): string {
    const keys = ['Ticker', 'Symbol', 'ticker', 'symbol'];
    for (const k of keys) {
      if (row[k]) {
        return String(row[k]).trim().toUpperCase();
      }
    }
    for (const [k, v] of Object.entries(row)) {
      if (/ticker|symbol/i.test(k) && v) {
        return String(v).trim().toUpperCase();
      }
    }
    return '';
  }

  cell(row: Record<string, string>, col: string): string {
    return row[col] ?? '';
  }

  trackRow(index: number, row: Record<string, string>): string {
    return this.rowSymbol(row) || `r${index}`;
  }

  private applyInitialSymbol(): void {
    const s = (this.initialSymbol || '').trim().toUpperCase();
    if (s) {
      this.optionsSymbol = s;
    }
  }

  private applyTable(t: FinvizEliteTableDto): void {
    this.table = t;
    this.loading = false;
    this.selected.clear();
  }

  private err(prefix: string, e: unknown): void {
    this.snackBar.open(`${prefix}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 7000 });
  }
}
