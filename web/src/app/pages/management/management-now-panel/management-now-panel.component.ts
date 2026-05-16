import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  ManagementNowAddCardDialogComponent,
  ManagementNowAddCardDialogResult,
} from './management-now-add-card-dialog.component';
import {
  newCustomNowCardId,
  nowBoardAddCustomCard,
  nowBoardFullCatalog,
  nowBoardReadLanes,
  nowBoardWriteLanes,
} from './management-now-board.storage';
import {
  NOW_CARD_TYPE_META,
  NOW_ROADMAP_CARD_TYPES,
  NOW_ROADMAP_META,
  NowRoadmapCard,
  NowRoadmapCardType,
} from './management-now-data';

@Component({
  selector: 'app-management-now-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './management-now-panel.component.html',
  styleUrl: './management-now-panel.component.scss',
})
export class ManagementNowPanelComponent implements OnInit {
  private readonly dialog = inject(MatDialog);

  readonly meta = NOW_ROADMAP_META;
  readonly typeMeta = NOW_CARD_TYPE_META;
  readonly cardTypes = NOW_ROADMAP_CARD_TYPES;

  /** When not `all`, lane lists are filtered for display only; drag is disabled. */
  typeFilter: 'all' | NowRoadmapCardType = 'all';

  plannedIds: string[] = [];
  activeIds: string[] = [];
  doneIds: string[] = [];

  private catalogCache = nowBoardFullCatalog();

  ngOnInit(): void {
    this.reloadFromStorage();
  }

  reloadFromStorage(): void {
    const s = nowBoardReadLanes();
    this.plannedIds = [...s.planned];
    this.activeIds = [...s.active];
    this.doneIds = [...s.done];
    this.rebuildCatalog();
  }

  private rebuildCatalog(): void {
    this.catalogCache = nowBoardFullCatalog();
  }

  card(id: string): NowRoadmapCard | undefined {
    return this.catalogCache.get(id);
  }

  idsForView(ids: readonly string[]): string[] {
    if (this.typeFilter === 'all') {
      return [...ids];
    }
    return ids.filter((id) => this.catalogCache.get(id)?.type === this.typeFilter);
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
    this.rebuildCatalog();
  }

  openAddCard(): void {
    this.dialog
      .open(ManagementNowAddCardDialogComponent, {
        width: 'min(100vw - 32px, 440px)',
        autoFocus: 'input',
        data: {},
      })
      .afterClosed()
      .subscribe((r: ManagementNowAddCardDialogResult | null | undefined) => {
        if (!r?.title?.trim()) {
          return;
        }
        const card: NowRoadmapCard = {
          id: newCustomNowCardId(),
          type: r.type,
          title: r.title.trim(),
          ...(r.body ? { body: r.body } : {}),
          ...(r.milestone ? { milestone: r.milestone } : {}),
        };
        nowBoardAddCustomCard(card, r.lane);
        this.reloadFromStorage();
      });
  }
}
