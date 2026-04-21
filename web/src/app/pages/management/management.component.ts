import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin } from 'rxjs';
import {
  BalanceUrgency,
  ManagementDayOneLogDto,
  ManagementTaskCategory,
  ManagementTaskDto,
  ManagementTaskType,
  TaskMonthCalendarDto,
} from '../../models/management.models';
import { ManagementApiService } from '../../services/management-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

interface CalendarCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  taskCount?: number;
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
  ],
  templateUrl: './management.component.html',
  styleUrl: './management.component.scss',
})
export class ManagementComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

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

  /** Journal (Day One) for the visible task-calendar month. */
  dayOneLogs: ManagementDayOneLogDto[] = [];
  dayOneDate: Date | null = null;
  dayOneText = '';
  dayOneColumns = ['d1Date', 'd1Preview', 'd1Updated', 'd1Actions'];

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.dayOneDate = this.dateFromIso(this.selectedDateIso);
    this.resetForm();
    this.reloadRefsAndCalendar();
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

  onMgmtTabChange(index: number): void {
    if (index === 1) {
      this.dayOneDate = this.dateFromIso(this.selectedDateIso);
      this.syncDayOneFormFromLogs();
    }
  }

  onDayOneDatePicked(): void {
    this.syncDayOneFormFromLogs();
  }

  dayOnePreview(text: string | null | undefined): string {
    const t = (text ?? '').replace(/\s+/g, ' ').trim();
    if (!t) {
      return '—';
    }
    return t.length > 160 ? `${t.slice(0, 160)}…` : t;
  }

  formatDayOneInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const ms = Date.parse(iso);
    if (Number.isNaN(ms)) {
      return iso;
    }
    return new Date(ms).toLocaleString();
  }

  loadDayOneRowIntoEditor(row: ManagementDayOneLogDto): void {
    if (!row.loggedOn || row.loggedOn.length < 10) {
      return;
    }
    this.dayOneDate = this.dateFromIso(row.loggedOn);
    this.dayOneText = row.entryText ?? '';
  }

  saveDayOne(): void {
    const iso = this.dayOneDate ? this.toIsoDate(this.dayOneDate) : this.selectedDateIso;
    if (!iso || iso.length < 10) {
      return;
    }
    const entryText = (this.dayOneText || '').trim();
    if (!entryText) {
      this.snackBar.open('Write something before saving', undefined, { duration: 3500 });
      return;
    }
    this.api.upsertDayOne({ loggedOn: iso, entryText }).subscribe({
      next: (dto) => {
        this.snackBar.open('Day One entry saved', undefined, { duration: 2500 });
        const y = Number(dto.loggedOn.slice(0, 4));
        const m = Number(dto.loggedOn.slice(5, 7));
        if (Number.isFinite(y) && Number.isFinite(m) && (y !== this.calendarYear || m !== this.calendarMonth)) {
          this.calendarYear = y;
          this.calendarMonth = m;
        }
        this.selectedDateIso = dto.loggedOn.length >= 10 ? dto.loggedOn : iso;
        this.clampSelectedToMonth();
        this.dayOneDate = this.dateFromIso(this.selectedDateIso);
        this.reloadRefsAndCalendar();
      },
      error: (e) => this.err('Could not save Day One entry', e),
    });
  }

  deleteDayOneRow(row: ManagementDayOneLogDto): void {
    this.api.deleteDayOne(row.id).subscribe({
      next: () => {
        this.snackBar.open('Entry removed', undefined, { duration: 2500 });
        this.syncDayOneFormFromLogs();
        this.reloadRefsAndCalendar();
      },
      error: (e) => this.err('Could not delete entry', e),
    });
  }

  private syncDayOneFormFromLogs(): void {
    const iso = this.dayOneDate ? this.toIsoDate(this.dayOneDate) : this.selectedDateIso;
    const sameDay = this.dayOneLogs.filter((l) => l.loggedOn === iso);
    const hit =
      sameDay.length === 0 ? undefined : sameDay.reduce((a, b) => (a.id > b.id ? a : b));
    this.dayOneText = hit?.entryText ?? '';
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
      cal: this.api.taskCalendar(this.calendarYear, this.calendarMonth),
      un: this.api.listUnscheduledTasks(),
      cat: this.api.listCategories(),
      tt: this.api.listTaskTypes(),
      d1: this.api.listDayOneLogs(this.calendarYear, this.calendarMonth),
    }).subscribe({
      next: ({ cal, un, cat, tt, d1 }) => {
        this.monthCal = cal;
        this.unscheduled = un;
        this.categories = cat;
        this.taskTypes = tt;
        this.dayOneLogs = [...d1].sort((a, b) => b.loggedOn.localeCompare(a.loggedOn));
        this.syncDayOneFormFromLogs();
      },
      error: (e) => this.err('Could not load management data', e),
    });
  }

  private reloadCalendarOnly(): void {
    forkJoin({
      cal: this.api.taskCalendar(this.calendarYear, this.calendarMonth),
      un: this.api.listUnscheduledTasks(),
      d1: this.api.listDayOneLogs(this.calendarYear, this.calendarMonth),
    }).subscribe({
      next: ({ cal, un, d1 }) => {
        this.monthCal = cal;
        this.unscheduled = un;
        this.dayOneLogs = [...d1].sort((a, b) => b.loggedOn.localeCompare(a.loggedOn));
        this.syncDayOneFormFromLogs();
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

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
