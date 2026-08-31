import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, of, throwError } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import {
  LifeMonthNoteAttachmentDto,
  LifeMonthNoteCalendarDto,
  LifeMonthNoteDto,
  LifeMonthNoteWriteBody,
} from '../../models/life.models';
import { LifeApiService } from '../../services/life-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  NoteAutosave,
  NoteSaveStatus,
  noteDraftFingerprint,
} from '../../util/note-autosave';
import {
  WriteupImageRemoveEvent,
  WriteupMarkdownBodyComponent,
} from '../management/writeup-markdown-body.component';

const MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;

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
export class LifePhotosComponent implements OnInit, OnDestroy {
  private readonly api = inject(LifeApiService);
  private readonly snackBar = inject(MatSnackBar);

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
  /** Ignores out-of-order list responses when months are clicked quickly. */
  private noteListLoadSeq = 0;
  noteDraft: LifeMonthNoteWriteBody = {
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
    subject: '',
    body: '',
  };
  noteSaveStatus: NoteSaveStatus = 'idle';
  unusedThumbUrls: Record<number, string> = {};
  private noteLastSavedFp = '';
  private readonly noteAutosave = new NoteAutosave({
    persist: () => this.persistMonthNote$({ exitCompose: false, quiet: true }),
    onStatus: (s) => (this.noteSaveStatus = s),
  });

  ngOnInit(): void {
    this.reloadMonthNotesData();
  }

