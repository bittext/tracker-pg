import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { ManagementApiService } from '../../services/management-api.service';

/**
 * Renders write-up markdown and rewrites authenticated attachment image URLs to blob: URLs
 * so images embedded via {@code ![alt](/api/management/writeups/attachments/{id}/file)} display.
 */
@Component({
  selector: 'app-writeup-markdown-body',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="markdown-body wu-read-body" [innerHTML]="html"></div>`,
})
export class WriteupMarkdownBodyComponent implements OnChanges, OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly host = inject(ElementRef<HTMLElement>);

  @Input() body: string | null | undefined = '';

  html: SafeHtml = this.sanitizer.bypassSecurityTrustHtml('');

  private blobUrls: string[] = [];
  private loadSeq = 0;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['body']) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.revokeAll();
  }

  private render(): void {
    this.revokeAll();
    const src = (this.body ?? '').trim();
    if (!src) {
      this.html = this.sanitizer.bypassSecurityTrustHtml('');
      return;
    }
    const raw = marked(src, { async: false, breaks: true }) as string;
    const clean = DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } });
    this.html = this.sanitizer.bypassSecurityTrustHtml(clean);
    const seq = ++this.loadSeq;
    // Wait a tick so innerHTML is in the DOM, then hydrate authenticated images.
    queueMicrotask(() => {
      if (seq !== this.loadSeq) {
        return;
      }
      this.hydrateAttachmentImages(seq);
    });
  }

  private hydrateAttachmentImages(seq: number): void {
    const root = this.host.nativeElement;
    const imgs = root.querySelectorAll('img');
    imgs.forEach((img: HTMLImageElement) => {
      const id = this.extractAttachmentId(img.getAttribute('src') || '');
      if (id == null) {
        return;
      }
      img.alt = img.alt || `attachment ${id}`;
      img.setAttribute('data-writeup-att', String(id));
      this.api.getWriteupAttachmentBlob(id, 'inline').subscribe({
        next: (blob: Blob) => {
          if (seq !== this.loadSeq) {
            return;
          }
          const url = URL.createObjectURL(blob);
          this.blobUrls.push(url);
          img.src = url;
        },
        error: () => {
          if (seq !== this.loadSeq) {
            return;
          }
          img.alt = `${img.alt || 'Image'} (failed to load)`;
        },
      });
    });
  }

  /** Match relative or absolute attachment file URLs produced by insert / legacy paste. */
  private extractAttachmentId(src: string): number | null {
    if (!src) {
      return null;
    }
    // Ignore temporary blob: URLs — they are session-only and cannot be rehydrated.
    if (src.startsWith('blob:')) {
      return null;
    }
    const m = src.match(/\/api\/management\/writeups\/attachments\/(\d+)\/file(?:\?.*)?$/i);
    if (m) {
      return Number(m[1]);
    }
    const rel = src.match(/^\/?api\/management\/writeups\/attachments\/(\d+)\/file(?:\?.*)?$/i);
    if (rel) {
      return Number(rel[1]);
    }
    return null;
  }

  private revokeAll(): void {
    for (const u of this.blobUrls) {
      URL.revokeObjectURL(u);
    }
    this.blobUrls = [];
  }
}
