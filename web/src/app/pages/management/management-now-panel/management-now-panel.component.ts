import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { ManagementNowCardType } from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
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
  NOW_ROADMAP_META,
  NowRoadmapCard,
  type NowRoadmapCardType,
  type NowRoadmapCardTypeMeta,
} from './management-now-data';
import {
  mergeNowCardTypeMeta,
  orderedNowCardTypeSlugs,
  resolveNowCardTypeMeta,
} from './management-now-type-meta';

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
    RouterLink,
  ],
  templateUrl: './management-now-panel.component.html',
  styleUrl: './management-now-panel.component.scss',
})
export class ManagementNowPanelComponent implements OnInit {
  private readonly dialog = inject(MatDialog);
  private readonly managementApi = inject(ManagementApiService);

  readonly meta = NOW_ROADMAP_META;

  /** When not `all`, lane lists are filtered for display only; drag is disabled. */
  typeFilter: 'all' | NowRoadmapCardType = 'all';

  plannedIds: string[] = [];
  activeIds: string[] = [];
  doneIds: string[] = [];

  private catalogCache = nowBoardFullCatalog();
  private nowCardTypesFromApi: ManagementNowCardType[] = [];
  private typeMetaBySlug = mergeNowCardTypeMeta([]);

  ngOnInit(): void {
    this.reloadFromStorage();
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

  typeMetaFor(slug: string): NowRoadmapCardTypeMeta {
    return resolveNowCardTypeMeta(this.typeMetaBySlug, slug);
  }

  typeSlugsForFilter(): string[] {
    return orderedNowCardTypeSlugs(this.nowCardTypesFromApi);
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
    const typeSlugs = orderedNowCardTypeSlugs(this.nowCardTypesFromApi);
    const typeMetaRecord = Object.fromEntries(this.typeMetaBySlug);
    this.dialog
      .open(ManagementNowAddCardDialogComponent, {
        width: 'min(100vw - 32px, 440px)',
        autoFocus: 'input',
        data: { typeSlugs, typeMetaRecord },
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
