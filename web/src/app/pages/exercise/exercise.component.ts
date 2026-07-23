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
import { MatTableModule } from '@angular/material/table';
import { BodyWeightLog, Exercise, ExerciseDayLog, MonthActivityCalendarDto } from '../../models/fitness.models';
import { FitnessApiService } from '../../services/fitness-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

interface ExCalCell {
  type: 'pad' | 'day';
  dateIso?: string;
  label?: string;
  exercised?: boolean;
  trackKey: string;
}

@Component({
  selector: 'app-exercise',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  templateUrl: './exercise.component.html',
  styleUrl: './exercise.component.scss',
})
export class ExerciseComponent implements OnInit {
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  exercises: Exercise[] = [];
  selectedExerciseId: number | null = null;
  logDay = this.todayIsoDate();
  calendarYear = new Date().getFullYear();
  calendarMonth = new Date().getMonth() + 1;
  monthCal: MonthActivityCalendarDto | null = null;
  /** All exercises’ logs on {@link logDay} (right panel; not filtered by exercise dropdown). */
  dayLogs: ExerciseDayLog[] = [];
  /** All exercises in the window before {@link logDay}. */
  previousExerciseLogs: ExerciseDayLog[] = [];
  logColumns = ['exercise', 'duration', 'notes', 'actions'];
  previousLogColumns = ['exercise', 'performedOn', 'duration', 'notes', 'actions'];
  newLog: Pick<ExerciseDayLog, 'performedOn' | 'notes'> = {
    performedOn: this.todayIsoDate(),
    notes: '',
  };
  /** Optional duration for the next log (whole hours and minutes 0–59). */
  newLogHours: number | null = null;
  newLogMinutes: number | null = null;
  quickExerciseName = '';

  bodyWeightLogs: BodyWeightLog[] = [];
  /** {@code weightLb} column shows values from API / DB column {@code weight_lb}. */
  bwColumns = ['loggedOn', 'weightLb', 'notes', 'actions'];
  /** Form: pounds only; POSTed as {@link BodyWeightLog.weightLb} → column {@code weight_lb}. */
  newBw = { weightLb: 0 as number, notes: '' };

  private static readonly LB_PER_KG = 2.2046226218;

  /** Display kg from API as pounds. */
  kgToLb(kg: number | null | undefined): number {
    if (kg == null || Number.isNaN(kg)) {
      return 0;
    }
    return kg * ExerciseComponent.LB_PER_KG;
  }

  /** Convert pounds from the form to kg for the API. */
  private lbToKg(lb: number): number {
    return lb / ExerciseComponent.LB_PER_KG;
  }

  /** Prefer stored lb; otherwise derive from kg (legacy rows). */
  displayBodyWeightLb(row: BodyWeightLog): number {
    const lb = row.weightLb;
    if (lb != null && !Number.isNaN(lb)) {
      return lb;
    }
    return this.kgToLb(row.weightKg);
  }

  ngOnInit(): void {
    this.syncCalendarToLogDay();
    this.reloadExercises();
    this.reloadBodyWeight();
    this.loadMonthCalendar();
  }