  ngOnDestroy(): void {
    this.noteAutosave.destroy();
    this.revokeUnusedThumbs();
  }

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.noteViewMode === 'compose' && this.noteAutosave.isDirty) {
      event.preventDefault();
      event.returnValue = '';
    }
  }

  onNoteDraftChanged(): void {
    if (this.noteViewMode !== 'compose') {
      return;
    }
    this.noteAutosave.markDirtyAndSchedule();
    this.syncUnusedThumbUrls();
  }

  onNoteDraftBlur(): void {
    if (this.noteViewMode !== 'compose') {
      return;
    }
    this.noteAutosave.flush();
  }

  get noteSaveStatusLabel(): string {
    switch (this.noteSaveStatus) {
      case 'dirty':
        return 'Unsaved changes…';
      case 'saving':
        return 'Saving…';
      case 'saved':
        return 'Saved';
      case 'error':
        return 'Save failed';
      default:
        return '';
    }
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

  get unusedLifeAttachments(): LifeMonthNoteAttachmentDto[] {
    const body = this.noteDraft.body ?? '';
    return this.noteSelectedAttachments.filter(
      (a) => this.isImageAttachment(a) && !this.bodyReferencesAttachment(body, a.id),
    );
  }

  unusedThumbUrl(id: number): string | null {
    return this.unusedThumbUrls[id] ?? null;
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
            this.syncUnusedThumbUrls();
          }
        } else {
          this.syncNoteSelectionAfterLoad();
          if (this.noteSelectedId != null) {
            const found = rows.find((r) => r.id === this.noteSelectedId);
            this.noteSelectedAttachments = [...(found?.attachments ?? [])];
          }
          this.syncUnusedThumbUrls();
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
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      this.noteLastSavedFp = '';
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
      this.noteSaveStatus = 'idle';
    });
  }

  selectMonthNote(n: LifeMonthNoteDto): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      this.noteLastSavedFp = '';
      this.noteSelectedId = n.id;
      this.noteViewMode = 'read';
      this.noteEditingId = null;
      this.noteSelectedAttachments = [...(n.attachments ?? [])];
      this.noteSaveStatus = 'idle';
    });
  }

  setNoteComposerPane(pane: 'split' | 'write' | 'preview'): void {
    this.noteComposerPane = pane;
  }

  selectNotesYearOnly(): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      this.noteFilterMonth = null;
      this.noteViewMode = 'read';
      this.noteEditingId = null;
      this.noteSaveStatus = 'idle';
      this.reloadMonthNotesListOnly();
    });
  }

  selectNoteMonth(m: number): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      if (this.noteFilterMonth === m) {
        this.noteFilterMonth = null;
      } else {
        this.noteFilterMonth = m;
        this.noteDraft.month = m;
      }
      this.noteViewMode = 'read';
      this.noteEditingId = null;
      this.noteSelectedId = null;
      this.noteSaveStatus = 'idle';
      this.reloadMonthNotesListOnly();
    });
  }

  prevNoteYear(): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      this.noteYear -= 1;
      this.noteDraft.year = this.noteYear;
      this.noteViewMode = 'read';
      this.noteEditingId = null;
      this.noteSaveStatus = 'idle';
      this.reloadMonthNotesData();
    });
  }

  nextNoteYear(): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
      this.noteYear += 1;
      this.noteDraft.year = this.noteYear;
      this.noteViewMode = 'read';
      this.noteEditingId = null;
      this.noteSaveStatus = 'idle';
      this.reloadMonthNotesData();
    });
  }

  resetMonthNoteForm(): void {
    this.noteAutosave.cancel();
    this.noteLastSavedFp = '';
    this.noteEditingId = null;
    this.noteDraft = {
      year: this.noteYear,
      month: this.noteFilterMonth != null ? this.noteFilterMonth : this.noteDraft.month,
      subject: '',
      body: '',
    };
    this.noteViewMode = 'read';
    this.noteSaveStatus = 'idle';
    this.syncNoteSelectionAfterLoad();
  }

  startEditMonthNote(n: LifeMonthNoteDto): void {
    this.noteAutosave.flush(() => {
      this.noteAutosave.cancel();
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
      this.noteLastSavedFp = noteDraftFingerprint(this.noteDraft);
      this.noteSaveStatus = 'idle';
      this.syncUnusedThumbUrls();
    });
  }

  saveMonthNote(): void {
    this.noteAutosave.cancel();
    this.persistMonthNote$({ exitCompose: true, quiet: false }).subscribe({
      error: () => undefined,
    });
  }

  private persistMonthNote$(opts: { exitCompose: boolean; quiet: boolean }): Observable<LifeMonthNoteDto> {
    if (this.noteViewMode !== 'compose' && opts.quiet) {
      return of(null as unknown as LifeMonthNoteDto);
    }

    const subject = (this.noteDraft.subject || '').trim() || 'Untitled';
    const body: LifeMonthNoteWriteBody = {
      year: this.noteDraft.year,
      month: this.noteDraft.month,
      subject,
      body: this.noteDraft.body || '',
    };
    const fp = noteDraftFingerprint(body);
    if (opts.quiet && fp === this.noteLastSavedFp && this.noteEditingId != null) {
      return of(null as unknown as LifeMonthNoteDto);
    }

    const req$ =
      this.noteEditingId != null
        ? this.api.updateMonthNote(this.noteEditingId, body)
        : this.api.createMonthNote(body);

    return req$.pipe(
      tap((saved) => {
        this.noteLastSavedFp = noteDraftFingerprint({
          year: saved.year,
          month: saved.month,
          subject: saved.subject,
          body: saved.body ?? '',
        });
        const wasCreate = this.noteEditingId == null;
        this.noteSelectedId = saved.id;
        if (!(this.noteDraft.subject || '').trim()) {
          this.noteDraft = { ...this.noteDraft, subject: saved.subject || 'Untitled' };
        }
        if (opts.exitCompose) {
          const wasUpdate = this.noteEditingId != null;
          this.noteViewMode = 'read';
          this.noteEditingId = null;
          this.noteYear = saved.year;
          this.noteFilterMonth = saved.month;
          if (!opts.quiet) {
            this.snackBar.open(wasUpdate ? 'Note updated' : 'Note saved', undefined, { duration: 2000 });
          }
          this.reloadMonthNotesData();
        } else {
          this.noteEditingId = saved.id;
          if (wasCreate) {
            this.reloadMonthNotesData();
          } else {
            this.patchNoteInList(saved);
          }
        }
      }),
      catchError((e) => {
        if (!opts.quiet) {
          this.err(this.noteEditingId != null ? 'Could not update note' : 'Could not save note', e);
        }
        return throwError(() => e);
      }),
    );
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

  onLifePhotosSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    input.value = '';
    if (!files.length) {
      return;
    }
    if (this.noteViewMode === 'compose') {
      this.addPhotosInCompose(files, this.noteBodyCaret);
      return;
    }
    this.addPhotosInRead(files);
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
  insertImageIntoBody(
    a: LifeMonthNoteAttachmentDto,
    widthPct?: number,
    atIndex?: number,
    quiet = false,
  ): void {
    if (!this.isImageAttachment(a)) {
      this.snackBar.open('Only image attachments can be inserted into the body', undefined, {
        duration: 3000,
      });
      return;
    }
    if (this.noteViewMode !== 'compose') {
      return;
    }
    const pct = Math.min(100, Math.max(10, widthPct ?? this.insertImageWidthPct));
    const tag = this.buildLifeImageTag(a, pct);
    let body = this.stripLifeImageEmbeds(this.noteDraft.body ?? '', a.id);
    const idx =
      atIndex != null
        ? atIndex
        : Math.max(0, Math.min(body.length, this.noteBodyCaret ?? body.length));
    const insertAt = Math.max(0, Math.min(body.length, idx));
    this.noteDraft = { ...this.noteDraft, body: this.insertTextAt(body, insertAt, tag) };
    this.noteBodyCaret = insertAt + tag.length + 1;
    this.onNoteDraftChanged();
    if (!quiet) {
      this.snackBar.open(`Photo placed at ${pct}%`, undefined, { duration: 2000 });
    }
  }

  onNoteBodyCaret(ev: Event): void {
    const t = ev.target as HTMLTextAreaElement;
    this.noteBodyCaret = t.selectionStart ?? (this.noteDraft.body ?? '').length;
  }

  onNoteEditorDragOver(ev: DragEvent): void {
    const types = ev.dataTransfer?.types ? Array.from(ev.dataTransfer.types) : [];
    if (!types.includes('Files')) {
      return;
    }
    ev.preventDefault();
    this.noteEditorDragOver = true;
    if (ev.dataTransfer) {
      ev.dataTransfer.dropEffect = 'copy';
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
    if (files && files.length) {
      this.noteBodyCaret = dropAt;
      this.addPhotosInCompose(Array.from(files), dropAt);
      queueMicrotask(() => {
        ta.focus();
        const caret = this.noteBodyCaret;
        ta.setSelectionRange(caret, caret);
      });
    }
  }

  private addPhotosInCompose(files: File[], atIndex?: number): void {
    const images = this.filterImageFiles(files);
    if (!images.length) {
      this.snackBar.open('Choose image files to add', undefined, { duration: 3000 });
      return;
    }
    this.ensureComposeNoteId$().subscribe({
      next: (noteId) => this.uploadLifeFilesAndPlace(images, noteId, atIndex ?? this.noteBodyCaret),
      error: (e) => this.err('Could not save note', e),
    });
  }

  private addPhotosInRead(files: File[]): void {
    const n = this.selectedMonthNote;
    if (!n) {
      return;
    }
    const images = this.filterImageFiles(files);
    if (!images.length) {
      this.snackBar.open('Choose image files to add', undefined, { duration: 3000 });
      return;
    }
    this.noteUploading = true;
    let i = 0;
    let body = n.body ?? '';
    const step = (): void => {
      if (i >= images.length) {
        this.noteUploading = false;
        this.api
          .updateMonthNote(n.id, {
            year: n.year,
            month: n.month,
            subject: n.subject,
            body,
          })
          .subscribe({
            next: () => {
              this.reloadMonthNotesData();
              this.snackBar.open('Photo(s) added', undefined, { duration: 2000 });
            },
            error: (e) => this.err('Could not save photos', e),
          });
        return;
      }
      this.api.uploadMonthNoteAttachment(n.id, images[i]).subscribe({
        next: (att) => {
          if (this.isImageAttachment(att)) {
            const tag = this.buildLifeImageTag(att);
            body = this.stripLifeImageEmbeds(body, att.id);
            body = this.insertTextAt(body, body.length, tag);
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

  private ensureComposeNoteId$(): Observable<number> {
    if (this.noteEditingId != null) {
      return of(this.noteEditingId);
    }
    return this.persistMonthNote$({ exitCompose: false, quiet: true }).pipe(
      switchMap((saved) => {
        const id = saved?.id ?? this.noteEditingId;
        if (id == null) {
          return throwError(() => new Error('Could not create note'));
        }
        return of(id);
      }),
    );
  }

  private uploadLifeFilesAndPlace(files: File[], noteId: number, atIndex: number): void {
    this.noteUploading = true;
    let i = 0;
    let caret = atIndex;
    const step = (): void => {
      if (i >= files.length) {
        this.noteUploading = false;
        this.reloadMonthNotesData();
        this.snackBar.open('Photo(s) added', undefined, { duration: 2000 });
        return;
      }
      this.api.uploadMonthNoteAttachment(noteId, files[i]).subscribe({
        next: (att) => {
          if (this.isImageAttachment(att)) {
            this.insertImageIntoBody(att, this.insertImageWidthPct, caret, true);
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

  private filterImageFiles(files: File[]): File[] {
    const images = files.filter(
      (f) => f.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(f.name),
    );
    const ok: File[] = [];
    let oversized = 0;
    for (const f of images) {
      if (f.size > MAX_ATTACHMENT_BYTES) {
        oversized += 1;
      } else {
        ok.push(f);
      }
    }
    if (oversized) {
      this.snackBar.open(`${oversized} photo(s) exceed 8 MB and were skipped`, undefined, {
        duration: 4000,
      });
    }
    return ok;
  }

  private buildLifeImageTag(a: LifeMonthNoteAttachmentDto, widthPct?: number): string {
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
    return (
      `<img src="${src}" alt="${name}" data-life-width="${pct}" data-life-float="${floatSide}" ` +
      `style="float:${floatCss};max-width:${pct}%;width:${pct}%;height:auto;margin:${margin};" />`
    );
  }

  private stripLifeImageEmbeds(body: string, attachmentId: number): string {
    const imgRe = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/life\\/notes\\/attachments\\/${attachmentId}\\/file[^"']*["'][^>]*>`,
      'gi',
    );
    const mdRe = new RegExp(
      `!\\[[^\\]]*\\]\\([^)]*\\/api\\/life\\/notes\\/attachments\\/${attachmentId}\\/file[^)]*\\)`,
      'gi',
    );
    return body.replace(imgRe, '').replace(mdRe, '').replace(/\n{3,}/g, '\n\n');
  }

  private bodyReferencesAttachment(body: string, attachmentId: number): boolean {
    return body.includes(`/api/life/notes/attachments/${attachmentId}/file`);
  }

  private insertTextAt(body: string, index: number, text: string): string {
    const i = Math.max(0, Math.min(body.length, index));
    const before = body.slice(0, i);
    const after = body.slice(i);
    const leftPad = !before.length || before.endsWith('\n') ? '' : '\n';
    const rightPad = !after.length || after.startsWith('\n') ? '' : '\n';
    return `${before}${leftPad}${text}${rightPad}${after}`;
  }

  onLifeImageRemove(ev: WriteupImageRemoveEvent): void {
    if (ev.kind !== 'life') {
      return;
    }
    this.removeLifePhoto(ev.id);
  }

  removeMonthNoteAttachment(attachmentId: number, ev?: Event): void {
    ev?.stopPropagation();
    this.removeLifePhoto(attachmentId);
  }

  private removeLifePhoto(attachmentId: number): void {
    if (!confirm('Remove this photo?')) {
      return;
    }
    if (this.noteViewMode === 'compose') {
      this.noteAutosave.cancel();
      this.noteDraft = {
        ...this.noteDraft,
        body: this.stripLifeImageEmbeds(this.noteDraft.body ?? '', attachmentId),
      };
      this.noteSelectedAttachments = this.noteSelectedAttachments.filter((a) => a.id !== attachmentId);
      this.syncUnusedThumbUrls();
    } else {
      const n = this.selectedMonthNote;
      if (n) {
        this.patchNoteInList({
          ...n,
          body: this.stripLifeImageEmbeds(n.body ?? '', attachmentId),
          attachments: (n.attachments ?? []).filter((a) => a.id !== attachmentId),
        });
      }
    }
    this.api.deleteMonthNoteAttachment(attachmentId).subscribe({
      next: () => {
        if (this.noteViewMode === 'compose' && this.noteEditingId != null) {
          this.persistMonthNote$({ exitCompose: false, quiet: true }).subscribe({
            error: () => undefined,
          });
        }
        this.snackBar.open('Photo removed', undefined, { duration: 2000 });
      },
      error: (e) => {
        this.err('Could not remove photo', e);
        this.reloadMonthNotesData();
      },
    });
  }

  private patchNoteInList(saved: LifeMonthNoteDto): void {
    const i = this.monthNotes.findIndex((n) => n.id === saved.id);
    if (i >= 0) {
      const next = this.monthNotes.slice();
      next[i] = saved;
      this.monthNotes = next;
    } else {
      this.monthNotes = [saved, ...this.monthNotes];
    }
    if (this.noteEditingId === saved.id || this.noteSelectedId === saved.id) {
      this.noteSelectedAttachments = [...(saved.attachments ?? [])];
      this.syncUnusedThumbUrls();
    }
  }

  private syncUnusedThumbUrls(): void {
    const keep = new Set(this.unusedLifeAttachments.map((a) => a.id));
    const next: Record<number, string> = { ...this.unusedThumbUrls };
    for (const id of Object.keys(next).map(Number)) {
      if (!keep.has(id)) {
        URL.revokeObjectURL(next[id]);
        delete next[id];
      }
    }
    this.unusedThumbUrls = next;
    for (const a of this.unusedLifeAttachments) {
      if (this.unusedThumbUrls[a.id]) {
        continue;
      }
      this.api.getMonthNoteAttachmentBlob(a.id, 'inline').subscribe({
        next: (blob) => {
          if (!this.unusedLifeAttachments.some((x) => x.id === a.id)) {
            return;
          }
          this.unusedThumbUrls = { ...this.unusedThumbUrls, [a.id]: URL.createObjectURL(blob) };
        },
      });
    }
  }

  private revokeUnusedThumbs(): void {
    for (const url of Object.values(this.unusedThumbUrls)) {
      URL.revokeObjectURL(url);
    }
    this.unusedThumbUrls = {};
  }

  private err(msg: string, e?: unknown): void {
    const detail = e != null ? formatHttpErrorDetail(e) : '';
    this.snackBar.open(detail ? `${msg}: ${detail}` : msg, 'Dismiss', { duration: 8000 });
  }
}
