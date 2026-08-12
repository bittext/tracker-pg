import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  MarketsRoadmapSlapCashNoteDto,
  MarketsRoadmapSlapPointsDto,
} from '../../../models/markets-journey.models';
import { RobinhoodCashIoRequestDto } from '../../../models/finance.models';
import { MarketsJourneyApiService } from '../../../services/markets-journey-api.service';
import { RobinhoodCashIoApiService } from '../../../services/robinhood-cash-io-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface PlotPt {
  x: number;
  y: number;
  date: string;
  value: number;
}

interface SlapMarker {
  x: number;
  y: number;
  threshold: number;
  crossedOn: string;
  totalOnDay: number;
  label: string;
}

interface CashMarker {
  id: number;
  x: number;
  y: number;
  direction: string;
  amount: number;
  note: string | null;
  activityDate: string;
  title: string;
}

@Component({
  selector: 'app-markets-roadmap-slap-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './markets-roadmap-slap-panel.component.html',
  styleUrl: './markets-roadmap-slap-panel.component.scss',
})
export class MarketsRoadmapSlapPanelComponent implements OnInit {
  private readonly api = inject(MarketsJourneyApiService);
  private readonly cashApi = inject(RobinhoodCashIoApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly data = signal<MarketsRoadmapSlapPointsDto | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);

  readonly crossingCols = ['threshold', 'crossedOn', 'totalOnDay', 'prior'] as const;
  readonly cashCols = ['date', 'direction', 'amount', 'note', 'actions'] as const;

  cashForm = {
    activityDate: this.isoToday(),
    direction: 'IN' as 'IN' | 'OUT',
    amount: null as number | null,
    note: '',
  };

  readonly yMin = computed(() => {
    const d = this.data();
    if (!d?.series.length) {
      return 0;
    }
    const vals = d.series.map((s) => s.totalAccountValue);
    const minV = Math.min(...vals, ...(d.guideLevels.length ? [d.guideLevels[0]] : []));
    return Math.max(0, minV * 0.92);
  });

  readonly yMax = computed(() => {
    const d = this.data();
    if (!d?.series.length) {
      return 100_000;
    }
    const vals = d.series.map((s) => s.totalAccountValue);
    const maxV = Math.max(...vals, ...(d.guideLevels.length ? d.guideLevels : [0]));
    return maxV * 1.06;
  });

  readonly plotPoints = computed((): PlotPt[] => {
    const d = this.data();
    if (!d?.series.length) {
      return [];
    }
    const n = d.series.length;
    const y0 = this.yMin();
    const y1 = this.yMax();
    const span = Math.max(y1 - y0, 1);
    return d.series.map((s, i) => ({
      x: n === 1 ? 50 : (i / (n - 1)) * 100,
      y: 100 - ((s.totalAccountValue - y0) / span) * 100,
      date: s.date,
      value: s.totalAccountValue,
    }));
  });

  readonly equityPath = computed(() => {
    const pts = this.plotPoints();
    if (!pts.length) {
      return '';
    }
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ');
  });

  readonly guideLines = computed(() => {
    const d = this.data();
    if (!d) {
      return [] as { y: number; label: string; value: number }[];
    }
    const y0 = this.yMin();
    const y1 = this.yMax();
    const span = Math.max(y1 - y0, 1);
    return d.guideLevels.map((g) => ({
      value: g,
      label: this.shortMoney(g),
      y: 100 - ((g - y0) / span) * 100,
    }));
  });

  readonly slapMarkers = computed((): SlapMarker[] => {
    const d = this.data();
    const pts = this.plotPoints();
    if (!d || !pts.length) {
      return [];
    }
    const byDate = new Map(pts.map((p) => [p.date, p]));
    const y0 = this.yMin();
    const y1 = this.yMax();
    const span = Math.max(y1 - y0, 1);
    return d.crossings.map((c) => {
      const pt = byDate.get(c.crossedOn);
      const x = pt?.x ?? 0;
      const y = 100 - ((c.threshold - y0) / span) * 100;
      return {
        x,
        y,
        threshold: c.threshold,
        crossedOn: c.crossedOn,
        totalOnDay: c.totalOnDay,
        label: `Crossed ${this.shortMoney(c.threshold)} · ${c.crossedOn} · equity ${this.shortMoney(c.totalOnDay)}`,
      };
    });
  });

