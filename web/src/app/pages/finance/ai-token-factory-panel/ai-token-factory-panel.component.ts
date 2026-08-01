import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  AiTokenFactoryCompanyDto,
  AiTokenFactoryDashboardDto,
  AiTokenFactoryLayerDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-ai-token-factory-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
  ],
  templateUrl: './ai-token-factory-panel.component.html',
  styleUrl: './ai-token-factory-panel.component.scss',
})
export class AiTokenFactoryPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  loading = false;
  data: AiTokenFactoryDashboardDto | null = null;
  filterEconomics: 'all' | 'profit_pool' | 'scarce' | 'demand' | 'commoditized' | 'private' = 'all';
  publicOnly = false;
  coveredOnly = false;
  selectedLayerId = 'all';
  selected = new Set<string>();

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.api.aiTokenFactoryDashboard().subscribe({
      next: (d) => {
        this.data = d;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(formatHttpErrorDetail(e) || 'AI Token Factory failed', 'Dismiss', {
          duration: 8000,
        });
      },
    });
  }

  visibleLayers(): AiTokenFactoryLayerDto[] {
    const layers = this.data?.layers ?? [];
    if (!this.selectedLayerId || this.selectedLayerId === 'all') {
      return layers;
    }
    return layers.filter((l) => l.id === this.selectedLayerId);
  }

  filteredCompanies(layer: AiTokenFactoryLayerDto): AiTokenFactoryCompanyDto[] {
    return (layer.companies ?? []).filter((c) => {
      if (this.publicOnly && !c.publicTicker) {
        return false;
      }
      if (this.coveredOnly && !c.coveredOnWatchImage) {
        return false;
      }
      if (this.filterEconomics === 'all') {
        return true;
      }
      if (this.filterEconomics === 'private') {
        return !c.publicTicker;
      }
      return (c.economicsNote || layer.economicsTag) === this.filterEconomics;
    });
  }

  toggle(symbol: string | null, checked: boolean): void {
    if (!symbol) {
      return;
    }
    if (checked) {
      this.selected.add(symbol);
    } else {
      this.selected.delete(symbol);
    }
  }

  isSelected(symbol: string | null): boolean {
    return !!symbol && this.selected.has(symbol);
  }

  selectLayerPublic(layer: AiTokenFactoryLayerDto): void {
    for (const c of this.filteredCompanies(layer)) {
      if (c.symbol) {
        this.selected.add(c.symbol);
      }
    }
  }

  clearSelection(): void {
    this.selected.clear();
  }

  addSelectedToWatch(): void {
    const symbols = [...this.selected];
    if (!symbols.length) {
      this.snackBar.open('Select one or more public tickers first.', 'Dismiss', { duration: 4000 });
      return;
    }
    this.api.aiTokenFactoryWatch({ symbols, thesisTag: 'ai-token-factory' }).subscribe({
      next: (r) => {
        this.snackBar.open(`Added/updated ${r.addedOrUpdated} on Your Watch.`, 'Dismiss', {
          duration: 4500,
        });
        this.clearSelection();
      },
      error: (e) =>
        this.snackBar.open(formatHttpErrorDetail(e) || 'Watch failed', 'Dismiss', { duration: 8000 }),
    });
  }

  chgClass(v: number | null | undefined): string {
    if (v == null || Number.isNaN(v)) {
      return '';
    }
    if (v > 0.05) {
      return 'chg-pos';
    }
    if (v < -0.05) {
      return 'chg-neg';
    }
    return '';
  }

  scoreClass(score: number | null | undefined): string {
    if (score == null) {
      return '';
    }
    if (score >= 72) {
      return 'score--strong';
    }
    if (score >= 55) {
      return 'score--ok';
    }
    if (score >= 40) {
      return 'score--neutral';
    }
    return 'score--soft';
  }

  economicsLabel(tag: string): string {
    switch (tag) {
      case 'profit_pool':
        return 'Profit pool';
      case 'scarce':
        return 'Scarce input';
      case 'commoditized':
        return 'Commoditized';
      case 'demand':
        return 'Demand';
      case 'private':
        return 'Private';
      default:
        return tag;
    }
  }

  formatPct(v: number | null | undefined): string {
    if (v == null || Number.isNaN(v)) {
      return '—';
    }
    const sign = v > 0 ? '+' : '';
    return `${sign}${v.toFixed(2)}%`;
  }

  formatUsd(v: number | null | undefined): string {
    if (v == null || Number.isNaN(v)) {
      return '—';
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: v >= 100 ? 2 : 4,
    }).format(v);
  }
}
