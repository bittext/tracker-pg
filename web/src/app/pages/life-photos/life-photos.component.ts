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
  LifeMonthNoteAttachmentDto,
  LifeMonthNoteCalendarDto,
  LifeMonthNoteDto,
  LifeMonthNoteWriteBody,
} from '../../models/life.models';
import { LifeApiService } from '../../services/life-api.service';
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
  selector: 'app-life-photos',
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
  templateUrl: './life-photos.component.html',
  styleUrl: './life-photos.component.scss',
})
export class LifePhotosComponent implements OnInit {
  private readonly api = inject(LifeApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  noteYear = new Date().getFullYear();
  noteFilterMonth: number | null = new Date().getMonth() + 1;
  noteCalendar: LifeMonthNoteCalendarDto | null = null;
  monthNotes: LifeMonthNoteDto[] = [];
  noteSelectedId: number | null = null;
  noteViewMode: 'read' | 'compose' = 'read';
  noteComposerPane: 'split' | 'write' | 'preview' = 'split';
  noteEditingId: number | null = null;
  noteUploading = false;
  noteMonthOptions = Array.from({ length: 12 }, (_, i) => i + 1);
  noteSelectedAttachments: LifeMonthNoteAttachmentDto[] = [];
  /** Default insert width as % of the note column (not full bleed). */
  insertImageWidthPct = 30;
  /** Text wraps around floated images (like a magazine layout). */
  insertImageFloat: 'left' | 'right' | 'none' = 'left';
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
  /** Last caret in the Life note body editor — Insert and drag-drop use this. */
  noteBodyCaret = 0;
  noteEditorDragOver = false;
  private noteDragAttachmentId: number | null = null;
  noteDraft: LifeMonthNoteWriteBody = {
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

  get selectedMonthNote(): LifeMonthNoteDto | null {
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
    this.api.listMonthNotes(this.noteYear, this.noteFilterMonth).subscribe({
      next: (rows) => {
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
      error: (e) => this.err('Could not load notes', e),
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

  selectMonthNote(n: LifeMonthNoteDto): void {
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

  startEditMonthNote(n: LifeMonthNoteDto): void {
    this.noteEditingId = n.id;
    this.noteSelectedId = n.id;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
    this.noteSelectedAttachments = [...(n.attachments ?? [])];
    this.noteDraft = {
      year: n.year,
      month: n.month,
      subject: n.subject,
      body: n.body ?? '',
    };
  }

  saveMonthNote(): void {
    const subject = (this.noteDraft.subject || '').trim();
    if (!subject) {
      this.snackBar.open('Subject is required', undefined, { duration: 2500 });
      return;
    }
    const body: LifeMonthNoteWriteBody = {
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

  deleteMonthNote(n: LifeMonthNoteDto): void {
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

  openMonthNoteAttachment(attachment: LifeMonthNoteAttachmentDto): void {
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
          source: 'life',
        },
      },
    );
  }

  isImageAttachment(a: LifeMonthNoteAttachmentDto): boolean {
    const ct = (a.contentType || '').toLowerCase();
    if (ct.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(a.originalFilename || '');
  }

  /**
   * Insert (or move/resize) a stable HTML image at the editor caret / drop index.
   * Width is a % of the note column so embeds stay inset while writing.
   */
  insertImageIntoBody(a: LifeMonthNoteAttachmentDto, widthPct?: number, atIndex?: number): void {
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
    const src = `/api/life/notes/attachments/${a.id}/file`;
    const margin =
      floatSide === 'right'
        ? '0.1rem 0 0.85rem 1rem'
        : floatSide === 'none'
          ? '0.75rem 0'
          : '0.1rem 1rem 0.85rem 0';
    const floatCss = floatSide === 'none' ? 'none' : floatSide;
    const tag =
      `<img src="${src}" alt="${name}" data-life-width="${pct}" data-life-float="${floatSide}" ` +
      `style="float:${floatCss};max-width:${pct}%;width:${pct}%;height:auto;margin:${margin};" />`;
    let body = this.noteDraft.body ?? '';
    const imgRe = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/life\\/notes\\/attachments\\/${a.id}\\/file[^"']*["'][^>]*>`,
      'gi',
    );
    const mdRe = new RegExp(`!\\[[^\\]]*\\]\\([^)]*\\/api\\/life\\/notes\\/attachments\\/${a.id}\\/file[^)]*\\)`, 'gi');
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
      hadExisting
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

  onNoteAttachDragStart(ev: DragEvent, a: LifeMonthNoteAttachmentDto): void {
    if (!this.isImageAttachment(a)) {
      ev.preventDefault();
      return;
    }
    this.noteDragAttachmentId = a.id;
    ev.dataTransfer?.setData('application/x-tracker-life-att', String(a.id));
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
      types.includes('application/x-tracker-life-att') ||
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
      this.uploadLifeFilesAndPlace(Array.from(files), this.noteEditingId, dropAt);
      return;
    }

    const idRaw =
      ev.dataTransfer?.getData('application/x-tracker-life-att') ||
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

  private uploadLifeFilesAndPlace(files: File[], noteId: number, atIndex: number): void {
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
