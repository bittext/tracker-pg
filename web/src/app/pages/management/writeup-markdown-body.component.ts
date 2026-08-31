import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  EventEmitter,
  Injector,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  afterNextRender,
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

export interface WriteupImageRemoveEvent {
  id: number;
  kind: AttachmentKind;
}

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
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        max-width: 100%;
      }
      :host .markdown-body img.life-embed-img--loading {
        min-height: 4.5rem;
        background: linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%);
        background-size: 200% 100%;
      }
      :host .markdown-body img.life-embed-img--error {
        min-height: 3rem;
        outline: 1px dashed #94a3b8;
        background: #f8fafc;
      }
      :host ::ng-deep .life-embed-wrap {
        position: relative;
        display: block;
      }
      :host ::ng-deep .life-embed-wrap img {
        display: block;
        width: 100%;
        max-width: 100%;
        height: auto;
        margin: 0;
        float: none;
      }
      :host ::ng-deep .life-embed-remove {
        position: absolute;
        top: 0.35rem;
        right: 0.35rem;
        z-index: 2;
        width: 1.7rem;
        height: 1.7rem;
        padding: 0;
        border: 0;
        border-radius: 999px;
        background: rgba(15, 23, 42, 0.72);
        color: #fff;
        font-size: 1.15rem;
        line-height: 1;
        cursor: pointer;
        opacity: 0;
      }
      :host ::ng-deep .life-embed-wrap:hover .life-embed-remove,
      :host ::ng-deep .life-embed-remove:focus {
        opacity: 1;
      }
      @media (hover: none) {
        :host ::ng-deep .life-embed-remove {
          opacity: 1;
        }
      }
    `,
  ],
})
export class WriteupMarkdownBodyComponent implements OnChanges, OnDestroy {
  private readonly managementApi = inject(ManagementApiService);
  private readonly lifeApi = inject(LifeApiService);
  private readonly trackerApi = inject(TrackerApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly dialog = inject(MatDialog);
  private readonly injector = inject(Injector);

  @Input() body: string | null | undefined = '';
  /** Life notes only: show an X on embedded photos to remove file + embed. */
  @Input() allowImageRemove = false;
  @Output() readonly imageRemove = new EventEmitter<WriteupImageRemoveEvent>();

  html: SafeHtml = this.sanitizer.bypassSecurityTrustHtml('');

  private blobUrls: string[] = [];
  private loadSeq = 0;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['body']) {
      this.render();
    } else if (changes['allowImageRemove'] && this.allowImageRemove) {
      this.wrapRemovableLifeImages();
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
    if (t.closest('.life-embed-remove')) {
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
    // Attachment <img> tags must not be swallowed by unclosed ``` fences (CommonMark
    // only closes a fence on its own line). Hoist embeds, close stray fences, then restore.
    const { markdown, embeds } = this.extractAttachmentEmbeds(src);
    const repaired = this.closeUnclosedCodeFences(markdown);
    let raw = marked(repaired, { async: false, breaks: true, gfm: true }) as string;
    raw = this.restoreAttachmentEmbeds(raw, embeds);
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
        'style',
      ],
      ADD_TAGS: ['button'],
    });
    this.html = this.sanitizer.bypassSecurityTrustHtml(clean);
    const seq = ++this.loadSeq;
    afterNextRender(
      () => {
        if (seq !== this.loadSeq) {
          return;
        }
        this.hydrateAttachments(seq);
        setTimeout(() => {
          if (seq !== this.loadSeq) {
            return;
          }
          const pending = this.host.nativeElement.querySelectorAll(
            'img[src*="/attachments/"][src*="/file"]',
          );
          if (pending.length) {
            this.hydrateAttachments(seq);
          }
        }, 0);
      },
      { injector: this.injector },
    );
  }

  private static readonly ATT_PATH =
    '\\/api\\/(?:management\\/writeups|life\\/notes|markets\\/tracker\\/notes)\\/attachments\\/\\d+\\/file';

  /**
   * Pull attachment images out of the markdown source so an unclosed ``` fence
   * cannot turn them into escaped text inside &lt;pre&gt;&lt;code&gt;.
   */
  private extractAttachmentEmbeds(src: string): { markdown: string; embeds: string[] } {
    const embeds: string[] = [];
    const pathRe = WriteupMarkdownBodyComponent.ATT_PATH;
    // Markdown image → HTML img (keeps alt; sizing attrs added only for existing HTML imgs).
    let markdown = src.replace(
      new RegExp(`!\\[([^\\]]*)\\]\\((${pathRe}[^)]*)\\)`, 'gi'),
      (_m, alt: string, url: string) => {
        const i = embeds.length;
        const safeAlt = String(alt || '').replace(/"/g, '');
        embeds.push(`<img src="${url}" alt="${safeAlt}" />`);
        return `\n\n@@ATT_EMBED_${i}@@\n\n`;
      },
    );
    markdown = markdown.replace(new RegExp(`<img\\b[^>]*${pathRe}[^>]*>`, 'gi'), (tag) => {
      const i = embeds.length;
      embeds.push(tag);
      return `\n\n@@ATT_EMBED_${i}@@\n\n`;
    });
    return { markdown, embeds };
  }

  private restoreAttachmentEmbeds(html: string, embeds: string[]): string {
    if (!embeds.length) {
      return html;
    }
    let out = html;
    for (let i = 0; i < embeds.length; i++) {
      const token = `@@ATT_EMBED_${i}@@`;
      const embed = embeds[i]!;
      if (!out.includes(token)) {
        // Placeholder lost (should not happen) — append so the image still shows.
        out += `\n${embed}\n`;
        continue;
      }
      // Prefer replacing a wrapping paragraph produced by marked.
      out = out.replace(new RegExp(`<p>\\s*${token}\\s*<\\/p>`, 'g'), embed);
      out = out.replaceAll(token, embed);
    }
    return out;
  }

  /** If a ``` / ~~~ fence was never closed on its own line, append a closing fence. */
  private closeUnclosedCodeFences(src: string): string {
    const lines = src.split('\n');
    let openMarker: '`' | '~' | null = null;
    let openLen = 0;
    for (const line of lines) {
      const m = /^( {0,3})(`{3,}|~{3,})(.*)$/.exec(line);
      if (!m) {
        continue;
      }
      const ticks = m[2]!;
      const marker = ticks[0] as '`' | '~';
      const len = ticks.length;
      const info = (m[3] || '').trim();
      if (openMarker == null) {
        openMarker = marker;
        openLen = len;
        continue;
      }
      // Closing fence: same marker, length >= open, no info string.
      if (marker === openMarker && len >= openLen && !info) {
        openMarker = null;
        openLen = 0;
      }
    }
    if (openMarker == null) {
      return src;
    }
    return `${src}\n${openMarker.repeat(Math.max(3, openLen))}\n`;
  }

  private hydrateAttachments(seq: number): void {
    const root = this.host.nativeElement;
    root.querySelectorAll('img').forEach((img: HTMLImageElement) => {
      this.applyEmbeddedImageSize(img);
      const ref = this.extractAttachmentRef(img.getAttribute('src') || '');
      if (ref == null) {
        return;
      }
      // Already hydrated (or in-flight) for this attachment.
      if (img.dataset['attHydrated'] === String(ref.id)) {
        this.wrapRemovableLifeImage(img, ref);
        return;
      }
      img.dataset['attHydrated'] = String(ref.id);
      const name = img.alt || `attachment ${ref.id}`;
      img.alt = name;
      img.setAttribute('data-att-kind', ref.kind);
      img.setAttribute('data-att-id', String(ref.id));
      img.setAttribute('data-att-name', name);
      this.wrapRemovableLifeImage(img, ref);
      const openPdfId = img.getAttribute('data-open-pdf-id');
      const openUrl = img.getAttribute('data-open-url');
      img.title = openUrl
        ? 'Click to open link'
        : openPdfId
          ? 'Click to open PDF'
          : 'Click to open';
      img.style.cursor = 'pointer';
      img.classList.add('life-embed-img--loading');
      img.classList.remove('life-embed-img--error');
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
            img.classList.remove('life-embed-img--loading', 'life-embed-img--error');
            img.src = url;
            this.applyEmbeddedImageSize(img);
            this.wrapRemovableLifeImage(img, ref);
          });
        },
        error: () => {
          if (seq !== this.loadSeq) {
            return;
          }
          img.dataset['attHydrated'] = '';
          img.classList.remove('life-embed-img--loading');
          img.classList.add('life-embed-img--error');
          img.alt = `${img.alt || 'Image'} (failed to load)`;
          this.wrapRemovableLifeImage(img, ref);
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
      const head = new Uint8Array(await blob.slice(0, 16).arrayBuffer());
      if (
        head.length >= 4 &&
        head[0] === 0x25 &&
        head[1] === 0x50 &&
        head[2] === 0x44 &&
        head[3] === 0x46
      ) {
        return 'pdf';
      }
      // PNG / JPEG / GIF / WEBP magic — keep as image even when Content-Type is octet-stream.
      if (
        head.length >= 8 &&
        head[0] === 0x89 &&
        head[1] === 0x50 &&
        head[2] === 0x4e &&
        head[3] === 0x47
      ) {
        return 'image';
      }
      if (head.length >= 3 && head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) {
        return 'image';
      }
      if (
        head.length >= 6 &&
        head[0] === 0x47 &&
        head[1] === 0x49 &&
        head[2] === 0x46 &&
        head[3] === 0x38
      ) {
        return 'image';
      }
      if (
        head.length >= 12 &&
        head[0] === 0x52 &&
        head[1] === 0x49 &&
        head[2] === 0x46 &&
        head[3] === 0x46 &&
        head[8] === 0x57 &&
        head[9] === 0x45 &&
        head[10] === 0x42 &&
        head[11] === 0x50
      ) {
        return 'image';
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
    const wrap = img.parentElement?.classList.contains('life-embed-wrap') ? img.parentElement : null;
    const box: HTMLElement = wrap ?? img;

    img.classList.add('life-embed-img');
    img.style.height = 'auto';
    if (wrap) {
      img.style.maxWidth = '100%';
      img.style.width = '100%';
      img.style.float = 'none';
      img.style.margin = '0';
      img.style.display = 'block';
      img.style.shapeOutside = '';
    } else {
      img.style.maxWidth = `${pct}%`;
      img.style.width = pct >= 100 || floatSide === 'none' ? (pct >= 100 ? '100%' : 'auto') : `${pct}%`;
    }

    if (floatSide === 'right') {
      box.style.float = 'right';
      box.style.display = 'block';
      box.style.margin = '0.1rem 0 0.85rem 1rem';
      box.style.shapeOutside = 'margin-box';
    } else if (floatSide === 'none') {
      box.style.float = 'none';
      box.style.display = 'block';
      box.style.margin = '0.75rem 0';
      box.style.shapeOutside = '';
    } else {
      box.style.float = 'left';
      box.style.display = 'block';
      box.style.margin = '0.1rem 1rem 0.85rem 0';
      box.style.shapeOutside = 'margin-box';
    }
    if (wrap) {
      wrap.style.maxWidth = `${pct}%`;
      wrap.style.width = pct >= 100 || floatSide === 'none' ? (pct >= 100 ? '100%' : `${pct}%`) : `${pct}%`;
    }
  }

  private wrapRemovableLifeImages(): void {
    const root = this.host.nativeElement;
    root.querySelectorAll('img[data-att-kind="life"][data-att-id]').forEach((el: Element) => {
      const img = el as HTMLImageElement;
      const id = Number(img.getAttribute('data-att-id'));
      if (Number.isFinite(id)) {
        this.wrapRemovableLifeImage(img, { kind: 'life', id });
      }
    });
  }

  private wrapRemovableLifeImage(
    img: HTMLImageElement,
    ref: { kind: AttachmentKind; id: number },
  ): void {
    if (!this.allowImageRemove || ref.kind !== 'life') {
      return;
    }
    if (img.closest('.life-embed-wrap')) {
      this.applyEmbeddedImageSize(img);
      return;
    }
    const wrap = document.createElement('span');
    wrap.className = 'life-embed-wrap';
    wrap.setAttribute('data-att-kind', ref.kind);
    wrap.setAttribute('data-att-id', String(ref.id));
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'life-embed-remove';
    btn.setAttribute('aria-label', 'Remove photo');
    btn.textContent = '×';
    btn.addEventListener('click', (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      this.imageRemove.emit({ id: ref.id, kind: ref.kind });
    });
    img.replaceWith(wrap);
    wrap.append(img, btn);
    this.applyEmbeddedImageSize(img);
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
