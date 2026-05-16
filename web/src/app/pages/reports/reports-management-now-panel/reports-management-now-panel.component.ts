import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import type { ManagementNowCardType } from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { nowBoardFullCatalog, nowBoardReadLanes } from '../../management/management-now-panel/management-now-board.storage';
import {
  NOW_ROADMAP_META,
  NowRoadmapCard,
  type NowRoadmapCardType,
  type NowRoadmapCardTypeMeta,
  type NowRoadmapLane,
} from '../../management/management-now-panel/management-now-data';
import {
  mergeNowCardTypeMeta,
  orderedNowCardTypeSlugs,
  resolveNowCardTypeMeta,
} from '../../management/management-now-panel/management-now-type-meta';

export interface NowReportRow {
  readonly lane: NowRoadmapLane;
  readonly laneLabel: string;
  readonly card: NowRoadmapCard;
}

@Component({
  selector: 'app-reports-management-now-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  templateUrl: './reports-management-now-panel.component.html',
  styleUrl: './reports-management-now-panel.component.scss',
})
export class ReportsManagementNowPanelComponent implements OnInit {
  private readonly managementApi = inject(ManagementApiService);

  readonly meta = NOW_ROADMAP_META;

  typeFilter: 'all' | NowRoadmapCardType = 'all';

  private nowCardTypesFromApi: ManagementNowCardType[] = [];
  private typeMetaBySlug = mergeNowCardTypeMeta([]);

  ngOnInit(): void {
    this.managementApi.listNowCardTypes().subscribe({
      next: (rows) => {
        this.nowCardTypesFromApi = rows;
        this.typeMetaBySlug = mergeNowCardTypeMeta(rows);
      },
      error: () => {
        this.nowCardTypesFromApi = [];
        this.typeMetaBySlug = mergeNowCardTypeMeta([]);
      },
    });
  }

  typeMetaFor(slug: string): NowRoadmapCardTypeMeta {
    return resolveNowCardTypeMeta(this.typeMetaBySlug, slug);
  }

  typeSlugsForFilter(): string[] {
    return orderedNowCardTypeSlugs(this.nowCardTypesFromApi);
  }

  private laneLabel(lane: NowRoadmapLane): string {
    if (lane === 'planned') {
      return 'Planned';
    }
    if (lane === 'active') {
      return 'In progress';
    }
    return 'Completed';
  }

  private flattenLanes(): NowReportRow[] {
    const catalog = nowBoardFullCatalog();
    const lanes = nowBoardReadLanes();
    const out: NowReportRow[] = [];
    const push = (lane: NowRoadmapLane, ids: readonly string[]) => {
      const label = this.laneLabel(lane);
      for (const id of ids) {
        const card = catalog.get(id);
        if (card) {
          out.push({ lane, laneLabel: label, card });
        }
      }
    };
    push('planned', lanes.planned);
    push('active', lanes.active);
    push('done', lanes.done);
    return out;
  }

  filteredRows(): NowReportRow[] {
    const rows = this.flattenLanes();
    if (this.typeFilter === 'all') {
      return rows;
    }
    return rows.filter((r) => r.card.type === this.typeFilter);
  }
}
