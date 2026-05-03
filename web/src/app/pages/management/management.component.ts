import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, forkJoin, of } from 'rxjs';
import {
  BalanceUrgency,
  ManagementMonthNoteCalendarDto,
  ManagementMonthNoteDto,
  ManagementWriteupDto,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  TaskMonthCalendarDto,
} from '../../models/management.models';
import {
  REPORT_CALENDAR_FILTER_OPTIONS,
  ReportCalendarEntryDto,
  ReportCalendarType,
  ReportCalendarTypeFilter,
  reportCalendarTypeLabel,
} from '../../models/report-calendar.models';
import { ManagementApiService } from '../../services/management-api.service';
import { ReportCalendarApiService } from '../../services/report-calendar-api.service';
import { AuthService } from '../../services/auth.service';
import { SafeMarkdownPipe } from '../../pipes/safe-markdown.pipe';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  MgmtTaskDueVisual,
  mgmtCalendarDayDueVisual,
  mgmtTaskDueRowClass,
} from '../../util/management-task-due';
import {
  ReportCalendarEntryDialogComponent,
  ReportCalendarEntryDialogData,
} from '../reports/report-calendar-entry-dialog.component';

interface CalendarCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  taskCount?: number;
  /** When the day has open tasks, how that day relates to today (for calendar color). */
  dayDueVisual?: MgmtTaskDueVisual | null;
  trackKey: string;
}

interface ReportCalCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  hasEntry?: boolean;
  trackKey: string;
}

interface UtilityEntry {
  id: number;
  itemName: string;
  folder: string;
  username: string;
  password: string;
  authenticatorKey: string;
  /** Single website URL (https recommended). */
  website: string;
  notes: string;
  createdAt: string;
  updatedAt?: string;
}

@Component({
  selector: 'app-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    SafeMarkdownPipe,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatCheckboxModule,
  ],
  templateUrl: './management.component.html',
  styleUrl: './management.component.scss',
})
export class ManagementComponent implements OnInit {
  /** Legacy unscoped key (pre–per-user storage). Migrated to the `spulickal` user key when they sign in. */
  private static readonly UTILITIES_STORAGE_KEY_BASE = 'management.utilities.entries.v1';
  private readonly auth = inject(AuthService);
  private readonly api = inject(ManagementApiService);
  private readonly reportCalApi = inject(ReportCalendarApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  readonly urgencies: BalanceUrgency[] = ['LOW', 'MEDIUM', 'HIGH'];

  calendarYear = new Date().getFullYear();
  calendarMonth = new Date().getMonth() + 1;
  selectedDateIso = '';

  monthCal: TaskMonthCalendarDto | null = null;
  unscheduled: ManagementTaskDto[] = [];
  categories: ManagementTaskCategory[] = [];
  taskTypes: ManagementTaskType[] = [];
  selectedDayTasks: ManagementTaskDto[] = [];

  newTask = {
    title: '',
    notes: '',
    dueDate: null as Date | null,
    urgency: 'MEDIUM' as BalanceUrgency,
    categoryId: null as number | null,
    taskTypeId: null as number | null,
    completed: false,
  };

  editingId: number | null = null;

  dayTaskColumns = ['title', 'urgency', 'category', 'type', 'done', 'actions'];
  unscheduledColumns = ['uTitle', 'uUrgency', 'uCategory', 'uType', 'uDone', 'uActions'];

  /** Management -> Calendar tab state. */
  repCalTypeFilter: ReportCalendarTypeFilter = 'ALL';
  repCalView: 'day' | 'month' | 'year' = 'month';
  repCalAnchorIso = '';
  repCalEntries: ReportCalendarEntryDto[] = [];
  /** yyyy-MM when year view: list filtered to that month (toggle same month to clear). */
  repCalFocusedMonthKey: string | null = null;
  /** yyyy-MM-dd when month view: list filtered to that day (toggle same day to clear). */
  repCalFocusedDayIso: string | null = null;
  /** Narrows the entries table to titles containing this text (case-insensitive). */
  repCalTitleFilter = '';
  readonly repCalFilterOptions = REPORT_CALENDAR_FILTER_OPTIONS;
  readonly yearMonthIndex = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;
  readonly reportCalendarTypeLabel = reportCalendarTypeLabel;

  utilityEntryDraft = {
    itemName: '',
    folder: '',
    username: '',
    password: '',
    authenticatorKey: '',
    website: '',
    notes: '',
  };
  /** When set, the form is editing an existing record. */
  utilityEditingId: number | null = null;
  /** Selected row for the details panel. */
  selectedUtilityEntryId: number | null = null;
  /** Single search string across folder, item name, username, and website. */
  utilitySearchQuery = '';
  /** Password field in add/edit form: hidden until toggled. */
  utilityFormPasswordVisible = false;
  /** Reveal password in the details panel. */
  utilityDetailPasswordVisible = false;
  utilityEntries: UtilityEntry[] = [];

  readonly utilityTableColumns: string[] = ['folder', 'itemName', 'username', 'actions'];

  /** 0 Tasks, 1 Calendar, 2 Utilities, 3 Notes, 4 Write-up */
  private readonly MGMT_TAB_NOTES = 3;
  private readonly MGMT_TAB_WRITEUP = 4;

  noteYear = new Date().getFullYear();
  /** When set, list is limited to that month; when null, all months in the year. */
  noteFilterMonth: number | null = null;
  noteCalendar: ManagementMonthNoteCalendarDto | null = null;
  monthNotes: ManagementMonthNoteDto[] = [];
  noteEditingId: number | null = null;
  noteDraft = {
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
    subject: '',
    body: '',
  };
  noteUploading = false;
  readonly noteMonthOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;

  /** Write-up: year-scoped long-form entries (current user only; API-enforced). */
  writeupYear = new Date().getFullYear();
  writeupsRaw: ManagementWriteupDto[] = [];
  writeupSearch = '';
  writeupEditingId: number | null = null;
  writeupSelectedId: number | null = null;
  writeupDraft = {
    topic: '',
    highlight: '',
    body: '',
  };
  writeupSaving = false;

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.repCalAnchorIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.resetForm();
    this.reloadRefsAndCalendar();
    this.loadReportCalendar();
    this.loadUtilitiesFromStorage();
  }