  get calendarTitle(): string {
    return new Date(this.calendarYear, this.calendarMonth - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  get exercisedDayCount(): number {
    return this.monthCal?.activeDays?.length ?? this.monthCal?.daysWithStrengthTraining?.length ?? 0;
  }

  calendarRows(): ExCalCell[][] {
    const y = this.calendarYear;
    const m = this.calendarMonth;
    const exercised = new Set([
      ...(this.monthCal?.activeDays ?? []),
      ...(this.monthCal?.daysWithStrengthTraining ?? []),
    ]);
    const last = new Date(y, m, 0).getDate();
    const firstDow = new Date(y, m - 1, 1).getDay();
    const flat: ExCalCell[] = [];
    let padSeq = 0;
    for (let i = 0; i < firstDow; i++) {
      padSeq += 1;
      flat.push({ type: 'pad', trackKey: `pad-${padSeq}` });
    }
    for (let d = 1; d <= last; d++) {
      const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      flat.push({
        type: 'day',
        dateIso: iso,
        label: String(d),
        exercised: exercised.has(iso),
        trackKey: `d-${iso}`,
      });
    }
    let tail = 0;
    while (flat.length % 7 !== 0) {
      tail += 1;
      flat.push({ type: 'pad', trackKey: `pt-${tail}` });
    }
    const rows: ExCalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  isCalDaySelected(iso: string | undefined): boolean {
    return !!iso && iso === this.logDay;
  }

  isCalDayToday(iso: string | undefined): boolean {
    return !!iso && iso === this.todayIsoDate();
  }

  selectCalDay(cell: ExCalCell): void {
    if (cell.type !== 'day' || !cell.dateIso) {
      return;
    }
    this.setLogDay(cell.dateIso);
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
    this.loadMonthCalendar();
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
    this.loadMonthCalendar();
  }

  private loadMonthCalendar(): void {
    this.fitnessApi.monthActivityCalendar(this.calendarYear, this.calendarMonth).subscribe({
      next: (cal) => (this.monthCal = cal),
      error: (e) => this.err('Could not load month calendar', e),
    });
  }

  private syncCalendarToLogDay(): void {
    this.calendarYear = +this.logDay.slice(0, 4);
    this.calendarMonth = +this.logDay.slice(5, 7);
  }

  private setLogDay(iso: string): void {
    if (iso === this.logDay) {
      return;
    }
    this.logDay = iso;
    const y = +iso.slice(0, 4);
    const m = +iso.slice(5, 7);
    if (y !== this.calendarYear || m !== this.calendarMonth) {
      this.calendarYear = y;
      this.calendarMonth = m;
      this.loadMonthCalendar();
    }
    this.onLogDayChange();
  }

  private todayIsoDate(): string {
    const d = new Date();
    return this.toIsoDate(d);
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private addDays(isoDate: string, deltaDays: number): string {
    const d = new Date(`${isoDate}T12:00:00`);
    d.setDate(d.getDate() + deltaDays);
    return this.toIsoDate(d);
  }

  dayKey(loggedOn: string): string {
    return loggedOn.slice(0, 10);
  }

  /** Inclusive start of the right-panel history window (5 days ending on `logDay`). */
  get historyWindowStart(): string {
    return this.addDays(this.logDay, -(this.historyDayCount - 1));
  }

  readonly historyDayCount = 5;

  /** Weight entries on the selected calendar day (within the 5-day window). */
  get bwForSelectedDay(): BodyWeightLog[] {
    return this.bodyWeightLogs.filter((b) => this.dayKey(b.loggedOn) === this.logDay);
  }

  /** Weight entries on earlier days in the 5-day window only (most recent first). */
  get bwEarlier(): BodyWeightLog[] {
    const ws = this.historyWindowStart;
    return this.bodyWeightLogs
      .filter((b) => {
        const d = this.dayKey(b.loggedOn);
        return d >= ws && d < this.logDay;
      })
      .sort((a, b) => this.dayKey(b.loggedOn).localeCompare(this.dayKey(a.loggedOn)));
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  onLogDayChange(): void {
    this.newLog.performedOn = this.logDay;
    this.resetNewLogDuration();
    this.loadDayLogs();
    this.loadPreviousExerciseLogs();
  }

  private resetNewLogDuration(): void {
    this.newLogHours = null;
    this.newLogMinutes = null;
  }

  /** Formats stored total minutes for the table. */
  formatDuration(total: number | null | undefined): string {
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

  private durationMinutesFromInputs(): number | undefined {
    const h = Math.max(0, Math.floor(Number(this.newLogHours) || 0));
    const min = Math.max(0, Math.floor(Number(this.newLogMinutes) || 0));
    const total = h * 60 + min;
    return total > 0 ? total : undefined;
  }

  reloadExercises(): void {
    this.fitnessApi.listExercises().subscribe({
      next: (rows) => {
        this.exercises = rows;
        if (this.selectedExerciseId == null && rows.length) {
          this.selectedExerciseId = rows[0].id!;
        }
        this.loadDayLogs();
        this.loadPreviousExerciseLogs();
      },
      error: (e) => this.err('Could not load exercises', e),
    });
  }

  quickAddExercise(): void {
    const name = this.quickExerciseName.trim();
    if (!name) {
      return;
    }
    this.fitnessApi.createExercise({ name, category: '', notes: '' }).subscribe({
      next: () => {
        this.quickExerciseName = '';
        this.reloadExercises();
        this.snackBar.open('Exercise added', undefined, { duration: 3000 });
      },
      error: (e) => this.err('Could not add exercise', e),
    });
  }

  loadDayLogs(): void {
    this.fitnessApi.listDayLogsBetween(this.logDay, this.logDay).subscribe({
      next: (rows) => {
        this.dayLogs = [...rows].sort((a, b) => {
          const na = a.exercise?.name ?? '';
          const nb = b.exercise?.name ?? '';
          const byName = na.localeCompare(nb, undefined, { sensitivity: 'base' });
          if (byName !== 0) {
            return byName;
          }
          return (a.id ?? 0) - (b.id ?? 0);
        });
      },
      error: (e) => this.err('Could not load logs', e),
    });
    this.newLog.performedOn = this.logDay;
  }

  loadPreviousExerciseLogs(): void {
    const dayBefore = this.addDays(this.logDay, -1);
    const rangeFrom = this.historyWindowStart;
    if (dayBefore < rangeFrom) {
      this.previousExerciseLogs = [];
      return;
    }
    this.fitnessApi.listDayLogsBetween(rangeFrom, dayBefore).subscribe({
      next: (rows) => {
        this.previousExerciseLogs = [...rows].sort((a, b) => {
          const byDate = b.performedOn.localeCompare(a.performedOn);
          return byDate !== 0 ? byDate : (b.id ?? 0) - (a.id ?? 0);
        });
      },
      error: (e) => this.err('Could not load earlier exercise logs', e),
    });
  }

  addDayLog(): void {
    if (this.selectedExerciseId == null) {
      return;
    }
    const notes = this.newLog.notes.trim();
    if (!notes) {
      return;
    }
    const durationMinutes = this.durationMinutesFromInputs();
    this.fitnessApi
      .addDayLog(this.selectedExerciseId, {
        performedOn: this.logDay,
        notes,
        ...(durationMinutes != null ? { durationMinutes } : {}),
      })
      .subscribe({
        next: () => {
          this.newLog = { performedOn: this.logDay, notes: '' };
          this.resetNewLogDuration();
          this.loadDayLogs();
          this.loadPreviousExerciseLogs();
          this.loadMonthCalendar();
          this.snackBar.open('Log saved', undefined, { duration: 2500 });
        },
        error: (e) => this.err('Save log failed', e),
      });
  }

  deleteDayLog(id: number | undefined): void {
    if (id == null) {
      return;
    }
    this.fitnessApi.deleteDayLog(id).subscribe({
      next: () => {
        this.loadDayLogs();
        this.loadPreviousExerciseLogs();
        this.loadMonthCalendar();
        this.snackBar.open('Removed', undefined, { duration: 2000 });
      },
      error: (e) => this.err('Delete failed', e),
    });
  }

  reloadBodyWeight(): void {
    this.fitnessApi.listBodyWeight().subscribe({
      next: (rows) => (this.bodyWeightLogs = rows),
      error: (e) => this.err('Could not load weight history', e),
    });
  }

  addBodyWeight(): void {
    const w = this.newBw.weightLb;
    if (w == null) {
      this.snackBar.open('Enter a valid weight (lb)', undefined, { duration: 4000 });
      return;
    }
    const lbs = Number(w);
    if (!Number.isFinite(lbs)) {
      this.snackBar.open('Enter a valid weight (lb)', undefined, { duration: 4000 });
      return;
    }
    this.fitnessApi
      .addBodyWeight({
        loggedOn: this.logDay,
        weightKg: this.lbToKg(lbs),
        weightLb: lbs,
        notes: this.newBw.notes || undefined,
      })
      .subscribe({
        next: () => {
          this.newBw = { weightLb: 0, notes: '' };
          this.reloadBodyWeight();
          this.snackBar.open('Weight saved', undefined, { duration: 2500 });
        },
        error: (e) => this.err('Save weight failed', e),
      });
  }

  deleteBodyWeight(id: number | undefined): void {
    if (id == null) {
      return;
    }
    this.fitnessApi.deleteBodyWeight(id).subscribe({
      next: () => {
        this.reloadBodyWeight();
        this.snackBar.open('Deleted', undefined, { duration: 2000 });
      },
      error: (e) => this.err('Delete failed', e),
    });
  }
}
