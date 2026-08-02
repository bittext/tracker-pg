import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  TrackerMonthNoteAttachmentDto,
  TrackerMonthNoteCalendarDto,
  TrackerMonthNoteDto,
  TrackerMonthNoteWriteBody,
} from '../../models/tracker.models';
import { TrackerApiService } from '../../services/tracker-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  WriteupAttachmentPreviewDialogComponent,
  WriteupAttachmentPreviewData,
} from '../management/writeup-attachment-preview-dialog.component';
import { WriteupMarkdownBodyComponent } from '../management/writeup-markdown-body.component';

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

@Component({
  selector: 'app-tracker-notes',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    WriteupMarkdownBodyComponent,
  ],
  templateUrl: './tracker-notes.component.html',
  styleUrl: './tracker-notes.component.scss',
})
export class TrackerNotesComponent implements OnInit {
  private readonly api = inject(TrackerApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  noteYear = new Date().getFullYear();
  noteFilterMonth: number | null = new Date().getMonth() + 1;
  noteCalendar: TrackerMonthNoteCalendarDto | null = null;
  monthNotes: TrackerMonthNoteDto[] = [];
  noteSelectedId: number | null = null;
  noteViewMode: 'read' | 'compose' = 'read';
  noteComposerPane: 'split' | 'write' | 'preview' = 'split';
  noteEditingId: number | null = null;
  noteUploading = false;
  noteMonthOptions = Array.from({ length: 12 }, (_, i) => i + 1);
  noteSelectedAttachments: TrackerMonthNoteAttachmentDto[] = [];
  /** Default insert width as % of the note column (not full bleed). */
  insertImageWidthPct = 30;
  /** Text wraps around floated images (like a magazine layout). */
  insertImageFloat: 'left' | 'right' | 'none' = 'left';
  /** When set, newly inserted images open this PDF on click (from the per-image Opens PDF picker). */
  notePdfCoverTargetId: number | null = null;
  readonly imageWidthOptions = [
    { pct: 25, label: 'S' },
    { pct: 30, label: 'M' },
    { pct: 45, label: 'L' },
    { pct: 60, label: 'XL' },
    { pct: 100, label: 'Full' },
  ];
  readonly imageFloatOptions: { id: 'left' | 'right' | 'none'; label: string }[] = [
    { id: 'left', label: 'Wrap left' },
    { id: 'right', label: 'Wrap right' },
    { id: 'none', label: 'Block' },
  ];
  /** Last caret in the Tracker note body editor — Insert and drag-drop use this. */
  noteBodyCaret = 0;
  noteEditorDragOver = false;
  private noteDragAttachmentId: number | null = null;
  /** Ignores out-of-order list responses when months are clicked quickly. */
  private noteListLoadSeq = 0;
  noteDraft: TrackerMonthNoteWriteBody = {
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
    subject: '',
    body: '',
  };

  ngOnInit(): void {
    this.reloadMonthNotesData();
  }

  get noteMonthCells(): { month: number; noteCount: number }[] {
    return this.noteCalendar?.months ?? this.emptyNoteCalendarMonths();
  }

  get selectedMonthNote(): TrackerMonthNoteDto | null {
    if (this.noteSelectedId == null) {
      return null;
    }
    return this.monthNotes.find((n) => n.id === this.noteSelectedId) ?? null;
  }

  get notePeriodLabel(): string {
    if (this.noteFilterMonth == null) {
      return `${this.noteYear} (all months)`;
    }
    return `${this.monthName(this.noteFilterMonth)} ${this.noteYear}`;
  }

  monthName(m: number): string {
    return MONTH_NAMES[m - 1] ?? String(m);
  }

  shortMonthName(m: number): string {
    return (MONTH_NAMES[m - 1] ?? String(m)).slice(0, 3);
  }

  reloadMonthNotesData(): void {
    this.api.notesCalendar(this.noteYear).subscribe({
      next: (c) => (this.noteCalendar = c),
      error: (e) => this.err('Could not load calendar', e),
    });
    this.reloadMonthNotesListOnly();
  }

  private reloadMonthNotesListOnly(): void {
    const seq = ++this.noteListLoadSeq;
    const year = this.noteYear;
    const month = this.noteFilterMonth;
    this.api.listMonthNotes(year, month).subscribe({
      next: (rows) => {
        if (seq !== this.noteListLoadSeq) {
          return;
        }
        this.monthNotes = rows;
        if (this.noteViewMode === 'compose' && this.noteEditingId != null) {
          const found = rows.find((r) => r.id === this.noteEditingId);
          if (!found) {
            this.resetMonthNoteForm();
          } else {
            this.noteSelectedAttachments = [...(found.attachments ?? [])];
          }
        } else {
          this.syncNoteSelectionAfterLoad();
          if (this.noteSelectedId != null) {
            const found = rows.find((r) => r.id === this.noteSelectedId);
            this.noteSelectedAttachments = [...(found?.attachments ?? [])];
          }
        }
      },
      error: (e) => {
        if (seq !== this.noteListLoadSeq) {
          return;
        }
        this.err('Could not load notes', e);
      },
    });
  }

  private syncNoteSelectionAfterLoad(): void {
    if (this.noteViewMode === 'compose') {
      return;
    }
    if (this.noteSelectedId != null && this.monthNotes.some((n) => n.id === this.noteSelectedId)) {
      return;
    }
    this.noteSelectedId = this.monthNotes[0]?.id ?? null;
    const found = this.monthNotes.find((n) => n.id === this.noteSelectedId);
    this.noteSelectedAttachments = [...(found?.attachments ?? [])];
  }

  private emptyNoteCalendarMonths(): { month: number; noteCount: number }[] {
    return Array.from({ length: 12 }, (_, i) => ({ month: i + 1, noteCount: 0 }));
  }

  startNewMonthNote(): void {
    this.noteEditingId = null;
    this.noteSelectedId = null;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
    this.noteSelectedAttachments = [];
    this.noteDraft = {
      year: this.noteYear,
      month: this.noteFilterMonth != null ? this.noteFilterMonth : new Date().getMonth() + 1,
      subject: '',
      body: '',
    };
  }

  selectMonthNote(n: TrackerMonthNoteDto): void {
    this.noteSelectedId = n.id;
    this.noteViewMode = 'read';
    this.noteEditingId = null;
    this.noteSelectedAttachments = [...(n.attachments ?? [])];
  }

  setNoteComposerPane(pane: 'split' | 'write' | 'preview'): void {
    this.noteComposerPane = pane;
  }

  selectNotesYearOnly(): void {
    this.noteFilterMonth = null;
    this.noteViewMode = 'read';
    this.reloadMonthNotesListOnly();
  }

  selectNoteMonth(m: number): void {
    if (this.noteFilterMonth === m) {
      this.noteFilterMonth = null;
    } else {
      this.noteFilterMonth = m;
      this.noteDraft.month = m;
    }
    this.noteViewMode = 'read';
    this.noteSelectedId = null;
    this.reloadMonthNotesListOnly();
  }

  prevNoteYear(): void {
    this.noteYear -= 1;
    this.noteDraft.year = this.noteYear;
    this.reloadMonthNotesData();
  }

  nextNoteYear(): void {
    this.noteYear += 1;
    this.noteDraft.year = this.noteYear;
    this.reloadMonthNotesData();
  }

  resetMonthNoteForm(): void {
    this.noteEditingId = null;
    this.noteDraft = {
      year: this.noteYear,
      month: this.noteFilterMonth != null ? this.noteFilterMonth : this.noteDraft.month,
      subject: '',
      body: '',
    };
    this.noteViewMode = 'read';
    this.syncNoteSelectionAfterLoad();
  }

  startEditMonthNote(n: TrackerMonthNoteDto): void {
    this.noteEditingId = n.id;
    this.noteSelectedId = n.id;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
    this.noteSelectedAttachments = [...(n.attachments ?? [])];
    this.noteDraft = {
      year: n.year,
      month: n.month,
      subject: n.subject,
      body: this.repairBrokenPdfCoverImgTags(n.body ?? ''),
    };
  }

  saveMonthNote(): void {
    const subject = (this.noteDraft.subject || '').trim();
    if (!subject) {
      this.snackBar.open('Subject is required', undefined, { duration: 2500 });
      return;
    }
    this.noteDraft = {
      ...this.noteDraft,
      body: this.repairBrokenPdfCoverImgTags(this.noteDraft.body ?? ''),
    };
    const body: TrackerMonthNoteWriteBody = {
      year: this.noteDraft.year,
      month: this.noteDraft.month,
      subject,
      body: (this.noteDraft.body || '').trim(),
    };
    if (this.noteEditingId != null) {
      this.api.updateMonthNote(this.noteEditingId, body).subscribe({
        next: (saved) => {
          this.snackBar.open('Note updated', undefined, { duration: 2000 });
          this.noteSelectedId = saved.id;
          this.noteViewMode = 'read';
          this.noteEditingId = null;
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not update note', e),
      });
    } else {
      this.api.createMonthNote(body).subscribe({
        next: (saved) => {
          this.snackBar.open('Note saved', undefined, { duration: 2000 });
          this.noteSelectedId = saved.id;
          this.noteViewMode = 'read';
          this.noteEditingId = null;
          this.noteYear = saved.year;
          this.noteFilterMonth = saved.month;
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not save note', e),
      });
    }
  }

  deleteMonthNote(n: TrackerMonthNoteDto): void {
    if (!confirm(`Delete “${n.subject}”?`)) {
      return;
    }
    this.api.deleteMonthNote(n.id).subscribe({
      next: () => {
        this.snackBar.open('Note removed', undefined, { duration: 2000 });
        if (this.noteEditingId === n.id) {
          this.resetMonthNoteForm();
        } else if (this.noteSelectedId === n.id) {
          this.noteSelectedId = null;
          this.syncNoteSelectionAfterLoad();
        }
        this.reloadMonthNotesData();
      },
      error: (e) => this.err('Could not delete note', e),
    });
  }

  onMonthNoteFilesSelected(event: Event, noteId: number): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    this.noteUploading = true;
    const list = Array.from(files);
    let i = 0;
    const step = (): void => {
      if (i >= list.length) {
        this.noteUploading = false;
        input.value = '';
        this.reloadMonthNotesData();
        this.snackBar.open('Attachment(s) uploaded', undefined, { duration: 2000 });
        return;
      }
      this.api.uploadMonthNoteAttachment(noteId, list[i]).subscribe({
        next: () => {
          i += 1;
          step();
        },
        error: (e) => {
          this.noteUploading = false;
          input.value = '';
          this.err('Upload failed', e);
        },
      });
    };
    step();
  }

  openMonthNoteAttachment(attachment: TrackerMonthNoteAttachmentDto): void {
    this.dialog.open<WriteupAttachmentPreviewDialogComponent, WriteupAttachmentPreviewData>(
      WriteupAttachmentPreviewDialogComponent,
      {
        width: 'min(96vw, 56rem)',
        maxWidth: '96vw',
        maxHeight: '92vh',
        data: {
          attachmentId: attachment.id,
          filename: attachment.originalFilename || 'attachment',
          contentType: attachment.contentType,
          source: 'tracker',
        },
      },
    );
  }

  isImageAttachment(a: TrackerMonthNoteAttachmentDto): boolean {
    if (this.isPdfAttachment(a)) {
      return false;
    }
    const ct = (a.contentType || '').toLowerCase();
    if (ct.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(a.originalFilename || '');
  }

  isPdfAttachment(a: TrackerMonthNoteAttachmentDto): boolean {
    const ct = (a.contentType || '').toLowerCase();
    if (ct === 'application/pdf' || ct.includes('pdf')) {
      return true;
    }
    return /\.pdf$/i.test(a.originalFilename || '');
  }

  private currentAttachments(): TrackerMonthNoteAttachmentDto[] {
    if (this.noteViewMode === 'compose' && this.noteSelectedAttachments.length) {
      return this.noteSelectedAttachments;
    }
    return this.selectedMonthNote?.attachments ?? this.noteSelectedAttachments;
  }

  get pdfAttachments(): TrackerMonthNoteAttachmentDto[] {
    return this.currentAttachments().filter((a) => this.isPdfAttachment(a));
  }

  private workingBody(): string {
    if (this.noteViewMode === 'compose') {
      return this.noteDraft.body ?? '';
    }
    return this.selectedMonthNote?.body ?? this.noteDraft.body ?? '';
  }

  /** Explicit pick, else the only PDF on the note (convenient for cover+book). */
  resolvePdfCoverTarget(): TrackerMonthNoteAttachmentDto | null {
    const pdfs = this.pdfAttachments;
    if (this.notePdfCoverTargetId != null) {
      return pdfs.find((p) => p.id === this.notePdfCoverTargetId) ?? null;
    }
    return pdfs.length === 1 ? pdfs[0]! : null;
  }

  /** Which PDF (if any) this image embed currently opens. */
  linkedPdfIdForImage(imageId: number): number | null {
    const body = this.workingBody();
    const re = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${imageId}\\/file[^"']*["'][^>]*>`,
      'i',
    );
    const tag = body.match(re)?.[0];
    if (!tag) {
      return null;
    }
    const idm = tag.match(/\bdata-open-pdf-id=["'](\d+)["']/i);
    return idm ? Number(idm[1]) : null;
  }

  shortAttachmentLabel(a: TrackerMonthNoteAttachmentDto, max = 36): string {
    const name = a.originalFilename || `file ${a.id}`;
    return name.length > max ? `${name.slice(0, max - 1)}…` : name;
  }

  /**
   * Per-image provision: choose which PDF this one image opens on click.
   * Inserts the image into the body if it is not already present.
   */
  onImagePdfLinkChange(image: TrackerMonthNoteAttachmentDto, pdfId: number | null): void {
    if (!this.isImageAttachment(image)) {
      return;
    }
    if (this.noteViewMode !== 'compose') {
      const n = this.selectedMonthNote;
      if (n) {
        this.startEditMonthNote(n);
      }
    }
    if (pdfId == null) {
      this.unlinkImageFromPdf(image.id);
      this.snackBar.open('PDF link removed from image — Save the note', undefined, { duration: 2800 });
      return;
    }
    const pdf = this.pdfAttachments.find((p) => p.id === pdfId);
    if (!pdf) {
      return;
    }
    this.linkOneImageToPdf(image, pdf);
  }

  /**
   * Normalize img attribute text so a stray self-closing slash never sits before
   * data-open-pdf-* (that pattern breaks HTML and hides the image in preview).
   */
  private normalizeImgAttrs(attrs: string): string {
    return attrs
      .replace(/\s\/\s+(?=data-open-pdf-|data-tracker-|data-life-|data-att-|alt=|src=|style=|class=)/gi, ' ')
      .replace(/\/\s*$/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private rewriteTrackerImageTags(
    body: string,
    imageId: number,
    mutator: (attrs: string) => string,
  ): { body: string; changed: number } {
    const imgRe = new RegExp(
      `<img\\b([^>]*\\bsrc=["'][^"']*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${imageId}\\/file[^"']*["'][^>]*?)\\s*\\/?>`,
      'gi',
    );
    let changed = 0;
    const nextBody = body.replace(imgRe, (_full, attrs: string) => {
      changed += 1;
      const next = mutator(this.normalizeImgAttrs(attrs));
      return `<img ${next} />`;
    });
    return { body: nextBody, changed };
  }

  /** Fix previously saved broken tags: {@code <img ... / data-open-pdf-id="…">}. */
  repairBrokenPdfCoverImgTags(body: string): string {
    return body.replace(
      /<img\b([^>]*?)\s\/\s*((?:data-open-pdf-(?:id|name)=(?:"[^"]*"|'[^']*')\s*)+)\s*\/?>/gi,
      (_m, attrs: string, pdfAttrs: string) => {
        const cleaned = this.normalizeImgAttrs(String(attrs));
        const pdf = String(pdfAttrs).replace(/\s+/g, ' ').trim();
        return `<img ${cleaned} ${pdf} />`;
      },
    );
  }

  private unlinkImageFromPdf(imageId: number): void {
    const { body } = this.rewriteTrackerImageTags(this.noteDraft.body ?? '', imageId, (attrs) =>
      attrs
        .replace(/\sdata-open-pdf-id=(["'])[^"']*\1/gi, '')
        .replace(/\sdata-open-pdf-name=(["'])[^"']*\1/gi, '')
        .trim(),
    );
    this.noteDraft = { ...this.noteDraft, body: this.repairBrokenPdfCoverImgTags(body) };
  }

  /** Link exactly one image attachment embed to one PDF. */
  linkOneImageToPdf(image: TrackerMonthNoteAttachmentDto, pdf: TrackerMonthNoteAttachmentDto): void {
    this.notePdfCoverTargetId = pdf.id;
    const pdfName = (pdf.originalFilename || 'document.pdf').replace(/"/g, '');
    let body = this.repairBrokenPdfCoverImgTags(this.noteDraft.body ?? '');
    const rewritten = this.rewriteTrackerImageTags(body, image.id, (attrs) => {
      const base = attrs
        .replace(/\sdata-open-pdf-id=(["'])[^"']*\1/gi, '')
        .replace(/\sdata-open-pdf-name=(["'])[^"']*\1/gi, '')
        .trim();
      return `${base} data-open-pdf-id="${pdf.id}" data-open-pdf-name="${pdfName}"`;
    });
    if (!rewritten.changed) {
      this.notePdfCoverTargetId = pdf.id;
      this.insertImageIntoBody(image);
      return;
    }
    this.noteDraft = { ...this.noteDraft, body: rewritten.body };
    this.snackBar.open('Image linked to PDF — click cover opens the book. Save the note.', undefined, {
      duration: 3200,
    });
  }

  /** Insert a clickable PDF / file link into the Markdown body. */
  insertFileIntoBody(a: TrackerMonthNoteAttachmentDto): void {
    if (this.noteViewMode !== 'compose') {
      const n = this.selectedMonthNote;
      if (n) {
        this.startEditMonthNote(n);
      }
    }
    const name = (a.originalFilename || (this.isPdfAttachment(a) ? 'document.pdf' : 'file')).replace(
      /"/g,
      '',
    );
    const src = `/api/markets/tracker/notes/attachments/${a.id}/file`;
    const label = this.isPdfAttachment(a) ? `PDF: ${name}` : name;
    const tag =
      `<a class="note-embed-file note-embed-file--link" href="${src}" ` +
      `data-att-kind="tracker" data-att-id="${a.id}" data-att-name="${name}"` +
      (this.isPdfAttachment(a) ? ` data-att-content-type="application/pdf"` : '') +
      `>${label}</a>`;
    let body = this.noteDraft.body ?? '';
    const linkRe = new RegExp(
      `<a\\b[^>]*\\bhref=["'][^"']*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${a.id}\\/file[^"']*["'][^>]*>[\\s\\S]*?<\\/a>`,
      'gi',
    );
    const imgRe = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${a.id}\\/file[^"']*["'][^>]*>`,
      'gi',
    );
    body = body.replace(linkRe, '').replace(imgRe, '').replace(/\n{3,}/g, '\n\n');
    const idx = Math.max(0, Math.min(body.length, this.noteBodyCaret ?? body.length));
    this.noteDraft = { ...this.noteDraft, body: this.insertTextAt(body, idx, tag) };
    this.noteBodyCaret = idx + tag.length + 1;
    this.snackBar.open(
      this.isPdfAttachment(a) ? 'PDF link placed — Save the note' : 'File link placed — Save the note',
      undefined,
      { duration: 3000 },
    );
  }

  /**
   * Insert (or move/resize) a stable HTML image at the editor caret / drop index.
   * Width is a % of the note column so embeds stay inset while writing.
   */
  insertImageIntoBody(a: TrackerMonthNoteAttachmentDto, widthPct?: number, atIndex?: number): void {
    if (!this.isImageAttachment(a)) {
      this.snackBar.open('Only image attachments can be inserted into the body', undefined, {
        duration: 3000,
      });
      return;
    }
    if (this.noteViewMode !== 'compose') {
      const n = this.selectedMonthNote;
      if (n) {
        this.startEditMonthNote(n);
      }
    }
    const pct = Math.min(100, Math.max(10, widthPct ?? this.insertImageWidthPct));
    const floatSide = this.insertImageFloat;
    const name = (a.originalFilename || 'image').replace(/"/g, '');
    const src = `/api/markets/tracker/notes/attachments/${a.id}/file`;
    const margin =
      floatSide === 'right'
        ? '0.1rem 0 0.85rem 1rem'
        : floatSide === 'none'
          ? '0.75rem 0'
          : '0.1rem 1rem 0.85rem 0';
    const floatCss = floatSide === 'none' ? 'none' : floatSide;
    const pdf = this.resolvePdfCoverTarget();
    const pdfAttrs = pdf
      ? ` data-open-pdf-id="${pdf.id}" data-open-pdf-name="${(pdf.originalFilename || 'document.pdf').replace(/"/g, '')}"`
      : '';
    const tag =
      `<img src="${src}" alt="${name}" data-tracker-width="${pct}" data-tracker-float="${floatSide}"` +
      `${pdfAttrs} ` +
      `style="float:${floatCss};max-width:${pct}%;width:${pct}%;height:auto;margin:${margin};" />`;
    let body = this.noteDraft.body ?? '';
    const imgRe = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${a.id}\\/file[^"']*["'][^>]*>`,
      'gi',
    );
    const mdRe = new RegExp(
      `!\\[[^\\]]*\\]\\([^)]*\\/api\\/markets\\/tracker\\/notes\\/attachments\\/${a.id}\\/file[^)]*\\)`,
      'gi',
    );
    const hadExisting = imgRe.test(body) || mdRe.test(body);
    imgRe.lastIndex = 0;
    mdRe.lastIndex = 0;
    body = body.replace(imgRe, '').replace(mdRe, '');
    body = body.replace(/\n{3,}/g, '\n\n');
    const idx =
      atIndex != null
        ? atIndex
        : Math.max(0, Math.min(body.length, this.noteBodyCaret ?? body.length));
    const insertAt = Math.max(0, Math.min(body.length, idx));
    this.noteDraft = { ...this.noteDraft, body: this.insertTextAt(body, insertAt, tag) };
    this.noteBodyCaret = insertAt + tag.length + 1;
    this.snackBar.open(
      pdf
        ? hadExisting
          ? `Cover linked to PDF — Save the note`
          : `Cover placed; click opens PDF — Save the note`
        : hadExisting
          ? `Image moved / sized to ${pct}% — Save the note`
          : `Image placed at ${pct}% — Save the note`,
      undefined,
      { duration: 3000 },
    );
  }

  onNoteBodyCaret(ev: Event): void {
    const t = ev.target as HTMLTextAreaElement;
    this.noteBodyCaret = t.selectionStart ?? (this.noteDraft.body ?? '').length;
  }

  onNoteAttachDragStart(ev: DragEvent, a: TrackerMonthNoteAttachmentDto): void {
    if (!this.isImageAttachment(a)) {
      ev.preventDefault();
      return;
    }
    this.noteDragAttachmentId = a.id;
    ev.dataTransfer?.setData('application/x-tracker-markets-att', String(a.id));
    ev.dataTransfer?.setData('text/plain', a.originalFilename || 'image');
    if (ev.dataTransfer) {
      ev.dataTransfer.effectAllowed = 'copyMove';
    }
  }

  onNoteAttachDragEnd(): void {
    this.noteDragAttachmentId = null;
  }

  onNoteEditorDragOver(ev: DragEvent): void {
    const types = ev.dataTransfer?.types ? Array.from(ev.dataTransfer.types) : [];
    const ok =
      types.includes('application/x-tracker-markets-att') ||
      types.includes('Files') ||
      this.noteDragAttachmentId != null;
    if (!ok) {
      return;
    }
    ev.preventDefault();
    this.noteEditorDragOver = true;
    if (ev.dataTransfer) {
      ev.dataTransfer.dropEffect = types.includes('Files') ? 'copy' : 'move';
    }
    const ta = ev.currentTarget as HTMLTextAreaElement;
    if (document.activeElement !== ta) {
      ta.focus();
    }
  }

  onNoteEditorDragLeave(ev: DragEvent): void {
    const related = ev.relatedTarget as Node | null;
    const pane = ev.currentTarget as HTMLElement;
    if (related && pane.contains(related)) {
      return;
    }
    this.noteEditorDragOver = false;
  }

  onNoteEditorDrop(ev: DragEvent): void {
    ev.preventDefault();
    this.noteEditorDragOver = false;
    const ta = ev.currentTarget as HTMLTextAreaElement;
    const dropAt = ta.selectionStart ?? this.noteBodyCaret;

    const files = ev.dataTransfer?.files;
    if (files && files.length && this.noteEditingId != null) {
      this.uploadTrackerFilesAndPlace(Array.from(files), this.noteEditingId, dropAt);
      return;
    }

    const idRaw =
      ev.dataTransfer?.getData('application/x-tracker-markets-att') ||
      (this.noteDragAttachmentId != null ? String(this.noteDragAttachmentId) : '');
    const id = Number(idRaw);
    this.noteDragAttachmentId = null;
    if (!Number.isFinite(id)) {
      return;
    }
    const a =
      this.noteSelectedAttachments.find((x) => x.id === id) ??
      this.selectedMonthNote?.attachments?.find((x) => x.id === id);
    if (a) {
      this.noteBodyCaret = dropAt;
      this.insertImageIntoBody(a, this.insertImageWidthPct, dropAt);
      queueMicrotask(() => {
        ta.focus();
        const caret = this.noteBodyCaret;
        ta.setSelectionRange(caret, caret);
      });
    }
  }

  private uploadTrackerFilesAndPlace(files: File[], noteId: number, atIndex: number): void {
    const images = files.filter(
      (f) => f.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(f.name),
    );
    if (!images.length) {
      this.snackBar.open('Drop image files to upload and place them', undefined, { duration: 3000 });
      return;
    }
    this.noteUploading = true;
    let i = 0;
    let caret = atIndex;
    const step = (): void => {
      if (i >= images.length) {
        this.noteUploading = false;
        this.reloadMonthNotesData();
        this.snackBar.open('Image(s) uploaded and placed — Save when ready', undefined, {
          duration: 3000,
        });
        return;
      }
      this.api.uploadMonthNoteAttachment(noteId, images[i]).subscribe({
        next: (att) => {
          if (this.isImageAttachment(att)) {
            this.insertImageIntoBody(att, this.insertImageWidthPct, caret);
            caret = this.noteBodyCaret;
          }
          i += 1;
          step();
        },
        error: (e) => {
          this.noteUploading = false;
          this.err('Upload failed', e);
        },
      });
    };
    step();
  }

  private insertTextAt(body: string, index: number, text: string): string {
    const i = Math.max(0, Math.min(body.length, index));
    const before = body.slice(0, i);
    const after = body.slice(i);
    const leftPad = !before.length || before.endsWith('\n') ? '' : '\n';
    const rightPad = !after.length || after.startsWith('\n') ? '' : '\n';
    return `${before}${leftPad}${text}${rightPad}${after}`;
  }

  removeMonthNoteAttachment(attachmentId: number, ev?: Event): void {
    ev?.stopPropagation();
    this.api.deleteMonthNoteAttachment(attachmentId).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', undefined, { duration: 2000 });
        this.reloadMonthNotesData();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
  }

  private err(msg: string, e?: unknown): void {
    const detail = e != null ? formatHttpErrorDetail(e) : '';
    this.snackBar.open(detail ? `${msg}: ${detail}` : msg, 'Dismiss', { duration: 8000 });
  }
}