  get filteredUtilityEntries(): UtilityEntry[] {
    const q = this.utilitySearchQuery.trim().toLowerCase();
    if (!q) {
      return this.utilityEntries;
    }
    return this.utilityEntries.filter((e) => {
      const folder = (e.folder || '').toLowerCase();
      const name = (e.itemName || '').toLowerCase();
      const user = (e.username || '').toLowerCase();
      const site = (e.website || '').toLowerCase();
      return folder.includes(q) || name.includes(q) || user.includes(q) || site.includes(q);
    });
  }

  get selectedUtilityEntry(): UtilityEntry | null {
    if (this.selectedUtilityEntryId == null) {
      return null;
    }
    return this.utilityEntries.find((e) => e.id === this.selectedUtilityEntryId) ?? null;
  }

  selectUtilityEntry(entry: UtilityEntry): void {
    this.selectedUtilityEntryId = entry.id;
    this.utilityDetailPasswordVisible = false;
  }

  trackByUtilityId = (_: number, e: UtilityEntry) => e.id;

  isUtilityRowSelected(entry: UtilityEntry): boolean {
    return this.selectedUtilityEntryId === entry.id;
  }

  startEditUtilityEntry(entry: UtilityEntry, ev?: Event): void {
    ev?.stopPropagation();
    this.utilityEditingId = entry.id;
    this.utilityFormPasswordVisible = false;
    this.utilityEntryDraft = {
      itemName: entry.itemName,
      folder: entry.folder,
      username: entry.username,
      password: entry.password,
      authenticatorKey: entry.authenticatorKey,
      website: entry.website,
      notes: entry.notes,
    };
  }

  saveUtilityEntry(): void {
    const itemName = this.utilityEntryDraft.itemName.trim();
    if (!itemName) {
      this.snackBar.open('Item name is required', undefined, { duration: 2500 });
      return;
    }
    const now = new Date().toISOString();
    const base = {
      itemName,
      folder: this.utilityEntryDraft.folder.trim(),
      username: this.utilityEntryDraft.username.trim(),
      password: this.utilityEntryDraft.password.trim(),
      authenticatorKey: this.utilityEntryDraft.authenticatorKey.trim(),
      website: this.utilityEntryDraft.website.trim(),
      notes: this.utilityEntryDraft.notes.trim(),
    };

    if (this.utilityEditingId != null) {
      const id = this.utilityEditingId;
      const prev = this.utilityEntries.find((e) => e.id === id);
      if (!prev) {
        this.snackBar.open('Entry no longer exists', undefined, { duration: 2500 });
        this.resetUtilityForm();
        return;
      }
      const updated: UtilityEntry = {
        ...base,
        id: prev.id,
        createdAt: prev.createdAt,
        updatedAt: now,
      };
      this.utilityEntries = this.utilityEntries.map((e) => (e.id === id ? updated : e));
      this.persistUtilitiesToStorage();
      this.resetUtilityForm();
      this.snackBar.open('Utility item updated', undefined, { duration: 2500 });
      return;
    }

    const entry: UtilityEntry = {
      ...base,
      id: Date.now(),
      createdAt: now,
    };
    this.utilityEntries = [entry, ...this.utilityEntries];
    this.persistUtilitiesToStorage();
    this.resetUtilityForm();
    this.selectedUtilityEntryId = entry.id;
    this.snackBar.open('Utility item saved', undefined, { duration: 2500 });
  }

  deleteUtilityEntry(id: number, ev?: Event): void {
    ev?.stopPropagation();
    if (!window.confirm('Delete this utility entry? This cannot be undone.')) {
      return;
    }
    this.utilityEntries = this.utilityEntries.filter((e) => e.id !== id);
    if (this.selectedUtilityEntryId === id) {
      this.selectedUtilityEntryId = null;
    }
    if (this.utilityEditingId === id) {
      this.resetUtilityForm();
    }
    this.persistUtilitiesToStorage();
    this.snackBar.open('Utility item removed', undefined, { duration: 2500 });
  }

  resetUtilityForm(): void {
    this.utilityEditingId = null;
    this.utilityFormPasswordVisible = false;
    this.utilityEntryDraft = {
      itemName: '',
      folder: '',
      username: '',
      password: '',
      authenticatorKey: '',
      website: '',
      notes: '',
    };
  }

