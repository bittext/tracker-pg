import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import {
  NOW_ROADMAP_ACTIVE,
  NOW_ROADMAP_DONE,
  NOW_ROADMAP_META,
  NOW_ROADMAP_PLANNED,
  NowRoadmapCard,
} from './management-now-data';

@Component({
  selector: 'app-management-now-panel',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  templateUrl: './management-now-panel.component.html',
  styleUrl: './management-now-panel.component.scss',
})
export class ManagementNowPanelComponent {
  readonly meta = NOW_ROADMAP_META;
  readonly planned: readonly NowRoadmapCard[] = NOW_ROADMAP_PLANNED;
  readonly active: readonly NowRoadmapCard[] = NOW_ROADMAP_ACTIVE;
  readonly done: readonly NowRoadmapCard[] = NOW_ROADMAP_DONE;
}
