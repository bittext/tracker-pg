import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import {
  AdminPredictsActionResultDto,
  AdminPredictsConfigDto,
  AdminPredictsPerSourceStat,
  AdminPredictsStatsDto,
} from '../../../models/admin-predicts.models';
import { PredictsSourceHealthDto } from '../../../models/finance-predicts.models';
import { AdminPredictsApiService } from '../../../services/admin-predicts-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type ActionId = 'poll-stocktwits' | 'poll-reddit' | 'recompute-baselines' | 'purge-mentions' | 'auto-seed';

interface ActionDefinition {
  id: ActionId;
  label: string;
  description: string;
  icon: string;
  requiresEnabled?: boolean;
}

/**
 * Admin → Finance → Predicts panel. Read-only view of the active configuration, a small KPI grid of
 * ingestion volume, the per-source health rows (with full error messages for admins), and one button
 * per scheduled job so an admin can kick a poll / recompute / purge without waiting for the cron tick.
 */
@Component({
  selector: 'app-admin-predicts-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './admin-predicts-panel.component.html',
  styleUrl: './admin-predicts-panel.component.scss',
})
export class AdminPredictsPanelComponent implements OnInit {
  private readonly api = inject(AdminPredictsApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly config = signal<AdminPredictsConfigDto | null>(null);
  readonly stats = signal<AdminPredictsStatsDto | null>(null);
  readonly sources = signal<PredictsSourceHealthDto[]>([]);

  readonly loading = signal<boolean>(false);
  readonly busyAction = signal<ActionId | null>(null);
  readonly errorMessage = signal<string | null>(null);

  /** Display columns for the per-source mini table. */
  readonly perSourceColumns = ['source', 'mentions24h', 'mentionsTotal', 'uniqueSymbols24h', 'lastMentionAt'];

  /** Static action catalogue rendered as buttons. */
  readonly actions: ActionDefinition[] = [
    {
      id: 'poll-stocktwits',
      label: 'Poll StockTwits now',
      description: 'Trigger the StockTwits ingestion cycle for every tracked ticker.',
      icon: 'stream',
    },
    {
      id: 'poll-reddit',
      label: 'Poll Reddit now',
      description: 'Walk every configured subreddit and ingest new posts mentioning tracked tickers.',
      icon: 'forum',
      requiresEnabled: true,
    },
    {
      id: 'recompute-baselines',
      label: 'Recompute baselines',
      description: 'Re-fits per-(symbol, source, hour-of-week) mean/stddev from the configured window.',
      icon: 'analytics',
    },
    {
      id: 'purge-mentions',
      label: 'Purge old mentions',
      description: 'Deletes raw mentions older than the retention window. Buckets are kept indefinitely.',
      icon: 'auto_delete',
    },
    {
      id: 'auto-seed',
      label: 'Auto-seed from Robinhood',
      description: 'Adds tracked tickers for users based on their Robinhood holdings.',
      icon: 'add_circle',
    },
  ];

  /**
   * Per-source rows in the small table — comes from the stats endpoint joined against the configured
   * sources so disabled sources still appear with zeros.
   */
  readonly perSourceRows = computed<AdminPredictsPerSourceStat[]>(() => {
    const stats = this.stats();
    if (!stats) {
      return [];
    }
    const known = new Map<string, AdminPredictsPerSourceStat>();
    for (const row of stats.perSource) {
      known.set(row.source, row);
    }
    return ['stocktwits', 'reddit', 'x'].map(
      (source) =>
        known.get(source) ?? {
          source,
          mentionsTotal: 0,
          mentions24h: 0,
          uniqueSymbols24h: 0,
          lastMentionAt: null,
        },
    );
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      config: this.api.config(),
      stats: this.api.stats(),
      sources: this.api.sources(),
    }).subscribe({
      next: (res) => {
        this.config.set(res.config);
        this.stats.set(res.stats);
        this.sources.set(res.sources);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(formatHttpErrorDetail(err) ?? 'Failed to load Admin → Predicts data');
        this.loading.set(false);
      },
    });
  }

  runAction(action: ActionDefinition): void {
    if (this.busyAction()) {
      return;
    }
    this.busyAction.set(action.id);
    const obs$ = this.invokeAction(action.id);
    obs$.subscribe({
      next: (result) => {
        this.busyAction.set(null);
        this.handleActionResult(action, result);
      },
      error: (err) => {
        this.busyAction.set(null);
        this.snackBar.open(
          formatHttpErrorDetail(err) ?? `Action ${action.label} failed`,
          'Dismiss',
          { duration: 5000 },
        );
      },
    });
  }

  private invokeAction(id: ActionId) {
    switch (id) {
      case 'poll-stocktwits':
        return this.api.pollStocktwits();
      case 'poll-reddit':
        return this.api.pollReddit();
      case 'recompute-baselines':
        return this.api.recomputeBaselines();
      case 'purge-mentions':
        return this.api.purgeMentions();
      case 'auto-seed':
        return this.api.autoSeed();
    }
  }

  private handleActionResult(action: ActionDefinition, result: AdminPredictsActionResultDto): void {
    const variant = result.ok ? 'OK' : 'Error';
    this.snackBar.open(`${variant}: ${result.message}`, 'Dismiss', { duration: 5000 });
    // Refresh stats + sources after an action so the panel reflects the new counts.
    this.refresh();
  }

  /** Disable an action button when it requires the source to be enabled and it isn't. */
  isActionDisabled(action: ActionDefinition): boolean {
    if (this.busyAction()) {
      return true;
    }
    if (!action.requiresEnabled) {
      return false;
    }
    const cfg = this.config();
    if (!cfg) {
      return false;
    }
    if (action.id === 'poll-reddit') {
      return !cfg.reddit.enabled;
    }
    return false;
  }

  // ----------------------- presentation helpers -----------------------

  sourceColor(source: string): string {
    switch ((source ?? '').toLowerCase()) {
      case 'stocktwits':
        return '#0ea5e9';
      case 'reddit':
        return '#f97316';
      case 'x':
        return '#111827';
      default:
        return '#6b7280';
    }
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) {
      return '–';
    }
    if (Math.abs(value) >= 1000) {
      return `${(value / 1000).toFixed(1)}k`;
    }
    return value.toLocaleString();
  }

  formatTimestamp(value: string | null | undefined): string {
    if (!value) {
      return '–';
    }
    return new Date(value).toLocaleString();
  }
}
