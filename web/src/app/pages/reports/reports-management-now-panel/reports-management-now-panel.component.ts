import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { nowBoardReadLanes } from '../../management/management-now-panel/management-now-board.storage';
import {
  NOW_CARD_TYPE_META,
  NOW_ROADMAP_CARD_TYPES,
  NOW_ROADMAP_META,
  NowRoadmapCard,
  NowRoadmapCardType,
  NowRoadmapLane,
  nowRoadmapCardById,
} from '../../management/management-now-panel/management-now-data';

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
export class ReportsManagementNowPanelComponent {
  readonly meta = NOW_ROADMAP_META;
  readonly typeMeta = NOW_CARD_TYPE_META;
  readonly cardTypes = NOW_ROADMAP_CARD_TYPES;

  typeFilter: 'all' | NowRoadmapCardType = 'all';

  private readonly catalog = nowRoadmapCardById();

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
    const lanes = nowBoardReadLanes();
    const out: NowReportRow[] = [];
    const push = (lane: NowRoadmapLane, ids: readonly string[]) => {
      const label = this.laneLabel(lane);
      for (const id of ids) {
        const card = this.catalog.get(id);
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
