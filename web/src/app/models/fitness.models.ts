export interface Exercise {
  id?: number;
  name: string;
  category?: string | null;
  notes?: string | null;
  createdAt?: string;
}

/** A simple log line for an exercise on a calendar day (free-form notes). */
export interface ExerciseDayLog {
  id?: number;
  exercise?: Exercise;
  performedOn: string;
  notes: string;
  /** Total duration in whole minutes (optional). */
  durationMinutes?: number | null;
}

export interface BodyWeightLog {
  id?: number;
  loggedOn: string;
  weightKg: number;
  /** Pounds as captured on save; older rows may be absent (derive from {@link weightKg}). */
  weightLb?: number | null;
  notes?: string | null;
}

/** One log row returned with GET /api/fitness/reports/daily */
export interface DailyExerciseLogLineDto {
  id?: number;
  exerciseId: number;
  exerciseName: string;
  notes: string;
  durationMinutes?: number | null;
}

/** GET /api/fitness/reports/daily */
export interface DailyExerciseReportDto {
  date: string;
  totalLogs: number;
  bodyWeightKg: number | null;
  exercises: ExerciseDayBreakdownDto[];
  /** Per-line logs for the selected day (includes notes). */
  logLines: DailyExerciseLogLineDto[];
}

export interface ExerciseDayBreakdownDto {
  exerciseId: number;
  exerciseName: string;
  logCount: number;
}

/** GET /api/fitness/reports/monthly */
export interface MonthlyExerciseReportDto {
  year: number;
  month: number;
  totalLogs: number;
  workoutDays: number;
  /** Distinct days with ≥1 exercise log row (from monthly report query over ExerciseDayLog). */
  exerciseLogActiveDays?: number;
  exercises: ExerciseMonthBreakdownDto[];
}

export interface ExerciseMonthBreakdownDto {
  exerciseId: number;
  exerciseName: string;
  logCount: number;
  daysTrained: number;
}

/** GET /api/fitness/reports/month-calendar */
export interface MonthActivityCalendarDto {
  year: number;
  month: number;
  daysWithStrengthTraining: string[];
  /** Distinct days with a body-weight entry (ISO yyyy-MM-dd). */
  daysWithWeightLogged: string[];
  /** Days with ≥1 exercise log (same as strength list); not weight-only days. */
  activeDays: string[];
  /** yyyy-MM-dd -> total exercise duration minutes for that day. */
  exerciseDurationMinutesByDay?: Record<string, number>;
  /** yyyy-MM-dd -> body weight in kg for that day. */
  bodyWeightKgByDay?: Record<string, number>;
}

/** GET /api/fitness/habit-streaks */
export interface FitnessHabitStreakDayDto {
  dayIndex: number;
  date: string;
  completed: boolean;
  today: boolean;
  future: boolean;
}

export interface FitnessHabitStreakHabitDto {
  kind: string;
  title: string;
  subtitle: string;
  completedCount: number;
  days: FitnessHabitStreakDayDto[];
}

export interface FitnessHabitStreakBoardDto {
  startDate: string;
  endDate: string;
  dayCount: number;
  today: string;
  habits: FitnessHabitStreakHabitDto[];
}
