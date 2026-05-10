import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { ManagementApiService } from '../../../services/management-api.service';

/** Loads a work-log attachment via authenticated API and exposes it as a blob URL for `<audio>`. */
@Component({
  selector: 'app-work-log-audio-player',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (error) {
      <span class="muted mw-audio-err">Could not load audio</span>
    } @else if (objectUrl) {
      <audio controls preload="metadata" class="mw-audio-el" [src]="objectUrl">
        Your browser does not support audio.
      </audio>
    } @else {
      <span class="muted mw-audio-loading">Loading…</span>
    }
  `,
  styles: [
    `
      .mw-audio-el {
        width: 100%;
        max-width: 28rem;
        vertical-align: middle;
      }
      .mw-audio-loading,
      .mw-audio-err {
        font-size: 0.82rem;
      }
    `,
  ],
})
export class WorkLogAudioPlayerComponent implements OnInit, OnDestroy {
  @Input({ required: true }) attachmentId!: number;

  private readonly api = inject(ManagementApiService);

  objectUrl: string | null = null;
  error = false;

  ngOnInit(): void {
    this.api.getWorkLogAttachmentBlob(this.attachmentId, 'inline').subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
      },
      error: () => {
        this.error = true;
      },
    });
  }

  ngOnDestroy(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
  }
}
