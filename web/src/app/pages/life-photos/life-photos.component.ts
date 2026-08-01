import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LifeMonthNoteCalendarDto, LifeMonthNoteDto, LifeMonthNoteWriteBody } from '../../models/life.models';
import { SafeMarkdownPipe } from '../../pipes/safe-markdown.pipe';
import { LifeApiService } from '../../services/life-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

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
    SafeMarkdownPipe,
  ],
  templateUrl: './life-photos.component.html',
  styleUrl: './life-photos.component.scss',
})
export class LifePhotosComponent implements OnInit {
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

  isImageAttachment(contentType: string | null): boolean {
    return !!contentType && contentType.toLowerCase().startsWith('image/');
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
        this.syncNoteSelectionAfterLoad();
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
  }

  private emptyNoteCalendarMonths(): { month: number; noteCount: number }[] {
    return Array.from({ length: 12 }, (_, i) => ({ month: i + 1, noteCount: 0 }));
  }

  startNewMonthNote(): void {
    this.noteEditingId = null;
    this.noteSelectedId = null;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
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
    }
    this.noteViewMode = 'read';
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
    this.noteViewMode = 'read';
    this.noteEditingId = null;
    this.noteDraft = {
      year: this.noteYear,
      month: this.noteFilterMonth != null ? this.noteFilterMonth : this.noteDraft.month,
      subject: '',
      body: '',
    };
    this.syncNoteSelectionAfterLoad();
  }

  startEditMonthNote(n: LifeMonthNoteDto): void {
    this.noteEditingId = n.id;
    this.noteSelectedId = n.id;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
    this.noteDraft = {
      year: n.year,
      month: n.month,
      subject: n.subject,
      body: n.body,
    };
  }

  saveMonthNote(): void {
    const subject = (this.noteDraft.subject || '').trim();
    if (!subject) {
      this.err('Subject is required');
      return;
    }
    const body: LifeMonthNoteWriteBody = {
      year: this.noteDraft.year,
      month: this.noteDraft.month,
      subject,
      body: this.noteDraft.body ?? '',
    };
    if (this.noteEditingId != null) {
      this.api.updateMonthNote(this.noteEditingId, body).subscribe({
        next: (row) => {
          this.snackBar.open('Note saved', 'Dismiss', { duration: 2500 });
          this.noteSelectedId = row.id;
          this.noteViewMode = 'read';
          this.noteEditingId = null;
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not save note', e),
      });
    } else {
      this.api.createMonthNote(body).subscribe({
        next: (row) => {
          this.snackBar.open('Note created', 'Dismiss', { duration: 2500 });
          this.noteSelectedId = row.id;
          this.noteViewMode = 'read';
          this.noteEditingId = null;
          this.noteYear = row.year;
          this.noteFilterMonth = row.month;
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not create note', e),
      });
    }
  }

  deleteMonthNote(n: LifeMonthNoteDto): void {
    if (!confirm(`Delete “${n.subject}”?`)) {
      return;
    }
    this.api.deleteMonthNote(n.id).subscribe({
      next: () => {
        this.snackBar.open('Note deleted', 'Dismiss', { duration: 2500 });
        if (this.noteSelectedId === n.id) {
          this.noteSelectedId = null;
        }
        this.reloadMonthNotesData();
      },
      error: (e) => this.err('Could not delete note', e),
    });
  }

  onMonthNoteFilesSelected(ev: Event, noteId: number): void {
    const input = ev.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    input.value = '';
    if (!files.length) {
      return;
    }
    this.noteUploading = true;
    let remaining = files.length;
    let ok = 0;
    for (const file of files) {
      this.api.uploadMonthNoteAttachment(noteId, file).subscribe({
        next: () => {
          ok += 1;
          remaining -= 1;
          if (remaining === 0) {
            this.noteUploading = false;
            this.snackBar.open(ok === 1 ? 'Photo added' : `${ok} files added`, 'Dismiss', {
              duration: 3000,
            });
            this.reloadMonthNotesData();
          }
        },
        error: (e) => {
          remaining -= 1;
          this.err(`Upload failed for ${file.name}`, e);
          if (remaining === 0) {
            this.noteUploading = false;
            if (ok) {
              this.reloadMonthNotesData();
            }
          }
        },
      });
    }
  }

  openMonthNoteAttachment(id: number, filename: string): void {
    this.api.getMonthNoteAttachmentBlob(id, 'inline').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (e) => this.err(`Could not open ${filename}`, e),
    });
  }

  removeMonthNoteAttachment(noteId: number, attachmentId: number): void {
    if (!confirm('Remove this attachment?')) {
      return;
    }
    this.api.deleteMonthNoteAttachment(attachmentId).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', 'Dismiss', { duration: 2500 });
        this.reloadMonthNotesData();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
  }

  private err(msg: string, e?: unknown): void {
    const detail = e != null ? formatHttpErrorDetail(e) : '';
    this.snackBar.open(detail ? `${msg}: ${detail}` : msg, 'Dismiss', { duration: 6000 });
  }
}
