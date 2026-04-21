import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ManagementDayOneAttachmentDto } from '../../models/management.models';

export interface DayOneAttachmentsDialogData {
  attachments: ManagementDayOneAttachmentDto[];
}

@Component({
  selector: 'app-day-one-attachments-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Attachments</h2>
    <mat-dialog-content class="d1-att-dialog">
      @if (loading) {
        <mat-spinner diameter="36" />
      } @else if (error) {
        <p class="d1-att-error">{{ error }}</p>
      } @else {
        <div class="d1-att-grid">
          @for (a of data.attachments; track a.id; let i = $index) {
            <figure class="d1-att-item">
              @if (isImage(a)) {
                <img [src]="urls[i]" [alt]="a.originalFilename" class="d1-att-img" />
              } @else {
                <a class="d1-att-link" [href]="urls[i]" [download]="a.originalFilename" target="_blank" rel="noopener">
                  <mat-icon>description</mat-icon>
                  {{ a.originalFilename }}
                </a>
              }
              <figcaption class="d1-att-cap">{{ a.originalFilename }}</figcaption>
            </figure>
          }
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .d1-att-dialog {
      min-width: min(92vw, 28rem);
      max-width: min(96vw, 48rem);
      max-height: 70vh;
      overflow: auto;
    }
    .d1-att-grid {
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
    }
    .d1-att-item {
      margin: 0;
      border-radius: 10px;
      overflow: hidden;
      background: #f1f5f9;
      border: 1px solid rgba(148, 163, 184, 0.35);
    }
    .d1-att-img {
      width: 100%;
      height: 10rem;
      object-fit: cover;
      display: block;
    }
    .d1-att-link {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.85rem;
      font-size: 0.85rem;
      color: #1d4ed8;
      text-decoration: none;
    }
    .d1-att-cap {
      font-size: 0.68rem;
      padding: 0.35rem 0.5rem;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .d1-att-error {
      color: #b91c1c;
      margin: 0;
    }
  `,
})
export class DayOneAttachmentsDialogComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  readonly data = inject<DayOneAttachmentsDialogData>(MAT_DIALOG_DATA);

  loading = true;
  error: string | null = null;
  urls: string[] = [];

  ngOnInit(): void {
    const base = environment.apiBaseUrl || '';
    if (!this.data.attachments?.length) {
      this.loading = false;
      return;
    }
    forkJoin(
      this.data.attachments.map((a) =>
        this.http.get(`${base}${a.downloadPath}`, { responseType: 'blob' }),
      ),
    ).subscribe({
      next: (blobs) => {
        this.urls = blobs.map((b) => URL.createObjectURL(b));
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load files. Try again when signed in.';
        this.loading = false;
      },
    });
  }

  ngOnDestroy(): void {
    this.urls.forEach((u) => URL.revokeObjectURL(u));
  }

  isImage(a: ManagementDayOneAttachmentDto): boolean {
    const ct = (a.contentType || '').toLowerCase();
    return ct.startsWith('image/');
  }
}
