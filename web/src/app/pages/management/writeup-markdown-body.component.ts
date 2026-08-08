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
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { Observable } from 'rxjs';
import { marked } from 'marked';
import { LifeApiService } from '../../services/life-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { TrackerApiService } from '../../services/tracker-api.service';
import {
  WriteupAttachmentPreviewDialogComponent,
  WriteupAttachmentPreviewData,
} from './writeup-attachment-preview-dialog.component';

type AttachmentKind = 'writeup' | 'life' | 'tracker';

/**
 * Renders markdown and rewrites authenticated attachment URLs to blob: URLs
 * so images embedded via insert display. PDF (and other non-image) attachments
 * become clickable cards that open the preview dialog.
 */
@Component({
  selector: 'app-writeup-markdown-body',
  standalone: true,
  imports: [CommonModule],
  template: `<div
    class="markdown-body wu-read-body notes-entry-body"
    [innerHTML]="html"
    (click)="onBodyClick($event)"
  ></div>`,
})
export class WriteupMarkdownBodyComponent implements OnChanges, OnDestroy {
  private readonly managementApi = inject(ManagementApiService);
  private readonly lifeApi = inject(LifeApiService);
  private readonly trackerApi = inject(TrackerApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly dialog = inject(MatDialog);

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

  onBodyClick(ev: MouseEvent): void {
    const t = ev.target as HTMLElement | null;
    if (!t) {
      return;
    }
    const clickable = t.closest(
      'img[data-att-id], img[data-open-pdf-id], img[data-open-url], a.note-embed-file, button.note-embed-file',
    ) as HTMLElement | null;
    if (!clickable) {
      return;
    }
    const openUrl = clickable.getAttribute('data-open-url');
    if (openUrl && this.isSafeOpenUrl(openUrl)) {
      ev.preventDefault();
      ev.stopPropagation();
      window.open(openUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    const openPdfId = clickable.getAttribute('data-open-pdf-id');
    let kind = (clickable.getAttribute('data-att-kind') || '') as AttachmentKind;
    if (kind !== 'life' && kind !== 'tracker' && kind !== 'writeup') {
      const fromSrc = this.extractAttachmentRef(
        clickable.getAttribute('src') || clickable.getAttribute('href') || '',
      );
      kind = fromSrc?.kind ?? ('' as AttachmentKind);
    }
    const idRaw = openPdfId || clickable.getAttribute('data-att-id');
    if (!idRaw || (kind !== 'life' && kind !== 'tracker' && kind !== 'writeup')) {
      return;
    }
    ev.preventDefault();
    ev.stopPropagation();
    const filename =
      (openPdfId ? clickable.getAttribute('data-open-pdf-name') : null) ||
      clickable.getAttribute('data-att-name') ||
      clickable.getAttribute('alt') ||
      clickable.textContent?.trim() ||
      (openPdfId ? 'document.pdf' : 'attachment');
    const contentType = openPdfId
      ? 'application/pdf'
      : clickable.getAttribute('data-att-content-type');
    this.openPreview({ kind, id: Number(idRaw) }, filename, contentType);
  }

  private isSafeOpenUrl(url: string): boolean {
    if (url.startsWith('/') && !url.startsWith('//')) {
      return true;
    }
    try {
      const u = new URL(url);
      return u.protocol === 'http:' || u.protocol === 'https:';
    } catch {
      return false;
    }
  }

  private render(): void {
    this.revokeAll();
    const src = this.repairBrokenPdfCoverImgTags((this.body ?? '').trim());
    if (!src) {
      this.html = this.sanitizer.bypassSecurityTrustHtml('');
      return;
    }
    const raw = marked(src, { async: false, breaks: true, gfm: true }) as string;
    const clean = DOMPurify.sanitize(raw, {
      USE_PROFILES: { html: true },
      ADD_ATTR: [
        'data-life-width',
        'data-life-float',
        'data-tracker-width',
        'data-tracker-float',
        'data-att-kind',
        'data-att-id',
        'data-att-name',
        'data-att-content-type',
        'data-open-pdf-id',
        'data-open-pdf-name',
        'data-open-url',
        'width',
        'height',
      ],
      ADD_TAGS: ['button'],
    });
    this.html = this.sanitizer.bypassSecurityTrustHtml(clean);
    const seq = ++this.loadSeq;
    queueMicrotask(() => {
      if (seq !== this.loadSeq) {
        return;
      }
      this.hydrateAttachments(seq);
    });
  }

  private hydrateAttachments(seq: number): void {
    const root = this.host.nativeElement;
    root.querySelectorAll('img').forEach((img: HTMLImageElement) => {
      this.applyEmbeddedImageSize(img);
      const ref = this.extractAttachmentRef(img.getAttribute('src') || '');
      if (ref == null) {
        return;
      }
      const name = img.alt || `attachment ${ref.id}`;
      img.alt = name;
      img.setAttribute('data-att-kind', ref.kind);
      img.setAttribute('data-att-id', String(ref.id));
      img.setAttribute('data-att-name', name);
      const openPdfId = img.getAttribute('data-open-pdf-id');
      const openUrl = img.getAttribute('data-open-url');
      img.title = openUrl
        ? 'Click to open link'
        : openPdfId
          ? 'Click to open PDF'
          : 'Click to open';
      img.style.cursor = 'pointer';
      if (openPdfId) {
        img.classList.add('note-embed-pdf-cover');
      }
      if (openUrl) {
        img.classList.add('note-embed-url-cover');
      }

      this.fetchBlob(ref).subscribe({
        next: (blob) => {
          void this.classifyBlob(blob, name).then((kind) => {
            if (seq !== this.loadSeq) {
              return;
            }
            // Cover images stay as images even if misclassified; open-pdf target handles the click.
            if ((kind === 'pdf' || kind === 'file') && !openPdfId) {
              this.replaceWithFileCard(img, ref, name, kind);
              return;
            }
            const url = URL.createObjectURL(blob);
            this.blobUrls.push(url);
            img.src = url;
            this.applyEmbeddedImageSize(img);
          });
        },
        error: () => {
          if (seq !== this.loadSeq) {
            return;
          }
          img.alt = `${img.alt || 'Image'} (failed to load)`;
        },
      });
    });

    root.querySelectorAll('a[href]').forEach((anchor: Element) => {
      const a = anchor as HTMLAnchorElement;
      const ref = this.extractAttachmentRef(a.getAttribute('href') || '');
      if (ref == null) {
        return;
      }
      const name = (a.textContent || '').trim() || `attachment ${ref.id}`;
      a.classList.add('note-embed-file', 'note-embed-file--link');
      a.setAttribute('data-att-kind', ref.kind);
      a.setAttribute('data-att-id', String(ref.id));
      a.setAttribute('data-att-name', name);
      a.title = 'Click to open';
    });
  }

  private replaceWithFileCard(
    img: HTMLImageElement,
    ref: { kind: AttachmentKind; id: number },
    name: string,
    kind: 'pdf' | 'file',
  ): void {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = `note-embed-file note-embed-file--${kind}`;
    btn.setAttribute('data-att-kind', ref.kind);
    btn.setAttribute('data-att-id', String(ref.id));
    btn.setAttribute('data-att-name', name);
    if (kind === 'pdf') {
      btn.setAttribute('data-att-content-type', 'application/pdf');
    }
    btn.title = kind === 'pdf' ? 'Open PDF' : 'Open attachment';

    const pctRaw = img.getAttribute('data-tracker-width') || img.getAttribute('data-life-width');
    const pct = pctRaw ? Math.min(100, Math.max(10, Number(pctRaw) || 30)) : 30;
    const floatRaw = (
      img.getAttribute('data-tracker-float') ||
      img.getAttribute('data-life-float') ||
      'left'
    ).toLowerCase();
    const floatSide = floatRaw === 'right' || floatRaw === 'none' ? floatRaw : 'left';
    btn.style.maxWidth = pct >= 100 ? '100%' : `${pct}%`;
    btn.style.width = pct >= 100 ? '100%' : `${pct}%`;
    if (floatSide === 'right') {
      btn.style.float = 'right';
      btn.style.margin = '0.1rem 0 0.85rem 1rem';
    } else if (floatSide === 'none') {
      btn.style.float = 'none';
      btn.style.margin = '0.75rem 0';
      btn.style.display = 'flex';
    } else {
      btn.style.float = 'left';
      btn.style.margin = '0.1rem 1rem 0.85rem 0';
    }

    const badge = document.createElement('span');
    badge.className = 'note-embed-file-badge';
    badge.textContent = kind === 'pdf' ? 'PDF' : 'FILE';
    const label = document.createElement('span');
    label.className = 'note-embed-file-name';
    label.textContent = name;
    btn.append(badge, label);
    img.replaceWith(btn);
  }

  private openPreview(
    ref: { kind: AttachmentKind; id: number },
    filename: string,
    contentType: string | null,
  ): void {
    const inferred =
      contentType ||
      (filename.toLowerCase().endsWith('.pdf') ? 'application/pdf' : null);
    this.dialog.open<WriteupAttachmentPreviewDialogComponent, WriteupAttachmentPreviewData>(
      WriteupAttachmentPreviewDialogComponent,
      {
        width: 'min(96vw, 56rem)',
        maxWidth: '96vw',
        maxHeight: '92vh',
        data: {
          attachmentId: ref.id,
          filename,
          contentType: inferred,
          source: ref.kind === 'writeup' ? 'writeup' : ref.kind,
        },
      },
    );
  }

  private async classifyBlob(blob: Blob, name: string): Promise<'image' | 'pdf' | 'file'> {
    const ct = (blob.type || '').toLowerCase();
    const lower = name.toLowerCase();
    if (ct.includes('pdf') || lower.endsWith('.pdf')) {
      return 'pdf';
    }
    if (ct.startsWith('image/')) {
      return 'image';
    }
    try {
      const head = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
      if (
        head.length >= 4 &&
        head[0] === 0x25 &&
        head[1] === 0x50 &&
        head[2] === 0x44 &&
        head[3] === 0x46
      ) {
        return 'pdf';
      }
    } catch {
      /* ignore */
    }
    if (/\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(lower)) {
      return 'image';
    }
    return ct ? 'file' : 'image';
  }

  /** Honor data-*-width / data-*-float so text wraps around inset images. */
  private applyEmbeddedImageSize(img: HTMLImageElement): void {
    const pctRaw = img.getAttribute('data-tracker-width') || img.getAttribute('data-life-width');
    const pct = pctRaw
      ? Math.min(100, Math.max(10, Number(pctRaw) || 30))
      : 30;
    const floatRaw = (
      img.getAttribute('data-tracker-float') ||
      img.getAttribute('data-life-float') ||
      'left'
    ).toLowerCase();
    const floatSide = floatRaw === 'right' || floatRaw === 'none' ? floatRaw : 'left';

    img.style.height = 'auto';
    img.style.maxWidth = `${pct}%`;
    img.style.width = pct >= 100 || floatSide === 'none' ? (pct >= 100 ? '100%' : 'auto') : `${pct}%`;
    img.classList.add('life-embed-img');

    if (floatSide === 'right') {
      img.style.float = 'right';
      img.style.display = 'block';
      img.style.margin = '0.1rem 0 0.85rem 1rem';
      img.style.shapeOutside = 'margin-box';
    } else if (floatSide === 'none') {
      img.style.float = 'none';
      img.style.display = 'block';
      img.style.margin = '0.75rem 0';
      img.style.shapeOutside = '';
    } else {
      img.style.float = 'left';
      img.style.display = 'block';
      img.style.margin = '0.1rem 1rem 0.85rem 0';
      img.style.shapeOutside = 'margin-box';
    }
  }

  private fetchBlob(ref: { kind: AttachmentKind; id: number }): Observable<Blob> {
    if (ref.kind === 'life') {
      return this.lifeApi.getMonthNoteAttachmentBlob(ref.id, 'inline');
    }
    if (ref.kind === 'tracker') {
      return this.trackerApi.getMonthNoteAttachmentBlob(ref.id, 'inline');
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
    const tracker = src.match(
      /\/api\/markets\/tracker\/notes\/attachments\/(\d+)\/file(?:\?.*)?$/i,
    );
    if (tracker) {
      return { kind: 'tracker', id: Number(tracker[1]) };
    }
    const writeup = src.match(/\/api\/management\/writeups\/attachments\/(\d+)\/file(?:\?.*)?$/i);
    if (writeup) {
      return { kind: 'writeup', id: Number(writeup[1]) };
    }
    return null;
  }

  /**
   * Repair {@code <img … / data-open-pdf-id="…">} (slash before PDF attrs) so covers
   * still render after an earlier linker bug.
   */
  private repairBrokenPdfCoverImgTags(body: string): string {
    if (!body || !body.includes('data-open-pdf-id')) {
      return body;
    }
    return body.replace(
      /<img\b([^>]*?)\s\/\s*((?:data-open-pdf-(?:id|name)=(?:"[^"]*"|'[^']*')\s*)+)\s*\/?>/gi,
      (_m, attrs: string, pdfAttrs: string) => {
        const cleaned = String(attrs)
          .replace(/\s\/\s*$/g, '')
          .replace(/\s+/g, ' ')
          .trim();
        const pdf = String(pdfAttrs).replace(/\s+/g, ' ').trim();
        return `<img ${cleaned} ${pdf} />`;
      },
    );
  }

  private revokeAll(): void {
    for (const u of this.blobUrls) {
      URL.revokeObjectURL(u);
    }
    this.blobUrls = [];
  }
}
