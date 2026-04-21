import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin } from 'rxjs';
import {
  BalanceUrgency,
  DayOneCalendarDayDto,
  DayOneCountsDto,
  ManagementDayOneLogDto,
  ManagementDayOneTagDefDto,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  TaskMonthCalendarDto,
} from '../../models/management.models';
import { ManagementApiService } from '../../services/management-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  DayOneAttachmentsDialogComponent,
  DayOneAttachmentsDialogData,
} from './day-one-attachments-dialog.component';

interface CalendarCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  taskCount?: number;
  trackKey: string;
}

interface JournalCalCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  entryCount?: number;
  level?: number;
  trackKey: string;
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
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatChipsModule,
    MatDividerModule,
    MatDialogModule,
  ],
  templateUrl: './management.component.html',
  styleUrl: './management.component.scss',
})
export class ManagementComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
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

  // --- Journal (Day One) — own month + API
  journalYear = new Date().getFullYear();
  journalMonth = new Date().getMonth() + 1;
  journalSelectedIso = '';
  journalEntries: ManagementDayOneLogDto[] = [];
  journalCalDays: DayOneCalendarDayDto[] = [];
  journalCounts: DayOneCountsDto | null = null;
  journalTagDefs: ManagementDayOneTagDefDto[] = [];
  journalFilterQ = '';
  journalFilterTagIds: number[] = [];
  journalPendingFilterQ = '';
  journalPendingFilterTagIds: number[] = [];
  journalShowDayOnly = false;

  journalEditingId: number | null = null;
  journalBody = '';
  journalLocation = '';
  journalWeather = '';
  journalFormTagIds: number[] = [];

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.journalYear = this.calendarYear;
    this.journalMonth = this.calendarMonth;
    this.journalSelectedIso = t;
    this.journalPendingFilterQ = '';
    this.journalPendingFilterTagIds = [];
    this.resetForm();
    this.reloadRefsAndCalendar();
  }

  get calendarTitle(): string {
    const d = new Date(this.calendarYear, this.calendarMonth - 1, 1);
    return d.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  get journalTitle(): string {
    const d = new Date(this.journalYear, this.journalMonth - 1, 1);
    return d.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  selectedDayLabel(): string {
    return this.longDateLabel(this.selectedDateIso);
  }

  journalSelectedLabel(): string {
    return this.longDateLabel(this.journalSelectedIso);
  }

  /** Bound to the journal date picker (Material expects a `Date`). */
  get journalPickerDate(): Date {
    const iso = this.normalizeJournalDate(this.journalSelectedIso);
    if (!iso || iso.length < 10) {
      return this.dateFromIso(this.defaultDayInMonth(this.journalYear, this.journalMonth));
    }
    return this.dateFromIso(iso);
  }

  onJournalPickerDateChange(d: Date | null): void {
    if (!d) {
      return;
    }
    this.journalSelectedIso = this.toIsoDate(d);
    this.reloadJournalCounts();
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

  journalCalMap(): Map<string, DayOneCalendarDayDto> {
    const m = new Map<string, DayOneCalendarDayDto>();
    for (const d of this.journalCalDays) {
      m.set(this.normalizeJournalDate(d.date), d);
    }
    return m;
  }

  journalCalendarRows(): JournalCalCell[][] {
    const y = this.journalYear;
    const m = this.journalMonth;
    const byDay = this.journalCalMap();
    const last = new Date(y, m, 0).getDate();
    const firstDow = new Date(y, m - 1, 1).getDay();

    const flat: JournalCalCell[] = [];
    let padSeq = 0;
    for (let i = 0; i < firstDow; i++) {
      padSeq += 1;
      flat.push({ type: 'pad', trackKey: `j-pad-head-${padSeq}` });
    }
    for (let d = 1; d <= last; d++) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      const info = byDay.get(iso);
      flat.push({
        type: 'day',
        iso,
        label: String(d),
        entryCount: info?.entryCount ?? 0,
        level: info?.level ?? 0,
        trackKey: `j-day-${iso}`,
      });
    }
    let tail = 0;
    while (flat.length % 7 !== 0) {
      tail += 1;
      flat.push({ type: 'pad', trackKey: `j-pad-tail-${tail}` });
    }
    const rows: JournalCalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  journalLevelClass(level: number | undefined): string {
    const n = level ?? 0;
    if (n <= 0) {
      return 'jcal--l0';
    }
    if (n === 1) {
      return 'jcal--l1';
    }
    if (n === 2) {
      return 'jcal--l2';
    }
    if (n === 3) {
      return 'jcal--l3';
    }
    return 'jcal--l4';
  }

  journalStreamEntries(): ManagementDayOneLogDto[] {
    const rows = [...this.journalEntries].sort((a, b) => {
      const da = this.normalizeJournalDate(a.loggedOn).localeCompare(this.normalizeJournalDate(b.loggedOn));
      if (da !== 0) {
        return da > 0 ? -1 : 1;
      }
      return b.id - a.id;
    });
    if (!this.journalShowDayOnly) {
      return rows;
    }
    const sel = this.normalizeJournalDate(this.journalSelectedIso);
    return rows.filter((r) => this.normalizeJournalDate(r.loggedOn) === sel);
  }

  isJournalSelected(iso: string | undefined): boolean {
    return !!iso && iso === this.normalizeJournalDate(this.journalSelectedIso);
  }

  isJournalEntryActive(row: ManagementDayOneLogDto): boolean {
    return this.journalEditingId != null && row.id === this.journalEditingId;
  }

  isSelected(iso: string | undefined): boolean {
    return !!iso && iso === this.selectedDateIso;
  }

  selectDay(cell: CalendarCell): void {
    if (cell.type !== 'day' || !cell.iso) {
      return;
    }
    this.selectedDateIso = cell.iso;
  }

  selectJournalDay(cell: JournalCalCell): void {
    if (cell.type !== 'day' || !cell.iso) {
      return;
    }
    this.journalSelectedIso = cell.iso;
    this.reloadJournalCounts();
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

  journalPrevMonth(): void {
    let y = this.journalYear;
    let mo = this.journalMonth - 1;
    if (mo < 1) {
      mo = 12;
      y -= 1;
    }
    this.journalYear = y;
    this.journalMonth = mo;
    this.clampJournalSelectedToMonth();
    this.loadJournalPage();
  }

  journalNextMonth(): void {
    let y = this.journalYear;
    let mo = this.journalMonth + 1;
    if (mo > 12) {
      mo = 1;
      y += 1;
    }
    this.journalYear = y;
    this.journalMonth = mo;
    this.clampJournalSelectedToMonth();
    this.loadJournalPage();
  }

  onMgmtTabChange(index: number): void {
    if (index === 1) {
      this.journalPendingFilterQ = this.journalFilterQ;
      this.journalPendingFilterTagIds = [...this.journalFilterTagIds];
      this.loadJournalPage();
    }
  }

  applyJournalFilters(): void {
    this.journalFilterQ = (this.journalPendingFilterQ || '').trim();
    this.journalFilterTagIds = [...(this.journalPendingFilterTagIds ?? [])];
    this.loadJournalPage();
  }

  clearJournalFilters(): void {
    this.journalPendingFilterQ = '';
    this.journalPendingFilterTagIds = [];
    this.journalFilterQ = '';
    this.journalFilterTagIds = [];
    this.loadJournalPage();
  }

  newJournalEntry(): void {
    this.journalEditingId = null;
    this.journalBody = '';
    this.journalLocation = '';
    this.journalWeather = '';
    this.journalFormTagIds = [];
  }

  editJournalEntry(row: ManagementDayOneLogDto): void {
    this.journalEditingId = row.id;
    this.journalSelectedIso = this.normalizeJournalDate(row.loggedOn);
    this.journalBody = row.entryText ?? '';
    this.journalLocation = row.locationText ?? '';
    this.journalWeather = row.weatherText ?? '';
    this.journalFormTagIds = (row.tags ?? []).map((t) => t.id).filter((id) => id != null) as number[];
  }

  saveJournalEntry(): void {
    const iso = this.normalizeJournalDate(this.journalSelectedIso);
    if (!iso || iso.length < 10) {
      return;
    }
    const entryText = (this.journalBody || '').trim();
    if (!entryText) {
      this.snackBar.open('Write something before saving', undefined, { duration: 3500 });
      return;
    }
    const body = {
      loggedOn: iso,
      entryText,
      locationText: (this.journalLocation || '').trim() || undefined,
      weatherText: (this.journalWeather || '').trim() || undefined,
      tagIds: this.journalFormTagIds.length ? [...this.journalFormTagIds] : undefined,
    };
    if (this.journalEditingId != null) {
      this.api.updateDayOneEntry(this.journalEditingId, body).subscribe({
        next: () => {
          this.snackBar.open('Entry updated', undefined, { duration: 2500 });
          this.loadJournalPage();
        },
        error: (e) => this.err('Could not update entry', e),
      });
    } else {
      this.api.createDayOneEntry(body).subscribe({
        next: () => {
          this.snackBar.open('Entry saved', undefined, { duration: 2500 });
          this.newJournalEntry();
          this.loadJournalPage();
        },
        error: (e) => this.err('Could not save entry', e),
      });
    }
  }

  deleteJournalEntry(row: ManagementDayOneLogDto): void {
    this.api.deleteDayOneEntry(row.id).subscribe({
      next: () => {
        this.snackBar.open('Entry removed', undefined, { duration: 2500 });
        if (this.journalEditingId === row.id) {
          this.newJournalEntry();
        }
        this.loadJournalPage();
      },
      error: (e) => this.err('Could not delete entry', e),
    });
  }

  onJournalFileSelected(event: Event, row: ManagementDayOneLogDto): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.api.uploadDayOneAttachment(row.id, file).subscribe({
      next: () => {
        this.snackBar.open('Attachment added', undefined, { duration: 2500 });
        this.loadJournalPage();
      },
      error: (e) => this.err('Could not upload file', e),
    });
  }

  openJournalAttachments(row: ManagementDayOneLogDto): void {
    const atts = row.attachments ?? [];
    if (!atts.length) {
      return;
    }
    this.dialog.open<DayOneAttachmentsDialogComponent, DayOneAttachmentsDialogData>(DayOneAttachmentsDialogComponent, {
      width: 'min(96vw, 520px)',
      data: { attachments: atts },
    });
  }

  journalPreview(text: string | null | undefined): string {
    const t = (text ?? '').replace(/\s+/g, ' ').trim();
    if (!t) {
      return '—';
    }
    return t.length > 220 ? `${t.slice(0, 220)}…` : t;
  }

  formatJournalInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const ms = Date.parse(iso);
    if (Number.isNaN(ms)) {
      return iso;
    }
    return new Date(ms).toLocaleString();
  }

  tasksForSelectedDay(): ManagementTaskDto[] {
    const iso = this.selectedDateIso;
    if (!iso || !this.monthCal?.tasksByDay) {
      return [];
    }
    return [...(this.monthCal.tasksByDay[iso] ?? [])].sort((a, b) =>
      b.urgency.localeCompare(a.urgency),
    );
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

  loadJournalPage(): void {
    const y = this.journalYear;
    const m = this.journalMonth;
    const q = this.journalFilterQ || undefined;
    const tagIds = this.journalFilterTagIds?.length ? [...this.journalFilterTagIds] : undefined;
    const sel = this.normalizeJournalDate(this.journalSelectedIso);
    const dayNum = sel.length >= 10 ? Number(sel.slice(8, 10)) : NaN;
    forkJoin({
      entries: this.api.dayOneEntriesForMonth(y, m, q, tagIds),
      cal: this.api.dayOneCalendar(y, m),
      counts: this.api.dayOneCounts(y, m, Number.isFinite(dayNum) ? dayNum : undefined),
      tags: this.api.listDayOneTagDefinitions(),
    }).subscribe({
      next: ({ entries, cal, counts, tags }) => {
        this.journalEntries = entries;
        this.journalCalDays = cal;
        this.journalCounts = counts;
        this.journalTagDefs = [...tags].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load journal', e),
    });
  }

  private reloadJournalCounts(): void {
    const y = this.journalYear;
    const m = this.journalMonth;
    const sel = this.normalizeJournalDate(this.journalSelectedIso);
    const dayNum = sel.length >= 10 ? Number(sel.slice(8, 10)) : NaN;
    this.api.dayOneCounts(y, m, Number.isFinite(dayNum) ? dayNum : undefined).subscribe({
      next: (c) => (this.journalCounts = c),
      error: () => {},
    });
  }

  private reloadRefsAndCalendar(): void {
    forkJoin({
      cal: this.api.taskCalendar(this.calendarYear, this.calendarMonth),
      un: this.api.listUnscheduledTasks(),
      cat: this.api.listCategories(),
      tt: this.api.listTaskTypes(),
    }).subscribe({
      next: ({ cal, un, cat, tt }) => {
        this.monthCal = cal;
        this.unscheduled = un;
        this.categories = cat;
        this.taskTypes = tt;
      },
      error: (e) => this.err('Could not load management data', e),
    });
  }

  private reloadCalendarOnly(): void {
    forkJoin({
      cal: this.api.taskCalendar(this.calendarYear, this.calendarMonth),
      un: this.api.listUnscheduledTasks(),
    }).subscribe({
      next: ({ cal, un }) => {
        this.monthCal = cal;
        this.unscheduled = un;
      },
      error: (e) => this.err('Could not load calendar', e),
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
  }

  private clampJournalSelectedToMonth(): void {
    const y = this.journalYear;
    const m = this.journalMonth;
    const last = new Date(y, m, 0).getDate();
    if (!this.journalSelectedIso) {
      this.journalSelectedIso = this.defaultDayInMonth(y, m);
      return;
    }
    const ySel = Number(this.journalSelectedIso.slice(0, 4));
    const mSel = Number(this.journalSelectedIso.slice(5, 7));
    if (ySel !== y || mSel !== m) {
      this.journalSelectedIso = this.defaultDayInMonth(y, m);
      return;
    }
    const d = Number(this.journalSelectedIso.slice(8, 10));
    const day = Math.min(Math.max(1, d), last);
    this.journalSelectedIso = `${y}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
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

  private longDateLabel(iso: string): string {
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

  /** Normalize API date (string or Jackson array) to yyyy-MM-dd */
  normalizeJournalDate(d: string | unknown): string {
    if (d == null) {
      return '';
    }
    if (typeof d === 'string') {
      return d.length >= 10 ? d.slice(0, 10) : d;
    }
    if (Array.isArray(d) && d.length >= 3) {
      const y = Number(d[0]);
      const mo = Number(d[1]);
      const day = Number(d[2]);
      return `${y}-${String(mo).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
    return String(d).slice(0, 10);
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
