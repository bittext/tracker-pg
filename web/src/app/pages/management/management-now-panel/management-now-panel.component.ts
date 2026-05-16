import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { nowBoardReadLanes, nowBoardWriteLanes } from './management-now-board.storage';
import {
  NOW_CARD_TYPE_META,
  NOW_ROADMAP_CARD_TYPES,
  NOW_ROADMAP_META,
  NowRoadmapCard,
  NowRoadmapCardType,
  nowRoadmapCardById,
} from './management-now-data';

@Component({
  selector: 'app-management-now-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  templateUrl: './management-now-panel.component.html',
  styleUrl: './management-now-panel.component.scss',
})
export class ManagementNowPanelComponent implements OnInit {
  readonly meta = NOW_ROADMAP_META;
  readonly typeMeta = NOW_CARD_TYPE_META;
  readonly cardTypes = NOW_ROADMAP_CARD_TYPES;

  /** When not `all`, lane lists are filtered for display only; drag is disabled. */
  typeFilter: 'all' | NowRoadmapCardType = 'all';

  plannedIds: string[] = [];
  activeIds: string[] = [];
  doneIds: string[] = [];

  private readonly catalog = nowRoadmapCardById();

  ngOnInit(): void {
    this.reloadFromStorage();
  }

  reloadFromStorage(): void {
    const s = nowBoardReadLanes();
    this.plannedIds = [...s.planned];
    this.activeIds = [...s.active];
    this.doneIds = [...s.done];
  }

  card(id: string): NowRoadmapCard | undefined {
    return this.catalog.get(id);
  }

  idsForView(ids: readonly string[]): string[] {
    if (this.typeFilter === 'all') {
      return [...ids];
    }
    return ids.filter((id) => this.catalog.get(id)?.type === this.typeFilter);
  }

  dragEnabled(): boolean {
    return this.typeFilter === 'all';
  }

  onDrop(event: CdkDragDrop<string[]>): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex,
      );
    }
    nowBoardWriteLanes({
      planned: this.plannedIds,
      active: this.activeIds,
      done: this.doneIds,
    });
  }
}
