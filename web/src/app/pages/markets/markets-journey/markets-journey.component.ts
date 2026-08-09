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
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MarketsJourneyDto,
  MarketsJourneyEntryDto,
  MarketsJourneyEntryWriteRequest,
} from '../../../models/markets-journey.models';
import { MarketsJourneyApiService } from '../../../services/markets-journey-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface ChartPoint {
  entry: MarketsJourneyEntryDto;
  x: number;
  targetY: number | null;
  actualY: number | null;
  label: string;
}

interface ActualSegment {
  d: string;
  direction: 'ABOVE' | 'ON' | 'BELOW' | 'UNKNOWN';
}

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
    MatTooltipModule,
    CurrencyPipe,
    DecimalPipe,
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

  /** Journey meta edit. */
  metaTitle = '';
  metaMilestone: number | null = 1_000_000;

  readonly chartPoints = computed<ChartPoint[]>(() => {
    const j = this.journey();
    if (!j?.entries?.length) {
      return [];
    }
    const entries = j.entries;
    const values: number[] = [0, Number(j.milestoneAmount) || 0];
    for (const e of entries) {
      if (e.targetAmount != null) {
        values.push(Number(e.targetAmount));
      }
      if (e.actualAmount != null) {
        values.push(Number(e.actualAmount));
      }
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const pad = 6;
    const n = entries.length;
    return entries.map((e, i) => {
      const x = n === 1 ? 50 : pad + (i * (100 - pad * 2)) / (n - 1);
      const toY = (v: number | null) =>
        v == null ? null : 100 - pad - ((Number(v) - min) / span) * (100 - pad * 2);
      return {
        entry: e,
        x,
        targetY: toY(e.targetAmount),
        actualY: toY(e.actualAmount),
        label: e.periodLabel || e.periodDate,
      };
    });
  });

  readonly targetPath = computed(() => {
    const pts = this.chartPoints().filter((p) => p.targetY != null);
    if (pts.length < 2) {
      return '';
    }
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${(p.targetY as number).toFixed(2)}`).join(' ');
  });

  readonly actualSegments = computed<ActualSegment[]>(() => {
    const pts = this.chartPoints().filter((p) => p.actualY != null);
    const segs: ActualSegment[] = [];
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1]!;
      const b = pts[i]!;
      segs.push({
        d: `M ${a.x.toFixed(2)} ${(a.actualY as number).toFixed(2)} L ${b.x.toFixed(2)} ${(b.actualY as number).toFixed(2)}`,
        direction: b.entry.direction,
      });
    }
    return segs;
  });

  readonly milestoneY = computed(() => {
    const j = this.journey();
    const pts = this.chartPoints();
    if (!j || !pts.length) {
      return null;
    }
    const values: number[] = [0, Number(j.milestoneAmount) || 0];
    for (const e of j.entries) {
      if (e.targetAmount != null) {
        values.push(Number(e.targetAmount));
      }
      if (e.actualAmount != null) {
        values.push(Number(e.actualAmount));
      }
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const pad = 6;
    const m = Number(j.milestoneAmount) || 0;
    return 100 - pad - ((m - min) / span) * (100 - pad * 2);
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
        this.snackBar.open(`Could not load journeys — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  selectJourney(id: number): void {
    this.selectedId.set(id);
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (j) => {
        this.journey.set(j);
        this.metaTitle = j.title;
        this.metaMilestone = j.milestoneAmount;
        this.loading.set(false);
        this.resetEntryDraft();
      },
      error: (e) => {
        this.loading.set(false);
        this.snackBar.open(`Could not load journey — ${formatHttpErrorDetail(e)}`, undefined, {
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
          this.snackBar.open('Journey updated', undefined, { duration: 2200 });
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
