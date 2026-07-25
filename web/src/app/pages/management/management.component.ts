import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, viewChild } from '@angular/core';
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
  ManagementAccountDto,
  ManagementAccountWriteBody,
  ManagementMonthNoteCalendarDto,
  ManagementMonthNoteDto,
  ManagementCalendarType,
  ManagementWriteupAttachmentDto,
  ManagementWriteupDto,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  TaskMonthCalendarDto,
} from '../../models/management.models';
import {
  reportCalendarFilterOptions,
  reportCalendarTypeOptionsFromProvisioned,
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
import { ManagementWorkPanelComponent } from './management-work-panel/management-work-panel.component';
import { ManagementTravelPanelComponent } from './management-travel-panel/management-travel-panel.component';
import { ManagementDocumentsPanelComponent } from './management-documents-panel/management-documents-panel.component';
import { ManagementRecordingsPanelComponent } from './management-recordings-panel/management-recordings-panel.component';
import { ManagementNowPanelComponent } from './management-now-panel/management-now-panel.component';

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

interface AccountEntry {
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
    ManagementWorkPanelComponent,
    ManagementTravelPanelComponent,
    ManagementDocumentsPanelComponent,
    ManagementRecordingsPanelComponent,
    ManagementNowPanelComponent,
  ],
  templateUrl: './management.component.html',
  styleUrl: './management.component.scss',
})
export class ManagementComponent implements OnInit {
  /**
   * Legacy unscoped key (pre–per-user storage). The string is kept verbatim so prior installs can still be detected
   * and migrated to the server vault on first login. Do not rename the string value.
   */
  private static readonly LEGACY_LOCAL_STORAGE_KEY_BASE = 'management.utilities.entries.v1';
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
  /** Narrows the entries table by type, title, information, or details (case-insensitive). */
  repCalSearchFilter = '';
  /** Selected entry ids for bulk browse in edit mode. */
  repCalSelectedIds = new Set<number>();
  repCalCalendarTypes: ManagementCalendarType[] = [];
  readonly yearMonthIndex = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;

  get repCalFilterOptions() {
    return reportCalendarFilterOptions(this.repCalCalendarTypes);
  }

  repCalTypeLabel(t: ReportCalendarType): string {
    return reportCalendarTypeLabel(t, this.repCalCalendarTypes);
  }

  accountEntryDraft = {
    itemName: '',
    folder: '',
    username: '',
    password: '',
    authenticatorKey: '',
    website: '',
    notes: '',
  };
  /** When set, the form is editing an existing record. */
  accountEditingId: number | null = null;
  /** Selected row for the details panel. */
  selectedAccountEntryId: number | null = null;
  /** Single search string across folder, item name, username, and website. */
  accountSearchQuery = '';
  /** Password field in add/edit form: hidden until toggled. */
  accountFormPasswordVisible = false;
  /** Reveal password in the details panel. */
  accountDetailPasswordVisible = false;
  accountEntries: AccountEntry[] = [];
  /** True while the initial list is being fetched from the server. */
  accountsLoading = false;
  /** True while a create/update/delete call is in flight. */
  accountsSaving = false;

  readonly accountTableColumns: string[] = ['folder', 'itemName', 'username', 'actions'];

  /** 0 Tasks, 1 Work, 2 Travel, 3 Documents, 4 Recordings, 5 Now, 6 Calendar, 7 Account, 8 Notes, 9 Write-up */
  private readonly MGMT_TAB_WORK = 1;
  private readonly MGMT_TAB_TRAVEL = 2;
  private readonly MGMT_TAB_DOCUMENTS = 3;
  private readonly MGMT_TAB_RECORDINGS = 4;
  private readonly MGMT_TAB_NOTES = 8;
  private readonly MGMT_TAB_WRITEUP = 9;

  private readonly workPanel = viewChild(ManagementWorkPanelComponent);
  private readonly travelPanel = viewChild(ManagementTravelPanelComponent);
  private readonly documentsPanel = viewChild(ManagementDocumentsPanelComponent);
  private readonly recordingsPanel = viewChild(ManagementRecordingsPanelComponent);

