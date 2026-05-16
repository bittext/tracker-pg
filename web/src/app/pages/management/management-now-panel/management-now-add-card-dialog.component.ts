import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogContent,
  MatDialogModule,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  NOW_CARD_TYPE_META,
  NOW_ROADMAP_CARD_TYPES,
  type NowRoadmapCardTypeMeta,
  type NowRoadmapLane,
} from './management-now-data';
import { fallbackNowCardTypeMeta } from './management-now-type-meta';

export interface ManagementNowAddCardDialogData {
  readonly defaultLane?: NowRoadmapLane;
  /** Slugs for the type dropdown (built-ins + API-defined). */
  readonly typeSlugs: string[];
  readonly typeMetaRecord: Record<string, NowRoadmapCardTypeMeta>;
}

export interface ManagementNowAddCardDialogResult {
  readonly title: string;
  readonly body: string;
  readonly milestone: string;
  readonly type: string;
  readonly lane: NowRoadmapLane;
}

@Component({
  selector: 'app-management-now-add-card-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './management-now-add-card-dialog.component.html',
  styleUrl: './management-now-add-card-dialog.component.scss',
})
export class ManagementNowAddCardDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<ManagementNowAddCardDialogComponent>);
  private readonly data = inject<ManagementNowAddCardDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  title = '';
  body = '';
  milestone = '';
  type = '';
  lane: NowRoadmapLane = this.data?.defaultLane ?? 'planned';

  ngOnInit(): void {
    const slugs =
      (this.data?.typeSlugs?.length ?? 0) > 0 ? this.data!.typeSlugs : ([...NOW_ROADMAP_CARD_TYPES] as string[]);
    this.type = slugs[0] ?? 'product';
  }

  typeSlugs(): string[] {
    return (this.data?.typeSlugs?.length ?? 0) > 0 ? this.data!.typeSlugs : ([...NOW_ROADMAP_CARD_TYPES] as string[]);
  }

  typeMetaFor(slug: string): NowRoadmapCardTypeMeta {
    const fromDialog = this.data?.typeMetaRecord?.[slug];
    if (fromDialog) {
      return fromDialog;
    }
    const builtin = NOW_CARD_TYPE_META[slug];
    if (builtin) {
      return builtin;
    }
    return fallbackNowCardTypeMeta(slug);
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    const title = this.title.trim();
    if (!title) {
      return;
    }
    const result: ManagementNowAddCardDialogResult = {
      title,
      body: this.body.trim(),
      milestone: this.milestone.trim(),
      type: this.type,
      lane: this.lane,
    };
    this.dialogRef.close(result);
  }
}
