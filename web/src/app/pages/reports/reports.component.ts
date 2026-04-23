import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
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
import { JournalEntryDto, JournalSummaryDto, JournalTagDefDto } from '../../models/journal.models';
import { ManagementTaskDto } from '../../models/management.models';
import { ReportCalendarEntryDto, ReportCalendarType } from '../../models/report-calendar.models';
import { FitnessApiService } from '../../services/fitness-api.service';
import { JournalApiService } from '../../services/journal-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { ReportCalendarApiService } from '../../services/report-calendar-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  ReportJournalAttachmentsDialogComponent,
  ReportJournalAttachmentsDialogData,
} from './report-journal-attachments-dialog.component';
import { ReportJournalBodyDialogComponent, ReportJournalBodyDialogData } from './report-journal-body-dialog.component';
import {
  ReportCalendarEntryDialogComponent,
  ReportCalendarEntryDialogData,
} from './report-calendar-entry-dialog.component';

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

/** Report calendar (typed notes) month grid. */
interface ReportCalCell {
  type: 'pad' | 'day';
  iso?: string;
  label?: string;
  hasEntry?: boolean;
  trackKey: string;
}

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatSnackBarModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatMenuModule,
    MatButtonToggleModule,
  ],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent implements OnInit {
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly journalApi = inject(JournalApiService);
  private readonly reportCalApi = inject(ReportCalendarApiService);
  private readonly dialog = inject(MatDialog);
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

  journalFrom = '';
  journalTo = '';
  journalQ = '';
  journalTagIds: number[] = [];
  journalTagDefs: JournalTagDefDto[] = [];
  journalReportRows: JournalEntryDto[] = [];
  journalReportColumns = ['jDate', 'jTags', 'jAtt', 'jExcerpt', 'jMenu'];
  journalSummary: JournalSummaryDto | null = null;
  journalSearched = false;

  /** Reports → Calendar: one calendar type and view at a time. */
  repCalType: ReportCalendarType = 'PERSONAL';
  repCalView: 'day' | 'month' | 'year' = 'month';
  repCalAnchorIso = '';
  repCalEntries: ReportCalendarEntryDto[] = [];
  repCalTableColumns = ['cDate', 'cTitle', 'cInfo', 'cAct'];
  readonly repCalTypeOptions: { value: ReportCalendarType; label: string }[] = [
    { value: 'BIRTHDAY', label: 'Birthday' },
    { value: 'WORK', label: 'Work' },
    { value: 'PERSONAL', label: 'Personal' },
  ];
  readonly yearMonthIndex = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] as const;

  ngOnInit(): void {
    const t = this.todayIso();
    this.selectedDateIso = t;
    this.calendarYear = Number(t.slice(0, 4));
    this.calendarMonth = Number(t.slice(5, 7));
    this.journalTo = t;
    this.journalFrom = `${t.slice(0, 7)}-01`;
    this.repCalAnchorIso = t;
    this.journalApi.listTagDefinitions().subscribe({
      next: (rows) => {
        this.journalTagDefs = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load journal tags', e),
    });
    this.loadExerciseMonth();
    this.loadSelectedDay();
    this.loadManagementTasksReport();
    this.loadReportCalendar();
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
    }).subscribe({
      next: ({ monthly, cal }) => {
        this.monthlyReport = monthly;
        this.monthCal = cal;
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

  runJournalSearch(): void {
    if (!this.journalFrom || !this.journalTo) {
      return;
    }
    forkJoin({
      rows: this.journalApi.search(
        this.journalFrom,
        this.journalTo,
        this.journalQ.trim() || null,
        this.journalTagIds.length ? this.journalTagIds : null,
        null,
      ),
      sum: this.journalApi.summary(
        this.journalFrom,
        this.journalTo,
        this.journalQ.trim() || null,
        this.journalTagIds.length ? this.journalTagIds : null,
        null,
      ),
    }).subscribe({
      next: ({ rows, sum }) => {
        this.journalSearched = true;
        this.journalReportRows = [...rows].sort((a, b) => {
          const d = b.loggedOn.localeCompare(a.loggedOn);
          return d !== 0 ? d : (b.id ?? 0) - (a.id ?? 0);
        });
        this.journalSummary = sum;
      },
      error: (e) => this.err('Journal search failed', e),
    });
  }

  journalExcerpt(md: string | null | undefined): string {
    const s = (md ?? '').replace(/\s+/g, ' ').trim();
    if (s.length <= 180) {
      return s;
    }
    return `${s.slice(0, 180)}…`;
  }

  formatJournalTagNames(row: JournalEntryDto): string {
    const tags = row.tags ?? [];
    if (!tags.length) {
      return '—';
    }
    return tags
      .map((t) => t.name)
      .sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      .join(', ');
  }

  journalAttachmentCount(row: JournalEntryDto): number {
    const n = row.attachmentCount;
    if (n != null && Number.isFinite(Number(n))) {
      return Math.max(0, Math.floor(Number(n)));
    }
    return row.attachments?.length ?? 0;
  }

  openJournalBodyDialog(row: JournalEntryDto): void {
    const data: ReportJournalBodyDialogData = {
      title: `Journal — ${row.loggedOn}`,
      bodyMarkdown: row.bodyMarkdown ?? '',
      tagsLine: this.formatJournalTagNames(row),
    };
    this.dialog.open(ReportJournalBodyDialogComponent, {
      width: 'min(92vw, 44rem)',
      maxHeight: '90vh',
      data,
    });
  }

  openJournalAttachmentsDialog(row: JournalEntryDto): void {
    const n = this.journalAttachmentCount(row);
    if (n < 1) {
      return;
    }
    this.journalApi.getEntry(row.id).subscribe({
      next: (e) => {
        const atts = e.attachments ?? [];
        const d: ReportJournalAttachmentsDialogData = {
          title: `Attachments — ${e.loggedOn}`,
          attachments: atts,
        };
        this.dialog.open(ReportJournalAttachmentsDialogComponent, {
          width: 'min(92vw, 30rem)',
          data: d,
        });
      },
      error: (err) => this.err('Could not load journal entry', err),
    });
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
    this.loadReportCalendar();
  }

  repCalStep(delta: number): void {
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
    this.repCalAnchorIso = this.toIsoFromDate(dt);
    this.loadReportCalendar();
  }

  private toIsoFromDate(d: Date): string {
    const y = d.getFullYear();
    const mo = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${mo}-${day}`;
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
      return { from: this.toIsoFromDate(fromD), to: this.toIsoFromDate(toD) };
    }
    return { from: `${y}-01-01`, to: `${y}-12-31` };
  }

  loadReportCalendar(): void {
    const { from, to } = this.repCalQueryRange();
    this.reportCalApi.list(from, to, this.repCalType).subscribe({
      next: (rows) => {
        this.repCalEntries = [...rows].sort((a, b) => {
          const c = a.entryDate.localeCompare(b.entryDate);
          if (c !== 0) {
            return c;
          }
          return a.id - b.id;
        });
      },
      error: (e) => this.err('Could not load report calendar', e),
    });
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
      const hasEntry = withEntry.has(iso);
      flat.push({ type: 'day', iso, label: String(d), hasEntry, trackKey: `rc-${iso}` });
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
      defaultDate: this.repCalAnchorIso,
      defaultType: this.repCalType,
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
      next: () => {
        this.loadReportCalendar();
      },
      error: (e) => this.err('Could not delete', e),
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
}