  noteYear = new Date().getFullYear();
  /** When set, list is limited to that month; when null, all months in the year. Default: current month (1–12). */
  noteFilterMonth: number | null = new Date().getMonth() + 1;
  noteCalendar: ManagementMonthNoteCalendarDto | null = null;
  monthNotes: ManagementMonthNoteDto[] = [];
  noteEditingId: number | null = null;
  /** Read saved note vs compose (new/edit) with live preview. */
  noteViewMode: 'read' | 'compose' = 'read';
  noteSelectedId: number | null = null;
  /** Split editor/preview on wide screens; write or preview only on narrow. */
  noteComposerPane: 'split' | 'write' | 'preview' = 'split';
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
  /** Read saved write-up vs compose (new/edit) with live preview. */
  writeupViewMode: 'read' | 'compose' = 'read';
  writeupComposerPane: 'split' | 'write' | 'preview' = 'split';
  writeupDraft = {
    topic: '',
    highlight: '',
    body: '',
  };
  writeupSaving = false;
  writeupUploading = false;
  /** Attachments for the write-up currently being edited (synced from API after load). */
  writeupSelectedAttachments: ManagementWriteupAttachmentDto[] = [];

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.repCalAnchorIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.resetForm();
    this.reloadRefsAndCalendar();
    this.loadRepCalCalendarTypes();
    this.loadReportCalendar();
    this.loadAccountsFromServer();
  }

  private loadRepCalCalendarTypes(): void {
    this.api.listCalendarTypes().subscribe({
      next: (rows: ManagementCalendarType[]) => {
        this.repCalCalendarTypes = [...rows].sort((a, b) => {
          const si = (a.sortIndex ?? 0) - (b.sortIndex ?? 0);
          if (si !== 0) {
            return si;
          }
          return (a.code || '').localeCompare(b.code || '', undefined, { sensitivity: 'base' });
        });
      },
      error: (e) => this.err('Could not load calendar types', e),
    });
  }

  private repCalDefaultType(): ReportCalendarType {
    const types = this.repCalCalendarTypes;
    if (this.repCalTypeFilter !== 'ALL') {
      return this.repCalTypeFilter;
    }
    const personal = types.find((t) => t.code === 'PERSONAL');
    if (personal) {
      return personal.code;
    }
    return types[0]?.code ?? 'PERSONAL';
  }

  private repCalTypeOptionsForDialog(): ReadonlyArray<{ value: ReportCalendarType; label: string }> {
    return reportCalendarTypeOptionsFromProvisioned(this.repCalCalendarTypes);
  }

  get filteredAccountEntries(): AccountEntry[] {
    const q = this.accountSearchQuery.trim().toLowerCase();
    if (!q) {
      return this.accountEntries;
    }
    return this.accountEntries.filter((e) => {
      const folder = (e.folder || '').toLowerCase();
      const name = (e.itemName || '').toLowerCase();
      const user = (e.username || '').toLowerCase();
      const site = (e.website || '').toLowerCase();
      return folder.includes(q) || name.includes(q) || user.includes(q) || site.includes(q);
    });
  }

  get selectedAccountEntry(): AccountEntry | null {
    if (this.selectedAccountEntryId == null) {
      return null;
    }
    return this.accountEntries.find((e) => e.id === this.selectedAccountEntryId) ?? null;
  }

  selectAccountEntry(entry: AccountEntry): void {
    this.selectedAccountEntryId = entry.id;
    this.accountDetailPasswordVisible = false;
  }

  trackByAccountId = (_: number, e: AccountEntry) => e.id;

  isAccountRowSelected(entry: AccountEntry): boolean {
    return this.selectedAccountEntryId === entry.id;
  }

  startEditAccountEntry(entry: AccountEntry, ev?: Event): void {
    ev?.stopPropagation();
    this.accountEditingId = entry.id;
    this.accountFormPasswordVisible = false;
    this.accountEntryDraft = {
      itemName: entry.itemName,
      folder: entry.folder,
      username: entry.username,
      password: entry.password,
      authenticatorKey: entry.authenticatorKey,
      website: entry.website,
      notes: entry.notes,
    };
  }

  saveAccountEntry(): void {
    const itemName = this.accountEntryDraft.itemName.trim();
    if (!itemName) {
      this.snackBar.open('Item name is required', undefined, { duration: 2500 });
      return;
    }
    if (this.accountsSaving) {
      return;
    }
    const body: ManagementAccountWriteBody = {
      itemName,
      folder: this.accountEntryDraft.folder.trim(),
      username: this.accountEntryDraft.username.trim(),
      password: this.accountEntryDraft.password.trim(),
      authenticatorKey: this.accountEntryDraft.authenticatorKey.trim(),
      website: this.accountEntryDraft.website.trim(),
      notes: this.accountEntryDraft.notes.trim(),
    };

    if (this.accountEditingId != null) {
      const id = this.accountEditingId;
      this.accountsSaving = true;
      this.api.updateAccount(id, body).subscribe({
        next: (dto) => {
          this.accountsSaving = false;
          const updated = this.toUiEntry(dto);
          this.accountEntries = this.accountEntries.map((e) => (e.id === id ? updated : e));
          this.resetAccountForm();
          this.snackBar.open('Account updated', undefined, { duration: 2500 });
        },
        error: (e) => {
          this.accountsSaving = false;
          this.err('Could not update account', e);
        },
      });
      return;
    }

    this.accountsSaving = true;
    this.api.createAccount(body).subscribe({
      next: (dto) => {
        this.accountsSaving = false;
        const entry = this.toUiEntry(dto);
        this.accountEntries = [entry, ...this.accountEntries];
        this.resetAccountForm();
        this.selectedAccountEntryId = entry.id;
        this.snackBar.open('Account saved', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.accountsSaving = false;
        this.err('Could not save account', e);
      },
    });
  }

  deleteAccountEntry(id: number, ev?: Event): void {
    ev?.stopPropagation();
    if (!window.confirm('Delete this account? This cannot be undone.')) {
      return;
    }
    if (this.accountsSaving) {
      return;
    }
    this.accountsSaving = true;
    this.api.deleteAccount(id).subscribe({
      next: () => {
        this.accountsSaving = false;
        this.accountEntries = this.accountEntries.filter((e) => e.id !== id);
        if (this.selectedAccountEntryId === id) {
          this.selectedAccountEntryId = null;
        }
        if (this.accountEditingId === id) {
          this.resetAccountForm();
        }
        this.snackBar.open('Account removed', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.accountsSaving = false;
        this.err('Could not remove account', e);
      },
    });
  }

  resetAccountForm(): void {
    this.accountEditingId = null;
    this.accountFormPasswordVisible = false;
    this.accountEntryDraft = {
      itemName: '',
      folder: '',
      username: '',
      password: '',
      authenticatorKey: '',
      website: '',
      notes: '',
    };
  }

  cancelAccountEdit(): void {
    this.resetAccountForm();
  }

  /** Normalize to http(s) URL or return null if invalid. */
  normalizeAccountUrl(raw: string): string | null {
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
  openAccountWebsiteInNewTab(raw: string, ev?: Event): void {
    ev?.preventDefault();
    ev?.stopPropagation();
    const url = this.normalizeAccountUrl(raw);
    if (!url) {
      this.snackBar.open('Invalid or empty URL', undefined, { duration: 2500 });
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  copyAccountUrlToClipboard(raw: string): void {
    const normalized = this.normalizeAccountUrl(raw);
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

  copyAccountPasswordToClipboard(raw: string): void {
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
    this.repCalSearchFilter = '';
  }

  get repCalHasTableNarrowing(): boolean {
    return !!(this.repCalFocusedDayIso || this.repCalFocusedMonthKey || this.repCalSearchFilter.trim());
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
    const q = this.repCalSearchFilter.trim().toLowerCase();
    if (q) {
      rows = rows.filter((e) => this.repCalEntryMatchesSearch(e, q));
    }
    return rows;
  }

  private repCalEntryMatchesSearch(e: ReportCalendarEntryDto, q: string): boolean {
    const typeCode = (e.calendarType ?? '').toLowerCase();
    const typeLabel = this.repCalTypeLabel(e.calendarType).toLowerCase();
    return (
      typeCode.includes(q) ||
      typeLabel.includes(q) ||
      (e.title ?? '').toLowerCase().includes(q) ||
      (e.body ?? '').toLowerCase().includes(q) ||
      (e.details ?? '').toLowerCase().includes(q)
    );
  }

  repCalHasDetails(row: ReportCalendarEntryDto): boolean {
    return !!(row.details ?? '').trim();
  }

  get repCalEntriesHeading(): string {
    const n = this.repCalDisplayedEntries.length;
    const eLabel = n === 1 ? 'Entry' : 'Entries';
    if (this.repCalFocusedDayIso) {
      return `${n} ${eLabel} for ${this.formatRepCalRowDate(this.repCalFocusedDayIso)}`;
    }
    if (this.repCalView === 'year' && this.repCalFocusedMonthKey) {
      return `${n} ${eLabel} in ${this.repCalFocusedMonthKey}`;
    }
    return `${n} ${eLabel} in this period`;
  }

  get repCalTableColumnsForList(): string[] {
    if (this.repCalTypeFilter === 'ALL') {
      return ['cSel', 'cDate', 'cType', 'cTitle', 'cInfo', 'cDetails', 'cAttach', 'cAct'];
    }
    return ['cSel', 'cDate', 'cTitle', 'cInfo', 'cDetails', 'cAttach', 'cAct'];
  }

  /** Entries to browse in edit mode: selected rows if any, else all displayed. */
  get repCalBrowseList(): ReportCalendarEntryDto[] {
    const displayed = this.repCalDisplayedEntries;
    const selected = displayed.filter((e) => this.repCalSelectedIds.has(e.id));
    return selected.length > 0 ? selected : displayed;
  }

  get repCalSelectionCount(): number {
    return this.repCalSelectedIds.size;
  }

  get repCalAllDisplayedSelected(): boolean {
    const rows = this.repCalDisplayedEntries;
    return rows.length > 0 && rows.every((r) => this.repCalSelectedIds.has(r.id));
  }

  get repCalSomeDisplayedSelected(): boolean {
    const rows = this.repCalDisplayedEntries;
    return rows.some((r) => this.repCalSelectedIds.has(r.id)) && !this.repCalAllDisplayedSelected;
  }

  isRepCalSelected(row: ReportCalendarEntryDto): boolean {
    return this.repCalSelectedIds.has(row.id);
  }

  toggleRepCalSelected(id: number, checked: boolean): void {
    const next = new Set(this.repCalSelectedIds);
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.repCalSelectedIds = next;
  }

  toggleRepCalSelectAllDisplayed(checked: boolean): void {
    const next = new Set(this.repCalSelectedIds);
    for (const row of this.repCalDisplayedEntries) {
      if (checked) {
        next.add(row.id);
      } else {
        next.delete(row.id);
      }
    }
    this.repCalSelectedIds = next;
  }

  clearRepCalSelection(): void {
    this.repCalSelectedIds = new Set();
  }

  openRepCalBrowseDialog(): void {
    const browseEntries = this.repCalBrowseList;
    if (!browseEntries.length) {
      return;
    }
    this.openRepCalEditDialog(browseEntries[0], browseEntries, 0);
  }

  /** Open an entry from a list-row click, browsing through all currently displayed entries. */
  openRepCalRowDialog(row: ReportCalendarEntryDto): void {
    const browseEntries = this.repCalDisplayedEntries;
    const index = Math.max(
      0,
      browseEntries.findIndex((e) => e.id === row.id),
    );
    this.openRepCalEditDialog(row, browseEntries, index);
  }

  repCalAttachmentSummary(row: ReportCalendarEntryDto): string {
    const n = row.attachments?.length ?? 0;
    if (!n) {
      return '';
    }
    const images = (row.attachments ?? []).filter((a) => this.repCalIsImageAttachment(a)).length;
    if (images && images === n) {
      return `${n} image${n === 1 ? '' : 's'}`;
    }
    return `${n} file${n === 1 ? '' : 's'}`;
  }

  private repCalIsImageAttachment(att: { contentType: string | null; originalFilename: string }): boolean {
    const ct = att.contentType?.toLowerCase() ?? '';
    if (ct.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(att.originalFilename);
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
      defaultType: this.repCalDefaultType(),
      typeOptions: this.repCalTypeOptionsForDialog(),
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

  openRepCalEditDialog(
    row: ReportCalendarEntryDto,
    browseEntries?: ReportCalendarEntryDto[],
    browseIndex?: number,
  ): void {
    const list = browseEntries ?? this.repCalBrowseList;
    const index =
      browseIndex ??
      Math.max(
        0,
        list.findIndex((e) => e.id === row.id),
      );
    const d: ReportCalendarEntryDialogData = {
      entry: row,
      defaultDate: row.entryDate,
      defaultType: row.calendarType,
      typeOptions: this.repCalTypeOptionsForDialog(),
      browseEntries: list,
      browseIndex: index,
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
    if (index === this.MGMT_TAB_WORK) {
      this.workPanel()?.refreshAll();
    }
    if (index === this.MGMT_TAB_TRAVEL) {
      this.travelPanel()?.refreshAll();
    }
    if (index === this.MGMT_TAB_DOCUMENTS) {
      this.documentsPanel()?.refreshAll();
    }
    if (index === this.MGMT_TAB_RECORDINGS) {
      this.recordingsPanel()?.refreshAll();
    }
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

  get writeupFilteredEntries(): ManagementWriteupDto[] {
    const q = this.writeupSearchTrim;
    if (!q) {
      return this.writeupsRaw;
    }
    const tokens = q.split(/\s+/).filter(Boolean);
    return this.writeupsRaw.filter((w) => this.writeupMatchesSearchTokens(w, tokens));
  }

  get selectedWriteup(): ManagementWriteupDto | null {
    if (this.writeupSelectedId == null) {
      return null;
    }
    return this.writeupsRaw.find((w) => w.id === this.writeupSelectedId) ?? null;
  }

  get writeupPeriodLabel(): string {
    return `${this.writeupYear}`;
  }

  /** Each token must appear in at least one of topic, highlight, body, or attachment filenames (same idea as banking txn filter). */
  private writeupMatchesSearchTokens(w: ManagementWriteupDto, tokens: string[]): boolean {
    const topic = (w.topic || '').toLowerCase();
    const hi = (w.highlight || '').toLowerCase();
    const body = (w.body || '').toLowerCase();
    const attNames = (w.attachments ?? [])
      .map((a) => (a.originalFilename || '').toLowerCase())
      .join(' ');
    const fields = [topic, hi, body, attNames];
    return tokens.every((tok) => fields.some((f) => f.includes(tok)));
  }

  loadWriteups(): void {
    this.api.listWriteups(this.writeupYear).subscribe({
      next: (rows) => {
        this.writeupsRaw = rows;
        if (this.writeupViewMode === 'compose' && this.writeupEditingId != null) {
          const found = rows.find((r) => r.id === this.writeupEditingId);
          if (!found) {
            this.resetWriteupForm();
          } else {
            this.writeupSelectedAttachments = [...(found.attachments ?? [])];
          }
        } else {
          this.syncWriteupSelectionAfterLoad();
          if (this.writeupSelectedId != null) {
            const found = rows.find((r) => r.id === this.writeupSelectedId);
            if (found) {
              this.writeupSelectedAttachments = [...(found.attachments ?? [])];
            }
          }
        }
      },
      error: (e) => this.err('Could not load write-ups', e),
    });
  }

  private syncWriteupSelectionAfterLoad(): void {
    if (this.writeupViewMode === 'compose') {
      return;
    }
    if (
      this.writeupSelectedId != null &&
      this.writeupFilteredEntries.some((w) => w.id === this.writeupSelectedId)
    ) {
      return;
    }
    this.writeupSelectedId = this.writeupFilteredEntries[0]?.id ?? null;
  }

  prevWriteupYear(): void {
    this.writeupYear -= 1;
    this.writeupViewMode = 'read';
    this.writeupEditingId = null;
    this.writeupSelectedId = null;
    this.loadWriteups();
  }

  nextWriteupYear(): void {
    this.writeupYear += 1;
    this.writeupViewMode = 'read';
    this.writeupEditingId = null;
    this.writeupSelectedId = null;
    this.loadWriteups();
  }

  startNewWriteup(): void {
    this.writeupEditingId = null;
    this.writeupSelectedId = null;
    this.writeupViewMode = 'compose';
    this.writeupComposerPane = 'split';
    this.writeupSelectedAttachments = [];
    this.writeupDraft = {
      topic: '',
      highlight: '',
      body: '',
    };
  }

  selectWriteup(w: ManagementWriteupDto): void {
    this.writeupSelectedId = w.id;
    this.writeupViewMode = 'read';
    this.writeupEditingId = null;
    this.writeupSelectedAttachments = [...(w.attachments ?? [])];
  }

  startEditWriteup(): void {
    const w = this.selectedWriteup;
    if (!w) {
      return;
    }
    this.writeupEditingId = w.id;
    this.writeupViewMode = 'compose';
    this.writeupComposerPane = 'split';
    this.writeupSelectedAttachments = [...(w.attachments ?? [])];
    this.writeupDraft = {
      topic: w.topic ?? '',
      highlight: w.highlight ?? '',
      body: w.body ?? '',
    };
  }

  cancelWriteupCompose(): void {
    if (this.writeupEditingId != null && this.writeupsRaw.some((w) => w.id === this.writeupEditingId)) {
      this.writeupSelectedId = this.writeupEditingId;
      this.writeupViewMode = 'read';
      this.writeupEditingId = null;
      const found = this.writeupsRaw.find((w) => w.id === this.writeupSelectedId);
      this.writeupSelectedAttachments = [...(found?.attachments ?? [])];
      return;
    }
    this.resetWriteupForm();
  }

  setWriteupComposerPane(pane: 'split' | 'write' | 'preview'): void {
    this.writeupComposerPane = pane;
  }

  resetWriteupForm(): void {
    this.writeupEditingId = null;
    this.writeupSelectedId = null;
    this.writeupViewMode = 'read';
    this.writeupComposerPane = 'split';
    this.writeupSelectedAttachments = [];
    this.writeupUploading = false;
    this.writeupDraft = {
      topic: '',
      highlight: '',
      body: '',
    };
    this.syncWriteupSelectionAfterLoad();
  }

  writeupAttachmentCountLabel(w: ManagementWriteupDto): string {
    const n = w.attachments?.length ?? 0;
    if (n < 1) {
      return '';
    }
    return `${n} ${n === 1 ? 'file' : 'files'}`;
  }

  onWriteupFilesSelected(event: Event, writeupId: number): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    this.writeupUploading = true;
    const list = Array.from(files);
    let i = 0;
    const step = (): void => {
      if (i >= list.length) {
        this.writeupUploading = false;
        input.value = '';
        this.loadWriteups();
        this.snackBar.open('Attachment(s) uploaded', undefined, { duration: 2000 });
        return;
      }
      this.api.uploadWriteupAttachment(writeupId, list[i]).subscribe({
        next: () => {
          i += 1;
          step();
        },
        error: (e) => {
          this.writeupUploading = false;
          input.value = '';
          this.err('Upload failed', e);
        },
      });
    };
    step();
  }

  openWriteupAttachment(attachmentId: number, _filename: string): void {
    this.api.getWriteupAttachmentBlob(attachmentId, 'inline').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const win = window.open(url, '_blank', 'noopener');
        if (!win) {
          URL.revokeObjectURL(url);
        } else {
          win.addEventListener('beforeunload', () => URL.revokeObjectURL(url));
        }
      },
      error: (e) => this.err('Could not open attachment', e),
    });
  }

  removeWriteupAttachment(attachmentId: number, ev: Event): void {
    ev.stopPropagation();
    this.api.deleteWriteupAttachment(attachmentId).subscribe({
      next: () => {
        this.snackBar.open('Attachment removed', undefined, { duration: 2000 });
        this.loadWriteups();
      },
      error: (e) => this.err('Could not remove attachment', e),
    });
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
          this.writeupEditingId = null;
          this.writeupViewMode = 'read';
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
          this.writeupEditingId = null;
          this.writeupViewMode = 'read';
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
    const id = this.writeupEditingId ?? this.writeupSelectedId;
    if (id == null) {
      return;
    }
    if (typeof window !== 'undefined' && !window.confirm('Delete this write-up permanently?')) {
      return;
    }
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

  get selectedMonthNote(): ManagementMonthNoteDto | null {
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

  private emptyNoteCalendarMonths(): { month: number; noteCount: number }[] {
    return Array.from({ length: 12 }, (_, i) => ({ month: i + 1, noteCount: 0 }));
  }

  reloadMonthNotesData(): void {
    this.api.notesCalendar(this.noteYear).subscribe({
      next: (c) => (this.noteCalendar = c),
      error: (e) => this.err('Could not load notes calendar', e),
    });
    this.api.listMonthNotes(this.noteYear, this.noteFilterMonth).subscribe({
      next: (rows) => {
        this.monthNotes = rows;
        this.syncNoteSelectionAfterLoad();
      },
      error: (e) => this.err('Could not load notes', e),
    });
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

  selectMonthNote(n: ManagementMonthNoteDto): void {
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
    this.noteViewMode = 'read';
    this.syncNoteSelectionAfterLoad();
  }

  startEditMonthNote(n: ManagementMonthNoteDto): void {
    this.noteEditingId = n.id;
    this.noteSelectedId = n.id;
    this.noteViewMode = 'compose';
    this.noteComposerPane = 'split';
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

  /** `localStorage` key for the signed-in app user; preserved only for one-time migration to the server vault. */
  private accountsStorageKey(): string | null {
    const u = this.auth.username?.trim();
    if (!u) {
      return null;
    }
    return `${ManagementComponent.LEGACY_LOCAL_STORAGE_KEY_BASE}.user.${u.toLowerCase()}`;
  }

  /** Server DTO → UI entry. UI keeps the same shape it had under localStorage so the template doesn't need changes. */
  private toUiEntry(dto: ManagementAccountDto): AccountEntry {
    return {
      id: dto.id,
      itemName: dto.itemName ?? '',
      folder: dto.folder ?? '',
      username: dto.username ?? '',
      password: dto.password ?? '',
      authenticatorKey: dto.authenticatorKey ?? '',
      website: dto.website ?? '',
      notes: dto.notes ?? '',
      createdAt: dto.createdAt ?? '',
      updatedAt: dto.updatedAt && dto.updatedAt.trim() !== '' ? dto.updatedAt : undefined,
    };
  }

  /**
   * Initial load: pull entries from the server. If the server is empty for this owner and the browser still has
   * legacy localStorage entries (per-user key or the unscoped pre-multi-user key), bulk-import them once and clear
   * the local copies so the server becomes the single source of truth.
   */
  private loadAccountsFromServer(): void {
    this.accountsLoading = true;
    this.api.listAccounts().subscribe({
      next: (dtos) => {
        this.accountEntries = (dtos ?? []).map((d) => this.toUiEntry(d));
        if (this.accountEntries.length === 0) {
          this.tryMigrateLocalStorageToServer();
        } else {
          // Server already has data — drop any leftover local copies so they can't drift.
          this.clearLegacyLocalStorage();
          this.accountsLoading = false;
        }
      },
      error: (e) => {
        this.accountsLoading = false;
        this.accountEntries = [];
        this.err('Could not load accounts', e);
      },
    });
  }

  /** Look for legacy localStorage entries and POST them to the server bulk-import endpoint. Idempotent on retry. */
  private tryMigrateLocalStorageToServer(): void {
    const legacy = this.readLegacyLocalStorageEntries();
    if (legacy.length === 0) {
      this.accountsLoading = false;
      return;
    }
    const bodies: ManagementAccountWriteBody[] = legacy.map((e) => ({
      itemName: e.itemName,
      folder: e.folder,
      username: e.username,
      password: e.password,
      authenticatorKey: e.authenticatorKey,
      website: e.website,
      notes: e.notes,
    }));
    this.api.bulkImportAccounts(bodies).subscribe({
      next: (res) => {
        this.api.listAccounts().subscribe({
          next: (dtos) => {
            this.accountEntries = (dtos ?? []).map((d) => this.toUiEntry(d));
            this.accountsLoading = false;
            this.clearLegacyLocalStorage();
            const msg =
              res.inserted > 0
                ? `Migrated ${res.inserted} account${res.inserted === 1 ? '' : 's'} to your server vault`
                : 'No new accounts to migrate';
            this.snackBar.open(msg, undefined, { duration: 4000 });
          },
          error: (e) => {
            this.accountsLoading = false;
            this.err('Migrated accounts but failed to reload', e);
          },
        });
      },
      error: (e) => {
        this.accountsLoading = false;
        this.err('Could not migrate local accounts to server', e);
      },
    });
  }

  private readLegacyLocalStorageEntries(): AccountEntry[] {
    if (typeof window === 'undefined') {
      return [];
    }
    const out: AccountEntry[] = [];
    const seenIds = new Set<number>();
    const keys = [this.accountsStorageKey(), ManagementComponent.LEGACY_LOCAL_STORAGE_KEY_BASE].filter(
      (k): k is string => !!k,
    );
    for (const key of keys) {
      let raw: string | null = null;
      try {
        raw = window.localStorage.getItem(key);
      } catch {
        continue;
      }
      if (!raw) {
        continue;
      }
      let parsed: unknown;
      try {
        parsed = JSON.parse(raw);
      } catch {
        continue;
      }
      if (!Array.isArray(parsed)) {
        continue;
      }
      for (const v of parsed) {
        if (!v || typeof v !== 'object') {
          continue;
        }
        const o = v as Record<string, unknown> & { websites?: unknown[] };
        let website = String(o['website'] ?? '');
        if (!website && Array.isArray(o.websites) && o.websites.length > 0) {
          website = String(o.websites[0]);
        }
        const id = Number(o['id']) || Date.now();
        if (seenIds.has(id)) {
          continue;
        }
        seenIds.add(id);
        const itemName = String(o['itemName'] ?? '').trim();
        if (!itemName) {
          continue;
        }
        out.push({
          id,
          itemName,
          folder: String(o['folder'] ?? ''),
          username: String(o['username'] ?? ''),
          password: String(o['password'] ?? ''),
          authenticatorKey: String(o['authenticatorKey'] ?? ''),
          website,
          notes: String(o['notes'] ?? ''),
          createdAt: String(o['createdAt'] ?? ''),
          updatedAt:
            o['updatedAt'] != null && String(o['updatedAt']).trim() !== ''
              ? String(o['updatedAt'])
              : undefined,
        });
      }
    }
    return out;
  }

  private clearLegacyLocalStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }
    const keys = [this.accountsStorageKey(), ManagementComponent.LEGACY_LOCAL_STORAGE_KEY_BASE].filter(
      (k): k is string => !!k,
    );
    for (const key of keys) {
      try {
        window.localStorage.removeItem(key);
      } catch {
        // best-effort cleanup
      }
    }
  }
}
