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
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  ReportCalendarEntryDialogComponent,
  ReportCalendarEntryDialogData,
} from '../reports/report-calendar-entry-dialog.component';

interface CalendarCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  taskCount?: number;
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
  websites: string[];
  notes: string;
  createdAt: string;
}

@Component({
  selector: 'app-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
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
  private static readonly UTILITIES_STORAGE_KEY = 'management.utilities.entries.v1';
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
    websiteInput: '',
    websites: [] as string[],
    notes: '',
  };
  utilityEntries: UtilityEntry[] = [];

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

  addUtilityWebsite(): void {
    const raw = this.utilityEntryDraft.websiteInput.trim();
    if (!raw) {
      return;
    }
    if (this.utilityEntryDraft.websites.includes(raw)) {
      this.utilityEntryDraft.websiteInput = '';
      return;
    }
    this.utilityEntryDraft.websites = [...this.utilityEntryDraft.websites, raw];
    this.utilityEntryDraft.websiteInput = '';
  }

  removeUtilityWebsite(site: string): void {
    this.utilityEntryDraft.websites = this.utilityEntryDraft.websites.filter((s) => s !== site);
  }

  saveUtilityEntry(): void {
    const itemName = this.utilityEntryDraft.itemName.trim();
    if (!itemName) {
      this.snackBar.open('Item name is required', undefined, { duration: 2500 });
      return;
    }
    const entry: UtilityEntry = {
      id: Date.now(),
      itemName,
      folder: this.utilityEntryDraft.folder.trim(),
      username: this.utilityEntryDraft.username.trim(),
      password: this.utilityEntryDraft.password.trim(),
      authenticatorKey: this.utilityEntryDraft.authenticatorKey.trim(),
      websites: [...this.utilityEntryDraft.websites],
      notes: this.utilityEntryDraft.notes.trim(),
      createdAt: new Date().toISOString(),
    };
    this.utilityEntries = [entry, ...this.utilityEntries];
    this.persistUtilitiesToStorage();
    this.resetUtilityForm();
    this.snackBar.open('Utility item saved', undefined, { duration: 2500 });
  }

  deleteUtilityEntry(id: number): void {
    this.utilityEntries = this.utilityEntries.filter((e) => e.id !== id);
    this.persistUtilitiesToStorage();
    this.snackBar.open('Utility item removed', undefined, { duration: 2500 });
  }

  resetUtilityForm(): void {
    this.utilityEntryDraft = {
      itemName: '',
      folder: '',
      username: '',
      password: '',
      authenticatorKey: '',
      websiteInput: '',
      websites: [],
      notes: '',
    };
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
    for (let d = 1; d <= last; d++) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const n = (byDay[iso] ?? []).length;
      flat.push({
        type: 'day',
        iso,
        label: String(d),
        taskCount: n,
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

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  private loadUtilitiesFromStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }
    try {
      const raw = window.localStorage.getItem(ManagementComponent.UTILITIES_STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return;
      }
      this.utilityEntries = parsed.filter((v) => v && typeof v === 'object').map((v) => ({
        id: Number((v as UtilityEntry).id) || Date.now(),
        itemName: String((v as UtilityEntry).itemName ?? ''),
        folder: String((v as UtilityEntry).folder ?? ''),
        username: String((v as UtilityEntry).username ?? ''),
        password: String((v as UtilityEntry).password ?? ''),
        authenticatorKey: String((v as UtilityEntry).authenticatorKey ?? ''),
        websites: Array.isArray((v as UtilityEntry).websites)
          ? (v as UtilityEntry).websites.map((s) => String(s))
          : [],
        notes: String((v as UtilityEntry).notes ?? ''),
        createdAt: String((v as UtilityEntry).createdAt ?? ''),
      }));
    } catch {
      this.utilityEntries = [];
    }
  }

  private persistUtilitiesToStorage(): void {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.setItem(ManagementComponent.UTILITIES_STORAGE_KEY, JSON.stringify(this.utilityEntries));
  }
}
