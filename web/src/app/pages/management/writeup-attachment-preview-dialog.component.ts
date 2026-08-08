import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ManagementApiService } from '../../services/management-api.service';
import { LifeApiService } from '../../services/life-api.service';
import { TrackerApiService } from '../../services/tracker-api.service';

export type WriteupAttachmentPreviewKind =
  | 'loading'
  | 'image'
  | 'pdf'
  | 'text'
  | 'video'
  | 'audio'
  | 'unsupported'
  | 'error';

export interface WriteupAttachmentPreviewData {
  attachmentId: number;
  filename: string;
  contentType: string | null;
  /** Defaults to write-up vault; month notes use {@code 'life'} or {@code 'tracker'}. */
  source?: 'writeup' | 'life' | 'tracker';
}

@Component({
  selector: 'app-writeup-attachment-preview-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title class="prev-title">
      <mat-icon aria-hidden="true">{{ kindIcon }}</mat-icon>
      <span class="prev-name">{{ data.filename }}</span>
    </h2>
    <mat-dialog-content class="prev-body">
      @if (kind === 'loading') {
        <div class="prev-loading">
          <mat-spinner diameter="36" />
          <p class="muted">Loading preview…</p>
        </div>
      } @else if (kind === 'error') {
        <p class="muted" role="alert">{{ error || 'Could not load attachment.' }}</p>
      } @else if (kind === 'image' && blobUrl) {
        <div class="img-wrap">
          <img [src]="blobUrl" [alt]="data.filename" />
        </div>
      } @else if (kind === 'pdf' && safePdfUrl) {
        <iframe class="pdf-frame" title="PDF preview" [src]="safePdfUrl"></iframe>
      } @else if (kind === 'text') {
        <pre class="text-preview">{{ previewText }}</pre>
      } @else if (kind === 'video' && blobUrl) {
        <video class="av-player" controls [src]="blobUrl" [attr.title]="data.filename"></video>
      } @else if (kind === 'audio' && blobUrl) {
        <audio class="av-player av-player--audio" controls [src]="blobUrl" [attr.title]="data.filename"></audio>
      } @else {
        <div class="prev-fallback">
          <mat-icon>insert_drive_file</mat-icon>
          <p class="muted">In-app preview is not available for this file type. Download to open it locally.</p>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (blobUrl && kind !== 'loading' && kind !== 'error') {
        <a mat-stroked-button color="primary" [href]="blobUrl" [download]="data.filename">
          <mat-icon>download</mat-icon>
          Download
        </a>
      }
      <button mat-button type="button" (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .prev-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-size: 1.05rem;
    }
    .prev-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: min(70vw, 36rem);
    }
    .prev-body {
      min-width: min(92vw, 48rem);
      min-height: 12rem;
    }
    .prev-loading {
      display: grid;
      place-items: center;
      gap: 0.75rem;
      min-height: 12rem;
    }
    .muted {
      opacity: 0.72;
      margin: 0;
    }
    .img-wrap {
      text-align: center;
    }
    .img-wrap img {
      max-width: 100%;
      max-height: 70vh;
      border-radius: 8px;
      object-fit: contain;
    }
    .pdf-frame {
      width: 100%;
      min-height: 70vh;
      border: 0;
      border-radius: 8px;
      background: #f1f5f9;
    }
    .text-preview {
      margin: 0;
      max-height: 70vh;
      overflow: auto;
      padding: 0.85rem 1rem;
      border-radius: 8px;
      background: color-mix(in srgb, currentColor 6%, transparent);
      font-size: 0.88rem;
      line-height: 1.45;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .av-player {
      display: block;
      width: 100%;
      max-height: 70vh;
      border-radius: 8px;
      background: #0f172a;
    }
    .av-player--audio {
      background: transparent;
      margin: 2rem auto;
      max-width: 36rem;
    }
    .prev-fallback {
      display: grid;
      place-items: center;
      gap: 0.5rem;
      min-height: 10rem;
      text-align: center;
      opacity: 0.85;
    }
    mat-dialog-actions a mat-icon {
      margin-right: 0.25rem;
      vertical-align: middle;
    }
  `,
})
export class WriteupAttachmentPreviewDialogComponent implements OnInit, OnDestroy {
  private readonly managementApi = inject(ManagementApiService);
  private readonly lifeApi = inject(LifeApiService);
  private readonly trackerApi = inject(TrackerApiService);
  private readonly dialogRef = inject(MatDialogRef<WriteupAttachmentPreviewDialogComponent>);
  private readonly sanitizer = inject(DomSanitizer);
  readonly data = inject<WriteupAttachmentPreviewData>(MAT_DIALOG_DATA);

  kind: WriteupAttachmentPreviewKind = 'loading';
  error: string | null = null;
  blobUrl: string | null = null;
  safePdfUrl: SafeResourceUrl | null = null;
  previewText = '';

  get kindIcon(): string {
    switch (this.kind) {
      case 'image':
        return 'image';
      case 'pdf':
        return 'picture_as_pdf';
      case 'text':
        return 'article';
      case 'video':
        return 'movie';
      case 'audio':
        return 'audio_file';
      case 'loading':
        return 'hourglass_empty';
      default:
        return 'attach_file';
    }
  }

  ngOnInit(): void {
    const source = this.data.source ?? 'writeup';
    const req =
      source === 'life'
        ? this.lifeApi.getMonthNoteAttachmentBlob(this.data.attachmentId, 'inline')
        : source === 'tracker'
          ? this.trackerApi.getMonthNoteAttachmentBlob(this.data.attachmentId, 'inline')
          : this.managementApi.getWriteupAttachmentBlob(this.data.attachmentId, 'inline');
    req.subscribe({
      next: (blob) => this.applyBlob(blob),
      error: () => {
        this.kind = 'error';
        this.error = 'Could not load attachment.';
      },
    });
  }

  ngOnDestroy(): void {
    this.revoke();
  }

  close(): void {
    this.dialogRef.close();
  }

  private applyBlob(blob: Blob): void {
    const declared = (this.data.contentType ?? '').trim().toLowerCase();
    const fromBlob = (blob.type || '').trim().toLowerCase();
    const ct = declared || fromBlob || 'application/octet-stream';
    const name = (this.data.filename || '').toLowerCase();

    void this.looksLikePdf(blob).then((pdfMagic) => {
      if (this.isText(ct, name) && !this.isPdf(ct, name) && !pdfMagic) {
        blob
          .text()
          .then((t) => {
            this.kind = 'text';
            this.previewText = t;
            this.blobUrl = URL.createObjectURL(blob);
          })
          .catch(() => {
            this.kind = 'unsupported';
            this.blobUrl = URL.createObjectURL(blob);
          });
        return;
      }

      this.blobUrl = URL.createObjectURL(blob);
      if (this.isPdf(ct, name) || pdfMagic) {
        this.kind = 'pdf';
        this.safePdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.blobUrl);
        return;
      }
      if (this.isImage(ct, name)) {
        this.kind = 'image';
        return;
      }
      if (this.isVideo(ct, name)) {
        this.kind = 'video';
        return;
      }
      if (this.isAudio(ct, name)) {
        this.kind = 'audio';
        return;
      }
      this.kind = 'unsupported';
    });
  }

  private async looksLikePdf(blob: Blob): Promise<boolean> {
    try {
      const head = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
      return (
        head.length >= 4 &&
        head[0] === 0x25 &&
        head[1] === 0x50 &&
        head[2] === 0x44 &&
        head[3] === 0x46
      );
    } catch {
      return false;
    }
  }

  private isImage(ct: string, name: string): boolean {
    return (
      ct.startsWith('image/') ||
      /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(name)
    );
  }

  private isPdf(ct: string, name: string): boolean {
    return ct === 'application/pdf' || ct.includes('pdf') || name.endsWith('.pdf');
  }

  private isText(ct: string, name: string): boolean {
    return (
      ct.startsWith('text/') ||
      ct === 'application/json' ||
      ct === 'application/xml' ||
      /\.(txt|md|csv|json|xml|log|yml|yaml)$/i.test(name)
    );
  }

  private isVideo(ct: string, name: string): boolean {
    return ct.startsWith('video/') || /\.(mp4|webm|ogg|mov|m4v)$/i.test(name);
  }

  private isAudio(ct: string, name: string): boolean {
    return ct.startsWith('audio/') || /\.(mp3|wav|ogg|m4a|aac|flac)$/i.test(name);
  }

  private revoke(): void {
    if (this.blobUrl) {
      URL.revokeObjectURL(this.blobUrl);
      this.blobUrl = null;
    }
    this.safePdfUrl = null;
  }
}
