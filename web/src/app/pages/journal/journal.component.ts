import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  JournalAttachmentDto,
  JournalEntryDto,
  JournalEntryWriteBody,
  JournalTagDefDto,
  JournalCalendarDayDto,
} from '../../models/journal.models';
import { JournalApiService } from '../../services/journal-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import { SafeMarkdownPipe } from '../../pipes/safe-markdown.pipe';
import {
  JournalAttachmentPreviewComponent,
  JournalAttachmentPreviewData,
} from './journal-attachment-preview.component';
import { JournalCoursesPanelComponent } from './journal-courses-panel/journal-courses-panel.component';
import { JournalBooksPanelComponent } from './journal-books-panel/journal-books-panel.component';
interface CalCell {
  type: 'pad' | 'day';
  dateIso?: string;
  label?: string;
  count?: number;
  level?: number;
  trackKey: string;
}

@Component({
  selector: 'app-journal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatDialogModule,
    MatTabsModule,
    SafeMarkdownPipe,
    JournalCoursesPanelComponent,
    JournalBooksPanelComponent,
  ],
  templateUrl: './journal.component.html',
  styleUrl: './journal.component.scss',
})
export class JournalComponent implements OnInit {
  private readonly api = inject(JournalApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  calendarYear = new Date().getFullYear();
  calendarMonth = new Date().getMonth() + 1;
  selectedDateIso = '';
  calendarDays: JournalCalendarDayDto[] = [];
  dayEntries: JournalEntryDto[] = [];
  tagDefs: JournalTagDefDto[] = [];

  form: JournalEntryWriteBody = { loggedOn: '', bodyMarkdown: '', tagIds: [] };
  editingId: number | null = null;
  /** Entry id currently receiving a multi-file upload, or null. */
  uploadingEntryId: number | null = null;

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.calendarYear = +t.slice(0, 4);
    this.calendarMonth = +t.slice(5, 7);
    this.resetForm();
    this.reloadTagsAndCalendar();
  }

