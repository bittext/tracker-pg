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
  insertImageWidthPct = 40;
  readonly imageWidthOptions = [
    { pct: 25, label: 'S' },
    { pct: 40, label: 'M' },
    { pct: 55, label: 'L' },
    { pct: 70, label: 'XL' },
    { pct: 100, label: 'Full' },
  ];
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
   * Insert (or resize) a stable HTML image that references the uploaded attachment.
   * Width is a % of the note column so embeds stay inset while writing.
   */
  insertImageIntoBody(a: LifeMonthNoteAttachmentDto, widthPct?: number): void {
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
    const name = (a.originalFilename || 'image').replace(/"/g, '');
    const src = `/api/life/notes/attachments/${a.id}/file`;
    const tag =
      `<img src="${src}" alt="${name}" data-life-width="${pct}" ` +
      `style="max-width:${pct}%;width:${pct}%;height:auto;" />`;
    const body = this.noteDraft.body ?? '';
    const imgRe = new RegExp(
      `<img\\b[^>]*\\bsrc=["'][^"']*\\/api\\/life\\/notes\\/attachments\\/${a.id}\\/file[^"']*["'][^>]*>`,
      'i',
    );
    const mdRe = new RegExp(`!\\[[^\\]]*\\]\\([^)]*\\/api\\/life\\/notes\\/attachments\\/${a.id}\\/file[^)]*\\)`, 'i');
    let next: string;
    if (imgRe.test(body)) {
      next = body.replace(imgRe, tag);
      this.snackBar.open(`Image size set to ${pct}% — Save the note`, undefined, { duration: 3000 });
    } else if (mdRe.test(body)) {
      next = body.replace(mdRe, tag);
      this.snackBar.open(`Image size set to ${pct}% — Save the note`, undefined, { duration: 3000 });
    } else {
      const sep = !body.trim() ? '' : body.endsWith('\n') ? '\n' : '\n\n';
      next = `${body}${sep}${tag}\n`;
      this.snackBar.open(`Image inserted at ${pct}% width — Save the note`, undefined, {
        duration: 3500,
      });
    }
    this.noteDraft = { ...this.noteDraft, body: next };
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
