import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MarketsJourneyDto } from '../../../models/markets-journey.models';
import { MarketsJourneyApiService } from '../../../services/markets-journey-api.service';
import {
  buildRoadmapChartPoints,
  pickPrimaryJourney,
  roadmapActualSegments,
  roadmapMilestoneY,
  roadmapTargetPath,
} from '../../../util/markets-roadmap-chart.util';

export type RoadmapSummaryMode = 'hero' | 'strip' | 'teaser';

@Component({
  selector: 'app-markets-roadmap-summary',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './markets-roadmap-summary.component.html',
  styleUrl: './markets-roadmap-summary.component.scss',
})
export class MarketsRoadmapSummaryComponent implements OnInit {
  private readonly api = inject(MarketsJourneyApiService);

  /** hero = Overview; strip = Markets shell; teaser = Money Standing */
  readonly mode = input<RoadmapSummaryMode>('hero');

  readonly loading = signal(true);
  readonly journey = signal<MarketsJourneyDto | null>(null);
  readonly failed = signal(false);

  readonly chartPoints = computed(() => buildRoadmapChartPoints(this.journey()));
  readonly targetPath = computed(() => roadmapTargetPath(this.chartPoints()));
  readonly actualSegments = computed(() => roadmapActualSegments(this.chartPoints()));
  readonly milestoneY = computed(() => roadmapMilestoneY(this.journey(), this.chartPoints()));

  readonly progressPct = computed(() => {
    const p = this.journey()?.progressPct;
    return p == null ? null : Math.max(0, Math.min(100, Number(p)));
  });

  readonly dialCircumference = 2 * Math.PI * 42;
  readonly dialDash = computed(() => {
    const pct = (this.progressPct() ?? 0) / 100;
    return this.dialCircumference * pct;
  });

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (rows) => {
        this.journey.set(pickPrimaryJourney(rows));
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  segmentClass(dir: string): string {
    return `rms-seg rms-seg--${(dir || 'UNKNOWN').toLowerCase()}`;
  }
}
