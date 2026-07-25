import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TradingJournalAttachmentDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';

export interface TradingJournalImageGalleryData {
  images: TradingJournalAttachmentDto[];
  startIndex: number;
}

@Component({
  selector: 'app-trading-journal-image-gallery-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="gal-title">
      <span class="gal-name">{{ current?.originalFilename || 'Image' }}</span>
      <span class="gal-pos muted">{{ index + 1 }} / {{ data.images.length }}</span>
    </h2>
    @if (current?.createdAt) {
      <p class="gal-when muted">{{ uploadedLabel(current!) }}</p>
    }
    <mat-dialog-content class="gal-body">
      @if (error) {
        <p class="muted" role="alert">{{ error }}</p>
      } @else if (loading) {
        <p class="muted">Loading…</p>
      } @else if (blobUrl) {
        <div
          class="gal-stage"
          (touchstart)="onTouchStart($event)"
          (touchend)="onTouchEnd($event)"
        >
          <button
            mat-icon-button
            type="button"
            class="gal-nav gal-nav--prev"
            (click)="prev()"
            [disabled]="data.images.length < 2"
            aria-label="Previous image"
          >
            <mat-icon>chevron_left</mat-icon>
          </button>
          <div class="gal-frame">
            <img class="gal-img" [src]="blobUrl" [alt]="current?.originalFilename || 'Image'" />
            @if (current?.createdAt) {
              <div class="gal-stamp">{{ uploadedLabel(current!) }}</div>
            }
          </div>
          <button
            mat-icon-button
            type="button"
            class="gal-nav gal-nav--next"
            (click)="next()"
            [disabled]="data.images.length < 2"
            aria-label="Next image"
          >
            <mat-icon>chevron_right</mat-icon>
          </button>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="prev()" [disabled]="data.images.length < 2">
        Previous
      </button>
      <button mat-button type="button" (click)="next()" [disabled]="data.images.length < 2">
        Next
      </button>
      <button mat-button type="button" (click)="close()">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .gal-title {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.5rem 1rem;
      margin: 0;
      font-size: 1rem;
    }
    .gal-name {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: min(70vw, 28rem);
    }
    .gal-pos {
      font-weight: 500;
      font-size: 0.85rem;
      flex-shrink: 0;
    }
    .gal-when {
      margin: -0.35rem 1.5rem 0.5rem;
      font-size: 0.85rem;
    }
    .muted {
      opacity: 0.7;
    }
    .gal-body {
      min-width: min(92vw, 48rem);
      min-height: 14rem;
      padding-top: 0.5rem !important;
    }
    .gal-stage {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 20rem;
      user-select: none;
      touch-action: pan-y;
    }
    .gal-frame {
      position: relative;
      display: inline-flex;
      max-width: 100%;
    }
    .gal-img {
      max-width: 100%;
      max-height: min(72vh, 40rem);
      border-radius: 8px;
      object-fit: contain;
      animation: gal-fade 0.22s ease;
    }
    .gal-stamp {
      position: absolute;
      left: 0.65rem;
      bottom: 0.65rem;
      padding: 0.3rem 0.55rem;
      border-radius: 6px;
      background: rgba(15, 23, 42, 0.72);
      color: #f8fafc;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.01em;
      pointer-events: none;
    }
    @keyframes gal-fade {
      from {
        opacity: 0.35;
        transform: translateX(8px);
      }
      to {
        opacity: 1;
        transform: translateX(0);
      }
    }
    .gal-nav {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      z-index: 1;
      background: rgba(15, 23, 42, 0.45);
      color: #fff;
    }
    .gal-nav--prev {
      left: 0.25rem;
    }
    .gal-nav--next {
      right: 0.25rem;
    }
    @media (max-width: 600px) {
      .gal-nav {
        display: none;
      }
    }
  `,
})
export class TradingJournalImageGalleryDialogComponent implements OnInit, OnDestroy {
  private readonly api = inject(FinanceApiService);
  private readonly dialogRef = inject(MatDialogRef<TradingJournalImageGalleryDialogComponent>);
  readonly data = inject<TradingJournalImageGalleryData>(MAT_DIALOG_DATA);

  index = 0;
  loading = true;
  error: string | null = null;
  blobUrl: string | null = null;

  private touchX: number | null = null;
  private loadSeq = 0;

  get current(): TradingJournalAttachmentDto | undefined {
    return this.data.images[this.index];
  }

  ngOnInit(): void {
    const start = this.data.startIndex ?? 0;
    this.index = Math.max(0, Math.min(start, this.data.images.length - 1));
    this.loadCurrent();
  }

  ngOnDestroy(): void {
    this.revoke();
  }

  uploadedLabel(att: TradingJournalAttachmentDto): string {
    if (!att.createdAt) {
      return '';
    }
    return (
      new Intl.DateTimeFormat('en-US', {
        timeZone: 'America/Chicago',
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      }).format(new Date(att.createdAt)) + ' CT'
    );
  }

  @HostListener('window:keydown', ['$event'])
  onKey(ev: KeyboardEvent): void {
    if (ev.key === 'ArrowLeft') {
      ev.preventDefault();
      this.prev();
    } else if (ev.key === 'ArrowRight') {
      ev.preventDefault();
      this.next();
    } else if (ev.key === 'Escape') {
      this.close();
    }
  }

  prev(): void {
    if (this.data.images.length < 2) {
      return;
    }
    this.index = (this.index - 1 + this.data.images.length) % this.data.images.length;
    this.loadCurrent();
  }

  next(): void {
    if (this.data.images.length < 2) {
      return;
    }
    this.index = (this.index + 1) % this.data.images.length;
    this.loadCurrent();
  }

  onTouchStart(ev: TouchEvent): void {
    this.touchX = ev.changedTouches[0]?.clientX ?? null;
  }

  onTouchEnd(ev: TouchEvent): void {
    if (this.touchX == null) {
      return;
    }
    const endX = ev.changedTouches[0]?.clientX ?? this.touchX;
    const dx = endX - this.touchX;
    this.touchX = null;
    if (Math.abs(dx) < 48) {
      return;
    }
    if (dx < 0) {
      this.next();
    } else {
      this.prev();
    }
  }

  close(): void {
    this.dialogRef.close();
  }

  private loadCurrent(): void {
    const att = this.current;
    if (!att) {
      this.loading = false;
      this.error = 'No images to show.';
      return;
    }
    const seq = ++this.loadSeq;
    this.loading = true;
    this.error = null;
    this.revoke();
    this.api.tradingJournalAttachmentBlob(att.id, 'inline').subscribe({
      next: (blob) => {
        if (seq !== this.loadSeq) {
          return;
        }
        this.loading = false;
        this.blobUrl = URL.createObjectURL(blob);
      },
      error: () => {
        if (seq !== this.loadSeq) {
          return;
        }
        this.loading = false;
        this.error = 'Could not load image.';
      },
    });
  }

  private revoke(): void {
    if (this.blobUrl) {
      URL.revokeObjectURL(this.blobUrl);
      this.blobUrl = null;
    }
  }
}
