import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import {
  DailyActivityPointDto,
  FeatureUsageDto,
  MemberUsageDto,
  SignInDailyPointDto,
  UsageSummaryDto,
} from '../../../models/admin-usage.models';
import { AdminUsageApiService } from '../../../services/admin-usage-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

interface FeatureBar {
  feature: string;
  totalCount: number;
  activeUsers: number;
  allTimeCount: number;
  /** Width percentage 0..100 for the inline bar. */
  pct: number;
  lastActivityAt: string | null;
}

interface ActivityLinePoint {
  day: string;
  count: number;
  /** SVG x in [0, 100]. */
  x: number;
  /** SVG y in [0, 100]; lower y = higher value (canvas axis). */
  y: number;
}

interface SignInDayBar {
  day: string;
  success: number;
  failed: number;
  mfaFailed: number;
  mfaRequired: number;
  logout: number;
  /** Stacked total used for scaling. */
  total: number;
}

/** Standalone panel rendered inside the Admin → Usage mat-tab. Lazy-loaded by the parent on tab change. */
@Component({
  selector: 'app-admin-usage-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './admin-usage-panel.component.html',
  styleUrl: './admin-usage-panel.component.scss',
})
export class AdminUsagePanelComponent implements OnInit {
  private readonly api = inject(AdminUsageApiService);

  /** Range in days for the windowed metrics. */
  readonly windowDays = signal<number>(30);
  readonly loading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly summary = signal<UsageSummaryDto | null>(null);
  readonly featureUsage = signal<FeatureUsageDto[]>([]);
  readonly activitySeries = signal<DailyActivityPointDto[]>([]);
  readonly signInSeries = signal<SignInDailyPointDto[]>([]);
  readonly members = signal<MemberUsageDto[]>([]);

  readonly memberColumns = ['user', 'role', 'last', 'signIns', 'items', 'top'];

  /** Sorted desc by `totalCount` for the popularity bar chart; bar width relative to the top item. */
  readonly featureBars = computed<FeatureBar[]>(() => {
    const items = [...this.featureUsage()].sort((a, b) => b.totalCount - a.totalCount);
    const max = items.reduce((m, f) => Math.max(m, f.totalCount), 0);
    return items.map((f) => ({
      feature: f.feature,
      totalCount: f.totalCount,
      activeUsers: f.activeUsers,
      allTimeCount: f.allTimeCount,
      pct: max > 0 ? Math.round((f.totalCount / max) * 100) : 0,
      lastActivityAt: f.lastActivityAt,
    }));
  });

  /**
   * Total daily activity rolled up across all features, padded to one point per day in the window so the line shows
   * gaps as zero rather than skipping days. Coordinates normalized to a 100x100 viewBox; the SVG scales responsively.
   */
  readonly activityLine = computed<ActivityLinePoint[]>(() => {
    const dayCounts = new Map<string, number>();
    for (const p of this.activitySeries()) {
      dayCounts.set(p.day, (dayCounts.get(p.day) ?? 0) + p.count);
    }
    const points = this.padDays(this.windowDays()).map((day) => ({
      day,
      count: dayCounts.get(day) ?? 0,
    }));
    const max = points.reduce((m, p) => Math.max(m, p.count), 0);
    const span = Math.max(points.length - 1, 1);
    return points.map((p, i) => ({
      day: p.day,
      count: p.count,
      x: (i / span) * 100,
      y: max === 0 ? 100 : 100 - (p.count / max) * 95,
    }));
  });

  /** Decides which day-axis labels to render so they don't overlap (target ~6 ticks). */
  readonly activityTickStep = computed<number>(() => {
    const n = this.activityLine().length;
    return n <= 1 ? 1 : Math.max(1, Math.ceil(n / 6));
  });

  /** SVG path string ("M x,y L x,y …") for the activity line. */
  readonly activityLinePath = computed<string>(() => {
    const pts = this.activityLine();
    if (pts.length === 0) {
      return '';
    }
    return pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');
  });

