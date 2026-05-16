import { Component, inject } from '@angular/core';
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
  NowRoadmapCardType,
  NowRoadmapLane,
} from './management-now-data';

export interface ManagementNowAddCardDialogData {
  readonly defaultLane?: NowRoadmapLane;
}

export interface ManagementNowAddCardDialogResult {
  readonly title: string;
  readonly body: string;
  readonly milestone: string;
  readonly type: NowRoadmapCardType;
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
export class ManagementNowAddCardDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ManagementNowAddCardDialogComponent>);
  private readonly data = inject<ManagementNowAddCardDialogData | null>(MAT_DIALOG_DATA, { optional: true });

  readonly typeMeta = NOW_CARD_TYPE_META;
  readonly cardTypes = NOW_ROADMAP_CARD_TYPES;

  title = '';
  body = '';
  milestone = '';
  type: NowRoadmapCardType = 'product';
  lane: NowRoadmapLane = this.data?.defaultLane ?? 'planned';

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
