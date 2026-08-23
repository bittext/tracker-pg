import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MarketsJourneyDto,
  MarketsJourneyEntryDto,
  MarketsJourneyEntryWriteRequest,
} from '../../../models/markets-journey.models';
import { MarketsJourneyApiService } from '../../../services/markets-journey-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  buildRoadmapChartPoints,
  roadmapActualSegments,
  roadmapMilestoneY,
  roadmapTargetPath,
} from '../../../util/markets-roadmap-chart.util';
import { MarketsRoadmapSlapPanelComponent } from '../markets-roadmap-slap-panel/markets-roadmap-slap-panel.component';

@Component({
  selector: 'app-markets-journey',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTabsModule,
    MatTooltipModule,
    CurrencyPipe,
    DecimalPipe,
    MarketsRoadmapSlapPanelComponent,
  ],
  templateUrl: './markets-journey.component.html',
  styleUrl: './markets-journey.component.scss',
})
export class MarketsJourneyComponent implements OnInit {
  private readonly api = inject(MarketsJourneyApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly journeys = signal<MarketsJourneyDto[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly journey = signal<MarketsJourneyDto | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);

  /** Draft for new / edit entry. */
  entryDraft: MarketsJourneyEntryWriteRequest = this.emptyEntryDraft();
  editingEntryId: number | null = null;

  /** Roadmap meta edit. */
  metaTitle = '';
  metaMilestone: number | null = 1_000_000;
  /** Runway station highlight (period entry id). */
  focusedEntryId: number | null = null;
  /** Daily Tracker close being viewed. */
  readonly viewedAsOf = signal<string | null>(null);

  readonly chartPoints = computed(() => buildRoadmapChartPoints(this.journey()));
  readonly targetPath = computed(() => roadmapTargetPath(this.chartPoints()));
  readonly actualSegments = computed(() => roadmapActualSegments(this.chartPoints()));
  readonly milestoneY = computed(() => roadmapMilestoneY(this.journey(), this.chartPoints()));

  readonly progressPct = computed(() => {
    const j = this.journey();
    const p = j?.liveNet?.progressPct ?? j?.progressPct;
    return p == null ? null : Math.max(0, Math.min(100, Number(p)));
  });

  readonly displayActual = computed(() => {
    const j = this.journey();
    if (j?.liveNet?.total != null) {
      return Number(j.liveNet.total);
    }
    return j?.latestActual != null ? Number(j.latestActual) : null;
  });

  readonly dayChange = computed(() => {
    const change = this.journey()?.liveNet?.dayChange;
    return change == null ? null : Number(change);
  });

  readonly dayChangePct = computed(() => {
    const change = this.journey()?.liveNet?.dayChangePct;
    return change == null ? null : Number(change);
  });

  accountSharePct(value: number | null | undefined): number {
    const milestone = Number(this.journey()?.milestoneAmount || 0);
    if (!milestone || value == null) {
      return 0;
    }
    return Math.max(0, (Number(value) / milestone) * 100);
  }

  sitBarPct(value: number | null | undefined): number {
    const accounts = this.journey()?.liveNet?.accounts ?? [];
    const max = Math.max(0, ...accounts.map((a) => Number(a.totalAccountValue) || 0));
    if (!max || value == null) {
      return 0;
    }
    return Math.max(0, Math.min(100, (Number(value) / max) * 100));
  }

  readonly leveredAccount = computed(() => {
    const accounts = this.journey()?.liveNet?.accounts ?? [];
    return accounts.find((a) => a.accountSuffix === '3370' && Number(a.cashBalance) < 0) ?? null;
  });

  readonly canPrevDay = computed(() => !!this.journey()?.liveNet?.priorAsOfDate);
  readonly canNextDay = computed(() => !!this.journey()?.liveNet?.nextAsOfDate);
  readonly viewingLatest = computed(() => {
    const live = this.journey()?.liveNet;
    if (!live) {
      return true;
    }
    const history = live.history ?? [];
    const last = history.length ? history[history.length - 1]?.date : null;
    return !last || live.asOfDate === last;
  });

  changeClass(value: number | null | undefined): string {
    if (value == null || value === 0) {
      return 'journey-dir--on';
    }
    return value > 0 ? 'journey-dir--above' : 'journey-dir--below';
  }

  readonly remaining = computed(() => {
    const j = this.journey();
    if (!j) {
      return null;
    }
    if (j.liveNet?.remaining != null) {
      return Number(j.liveNet.remaining);
    }
    const actual = this.displayActual();
    return actual == null ? null : Number(j.milestoneAmount) - actual;
  });

  readonly dialCircumference = 2 * Math.PI * 42;
  readonly dialDash = computed(() => {
    const pct = (this.progressPct() ?? 0) / 100;
    return this.dialCircumference * pct;
  });

  ngOnInit(): void {
    this.refreshList(true);
  }

  refreshList(selectFirst = false): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (rows) => {
        this.journeys.set(rows);
        this.loading.set(false);
        const prefer = this.selectedId() ?? (selectFirst ? rows[0]?.id ?? null : null);
        if (prefer != null && rows.some((r) => r.id === prefer)) {
          this.selectJourney(prefer);
        } else if (rows[0]) {
          this.selectJourney(rows[0].id);
        } else {
          this.journey.set(null);
        }
      },
      error: (e) => {
        this.loading.set(false);
        this.snackBar.open(`Could not load roadmaps — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  selectJourney(id: number, asOf?: string | null): void {
    const switching = this.selectedId() !== id;
    this.selectedId.set(id);
    if (switching) {
      this.viewedAsOf.set(null);
    }
    if (asOf !== undefined) {
      this.viewedAsOf.set(asOf);
    }
    this.loading.set(true);
    this.api.get(id, this.viewedAsOf()).subscribe({
      next: (j) => {
        this.journey.set(j);
        this.metaTitle = j.title;
        this.metaMilestone = j.milestoneAmount;
        this.viewedAsOf.set(j.liveNet?.asOfDate ?? this.viewedAsOf());
        this.loading.set(false);
        this.resetEntryDraft();
      },
      error: (e) => {
        this.loading.set(false);
        this.snackBar.open(`Could not load roadmap — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  saveMeta(): void {
    const id = this.selectedId();
    if (id == null) {
      return;
    }
    this.saving.set(true);
    this.api
      .update(id, { title: this.metaTitle, milestoneAmount: this.metaMilestone })
      .subscribe({
        next: (j) => {
          this.journey.set(j);
          this.saving.set(false);
          this.refreshList();
          this.snackBar.open('Roadmap updated', undefined, { duration: 2200 });
        },
        error: (e) => {
          this.saving.set(false);
          this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
        },
      });
  }

  createNextMillion(): void {
    const list = this.journeys();
    const n = list.length + 1;
    const title =
      n === 1
        ? 'Road to my first million'
        : n === 2
          ? 'Road to my second million'
          : `Road to my ${this.ordinal(n)} million`;
    const milestone = n * 1_000_000;
    this.saving.set(true);
    this.api.create({ title, milestoneAmount: milestone }).subscribe({
      next: (j) => {
        this.saving.set(false);
        this.snackBar.open(`Started “${j.title}”`, undefined, { duration: 2800 });
        this.selectedId.set(j.id);
        this.refreshList();
      },
      error: (e) => {
        this.saving.set(false);
        this.snackBar.open(`Create failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
      },
    });
  }

  editEntry(e: MarketsJourneyEntryDto): void {
    this.editingEntryId = e.id;
    this.entryDraft = {
      periodDate: e.periodDate,
      periodLabel: e.periodLabel,
      targetAmount: e.targetAmount,
      actualAmount: e.actualAmount,
      targetNote: e.targetNote,
      actualNote: e.actualNote,
    };
  }

  resetEntryDraft(): void {
    this.editingEntryId = null;
    this.entryDraft = this.emptyEntryDraft();
  }

  saveEntry(): void {
    const id = this.selectedId();
    if (id == null || !this.entryDraft.periodDate) {
      this.snackBar.open('Period date is required', undefined, { duration: 3000 });
      return;
    }
    this.saving.set(true);
    this.api.upsertEntry(id, { ...this.entryDraft }).subscribe({
      next: () => {
        this.saving.set(false);
        this.resetEntryDraft();
        this.selectJourney(id);
        this.refreshList();
        this.snackBar.open('Period saved', undefined, { duration: 2200 });
      },
      error: (e) => {
        this.saving.set(false);
        this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 });
      },
    });
  }

  deleteEntry(entryId: number): void {
    const id = this.selectedId();
    if (id == null || !confirm('Delete this period entry?')) {
      return;
    }
    this.api.deleteEntry(id, entryId).subscribe({
      next: () => {
        this.selectJourney(id);
        this.refreshList();
      },
      error: (e) =>
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 6000 }),
    });
  }

  directionClass(dir: string | null | undefined): string {
    switch (dir) {
      case 'ABOVE':
        return 'journey-dir--above';
      case 'BELOW':
        return 'journey-dir--below';
      case 'ON':
        return 'journey-dir--on';
      default:
        return 'journey-dir--unknown';
    }
  }

  segmentClass(dir: string): string {
    return `journey-seg journey-seg--${(dir || 'UNKNOWN').toLowerCase()}`;
  }

  goPrevDay(): void {
    const prior = this.journey()?.liveNet?.priorAsOfDate;
    const id = this.selectedId();
    if (id == null || !prior) {
      return;
    }
    this.selectJourney(id, prior);
  }

  goNextDay(): void {
    const next = this.journey()?.liveNet?.nextAsOfDate;
    const id = this.selectedId();
    if (id == null || !next) {
      return;
    }
    this.selectJourney(id, next);
  }

  goLatestDay(): void {
    const id = this.selectedId();
    if (id == null) {
      return;
    }
    this.selectJourney(id, null);
  }

  focusPeriod(entryId: number): void {
    this.focusedEntryId = entryId;
    queueMicrotask(() => {
      document.getElementById(`journey-period-${entryId}`)?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
      });
    });
  }

  runwayAhead(e: MarketsJourneyEntryDto): boolean {
    return e.direction === 'ABOVE' || e.direction === 'ON';
  }

  private emptyEntryDraft(): MarketsJourneyEntryWriteRequest {
    const now = new Date();
    const q = Math.floor(now.getMonth() / 3) + 1;
    const endMonth = q * 3;
    const end = new Date(now.getFullYear(), endMonth, 0);
    const iso = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}`;
    return {
      periodDate: iso,
      periodLabel: `Q${q} ${now.getFullYear()}`,
      targetAmount: null,
      actualAmount: null,
      targetNote: '',
      actualNote: '',
    };
  }

  private ordinal(n: number): string {
    const s = ['th', 'st', 'nd', 'rd'];
    const v = n % 100;
    return n + (s[(v - 20) % 10] || s[v] || s[0]!);
  }
}