  readonly cashMarkers = computed((): CashMarker[] => {
    const d = this.data();
    const pts = this.plotPoints();
    if (!d || !pts.length) {
      return [];
    }
    const dates = pts.map((p) => p.date);
    const first = dates[0];
    const last = dates[dates.length - 1];
    return d.cashNotes.map((n) => {
      const x = this.xForDate(n.activityDate, first, last, pts);
      const nearest = this.nearestPoint(n.activityDate, pts);
      const y = Math.min(96, (nearest?.y ?? 50) + (n.direction === 'OUT' ? 6 : -6));
      return {
        id: n.id,
        x,
        y,
        direction: n.direction,
        amount: n.amount,
        note: n.note,
        activityDate: n.activityDate,
        title: `${n.direction === 'OUT' ? 'Out' : 'In'} $${n.amount.toFixed(2)} · ${n.activityDate}${
          n.note ? ' — ' + n.note : ''
        }`,
      };
    });
  });

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.api.slapPoints('3370', 50_000).subscribe({
      next: (dto) => {
        this.data.set(dto);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.snackBar.open(`Could not load slap points — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  saveCashNote(): void {
    const d = this.data();
    const amount = Number(this.cashForm.amount);
    if (!d) {
      return;
    }
    if (!this.cashForm.activityDate || !(amount > 0)) {
      this.snackBar.open('Date and amount are required.', undefined, { duration: 3500 });
      return;
    }
    const body: RobinhoodCashIoRequestDto = {
      accountSuffix: d.accountSuffix,
      activityDate: this.cashForm.activityDate,
      direction: this.cashForm.direction,
      amount,
      note: this.cashForm.note.trim() || null,
    };
    this.saving.set(true);
    this.cashApi.create(body).subscribe({
      next: () => {
        this.saving.set(false);
        this.cashForm = {
          activityDate: this.isoToday(),
          direction: 'IN',
          amount: null,
          note: '',
        };
        this.refresh();
        this.snackBar.open('Cash note saved.', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.saving.set(false);
        this.snackBar.open(formatHttpErrorDetail(e) || 'Save failed', undefined, { duration: 5000 });
      },
    });
  }

  deleteCashNote(note: MarketsRoadmapSlapCashNoteDto): void {
    if (!confirm(`Delete ${note.direction} $${note.amount} on ${note.activityDate}?`)) {
      return;
    }
    this.cashApi.delete(note.id).subscribe({
      next: () => this.refresh(),
      error: (e) =>
        this.snackBar.open(formatHttpErrorDetail(e) || 'Delete failed', undefined, { duration: 5000 }),
    });
  }

  shortMoney(n: number): string {
    if (n >= 1_000_000) {
      return `$${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`;
    }
    if (n >= 1000) {
      return `$${(n / 1000).toFixed(n % 1000 === 0 ? 0 : 0)}k`;
    }
    return `$${n.toFixed(0)}`;
  }

  private xForDate(date: string, first: string, last: string, pts: PlotPt[]): number {
    const exact = pts.find((p) => p.date === date);
    if (exact) {
      return exact.x;
    }
    const t0 = Date.parse(first);
    const t1 = Date.parse(last);
    const t = Date.parse(date);
    if (!Number.isFinite(t0) || !Number.isFinite(t1) || t1 <= t0) {
      return 50;
    }
    const clamped = Math.min(t1, Math.max(t0, t));
    return ((clamped - t0) / (t1 - t0)) * 100;
  }

  private nearestPoint(date: string, pts: PlotPt[]): PlotPt | null {
    if (!pts.length) {
      return null;
    }
    const t = Date.parse(date);
    let best = pts[0];
    let bestDist = Math.abs(Date.parse(best.date) - t);
    for (const p of pts) {
      const d = Math.abs(Date.parse(p.date) - t);
      if (d < bestDist) {
        best = p;
        bestDist = d;
      }
    }
    return best;
  }

  private isoToday(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }
}