  cancelUtilityEdit(): void {
    this.resetUtilityForm();
  }

  /** Normalize to http(s) URL or return null if invalid. */
  normalizeUtilityUrl(raw: string): string | null {
    const t = raw.trim();
    if (!t) {
      return null;
    }
    let href = t;
    if (!/^https?:\/\//i.test(href)) {
      href = 'https://' + href.replace(/^\/+/, '');
    }
    try {
      const u = new URL(href);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') {
        return null;
      }
      return u.href;
    } catch {
      return null;
    }
  }

  /** Open saved website in a new browser tab. */
  openUtilityWebsiteInNewTab(raw: string, ev?: Event): void {
    ev?.preventDefault();
    ev?.stopPropagation();
    const url = this.normalizeUtilityUrl(raw);
    if (!url) {
      this.snackBar.open('Invalid or empty URL', undefined, { duration: 2500 });
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  copyUtilityUrlToClipboard(raw: string): void {
    const normalized = this.normalizeUtilityUrl(raw);
    const text = normalized ?? raw.trim();
    if (!text) {
      return;
    }
    if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
      this.snackBar.open('Clipboard not available', undefined, { duration: 2500 });
      return;
    }
    navigator.clipboard.writeText(text).then(
      () => this.snackBar.open('Link copied', undefined, { duration: 2000 }),
      () => this.snackBar.open('Could not copy', undefined, { duration: 2500 }),
    );
  }

  copyUtilityPasswordToClipboard(raw: string): void {
    const text = raw.trim();
    if (!text) {
      return;
    }
    if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
      this.snackBar.open('Clipboard not available', undefined, { duration: 2500 });
      return;
    }
    navigator.clipboard.writeText(text).then(
      () => this.snackBar.open('Password copied', undefined, { duration: 2000 }),
      () => this.snackBar.open('Could not copy', undefined, { duration: 2500 }),
    );
  }

  get calendarTitle(): string {
    const d = new Date(this.calendarYear, this.calendarMonth - 1, 1);
    return d.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  selectedDayLabel(): string {
    const iso = this.selectedDateIso;
    if (!iso || iso.length < 10) {
      return '';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d).toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }

  calendarRows(): CalendarCell[][] {
    const cal = this.monthCal;
    if (!cal) {
      return [];
    }
    const y = cal.year;
    const m = cal.month;
    const byDay = cal.tasksByDay ?? {};
    const last = new Date(y, m, 0).getDate();
    const firstDow = new Date(y, m - 1, 1).getDay();

    const flat: CalendarCell[] = [];
    let padSeq = 0;
    for (let i = 0; i < firstDow; i++) {
      padSeq += 1;
      flat.push({ type: 'pad', trackKey: `pad-head-${padSeq}` });
    }
    const todayIso = this.todayIso();
    for (let d = 1; d <= last; d++) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const tasks = byDay[iso] ?? [];
      const n = tasks.length;
      const dayDueVisual = n > 0 ? mgmtCalendarDayDueVisual(iso, tasks, todayIso) : null;
      flat.push({
        type: 'day',
        iso,
        label: String(d),
        taskCount: n,
        dayDueVisual,
        trackKey: `day-${iso}`,
      });
    }
    let tail = 0;
    while (flat.length % 7 !== 0) {
      tail += 1;
      flat.push({ type: 'pad', trackKey: `pad-tail-${tail}` });
    }
    const rows: CalendarCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  isSelected(iso: string | undefined): boolean {
    return !!iso && iso === this.selectedDateIso;
  }

  selectDay(cell: CalendarCell): void {
    if (cell.type !== 'day' || !cell.iso) {
      return;
    }
    this.selectedDateIso = cell.iso;
    this.refreshSelectedDayTasks();
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
    this.reloadCalendarOnly();
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
    this.reloadCalendarOnly();
  }

  tasksForSelectedDay(): ManagementTaskDto[] {
    return this.selectedDayTasks;
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

  /** CSS class on task table rows for due / overdue vs done. */
  taskDueRowClass(row: ManagementTaskDto): string {
    return mgmtTaskDueRowClass(row, this.todayIso());
  }

  /** Accent class on month grid day cells when open tasks need attention. */
  calendarDayDueClass(cell: CalendarCell): string | null {
    if (cell.type !== 'day' || !cell.dayDueVisual) {
      return null;
    }
    const m: Record<MgmtTaskDueVisual, string> = {
      open_due_future: 'cal-day-accent--future',
      open_due_today: 'cal-day-accent--today',
      overdue_1_7: 'cal-day-accent--od1',
      overdue_8_30: 'cal-day-accent--od2',
      overdue_31_plus: 'cal-day-accent--od3',
      completed: '',
      open_no_due: '',
    };
    return m[cell.dayDueVisual] || null;
  }

  calendarDayNgClass(cell: CalendarCell): Record<string, boolean> {
    const c = this.calendarDayDueClass(cell);
    return c ? { [c]: true } : {};
  }

  /** Open tasks in the visible month with due date strictly before today. */
  openOverdueCountInMonth(): number {
    const cal = this.monthCal;
    const by = cal?.tasksByDay;
    if (!by) {
      return 0;
    }
    const today = this.todayIso();
    let n = 0;
    for (const [iso, list] of Object.entries(by)) {
      if (iso >= today) {
        continue;
      }
      n += list.filter((t) => !t.completed).length;
    }
    return n;
  }

  /** Selected calendar day is in the past: count of tasks still not completed (missed that due date). */
  selectedDayOpenPastDueCount(): number {
    const iso = this.selectedDateIso;
    const today = this.todayIso();
    if (!iso || iso.length < 10 || iso >= today) {
      return 0;
    }
    return this.selectedDayTasks.filter((t) => !t.completed).length;
  }

  resetForm(): void {
    this.editingId = null;
    this.newTask = {
      title: '',
      notes: '',
      dueDate: this.dateFromIso(this.selectedDateIso),
      urgency: 'MEDIUM',
      categoryId: null,
      taskTypeId: null,
      completed: false,
    };
  }

  startEdit(row: ManagementTaskDto): void {
    this.editingId = row.id;
    this.newTask = {
      title: row.title,
      notes: row.notes ?? '',
      dueDate: row.dueDate ? this.dateFromIso(row.dueDate) : null,
      urgency: row.urgency,
      categoryId: row.categoryId ?? null,
      taskTypeId: row.taskTypeId ?? null,
      completed: row.completed,
    };
  }

  saveTask(): void {
    const title = (this.newTask.title || '').trim();
    if (!title) {
      return;
    }
    const body = {
      title,
      notes: (this.newTask.notes || '').trim(),
      dueDate: this.newTask.dueDate ? this.toIsoDate(this.newTask.dueDate) : null,
      urgency: this.newTask.urgency,
      categoryId: this.newTask.categoryId,
      taskTypeId: this.newTask.taskTypeId,
      completed: this.newTask.completed,
    };
    if (this.editingId != null) {
      this.api.updateTask(this.editingId, body).subscribe({
        next: () => {
          this.snackBar.open('Task updated', undefined, { duration: 2500 });
          this.resetForm();
          this.reloadRefsAndCalendar();
        },
        error: (e) => this.err('Could not update task', e),
      });
    } else {
      this.api.createTask(body).subscribe({
        next: () => {
          this.snackBar.open('Task added', undefined, { duration: 2500 });
          this.resetForm();
          this.reloadRefsAndCalendar();
        },
        error: (e) => this.err('Could not add task', e),
      });
    }
  }

  deleteTask(row: ManagementTaskDto): void {
    this.api.deleteTask(row.id).subscribe({
      next: () => {
        this.snackBar.open('Task removed', undefined, { duration: 2500 });
        if (this.editingId === row.id) {
          this.resetForm();
        }
        this.reloadRefsAndCalendar();
      },
      error: (e) => this.err('Could not delete task', e),
    });
  }

  toggleDone(row: ManagementTaskDto, checked: boolean): void {
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
        next: () => this.reloadRefsAndCalendar(),
        error: (e) => this.err('Could not update task', e),
      });
  }

  private reloadRefsAndCalendar(): void {
    forkJoin({
      cal: this.api
        .taskCalendar(this.calendarYear, this.calendarMonth)
        .pipe(catchError(() => of<TaskMonthCalendarDto | null>(null))),
      un: this.api.listUnscheduledTasks().pipe(catchError(() => of<ManagementTaskDto[]>([]))),
      cat: this.api.listCategories().pipe(catchError(() => of<ManagementTaskCategory[]>([]))),
      tt: this.api.listTaskTypes().pipe(catchError(() => of<ManagementTaskType[]>([]))),
    }).subscribe({
      next: ({ cal, un, cat, tt }) => {
        this.monthCal = cal;
        this.unscheduled = un;
        this.categories = cat;
        this.taskTypes = tt;
        this.refreshSelectedDayTasks();
      },
      error: () => {},
    });
  }

  private reloadCalendarOnly(): void {
    forkJoin({
      cal: this.api
        .taskCalendar(this.calendarYear, this.calendarMonth)
        .pipe(catchError(() => of<TaskMonthCalendarDto | null>(null))),
      un: this.api.listUnscheduledTasks().pipe(catchError(() => of<ManagementTaskDto[]>([]))),
    }).subscribe({
      next: ({ cal, un }) => {
        this.monthCal = cal;
        this.unscheduled = un;
        this.refreshSelectedDayTasks();
      },
      error: () => {},
    });
  }

  private clampSelectedToMonth(): void {
    const y = this.calendarYear;
    const m = this.calendarMonth;
    const last = new Date(y, m, 0).getDate();
    if (!this.selectedDateIso) {
      this.selectedDateIso = this.defaultDayInMonth(y, m);
      return;
    }
    const ySel = Number(this.selectedDateIso.slice(0, 4));
    const mSel = Number(this.selectedDateIso.slice(5, 7));
    if (ySel !== y || mSel !== m) {
      this.selectedDateIso = this.defaultDayInMonth(y, m);
      return;
    }
    const d = Number(this.selectedDateIso.slice(8, 10));
    const day = Math.min(Math.max(1, d), last);
    this.selectedDateIso = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    this.refreshSelectedDayTasks();
  }

  private defaultDayInMonth(y: number, m: number): string {
    const t = this.todayIso();
    if (Number(t.slice(0, 4)) === y && Number(t.slice(5, 7)) === m) {
      return t;
    }
    return `${y}-${String(m).padStart(2, '0')}-01`;
  }

  private todayIso(): string {
    const d = new Date();
    const y = d.getFullYear();
    const mo = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${mo}-${day}`;
  }

  private refreshSelectedDayTasks(): void {
    const iso = this.selectedDateIso;
    if (!iso || !this.monthCal?.tasksByDay) {
      this.selectedDayTasks = [];
      return;
    }
    this.selectedDayTasks = [...(this.monthCal.tasksByDay[iso] ?? [])].sort((a, b) =>
      b.urgency.localeCompare(a.urgency),
    );
  }

  private dateFromIso(iso: string): Date {
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d);
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  get repCalViewTitle(): string {
    const a = this.repCalAnchorIso;
    if (!a || a.length < 10) {
      return '';
    }
    const y = Number(a.slice(0, 4));
    const m = Number(a.slice(5, 7));
    const d = Number(a.slice(8, 10));
    const dt = new Date(y, m - 1, d);
    if (this.repCalView === 'year') {
      return String(y);
    }
    if (this.repCalView === 'month') {
      return dt.toLocaleString(undefined, { month: 'long', year: 'numeric' });
    }
    return dt.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
  }

  onRepCalTypeOrViewChange(): void {
    this.clearRepCalListFilters();
    this.loadReportCalendar();
  }

  clearRepCalListFilters(): void {
    this.repCalFocusedDayIso = null;
    this.repCalFocusedMonthKey = null;
  }

  /** Clears day/month list filters and the title text filter (toolbar “Clear” action). */
  clearRepCalTableFilters(): void {
    this.clearRepCalListFilters();
    this.repCalTitleFilter = '';
  }

  get repCalHasTableNarrowing(): boolean {
    return !!(this.repCalFocusedDayIso || this.repCalFocusedMonthKey || this.repCalTitleFilter.trim());
  }

  repCalStep(delta: number): void {
    this.clearRepCalListFilters();
    const a = this.repCalAnchorIso;
    if (!a || a.length < 10) {
      return;
    }
    const y = Number(a.slice(0, 4));
    const m = Number(a.slice(5, 7));
    const d = Number(a.slice(8, 10));
    const dt = new Date(y, m - 1, d);
    if (this.repCalView === 'day') {
      dt.setDate(dt.getDate() + delta);
    } else if (this.repCalView === 'month') {
      dt.setMonth(dt.getMonth() + delta);
    } else {
      dt.setFullYear(dt.getFullYear() + delta);
    }
    this.repCalAnchorIso = this.toIsoDate(dt);
    this.loadReportCalendar();
  }

  private repCalQueryRange(): { from: string; to: string } {
    const a = this.repCalAnchorIso;
    if (!a || a.length < 10) {
      const t = this.todayIso();
      return { from: t, to: t };
    }
    const y = Number(a.slice(0, 4));
    const m = Number(a.slice(5, 7));
    if (this.repCalView === 'day') {
      return { from: a, to: a };
    }
    if (this.repCalView === 'month') {
      const fromD = new Date(y, m - 1, 1);
      const toD = new Date(y, m, 0);
      return { from: this.toIsoDate(fromD), to: this.toIsoDate(toD) };
    }
    return { from: `${y}-01-01`, to: `${y}-12-31` };
  }

  loadReportCalendar(): void {
    const { from, to } = this.repCalQueryRange();
    const typeParam = this.repCalTypeFilter === 'ALL' ? null : this.repCalTypeFilter;
    this.reportCalApi.list(from, to, typeParam).subscribe({
      next: (rows) => {
        this.repCalEntries = [...rows].sort((a, b) => {
          const c = a.entryDate.localeCompare(b.entryDate);
          if (c !== 0) {
            return c;
          }
          const ct = a.calendarType.localeCompare(b.calendarType);
          if (ct !== 0) {
            return ct;
          }
          return a.id - b.id;
        });
      },
      error: (e) => this.err('Could not load calendar', e),
    });
  }

  get repCalDisplayedEntries(): ReportCalendarEntryDto[] {
    let rows = this.repCalEntries;
    if (this.repCalFocusedDayIso) {
      rows = rows.filter((e) => e.entryDate === this.repCalFocusedDayIso);
    } else if (this.repCalView === 'year' && this.repCalFocusedMonthKey) {
      rows = rows.filter((e) => e.entryDate.slice(0, 7) === this.repCalFocusedMonthKey);
    }
    const q = this.repCalTitleFilter.trim().toLowerCase();
    if (q) {
      rows = rows.filter((e) => (e.title ?? '').toLowerCase().includes(q));
    }
    return rows;
  }

  get repCalEntriesHeading(): string {
    if (this.repCalFocusedDayIso) {
      return `Entries for ${this.formatRepCalRowDate(this.repCalFocusedDayIso)}`;
    }
    if (this.repCalView === 'year' && this.repCalFocusedMonthKey) {
      return `Entries in ${this.repCalFocusedMonthKey}`;
    }
    return 'Entries in this period';
  }

  get repCalTableColumnsForList(): string[] {
    if (this.repCalTypeFilter === 'ALL') {
      return ['cDate', 'cType', 'cTitle', 'cInfo', 'cAct'];
    }
    return ['cDate', 'cTitle', 'cInfo', 'cAct'];
  }

  onRepCalYearMonthClicked(m: number): void {
    const a = this.repCalAnchorIso;
    if (!a || a.length < 4 || this.repCalView !== 'year') {
      return;
    }
    const y = a.slice(0, 4);
    const ym = `${y}-${String(m).padStart(2, '0')}`;
    if (this.repCalFocusedMonthKey === ym) {
      this.repCalFocusedMonthKey = null;
    } else {
      this.repCalFocusedMonthKey = ym;
    }
    this.repCalFocusedDayIso = null;
  }

  onRepCalMonthDayClicked(iso: string): void {
    if (this.repCalView !== 'month' || !iso) {
      return;
    }
    if (this.repCalFocusedDayIso === iso) {
      this.repCalFocusedDayIso = null;
    } else {
      this.repCalFocusedDayIso = iso;
    }
    this.repCalFocusedMonthKey = null;
  }

  repCalYearMonthSelected(m: number): boolean {
    const a = this.repCalAnchorIso;
    if (!a || a.length < 4 || !this.repCalFocusedMonthKey) {
      return false;
    }
    const y = a.slice(0, 4);
    const ym = `${y}-${String(m).padStart(2, '0')}`;
    return this.repCalFocusedMonthKey === ym;
  }

  private repCalDateSet(): Set<string> {
    return new Set(this.repCalEntries.map((e) => e.entryDate));
  }

  reportCalRows(): ReportCalCell[][] {
    if (this.repCalView !== 'month') {
      return [];
    }
    const a = this.repCalAnchorIso;
    if (!a || a.length < 10) {
      return [];
    }
    const y = Number(a.slice(0, 4));
    const m = Number(a.slice(5, 7));
    const withEntry = this.repCalDateSet();
    const last = new Date(y, m, 0).getDate();
    const firstDow = new Date(y, m - 1, 1).getDay();

    const flat: ReportCalCell[] = [];
    let padSeq = 0;
    for (let i = 0; i < firstDow; i++) {
      padSeq += 1;
      flat.push({ type: 'pad', trackKey: `rc-pad-h-${padSeq}` });
    }
    for (let d = 1; d <= last; d++) {
      const mo = String(m).padStart(2, '0');
      const day = String(d).padStart(2, '0');
      const iso = `${y}-${mo}-${day}`;
      flat.push({
        type: 'day',
        iso,
        label: String(d),
        hasEntry: withEntry.has(iso),
        trackKey: `rc-${iso}`,
      });
    }
    let tail = 0;
    while (flat.length % 7 !== 0) {
      tail += 1;
      flat.push({ type: 'pad', trackKey: `rc-pad-t-${tail}` });
    }

    const rows: ReportCalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  repCalYearHasMonth(m: number): boolean {
    const a = this.repCalAnchorIso;
    if (!a || a.length < 4) {
      return false;
    }
    const y = a.slice(0, 4);
    const ym = `${y}-${String(m).padStart(2, '0')}`;
    return this.repCalEntries.some((e) => e.entryDate.slice(0, 7) === ym);
  }

  repCalShortMonthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'short' });
  }

  openRepCalAddDialog(): void {
    const d: ReportCalendarEntryDialogData = {
      entry: null,
      defaultDate: this.repCalFocusedDayIso ?? this.repCalAnchorIso,
      defaultType: this.repCalTypeFilter === 'ALL' ? 'PERSONAL' : this.repCalTypeFilter,
    };
    this.dialog
      .open(ReportCalendarEntryDialogComponent, {
        width: 'min(92vw, 32rem)',
        data: d,
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.loadReportCalendar();
        }
      });
  }

  openRepCalEditDialog(row: ReportCalendarEntryDto): void {
    const d: ReportCalendarEntryDialogData = {
      entry: row,
      defaultDate: row.entryDate,
      defaultType: row.calendarType,
    };
    this.dialog
      .open(ReportCalendarEntryDialogComponent, {
        width: 'min(92vw, 32rem)',
        data: d,
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.loadReportCalendar();
        }
      });
  }

  deleteRepCalEntry(row: ReportCalendarEntryDto): void {
    if (typeof window !== 'undefined' && !window.confirm('Delete this calendar entry?')) {
      return;
    }
    this.reportCalApi.delete(row.id).subscribe({
      next: () => this.loadReportCalendar(),
      error: (e) => this.err('Could not delete entry', e),
    });
  }

  repCalInfoPreview(body: string | null | undefined): string {
    const s = (body ?? '').replace(/\s+/g, ' ').trim();
    if (s.length <= 160) {
      return s;
    }
    return `${s.slice(0, 160)}…`;
  }

  formatRepCalRowDate(iso: string | null | undefined): string {
    if (!iso || iso.length < 10) {
      return '—';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d).toLocaleDateString();
  }

  onManagementTabIndexChange(index: number): void {
    if (index === this.MGMT_TAB_NOTES) {
      this.noteDraft.year = this.noteYear;
      if (this.noteFilterMonth != null) {
        this.noteDraft.month = this.noteFilterMonth;
      }
      this.reloadMonthNotesData();
    }
    if (index === this.MGMT_TAB_WRITEUP) {
      this.loadWriteups();
    }
  }

  get writeupSearchTrim(): string {
    return (this.writeupSearch || '').trim().toLowerCase();
  }

  get writeupHasDocPreview(): boolean {
    return !!(
      (this.writeupDraft.topic || '').trim() ||
      (this.writeupDraft.body || '').trim() ||
      (this.writeupDraft.highlight || '').trim()
    );
  }

  get writeupFilteredEntries(): ManagementWriteupDto[] {
    const q = this.writeupSearchTrim;
    if (!q) {
      return this.writeupsRaw;
    }
    return this.writeupsRaw.filter((w) => {
      const topic = (w.topic || '').toLowerCase();
      const hi = (w.highlight || '').toLowerCase();
      const body = (w.body || '').toLowerCase();
      return topic.includes(q) || hi.includes(q) || body.includes(q);
    });
  }

  loadWriteups(): void {
    this.api.listWriteups(this.writeupYear).subscribe({
      next: (rows) => {
        this.writeupsRaw = rows;
        if (this.writeupEditingId != null && !rows.some((r) => r.id === this.writeupEditingId)) {
          this.resetWriteupForm();
        }
      },
      error: (e) => this.err('Could not load write-ups', e),
    });
  }

  prevWriteupYear(): void {
    this.writeupYear -= 1;
    this.loadWriteups();
  }

  nextWriteupYear(): void {
    this.writeupYear += 1;
    this.loadWriteups();
  }

  selectWriteup(w: ManagementWriteupDto): void {
    this.writeupSelectedId = w.id;
    this.writeupEditingId = w.id;
    this.writeupDraft = {
      topic: w.topic ?? '',
      highlight: w.highlight ?? '',
      body: w.body ?? '',
    };
  }

  resetWriteupForm(): void {
    this.writeupEditingId = null;
    this.writeupSelectedId = null;
    this.writeupDraft = {
      topic: '',
      highlight: '',
      body: '',
    };
  }

  saveWriteup(): void {
    const topic = (this.writeupDraft.topic || '').trim();
    if (!topic) {
      this.snackBar.open('Topic is required', undefined, { duration: 2500 });
      return;
    }
    const highlight = (this.writeupDraft.highlight || '').trim();
    const body = {
      year: this.writeupYear,
      topic,
      highlight: highlight.length ? highlight : null,
      body: this.writeupDraft.body ?? '',
    };
    this.writeupSaving = true;
    if (this.writeupEditingId != null) {
      this.api.updateWriteup(this.writeupEditingId, body).subscribe({
        next: (row) => {
          this.writeupSaving = false;
          this.snackBar.open('Write-up saved', undefined, { duration: 2000 });
          this.writeupSelectedId = row.id;
          this.writeupEditingId = row.id;
          this.loadWriteups();
        },
        error: (e) => {
          this.writeupSaving = false;
          this.err('Could not save write-up', e);
        },
      });
    } else {
      this.api.createWriteup(body).subscribe({
        next: (row) => {
          this.writeupSaving = false;
          this.snackBar.open('Write-up created', undefined, { duration: 2000 });
          this.writeupSelectedId = row.id;
          this.writeupEditingId = row.id;
          this.loadWriteups();
        },
        error: (e) => {
          this.writeupSaving = false;
          this.err('Could not create write-up', e);
        },
      });
    }
  }

  deleteWriteup(): void {
    if (this.writeupEditingId == null) {
      return;
    }
    if (typeof window !== 'undefined' && !window.confirm('Delete this write-up permanently?')) {
      return;
    }
    const id = this.writeupEditingId;
    this.api.deleteWriteup(id).subscribe({
      next: () => {
        this.snackBar.open('Write-up removed', undefined, { duration: 2000 });
        this.resetWriteupForm();
        this.loadWriteups();
      },
      error: (e) => this.err('Could not delete write-up', e),
    });
  }

  writeupSnippet(s: string | null | undefined, max = 80): string {
    const t = (s ?? '').replace(/\s+/g, ' ').trim();
    if (!t) {
      return '';
    }
    return t.length <= max ? t : `${t.slice(0, max)}…`;
  }

  get noteMonthCells(): { month: number; noteCount: number }[] {
    return this.noteCalendar?.months ?? this.emptyNoteCalendarMonths();
  }

  private emptyNoteCalendarMonths(): { month: number; noteCount: number }[] {
    return Array.from({ length: 12 }, (_, i) => ({ month: i + 1, noteCount: 0 }));
  }

  reloadMonthNotesData(): void {
    this.api.notesCalendar(this.noteYear).subscribe({
      next: (c) => (this.noteCalendar = c),
      error: (e) => this.err('Could not load notes calendar', e),
    });
    this.api.listMonthNotes(this.noteYear, this.noteFilterMonth).subscribe({
      next: (rows) => (this.monthNotes = rows),
      error: (e) => this.err('Could not load notes', e),
    });
  }

  private reloadMonthNotesListOnly(): void {
    this.api.listMonthNotes(this.noteYear, this.noteFilterMonth).subscribe({
      next: (rows) => (this.monthNotes = rows),
      error: (e) => this.err('Could not load notes', e),
    });
  }

  selectNotesYearOnly(): void {
    this.noteFilterMonth = null;
    this.reloadMonthNotesListOnly();
  }

  selectNoteMonth(m: number): void {
    if (this.noteFilterMonth === m) {
      this.noteFilterMonth = null;
    } else {
      this.noteFilterMonth = m;
      this.noteDraft.month = m;
    }
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

  monthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'long' });
  }

  shortMonthName(m: number): string {
    return new Date(2000, m - 1, 1).toLocaleString(undefined, { month: 'short' });
  }

  saveMonthNote(): void {
    const subject = (this.noteDraft.subject || '').trim();
    if (!subject) {
      this.snackBar.open('Subject is required', undefined, { duration: 2500 });
      return;
    }
    const body = {
      year: this.noteDraft.year,
      month: this.noteDraft.month,
      subject,
      body: (this.noteDraft.body || '').trim(),
    };
    if (this.noteEditingId != null) {
      this.api.updateMonthNote(this.noteEditingId, body).subscribe({
        next: () => {
          this.snackBar.open('Note updated', undefined, { duration: 2000 });
          this.resetMonthNoteForm();
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not update note', e),
      });
    } else {
      this.api.createMonthNote(body).subscribe({
        next: () => {
          this.snackBar.open('Note saved', undefined, { duration: 2000 });
          this.resetMonthNoteForm();
          this.reloadMonthNotesData();
        },
        error: (e) => this.err('Could not save note', e),
      });
    }
  }

  resetMonthNoteForm(): void {
    this.noteEditingId = null;
    this.noteDraft = {
      year: this.noteYear,
      month: this.noteFilterMonth != null ? this.noteFilterMonth : this.noteDraft.month,
      subject: '',
      body: '',
    };
  }

  startEditMonthNote(n: ManagementMonthNoteDto): void {
    this.noteEditingId = n.id;
    this.noteDraft = {
      year: n.year,
      month: n.month,
      subject: n.subject,
      body: n.body ?? '',
    };
  }

  deleteMonthNote(n: ManagementMonthNoteDto): void {
    if (typeof window !== 'undefined' && !window.confirm('Delete this note?')) {
      return;
    }
    this.api.deleteMonthNote(n.id).subscribe({
      next: () => {
        this.snackBar.open('Note removed', undefined, { duration: 2000 });
        if (this.noteEditingId === n.id) {
          this.resetMonthNoteForm();
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

  openMonthNoteAttachment(attachmentId: number, _filename: string): void {
    this.api.getMonthNoteAttachmentBlob(attachmentId, 'inline').subscribe({
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

  removeMonthNoteAttachment(_noteId: number, attachmentId: number): void {
    this.api.deleteMonthNoteAttachment(attachmentId).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', undefined, { duration: 2000 });
        this.reloadMonthNotesData();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  /** `localStorage` key for the signed-in app user; not the “site username” field on each entry. */
  private utilitiesStorageKey(): string | null {
    const u = this.auth.username?.trim();
    if (!u) {
      return null;
    }
    return `${ManagementComponent.UTILITIES_STORAGE_KEY_BASE}.user.${u.toLowerCase()}`;
  }

  /**
   * One-time: data stored under the legacy unscoped key is assigned to the `spulickal` app account.
   */
  private migrateLegacyUtilitiesToSpulickalIfNeeded(userKey: string): void {
    if (typeof window === 'undefined') {
      return;
    }
    if (this.auth.username?.trim().toLowerCase() !== 'spulickal') {
      return;
    }
    const current = window.localStorage.getItem(userKey);
    if (current) {
      try {
        const parsed = JSON.parse(current) as unknown;
        if (Array.isArray(parsed) && parsed.length > 0) {
          return;
        }
      } catch {
        // fall through: migrate from legacy
      }
    }
    const legacy = window.localStorage.getItem(ManagementComponent.UTILITIES_STORAGE_KEY_BASE);
    if (!legacy) {
      return;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(legacy);
    } catch {
      return;
    }
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return;
    }
    window.localStorage.setItem(userKey, legacy);
    window.localStorage.removeItem(ManagementComponent.UTILITIES_STORAGE_KEY_BASE);
  }

  private loadUtilitiesFromStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const storageKey = this.utilitiesStorageKey();
    if (!storageKey) {
      this.utilityEntries = [];
      return;
    }
    this.migrateLegacyUtilitiesToSpulickalIfNeeded(storageKey);
    try {
      const raw = window.localStorage.getItem(storageKey);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return;
      }
      this.utilityEntries = parsed.filter((v) => v && typeof v === 'object').map((v) => {
        const o = v as Record<string, unknown> & Partial<UtilityEntry> & { websites?: unknown[] };
        let website = String(o.website ?? '');
        if (!website && Array.isArray(o.websites) && o.websites.length > 0) {
          website = String(o.websites[0]);
        }
        return {
          id: Number(o.id) || Date.now(),
          itemName: String(o.itemName ?? ''),
          folder: String(o.folder ?? ''),
          username: String(o.username ?? ''),
          password: String(o.password ?? ''),
          authenticatorKey: String(o.authenticatorKey ?? ''),
          website,
          notes: String(o.notes ?? ''),
          createdAt: String(o.createdAt ?? ''),
          updatedAt:
            o.updatedAt != null && String(o.updatedAt).trim() !== '' ? String(o.updatedAt) : undefined,
        };
      });
    } catch {
      this.utilityEntries = [];
    }
  }

  private persistUtilitiesToStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const storageKey = this.utilitiesStorageKey();
    if (!storageKey) {
      return;
    }
    window.localStorage.setItem(storageKey, JSON.stringify(this.utilityEntries));
  }
}