  get calendarTitle(): string {
    return new Date(this.calendarYear, this.calendarMonth - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  reloadTagsAndCalendar(): void {
    forkJoin({
      tags: this.api.listTagDefinitions().pipe(catchError(() => of<JournalTagDefDto[]>([]))),
      cal: this.api.calendar(this.calendarYear, this.calendarMonth).pipe(
        catchError(() => of<JournalCalendarDayDto[]>([])),
      ),
    }).subscribe({
      next: ({ tags, cal }) => {
        this.tagDefs = tags;
        this.calendarDays = cal;
        this.loadDay();
      },
      error: (e) => this.err('Could not load journal', e),
    });
  }

  loadDay(): void {
    this.api.listEntriesForDay(this.selectedDateIso).subscribe({
      next: (rows) => (this.dayEntries = rows),
      error: (e) => this.err('Could not load entries', e),
    });
  }

  calendarRows(): CalCell[][] {
    const y = this.calendarYear;
    const m = this.calendarMonth;
    const byDate = new Map(this.calendarDays.map((d) => [d.date, d]));
    const last = new Date(y, m, 0).getDate();
    const firstDow = new Date(y, m - 1, 1).getDay();
    const flat: CalCell[] = [];
    let padSeq = 0;
    for (let i = 0; i < firstDow; i++) {
      padSeq += 1;
      flat.push({ type: 'pad', trackKey: `pad-${padSeq}` });
    }
    for (let d = 1; d <= last; d++) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const day = byDate.get(iso);
      flat.push({
        type: 'day',
        dateIso: iso,
        label: String(d),
        count: day?.entryCount ?? 0,
        level: day?.level ?? 0,
        trackKey: `d-${iso}`,
      });
    }
    let tail = 0;
    while (flat.length % 7 !== 0) {
      tail += 1;
      flat.push({ type: 'pad', trackKey: `pt-${tail}` });
    }
    const rows: CalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  isToday(iso: string | undefined): boolean {
    return !!iso && iso === this.todayIso();
  }

  isSelected(iso: string | undefined): boolean {
    return !!iso && iso === this.selectedDateIso;
  }

  dayTooltip(c: CalCell): string {
    if (c.type !== 'day' || !c.dateIso) {
      return '';
    }
    const n = c.count ?? 0;
    return `${c.dateIso} — ${n} ${n === 1 ? 'entry' : 'entries'}`;
  }

  selectDay(c: CalCell): void {
    if (c.type !== 'day' || !c.dateIso) {
      return;
    }
    this.selectedDateIso = c.dateIso;
    this.resetForm();
    this.loadDay();
  }

  prevMonth(): void {
    let y = this.calendarYear;
    let mo = this.calendarMonth - 1;
    if (mo < 1) {
      mo = 12;
      y -= 1;
    }
    this.calendarYear = y;
    this.calendarMonth = mo;
    this.clampSelectedToMonth();
    this.api.calendar(y, mo).subscribe({
      next: (c) => (this.calendarDays = c),
      error: (e) => this.err('Could not load calendar', e),
    });
    this.loadDay();
  }

  nextMonth(): void {
    let y = this.calendarYear;
    let mo = this.calendarMonth + 1;
    if (mo > 12) {
      mo = 1;
      y += 1;
    }
    this.calendarYear = y;
    this.calendarMonth = mo;
    this.clampSelectedToMonth();
    this.api.calendar(y, mo).subscribe({
      next: (c) => (this.calendarDays = c),
      error: (e) => this.err('Could not load calendar', e),
    });
    this.loadDay();
  }

  private clampSelectedToMonth(): void {
    const y = this.calendarYear;
    const m = this.calendarMonth;
    const last = new Date(y, m, 0).getDate();
    if (!this.selectedDateIso) {
      this.selectedDateIso = this.defaultDayInMonth(y, m);
      return;
    }
    const ySel = +this.selectedDateIso.slice(0, 4);
    const mSel = +this.selectedDateIso.slice(5, 7);
    if (ySel !== y || mSel !== m) {
      this.selectedDateIso = this.defaultDayInMonth(y, m);
    } else {
      const d = +this.selectedDateIso.slice(8, 10);
      const day = Math.min(Math.max(1, d), last);
      this.selectedDateIso = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  private defaultDayInMonth(y: number, m: number): string {
    const t = this.todayIso();
    if (+t.slice(0, 4) === y && +t.slice(5, 7) === m) {
      return t;
    }
    return `${y}-${String(m).padStart(2, '0')}-01`;
  }

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  selectedDayLabel(): string {
    const iso = this.selectedDateIso;
    if (iso.length < 10) {
      return '';
    }
    const y = +iso.slice(0, 4);
    const m = +iso.slice(5, 7);
    const d = +iso.slice(8, 10);
    return new Date(y, m - 1, d).toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }

  attachmentCountSuffix(e: JournalEntryDto): string {
    const n = e.attachmentCount ?? e.attachments?.length ?? 0;
    if (n < 1) {
      return '';
    }
    return ` · ${n} ${n === 1 ? 'attachment' : 'attachments'}`;
  }

  resetForm(): void {
    this.editingId = null;
    this.form = {
      loggedOn: this.selectedDateIso,
      bodyMarkdown: '',
      tagIds: [],
    };
  }

  startEdit(e: JournalEntryDto): void {
    this.editingId = e.id;
    this.form = {
      loggedOn: e.loggedOn,
      bodyMarkdown: e.bodyMarkdown,
      tagIds: (e.tags ?? []).map((t) => t.id),
    };
  }

  save(): void {
    const body: JournalEntryWriteBody = {
      loggedOn: this.form.loggedOn,
      bodyMarkdown: (this.form.bodyMarkdown ?? '').trim() || ' ',
      tagIds: this.form.tagIds ?? [],
    };
    if (this.editingId != null) {
      this.api.updateEntry(this.editingId, body).subscribe({
        next: () => {
          this.snackBar.open('Entry saved', undefined, { duration: 2000 });
          this.resetForm();
          this.reloadTagsAndCalendar();
        },
        error: (e) => this.err('Could not update entry', e),
      });
    } else {
      this.api.createEntry(body).subscribe({
        next: () => {
          this.snackBar.open('Entry added', undefined, { duration: 2000 });
          this.resetForm();
          this.reloadTagsAndCalendar();
        },
        error: (e) => this.err('Could not add entry', e),
      });
    }
  }

  deleteEntry(e: JournalEntryDto): void {
    this.api.deleteEntry(e.id).subscribe({
      next: () => {
        this.snackBar.open('Entry deleted', undefined, { duration: 2000 });
        if (this.editingId === e.id) {
          this.resetForm();
        }
        this.reloadTagsAndCalendar();
      },
      error: (er) => this.err('Could not delete', er),
    });
  }

  onFileSelected(event: Event, entryId: number): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    if (!files.length) {
      input.value = '';
      return;
    }
    // Oldest image first (file mtime / name cues); server also sorts by EXIF captured_at.
    const ordered = this.sortFilesOldestFirst(files);
    this.uploadingEntryId = entryId;
    let i = 0;
    const step = (): void => {
      if (i >= ordered.length) {
        this.uploadingEntryId = null;
        input.value = '';
        this.snackBar.open(
          ordered.length === 1 ? 'Attachment uploaded' : `${ordered.length} attachments uploaded`,
          undefined,
          { duration: 2500 },
        );
        this.loadDay();
        this.api.calendar(this.calendarYear, this.calendarMonth).subscribe({
          next: (c) => (this.calendarDays = c),
        });
        return;
      }
      this.api.uploadAttachment(entryId, ordered[i]).subscribe({
        next: () => {
          i += 1;
          step();
        },
        error: (e) => {
          this.uploadingEntryId = null;
          input.value = '';
          this.err('Upload failed', e);
          this.loadDay();
        },
      });
    };
    step();
  }

  /** Display attachments oldest capture/upload first (matches server order). */
  sortedAttachments(entry: JournalEntryDto): JournalAttachmentDto[] {
    const atts = entry.attachments ?? [];
    return [...atts].sort((a, b) => {
      const ta = Date.parse(a.capturedAt || a.createdAt || '') || 0;
      const tb = Date.parse(b.capturedAt || b.createdAt || '') || 0;
      if (ta !== tb) {
        return ta - tb;
      }
      return a.id - b.id;
    });
  }

  /** Prefer lastModified (often capture time on phone exports), then filename date cues, then name. */
  private sortFilesOldestFirst(files: File[]): File[] {
    return [...files].sort((a, b) => {
      const ta = this.fileSortInstantMs(a);
      const tb = this.fileSortInstantMs(b);
      if (ta !== tb) {
        return ta - tb;
      }
      return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
    });
  }

  private fileSortInstantMs(file: File): number {
    const fromName = this.filenameCaptureMs(file.name);
    if (fromName != null) {
      return fromName;
    }
    return Number.isFinite(file.lastModified) ? file.lastModified : 0;
  }

  /** Best-effort parse of IMG_/SCR-/Screenshot filenames (Central-ish local wall clock). */
  private filenameCaptureMs(name: string): number | null {
    const base = name.replace(/^.*[/\\]/, '');
    const mac = base.match(
      /Screenshot\s+(\d{4})-(\d{2})-(\d{2})\s+at\s+(\d{1,2})[.:](\d{2})[.:](\d{2})(?:\s*(AM|PM))?/i,
    );
    if (mac) {
      let hour = Number(mac[4]);
      const minute = Number(mac[5]);
      const second = Number(mac[6]);
      const ampm = mac[7];
      if (ampm) {
        const pm = ampm.toUpperCase() === 'PM';
        if (pm && hour < 12) {
          hour += 12;
        } else if (!pm && hour === 12) {
          hour = 0;
        }
      }
      return Date.UTC(Number(mac[1]), Number(mac[2]) - 1, Number(mac[3]), hour, minute, second);
    }
    const compact = base.match(/(?:IMG[_-]?)?(\d{8})[_-](\d{6})/i) || base.match(/SCR[-_](\d{8})[-_]?(\d{6})?/i);
    if (compact) {
      const d = compact[1];
      const t = compact[2] || '000000';
      return Date.UTC(
        Number(d.slice(0, 4)),
        Number(d.slice(4, 6)) - 1,
        Number(d.slice(6, 8)),
        Number(t.slice(0, 2)),
        Number(t.slice(2, 4)),
        Number(t.slice(4, 6)),
      );
    }
    return null;
  }

  removeAttachment(id: number, ev: Event): void {
    ev.stopPropagation();
    this.api.deleteAttachment(id).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', undefined, { duration: 2000 });
        this.loadDay();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
  }

  openAttachment(a: JournalAttachmentDto): void {
    this.dialog.open<JournalAttachmentPreviewComponent, JournalAttachmentPreviewData>(JournalAttachmentPreviewComponent, {
      width: 'min(95vw, 48rem)',
      data: {
        attachmentId: a.id,
        filename: a.originalFilename,
        contentType: a.contentType ?? null,
      },
    });
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
