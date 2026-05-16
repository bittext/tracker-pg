import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HttpErrorResponse } from '@angular/common/http';
import { AdminGithubApiService } from '../../../services/admin-github-api.service';
import { GithubFeatureHistoryDto, GithubFeatureHistoryEntryDto } from '../../../models/github-insights.models';
import { formatHttpErrorDetail } from '../../../util/http-error';

export interface FeatureTimelineMonthGroup {
  readonly monthKey: string;
  readonly monthLabel: string;
  readonly entries: GithubFeatureHistoryEntryDto[];
}

@Component({
  selector: 'app-admin-features-panel',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './admin-features-panel.component.html',
  styleUrl: './admin-features-panel.component.scss',
})
export class AdminFeaturesPanelComponent implements OnInit {
  private readonly githubApi = inject(AdminGithubApiService);

  history: GithubFeatureHistoryDto | null = null;
  loading = false;
  notConfigured = false;
  loadError: string | null = null;
  monthGroups: FeatureTimelineMonthGroup[] = [];

  ngOnInit(): void {
    this.load();
  }

  refresh(): void {
    this.history = null;
    this.monthGroups = [];
    this.notConfigured = false;
    this.loadError = null;
    this.load();
  }

  load(): void {
    if (this.loading) {
      return;
    }
    this.loading = true;
    this.loadError = null;
    this.githubApi.getFeatureHistory().subscribe({
      next: (d) => {
        this.loading = false;
        this.notConfigured = false;
        this.history = d;
        this.monthGroups = this.groupByMonth(d.entries);
      },
      error: (e) => {
        this.loading = false;
        this.history = null;
        this.monthGroups = [];
        if (this.isGithubUnavailable(e)) {
          this.notConfigured = true;
          this.loadError = null;
          return;
        }
        this.notConfigured = false;
        this.loadError = formatHttpErrorDetail(e);
      },
    });
  }

  authorLabel(row: GithubFeatureHistoryEntryDto): string {
    return row.authorLogin || row.authorName || '—';
  }

  private groupByMonth(entries: GithubFeatureHistoryEntryDto[]): FeatureTimelineMonthGroup[] {
    const map = new Map<string, GithubFeatureHistoryEntryDto[]>();
    for (const e of entries) {
      const key = this.monthKey(e.committedAt);
      if (!key) {
        continue;
      }
      const list = map.get(key) ?? [];
      list.push(e);
      map.set(key, list);
    }
    return [...map.entries()]
      .sort((a, b) => b[0].localeCompare(a[0]))
      .map(([monthKey, groupEntries]) => ({
        monthKey,
        monthLabel: this.monthLabel(monthKey),
        entries: groupEntries,
      }));
  }

  private monthKey(iso: string): string {
    if (!iso || iso.length < 7) {
      return '';
    }
    return iso.slice(0, 7);
  }

  private monthLabel(monthKey: string): string {
    const y = Number(monthKey.slice(0, 4));
    const m = Number(monthKey.slice(5, 7));
    if (!Number.isFinite(y) || !Number.isFinite(m)) {
      return monthKey;
    }
    return new Date(y, m - 1, 1).toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  private isGithubUnavailable(e: unknown): boolean {
    if (e instanceof HttpErrorResponse && e.status === 503) {
      return true;
    }
    const detail = formatHttpErrorDetail(e);
    return detail.includes('GitHub is disabled') || detail.includes('owner/repo is not set');
  }
}
