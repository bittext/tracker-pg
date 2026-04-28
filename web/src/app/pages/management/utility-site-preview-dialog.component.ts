import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

export interface UtilitySitePreviewDialogData {
  url: string;
}

/**
 * In-app website preview. Many sites send X-Frame-Options: DENY and will not load in the iframe; users can use “Open in browser”.
 */
@Component({
  selector: 'app-utility-site-preview-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title class="utility-preview-title">
      <span class="utility-preview-title-text">{{ data.url }}</span>
      <button mat-icon-button type="button" mat-dialog-close aria-label="Close">
        <mat-icon>close</mat-icon>
      </button>
    </h2>
    <mat-dialog-content class="utility-preview-content">
      <iframe
        [src]="safeUrl"
        class="utility-preview-iframe"
        title="Website preview"
        sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads"
      ></iframe>
      <p class="utility-preview-note muted">
        Some sites block in-app embedding. If the page stays blank, use
        <strong>Open in browser</strong> below (e.g. Zen Browser or your default browser in a new tab).
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="openInBrowser()">Open in browser</button>
      <button mat-flat-button type="button" mat-dialog-close color="primary">Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .utility-preview-title {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.5rem;
        margin: 0;
      }
      .utility-preview-title-text {
        word-break: break-all;
        font-size: 0.95rem;
        font-weight: 600;
        padding-right: 0.5rem;
      }
      .utility-preview-content {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        min-width: min(100%, 32rem);
      }
      .utility-preview-iframe {
        width: 100%;
        height: min(70vh, 640px);
        border: 1px solid rgba(15, 23, 42, 0.12);
        border-radius: 8px;
        background: #f1f5f9;
      }
      .utility-preview-note {
        margin: 0;
        font-size: 0.82rem;
        line-height: 1.4;
      }
    `,
  ],
})
export class UtilitySitePreviewDialogComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly dialogRef = inject(MatDialogRef<UtilitySitePreviewDialogComponent>);

  readonly data = inject<UtilitySitePreviewDialogData>(MAT_DIALOG_DATA);

  get safeUrl(): SafeResourceUrl {
    return this.sanitizer.bypassSecurityTrustResourceUrl(this.data.url);
  }

  openInBrowser(): void {
    window.open(this.data.url, '_blank', 'noopener,noreferrer');
    this.dialogRef.close();
  }
}
