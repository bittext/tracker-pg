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
import { Observable } from 'rxjs';
import { marked } from 'marked';
import { LifeApiService } from '../../services/life-api.service';
import { ManagementApiService } from '../../services/management-api.service';

type AttachmentKind = 'writeup' | 'life';

/**
 * Renders markdown and rewrites authenticated attachment image URLs to blob: URLs
 * so images embedded via insert (write-ups or Life notes) display on the page.
 */
@Component({
  selector: 'app-writeup-markdown-body',
  standalone: true,
  imports: [CommonModule],
  template: `<div class="markdown-body wu-read-body notes-entry-body" [innerHTML]="html"></div>`,
})
export class WriteupMarkdownBodyComponent implements OnChanges, OnDestroy {
  private readonly managementApi = inject(ManagementApiService);
  private readonly lifeApi = inject(LifeApiService);
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
      const ref = this.extractAttachmentRef(img.getAttribute('src') || '');
      if (ref == null) {
        return;
      }
      img.alt = img.alt || `attachment ${ref.id}`;
      img.setAttribute('data-att-kind', ref.kind);
      img.setAttribute('data-att-id', String(ref.id));
      this.fetchBlob(ref).subscribe({
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

  private fetchBlob(ref: { kind: AttachmentKind; id: number }): Observable<Blob> {
    if (ref.kind === 'life') {
      return this.lifeApi.getMonthNoteAttachmentBlob(ref.id, 'inline');
    }
    return this.managementApi.getWriteupAttachmentBlob(ref.id, 'inline');
  }

  /** Match relative or absolute attachment file URLs produced by insert / legacy paste. */
  private extractAttachmentRef(src: string): { kind: AttachmentKind; id: number } | null {
    if (!src || src.startsWith('blob:')) {
      return null;
    }
    const life = src.match(/\/api\/life\/notes\/attachments\/(\d+)\/file(?:\?.*)?$/i);
    if (life) {
      return { kind: 'life', id: Number(life[1]) };
    }
    const writeup = src.match(/\/api\/management\/writeups\/attachments\/(\d+)\/file(?:\?.*)?$/i);
    if (writeup) {
      return { kind: 'writeup', id: Number(writeup[1]) };
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
