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
  flashLayerId: string | null = null;
  private flashTimer: ReturnType<typeof setTimeout> | null = null;

  /** Approximate image-map regions (percent of the reference PNG) → analysis layers. */
  readonly mapHotspots: {
    layerId: string;
    label: string;
    left: number;
    top: number;
    width: number;
    height: number;
  }[] = [
    { layerId: 'demand-hyperscalers', label: 'Hyperscalers', left: 1.5, top: 10, width: 24, height: 14 },
    { layerId: 'demand-neoclouds', label: 'Neoclouds', left: 1.5, top: 25, width: 24, height: 12 },
    { layerId: 'demand-ai-labs', label: 'AI Labs', left: 1.5, top: 38, width: 24, height: 12 },
    { layerId: 'layer-6-software', label: 'Software + Models', left: 28, top: 14, width: 44, height: 9 },
    { layerId: 'layer-5-networking', label: 'Networking + Optics', left: 28, top: 24, width: 44, height: 9 },
    { layerId: 'layer-4-memory', label: 'Memory + Storage', left: 28, top: 34, width: 44, height: 9 },
    { layerId: 'layer-3-servers', label: 'Servers + Compute', left: 28, top: 44, width: 44, height: 10 },
    { layerId: 'layer-2-cooling', label: 'Cooling', left: 28, top: 55, width: 44, height: 8 },
    { layerId: 'layer-1-power', label: 'Power + Plant', left: 28, top: 64, width: 44, height: 12 },
  ];

  readonly mapJumpLinks: { layerId: string; label: string; icon: string }[] = [
    { layerId: 'demand-hyperscalers', label: 'Hyperscalers', icon: 'cloud' },
    { layerId: 'demand-neoclouds', label: 'Neoclouds', icon: 'dns' },
    { layerId: 'demand-ai-labs', label: 'AI Labs', icon: 'science' },
    { layerId: 'layer-6-software', label: 'L6 Software', icon: 'terminal' },
    { layerId: 'layer-5-networking', label: 'L5 Networking', icon: 'hub' },
    { layerId: 'layer-4-memory', label: 'L4 Memory', icon: 'memory' },
    { layerId: 'layer-3-servers', label: 'L3 Compute', icon: 'developer_board' },
    { layerId: 'layer-2-cooling', label: 'L2 Cooling', icon: 'ac_unit' },
    { layerId: 'layer-1-power', label: 'L1 Power', icon: 'bolt' },
  ];

  ngOnInit(): void {
    this.refresh();
  }

  jumpToLayer(layerId: string): void {
    this.selectedLayerId = 'all';
    this.filterEconomics = 'all';
    this.publicOnly = false;
    this.coveredOnly = false;
    // Wait a tick so filtered-out layers reappear before scrolling.
    setTimeout(() => {
      const el = document.getElementById('atf-section-' + layerId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      this.flashLayerId = layerId;
      if (this.flashTimer) {
        clearTimeout(this.flashTimer);
      }
      this.flashTimer = setTimeout(() => {
        this.flashLayerId = null;
        this.flashTimer = null;
      }, 1600);
    }, 0);
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
