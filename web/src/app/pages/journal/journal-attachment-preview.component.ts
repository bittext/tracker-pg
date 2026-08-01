import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { JournalApiService } from '../../services/journal-api.service';

export interface JournalAttachmentPreviewData {
  attachmentId: number;
  filename: string;
  contentType: string | null;
}

@Component({
  selector: 'app-journal-attachment-preview',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.filename }}</h2>
    <mat-dialog-content class="prev-body">
      @if (error) {
        <p class="muted" role="alert">{{ error }}</p>
      } @else if (loading) {
        <p class="muted">Loading…</p>
      } @else if (isImage && blobUrl) {
        <div class="img-wrap">
          <img [src]="blobUrl" [alt]="data.filename" />
        </div>
      } @else if (isPdf && safePdfUrl) {
        <iframe class="pdf-frame" title="PDF preview" [src]="safePdfUrl"></iframe>
      } @else if (blobUrl) {
        <p class="muted">Preview is not available for this file type.</p>
        <a mat-button color="primary" [href]="blobUrl" [download]="data.filename">Download</a>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .prev-body {
      min-width: min(90vw, 40rem);
      min-height: 12rem;
    }
    .img-wrap {
      text-align: center;
    }
    .img-wrap img {
      max-width: 100%;
      max-height: 70vh;
      border-radius: 8px;
    }
    .pdf-frame {
      width: 100%;
      min-height: 70vh;
      border: 0;
      border-radius: 8px;
      background: #f1f5f9;
    }
  `,
})
export class JournalAttachmentPreviewComponent implements OnInit, OnDestroy {
  private readonly journalApi = inject(JournalApiService);
  private readonly dialog = inject(MatDialogRef<JournalAttachmentPreviewComponent>);
  private readonly dom = inject(DomSanitizer);
  readonly data = inject<JournalAttachmentPreviewData>(MAT_DIALOG_DATA);

  loading = true;
  error: string | null = null;
  blobUrl: string | null = null;
  safePdfUrl: SafeResourceUrl | null = null;

  get isImage(): boolean {
    const t = (this.data.contentType ?? '').toLowerCase();
    if (t.startsWith('image/') || t.includes('heic') || t.includes('heif')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(this.data.filename || '');
  }

  get isPdf(): boolean {
    const t = (this.data.contentType ?? '').toLowerCase();
    return t === 'application/pdf' || this.data.filename.toLowerCase().endsWith('.pdf');
  }

  ngOnInit(): void {
    this.journalApi.getAttachmentBlob(this.data.attachmentId, 'inline').subscribe({
      next: (blob) => {
        this.loading = false;
        this.blobUrl = URL.createObjectURL(blob);
        if (this.isPdf) {
          this.safePdfUrl = this.dom.bypassSecurityTrustResourceUrl(this.blobUrl);
        }
      },
      error: () => {
        this.loading = false;
        this.error = 'Could not load attachment.';
      },
    });
  }

  ngOnDestroy(): void {
    if (this.blobUrl) {
      URL.revokeObjectURL(this.blobUrl);
    }
  }

  close(): void {
    this.dialog.close();
  }
}
