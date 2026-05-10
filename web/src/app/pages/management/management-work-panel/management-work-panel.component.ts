import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  BalanceUrgency,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  ManagementTaskWriteBody,
  ManagementWorkLogAttachmentDto,
  ManagementWorkLogCalendarDto,
  ManagementWorkLogEntryDto,
  TaskMonthCalendarDto,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { SafeMarkdownPipe } from '../../../pipes/safe-markdown.pipe';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { mgmtTaskDueRowClass } from '../../../util/management-task-due';
import { WorkLogAudioPlayerComponent } from './work-log-audio-player.component';

@Component({
  selector: 'app-management-work-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
    SafeMarkdownPipe,
    WorkLogAudioPlayerComponent,
  ],
  templateUrl: './management-work-panel.component.html',
  styleUrl: './management-work-panel.component.scss',
})
export class ManagementWorkPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  readonly urgencies: BalanceUrgency[] = ['LOW', 'MEDIUM', 'HIGH'];
  readonly taskColumns = ['title', 'urgency', 'type', 'due', 'done', 'actions'];

  workCategoryId: number | null = null;
  categories: ManagementTaskCategory[] = [];
  taskTypes: ManagementTaskType[] = [];

  taskCalYear = new Date().getFullYear();
  taskCalMonth = new Date().getMonth() + 1;
  taskMonthCal: TaskMonthCalendarDto | null = null;
  taskUnscheduled: ManagementTaskDto[] = [];
  taskSelectedIso = '';

  quickTask = {
    title: '',
    dueDate: null as Date | null,
    urgency: 'MEDIUM' as BalanceUrgency,
    taskTypeId: null as number | null,
  };

  logView: 'day' | 'month' | 'year' = 'day';
  logYear = new Date().getFullYear();
  logMonth = new Date().getMonth() + 1;
  logSelectedDayIso = this.toIsoDate(new Date());
  logSearch = '';
  logRawEntries: ManagementWorkLogEntryDto[] = [];
  logCalendar: ManagementWorkLogCalendarDto | null = null;
  logLoading = false;
  logUploading = false;
  logEditingId: number | null = null;
  logDraft = {
    entryDate: this.toIsoDate(new Date()),
    subject: '',
    body: '',
  };

  readonly logMonthOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.resolveWorkCategory();
    this.reloadTasks();
    this.reloadLogCalendar();
    this.reloadLogEntries();
  }

  monthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'long' });
  }

  shortMonthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'short' });
  }

  logMonthCells(): { month: number; count: number }[] {
    const cells: { month: number; count: number }[] = [];
    const byMonth = new Map<number, number>();
    for (let m = 1; m <= 12; m++) {
      byMonth.set(m, 0);
    }
    for (const d of this.logCalendar?.days ?? []) {
      if (!d.date.startsWith(`${this.logYear}-`)) {
        continue;
      }
      const mo = Number(d.date.slice(5, 7));
      if (mo >= 1 && mo <= 12) {
        byMonth.set(mo, (byMonth.get(mo) ?? 0) + d.count);
      }
    }
    for (let m = 1; m <= 12; m++) {
      cells.push({ month: m, count: byMonth.get(m) ?? 0 });
    }
    return cells;
  }

  displayedLogEntries(): ManagementWorkLogEntryDto[] {
    const q = (this.logSearch || '').trim().toLowerCase();
    if (!q) {
      return this.logRawEntries;
    }
    const tokens = q.split(/\s+/).filter(Boolean);
    return this.logRawEntries.filter((e) => this.entryMatchesTokens(e, tokens));
  }

  workTasksForSelectedDay(): ManagementTaskDto[] {
    const cal = this.taskMonthCal;
    if (!cal?.tasksByDay || !this.taskSelectedIso) {
      return [];
    }
    const rows = [...(cal.tasksByDay[this.taskSelectedIso] ?? [])];
    return rows.filter((r) => this.isWorkTask(r)).sort((a, b) => a.title.localeCompare(b.title));
  }

  workUnscheduled(): ManagementTaskDto[] {
    return this.taskUnscheduled.filter((r) => this.isWorkTask(r)).sort((a, b) => a.title.localeCompare(b.title));
  }

  urgencyClass(u: BalanceUrgency): string {
    if (u === 'HIGH') {
      return 'urgency-high';
    }
    if (u === 'LOW') {
      return 'urgency-low';
    }
    return 'urgency-mid';
  }

  taskDueRowClass(row: ManagementTaskDto): string {
    return mgmtTaskDueRowClass(row, this.todayIso());
  }

  taskCountOnDay(iso: string): number {
    const cal = this.taskMonthCal;
    if (!cal?.tasksByDay) {
      return 0;
    }
    const rows = cal.tasksByDay[iso] ?? [];
    return rows.filter((r) => this.isWorkTask(r)).length;
  }

  taskCalendarTitle(): string {
    return new Date(this.taskCalYear, this.taskCalMonth - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  prevTaskMonth(): void {
    if (this.taskCalMonth <= 1) {
      this.taskCalMonth = 12;
      this.taskCalYear -= 1;
    } else {
      this.taskCalMonth -= 1;
    }
    this.clampTaskSelectedToMonth();
    this.reloadTasks();
  }

  nextTaskMonth(): void {
    if (this.taskCalMonth >= 12) {
      this.taskCalMonth = 1;
      this.taskCalYear += 1;
    } else {
      this.taskCalMonth += 1;
    }
    this.clampTaskSelectedToMonth();
    this.reloadTasks();
  }

  selectTaskDay(iso: string): void {
    this.taskSelectedIso = iso;
  }

  isTaskDaySelected(iso: string): boolean {
    return this.taskSelectedIso === iso;
  }

  /** Calendar rows for task month (work counts only in badge via taskCountOnDay). */
  taskCalendarRows(): { trackKey: string; type: 'pad' | 'day'; iso?: string; label?: string }[][] {
    const y = this.taskCalYear;
    const m = this.taskCalMonth;
    const firstDow = new Date(y, m - 1, 1).getDay();
    const lastDate = new Date(y, m, 0).getDate();
    const rows: { trackKey: string; type: 'pad' | 'day'; iso?: string; label?: string }[][] = [];
    let day = 1;
    const row0: { trackKey: string; type: 'pad' | 'day'; iso?: string; label?: string }[] = [];
    for (let i = 0; i < firstDow; i++) {
      row0.push({ trackKey: `pad-0-${i}`, type: 'pad' });
    }
    while (row0.length < 7 && day <= lastDate) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      row0.push({ trackKey: iso, type: 'day', iso, label: String(day) });
      day++;
    }
    rows.push(row0);
    while (day <= lastDate) {
      const row: { trackKey: string; type: 'pad' | 'day'; iso?: string; label?: string }[] = [];
      while (row.length < 7 && day <= lastDate) {
        const iso = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        row.push({ trackKey: iso, type: 'day', iso, label: String(day) });
        day++;
      }
      while (row.length < 7) {
        row.push({ trackKey: `pad-${rows.length}-${row.length}`, type: 'pad' });
      }
      rows.push(row);
    }
    return rows;
  }

  saveQuickWorkTask(): void {
    const title = (this.quickTask.title || '').trim();
    if (!title) {
      this.snackBar.open('Enter a task title', 'Dismiss', { duration: 4000 });
      return;
    }
    if (this.workCategoryId == null) {
      this.snackBar.open('Create a balance category named Work (Admin → Management) first.', 'Dismiss', { duration: 6000 });
      return;
    }
    const body: ManagementTaskWriteBody = {
      title,
      notes: '',
      dueDate: this.quickTask.dueDate ? this.toIsoDate(this.quickTask.dueDate) : null,
      urgency: this.quickTask.urgency,
      categoryId: this.workCategoryId,
      taskTypeId: this.quickTask.taskTypeId,
      completed: false,
    };
    this.api.createTask(body).subscribe({
      next: () => {
        this.snackBar.open('Work task added', undefined, { duration: 2500 });
        this.quickTask = {
          title: '',
          dueDate: null,
          urgency: 'MEDIUM',
          taskTypeId: null,
        };
        this.reloadTasks();
      },
      error: (e) => this.err('Could not add task', e),
    });
  }

  toggleWorkTaskDone(row: ManagementTaskDto, checked: boolean): void {
    this.api
      .updateTask(row.id, {
        title: row.title,
        notes: row.notes ?? '',
        dueDate: row.dueDate ?? null,
        urgency: row.urgency,
        categoryId: row.categoryId ?? null,
        taskTypeId: row.taskTypeId ?? null,
        completed: checked,
      })
      .subscribe({
        next: () => this.reloadTasks(),
        error: (e) => this.err('Could not update task', e),
      });
  }

  deleteWorkTask(row: ManagementTaskDto): void {
    this.api.deleteTask(row.id).subscribe({
      next: () => {
        this.snackBar.open('Task removed', undefined, { duration: 2500 });
        this.reloadTasks();
      },
      error: (e) => this.err('Could not delete task', e),
    });
  }

  setLogView(v: 'day' | 'month' | 'year'): void {
    this.logView = v;
    if (v === 'day') {
      this.syncLogSelectedDayIsoToLogYear();
    }
    this.reloadLogEntries();
  }

  selectLogMonth(m: number): void {
    this.logMonth = m;
    if (this.logView === 'month') {
      this.reloadLogEntries();
    }
  }

  selectLogYearOnly(): void {
    this.logMonth = 1;
    this.reloadLogCalendar();
    if (this.logView === 'year') {
      this.reloadLogEntries();
    }
  }

  prevLogYear(): void {
    this.logYear -= 1;
    if (this.logView === 'day') {
      this.syncLogSelectedDayIsoToLogYear();
    }
    this.reloadLogCalendar();
    this.reloadLogEntries();
  }

  nextLogYear(): void {
    this.logYear += 1;
    if (this.logView === 'day') {
      this.syncLogSelectedDayIsoToLogYear();
    }
    this.reloadLogCalendar();
    this.reloadLogEntries();
  }

  onLogDayPicked(): void {
    const parsed = this.parseDayIso(this.logSelectedDayIso);
    if (parsed) {
      this.logYear = parsed.y;
      this.logMonth = parsed.m;
      this.reloadLogCalendar();
    }
    if (this.logView === 'day') {
      this.reloadLogEntries();
    }
  }

  saveLogEntry(): void {
    const subject = (this.logDraft.subject || '').trim();
    const body = {
      entryDate: this.logDraft.entryDate,
      subject,
      body: this.logDraft.body ?? '',
    };
    const call =
      this.logEditingId == null
        ? this.api.createWorkLogEntry(body)
        : this.api.updateWorkLogEntry(this.logEditingId, body);
    call.subscribe({
      next: () => {
        this.snackBar.open(this.logEditingId == null ? 'Log entry saved' : 'Log entry updated', undefined, {
          duration: 2500,
        });
        this.resetLogForm();
        this.reloadLogEntries();
        this.reloadLogCalendar();
      },
      error: (e) => this.err('Could not save work log', e),
    });
  }

  startEditLogEntry(e: ManagementWorkLogEntryDto): void {
    this.logEditingId = e.id;
    this.logDraft = {
      entryDate: e.entryDate,
      subject: e.subject,
      body: e.body,
    };
  }

  cancelLogEdit(): void {
    this.resetLogForm();
  }

  deleteLogEntry(e: ManagementWorkLogEntryDto): void {
    this.api.deleteWorkLogEntry(e.id).subscribe({
      next: () => {
        this.snackBar.open('Entry deleted', undefined, { duration: 2500 });
        if (this.logEditingId === e.id) {
          this.resetLogForm();
        }
        this.reloadLogEntries();
        this.reloadLogCalendar();
      },
      error: (err) => this.err('Could not delete entry', err),
    });
  }

  isAudioAttachment(a: ManagementWorkLogAttachmentDto): boolean {
    const ct = (a.contentType || '').toLowerCase();
    if (ct.startsWith('audio/')) {
      return true;
    }
    const fn = (a.originalFilename || '').toLowerCase();
    return /\.(mp3|m4a|aac|wav|webm|ogg|flac|opus)$/.test(fn);
  }

  onWorkLogFilesSelected(event: Event, entryId: number): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    this.logUploading = true;
    const list = Array.from(files);
    let i = 0;
    const step = (): void => {
      if (i >= list.length) {
        this.logUploading = false;
        input.value = '';
        this.reloadLogEntries();
        this.reloadLogCalendar();
        this.snackBar.open('Attachment(s) uploaded', undefined, { duration: 2000 });
        return;
      }
      this.api.uploadWorkLogAttachment(entryId, list[i]).subscribe({
        next: () => {
          i += 1;
          step();
        },
        error: (e) => {
          this.logUploading = false;
          input.value = '';
          this.err('Upload failed', e);
        },
      });
    };
    step();
  }

  openWorkLogAttachment(a: ManagementWorkLogAttachmentDto): void {
    this.api.getWorkLogAttachmentBlob(a.id, 'inline').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const w = window.open(url, '_blank', 'noopener');
        if (!w) {
          URL.revokeObjectURL(url);
        } else {
          w.addEventListener('beforeunload', () => URL.revokeObjectURL(url));
        }
      },
      error: (e) => this.err('Could not open attachment', e),
    });
  }

  removeWorkLogAttachment(_entryId: number, attachmentId: number): void {
    this.api.deleteWorkLogAttachment(attachmentId).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', undefined, { duration: 2000 });
        this.reloadLogEntries();
        this.reloadLogCalendar();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
  }

  private resetLogForm(): void {
    this.logEditingId = null;
    this.logDraft = {
      entryDate: this.logView === 'day' ? this.logSelectedDayIso : this.toIsoDate(new Date()),
      subject: '',
      body: '',
    };
    if (this.logView === 'day') {
      this.logDraft.entryDate = this.logSelectedDayIso;
    }
  }

  private resolveWorkCategory(): void {
    this.api.listCategories().subscribe({
      next: (cats) => {
        this.categories = cats;
        const w = cats.find((c) => (c.name || '').trim().toLowerCase() === 'work');
        this.workCategoryId = w?.id ?? null;
      },
      error: (e) => this.err('Could not load categories', e),
    });
  }

  private isWorkTask(row: ManagementTaskDto): boolean {
    if (this.workCategoryId != null && row.categoryId === this.workCategoryId) {
      return true;
    }
    return (row.categoryName || '').trim().toLowerCase() === 'work';
  }

  private reloadTasks(): void {
    forkJoin({
      cal: this.api.taskCalendar(this.taskCalYear, this.taskCalMonth).pipe(catchError(() => of<TaskMonthCalendarDto | null>(null))),
      un: this.api.listUnscheduledTasks().pipe(catchError(() => of<ManagementTaskDto[]>([]))),
      tt: this.api.listTaskTypes().pipe(catchError(() => of<ManagementTaskType[]>([]))),
    }).subscribe({
      next: ({ cal, un, tt }) => {
        this.taskMonthCal = cal;
        this.taskUnscheduled = un;
        this.taskTypes = tt;
        if (!this.taskSelectedIso) {
          this.taskSelectedIso = this.defaultDayInTaskMonth();
        }
      },
      error: () => {},
    });
  }

  private reloadLogCalendar(): void {
    this.api.workLogCalendar(this.logYear).subscribe({
      next: (c) => (this.logCalendar = c),
      error: () => (this.logCalendar = null),
    });
  }

  /** Public for template bindings (month selector, etc.). */
  reloadLogEntries(): void {
    this.logLoading = true;
    let sub;
    if (this.logView === 'day') {
      sub = this.api.workLogListForDay(this.logSelectedDayIso);
    } else if (this.logView === 'month') {
      const from = `${this.logYear}-${String(this.logMonth).padStart(2, '0')}-01`;
      const last = new Date(this.logYear, this.logMonth, 0).getDate();
      const to = `${this.logYear}-${String(this.logMonth).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
      sub = this.api.workLogListBetween(from, to);
    } else {
      sub = this.api.workLogListBetween(`${this.logYear}-01-01`, `${this.logYear}-12-31`);
    }
    sub.subscribe({
      next: (rows) => {
        this.logRawEntries = rows;
        this.logLoading = false;
      },
      error: (e) => {
        this.logRawEntries = [];
        this.logLoading = false;
        this.err('Could not load work log', e);
      },
    });
  }

  private clampTaskSelectedToMonth(): void {
    const y = this.taskCalYear;
    const m = this.taskCalMonth;
    const last = new Date(y, m, 0).getDate();
    if (!this.taskSelectedIso) {
      this.taskSelectedIso = this.defaultDayInTaskMonth();
      return;
    }
    const ys = Number(this.taskSelectedIso.slice(0, 4));
    const ms = Number(this.taskSelectedIso.slice(5, 7));
    if (ys !== y || ms !== m) {
      this.taskSelectedIso = this.defaultDayInTaskMonth();
      return;
    }
    const d = Number(this.taskSelectedIso.slice(8, 10));
    if (d > last) {
      this.taskSelectedIso = `${y}-${String(m).padStart(2, '0')}-${String(last).padStart(2, '0')}`;
    }
  }

  private defaultDayInTaskMonth(): string {
    const y = this.taskCalYear;
    const m = this.taskCalMonth;
    const today = new Date();
    if (today.getFullYear() === y && today.getMonth() + 1 === m) {
      return this.toIsoDate(today);
    }
    return `${y}-${String(m).padStart(2, '0')}-01`;
  }

  private entryMatchesTokens(e: ManagementWorkLogEntryDto, tokens: string[]): boolean {
    const attNames = (e.attachments ?? []).map((a) => a.originalFilename || '').join(' ');
    const hay = `${e.subject || ''} ${e.body || ''} ${attNames}`.toLowerCase();
    return tokens.every((t) => hay.includes(t));
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Parse `yyyy-MM-dd`; returns null if invalid or incomplete. */
  private parseDayIso(iso: string): { y: number; m: number; d: number } | null {
    const s = (iso || '').trim();
    if (s.length < 10) {
      return null;
    }
    const y = Number(s.slice(0, 4));
    const m = Number(s.slice(5, 7));
    const d = Number(s.slice(8, 10));
    if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d)) {
      return null;
    }
    if (m < 1 || m > 12 || d < 1 || d > 31) {
      return null;
    }
    return { y, m, d };
  }

  /**
   * In day view the entry list follows `logSelectedDayIso`, while the strip uses `logYear`.
   * After changing year, move the selected calendar day into that year (same month/day, clamped).
   */
  private syncLogSelectedDayIsoToLogYear(): void {
    const parsed = this.parseDayIso(this.logSelectedDayIso);
    let m: number;
    let d: number;
    if (parsed) {
      m = parsed.m;
      d = parsed.d;
    } else {
      const today = new Date();
      if (this.logYear === today.getFullYear()) {
        m = today.getMonth() + 1;
        d = today.getDate();
      } else {
        m = 1;
        d = 1;
      }
    }
    const last = new Date(this.logYear, m, 0).getDate();
    const day = Math.min(d, last);
    this.logSelectedDayIso = `${this.logYear}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    if (this.logEditingId == null) {
      this.logDraft.entryDate = this.logSelectedDayIso;
    }
  }

  private todayIso(): string {
    return this.toIsoDate(new Date());
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