  /** Area-under-line path (closes back to the baseline at y=100). */
  readonly activityAreaPath = computed<string>(() => {
    const pts = this.activityLine();
    if (pts.length === 0) {
      return '';
    }
    const first = pts[0];
    const last = pts[pts.length - 1];
    const body = pts.map((p) => `L ${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');
    return `M ${first.x.toFixed(2)},100 ${body} L ${last.x.toFixed(2)},100 Z`;
  });

  /** One stacked bar per day, padded to the full window so the x-axis is regular. */
  readonly signInBars = computed<SignInDayBar[]>(() => {
    const byDay = new Map<string, SignInDayBar>();
    for (const day of this.padDays(this.windowDays())) {
      byDay.set(day, {
        day,
        success: 0,
        failed: 0,
        mfaFailed: 0,
        mfaRequired: 0,
        logout: 0,
        total: 0,
      });
    }
    for (const p of this.signInSeries()) {
      const bar = byDay.get(p.day);
      if (!bar) {
        continue;
      }
      switch (p.eventType) {
        case 'LOGIN_SUCCESS':
          bar.success += p.count;
          break;
        case 'LOGIN_FAILED':
          bar.failed += p.count;
          break;
        case 'MFA_FAILED':
          bar.mfaFailed += p.count;
          break;
        case 'MFA_REQUIRED':
          bar.mfaRequired += p.count;
          break;
        case 'LOGOUT':
          bar.logout += p.count;
          break;
      }
      bar.total = bar.success + bar.failed + bar.mfaFailed + bar.mfaRequired + bar.logout;
    }
    return [...byDay.values()];
  });

  readonly signInMaxTotal = computed<number>(() =>
    this.signInBars().reduce((m, b) => Math.max(m, b.total), 0),
  );

  /** Top-3 most-used features per member, comma-joined; empty when member has no activity. */
  topFeaturesFor(member: MemberUsageDto): string {
    const entries = Object.entries(member.perFeature ?? {})
      .filter(([, v]) => v > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([k, v]) => `${k} (${v})`);
    return entries.join(', ');
  }

  ngOnInit(): void {
    this.reload();
  }

  setWindow(days: number): void {
    if (days === this.windowDays()) {
      return;
    }
    this.windowDays.set(days);
    this.reload();
  }

  reload(): void {
    const d = this.windowDays();
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      summary: this.api.summary(),
      featureUsage: this.api.featureUsage(d),
      activity: this.api.activityTimeseries(d),
      signIns: this.api.signInsTimeseries(d),
      members: this.api.members(d),
    }).subscribe({
      next: (res) => {
        this.summary.set(res.summary);
        this.featureUsage.set(res.featureUsage ?? []);
        this.activitySeries.set(res.activity ?? []);
        this.signInSeries.set(res.signIns ?? []);
        this.members.set(res.members ?? []);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.errorMessage.set(`Could not load usage metrics: ${formatHttpErrorDetail(e)}`);
      },
    });
  }

  /** Return an array of ISO local dates (UTC) for the last {@code days} including today, oldest → newest. */
  private padDays(days: number): string[] {
    const out: string[] = [];
    const today = new Date();
    today.setUTCHours(0, 0, 0, 0);
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(today);
      d.setUTCDate(today.getUTCDate() - i);
      out.push(d.toISOString().slice(0, 10));
    }
    return out;
  }

  /** Short day label (e.g. "5/11") for axis ticks. Only every Nth tick is rendered in the template. */
  shortDay(day: string): string {
    const parts = day.split('-');
    if (parts.length < 3) {
      return day;
    }
    return `${Number(parts[1])}/${Number(parts[2])}`;
  }

  /** Format an ISO instant to a short relative label, or "—" when null. */
  shortInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    try {
      const d = new Date(iso);
      return d.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      });
    } catch {
      return iso;
    }
  }
}
