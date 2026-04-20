import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, forkJoin, of } from 'rxjs';
import {
  DailyExerciseLogLineDto,
  DailyExerciseReportDto,
  ExerciseDayLog,
  MonthActivityCalendarDto,
  MonthlyExerciseReportDto,
} from '../../models/fitness.models';
import { ManagementDayOneLogDto, ManagementTaskDto } from '../../models/management.models';
import { FitnessApiService } from '../../services/fitness-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

/** Padding slot or a real day in the month grid. */
interface CalendarCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  kind?: 'empty' | 'strength' | 'weight' | 'both';
  exerciseMinutes?: number;
  weightKg?: number | null;
  /** Stable id for @for track (outer row index not visible in nested track). */
  trackKey: string;
}

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatSnackBarModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent implements OnInit {
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  private static readonly LB_PER_KG = 2.2046226218;

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  /** Visible calendar month. */
  calendarYear = new Date().getFullYear();
  calendarMonth = new Date().getMonth() + 1;

  /** Highlighted day for the daily panel (yyyy-MM-dd). */
  selectedDateIso = '';

  monthCal: MonthActivityCalendarDto | null = null;
  monthlyReport: MonthlyExerciseReportDto | null = null;
  dailyReport: DailyExerciseReportDto | null = null;
  /** Inline message when GET /reports/daily fails (readable HTTP detail). */
  dayDetailError: string | null = null;

  /** Selected-day exercise table (prefixes avoid clashing with monthly matColumnDef names). */
  dailyLogColumns = ['dailyExName', 'dailyDuration', 'dailyNotes'];
  monthlyExColumns = ['monthExName', 'monthLogCount', 'monthDaysTrained'];

  /** Management → Tasks report (full task list). */
  managementTasks: ManagementTaskDto[] = [];
  managementTaskColumns = [
    'mtTitle',
    'mtDue',
    'mtUrgency',
    'mtCategory',
    'mtType',
    'mtDone',
    'mtCreated',
  ];

  /** Management → Day One entries for {@link calendarYear}/{@link calendarMonth} (same month as Exercise calendar). */
  managementDayOneLogs: ManagementDayOneLogDto[] = [];
  managementDayOneColumns = ['d1Date', 'd1Preview', 'd1Updated'];

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.loadExerciseMonth();
    this.loadSelectedDay();
    this.loadManagementTasksReport();
  }

  get calendarTitle(): string {
    const d = new Date(this.calendarYear, this.calendarMonth - 1, 1);
    return d.toLocaleString(undefined, { month: 'long', year: 'numeric' });
  }

  /** Long form of {@link selectedDateIso} for the day-detail column header. */
  selectedDayDisplayLabel(): string {
    const iso = this.selectedDateIso;
    if (!iso || iso.length < 10) {
      return '';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d)) {
      return iso;
    }
    return new Date(y, m - 1, d).toLocaleDateString(undefined, {
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
    });
  }

  kgToLb(kg: number | null | undefined): number {
    if (kg == null || Number.isNaN(kg)) {
      return 0;
    }
    return kg * ReportsComponent.LB_PER_KG;
  }

  /** Formats stored total minutes for the daily log table. */
  formatDurationMinutes(total: number | null | undefined): string {
    if (total == null || total <= 0) {
      return '—';
    }
    const h = Math.floor(total / 60);
    const m = total % 60;
    const parts: string[] = [];
    if (h > 0) {
      parts.push(`${h} h`);
    }
    if (m > 0) {
      parts.push(`${m} min`);
    }
    return parts.length ? parts.join(' ') : '—';
  }

  formatCalendarWeightLb(kg: number | null | undefined): string {
    if (kg == null || Number.isNaN(Number(kg))) {
      return '—';
    }
    return `${this.kgToLb(Number(kg)).toFixed(1)} lb`;
  }

  formatCalendarExerciseMinutes(total: number | null | undefined): string {
    if (total == null || !Number.isFinite(Number(total)) || Number(total) <= 0) {
      return '—';
    }
    const mins = Math.floor(Number(total));
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    if (h > 0 && m > 0) {
      return `${h}h ${m}m`;
    }
    if (h > 0) {
      return `${h}h`;
    }
    return `${m}m`;
  }

  monthExerciseMinutes(): number {
    const map = this.monthCal?.exerciseDurationMinutesByDay;
    if (!map) {
      return 0;
    }
    return Object.values(map).reduce((sum, v) => {
      const n = Number(v);
      return Number.isFinite(n) && n > 0 ? sum + Math.floor(n) : sum;
    }, 0);
  }

  selectedDayExerciseMinutes(): number {
    const lines = this.dailyReport?.logLines ?? [];
    return lines.reduce((sum, row) => {
      const n = Number(row.durationMinutes ?? 0);
      return Number.isFinite(n) && n > 0 ? sum + Math.floor(n) : sum;
    }, 0);
  }

  /** 7-column rows: week headers + day cells (padding + days). */
  calendarRows(): CalendarCell[][] {
    const cal = this.monthCal;
    if (!cal) {
      return [];
    }
    const y = cal.year;
    const m = cal.month;
    const strength = new Set(cal.daysWithStrengthTraining);
    const weight = new Set(cal.daysWithWeightLogged);
    const exDurationByDay = cal.exerciseDurationMinutesByDay ?? {};
    const weightKgByDay = cal.bodyWeightKgByDay ?? {};
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
      const hasS = strength.has(iso);
      const hasW = weight.has(iso);
      const exerciseMinutesRaw = exDurationByDay[iso];
      const exerciseMinutes = Number.isFinite(Number(exerciseMinutesRaw))
        ? Math.max(0, Math.floor(Number(exerciseMinutesRaw)))
        : 0;
      const weightKgRaw = weightKgByDay[iso];
      const weightKg = Number.isFinite(Number(weightKgRaw)) ? Number(weightKgRaw) : null;
      let kind: 'empty' | 'strength' | 'weight' | 'both' = 'empty';
      if (hasS && hasW) {
        kind = 'both';
      } else if (hasS) {
        kind = 'strength';
      } else if (hasW) {
        kind = 'weight';
      }
      flat.push({ type: 'day', iso, label: String(d), kind, exerciseMinutes, weightKg, trackKey: `day-${iso}` });
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
    this.loadSelectedDay();
  }

  prevMonth(): void {
    let y = this.calendarYear;
    let m = this.calendarMonth - 1;
    if (m < 1) {
      m = 12;
      y -= 1;
    }
    this.calendarYear = y;
    this.calendarMonth = m;
    this.clampSelectedToMonth();
    this.loadExerciseMonth();
    this.loadSelectedDay();
  }

  nextMonth(): void {
    let y = this.calendarYear;
    let m = this.calendarMonth + 1;
    if (m > 12) {
      m = 1;
      y += 1;
    }
    this.calendarYear = y;
    this.calendarMonth = m;
    this.clampSelectedToMonth();
    this.loadExerciseMonth();
    this.loadSelectedDay();
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

  private loadExerciseMonth(): void {
    const y = this.calendarYear;
    const mo = this.calendarMonth;
    if (!y || mo < 1 || mo > 12) {
      return;
    }
    forkJoin({
      monthly: this.fitnessApi.monthlyReport(y, mo),
      cal: this.fitnessApi.monthActivityCalendar(y, mo),
      dayOne: this.managementApi.listDayOneLogsReport(y, mo).pipe(catchError(() => of<ManagementDayOneLogDto[]>([]))),
    }).subscribe({
      next: ({ monthly, cal, dayOne }) => {
        this.monthlyReport = monthly;
        this.monthCal = cal;
        this.managementDayOneLogs = [...dayOne].sort((a, b) => b.loggedOn.localeCompare(a.loggedOn));
      },
      error: (e) => this.err('Could not load month report', e),
    });
  }

  private loadSelectedDay(): void {
    const date = this.selectedDateIso;
    if (!date) {
      return;
    }
    this.dayDetailError = null;
    this.dailyReport = null;
    forkJoin({
      report: this.fitnessApi.dailyReport(date),
      dayLogs: this.fitnessApi.listDayLogsBetween(date, date).pipe(catchError(() => of<ExerciseDayLog[]>([]))),
    }).subscribe({
      next: ({ report, dayLogs }) => {
        this.dayDetailError = null;
        const fromReport = Array.isArray(report.logLines) ? report.logLines : [];
        const logLines = fromReport.length > 0 ? fromReport : this.mapExerciseDayLogsToDailyLines(dayLogs);
        this.dailyReport = {
          ...report,
          logLines,
        };
      },
      error: (e) => {
        const detail = formatHttpErrorDetail(e);
        this.dayDetailError = detail;
        this.snackBar.open(`Could not load day detail: ${detail}`, 'Dismiss', { duration: 12_000 });
      },
    });
  }

  private mapExerciseDayLogsToDailyLines(logs: ExerciseDayLog[]): DailyExerciseLogLineDto[] {
    return logs
      .filter((l) => l.exercise?.id != null)
      .sort((a, b) => {
        const na = a.exercise!.name ?? '';
        const nb = b.exercise!.name ?? '';
        const byName = na.localeCompare(nb, undefined, { sensitivity: 'base' });
        if (byName !== 0) {
          return byName;
        }
        return (a.id ?? 0) - (b.id ?? 0);
      })
      .map((l) => ({
        id: l.id,
        exerciseId: l.exercise!.id!,
        exerciseName: l.exercise!.name ?? '—',
        notes: l.notes ?? '',
        durationMinutes: l.durationMinutes ?? null,
      }));
  }

  private todayIso(): string {
    const d = new Date();
    const y = d.getFullYear();
    const mo = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${mo}-${day}`;
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  private loadManagementTasksReport(): void {
    this.managementApi
      .listTasksReport()
      .pipe(catchError(() => of<ManagementTaskDto[]>([])))
      .subscribe({
        next: (rows) => {
          this.managementTasks = [...rows].sort((a, b) => {
            const da = a.dueDate ?? '';
            const db = b.dueDate ?? '';
            if (da !== db) {
              return db.localeCompare(da);
            }
            return (b.id ?? 0) - (a.id ?? 0);
          });
        },
      });
  }

  formatMgmtDate(iso: string | null | undefined): string {
    if (!iso || iso.length < 10) {
      return '—';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d).toLocaleDateString();
  }

  formatMgmtInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const t = Date.parse(iso);
    if (Number.isNaN(t)) {
      return iso;
    }
    return new Date(t).toLocaleString();
  }

  urgencyClass(u: string): string {
    if (u === 'HIGH') {
      return 'rep-urgency-high';
    }
    if (u === 'LOW') {
      return 'rep-urgency-low';
    }
    return 'rep-urgency-mid';
  }

  dayOneReportPreview(text: string | null | undefined): string {
    const t = (text ?? '').replace(/\s+/g, ' ').trim();
    if (!t) {
      return '—';
    }
    return t.length > 200 ? `${t.slice(0, 200)}…` : t;
  }

  formatDayOneReportInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const ms = Date.parse(iso);
    if (Number.isNaN(ms)) {
      return iso;
    }
    return new Date(ms).toLocaleString();
  }
}
