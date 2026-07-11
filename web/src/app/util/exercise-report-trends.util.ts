import { BodyWeightLog, MonthActivityCalendarDto } from '../models/fitness.models';

export const LB_PER_KG = 2.2046226218;

export interface DurationBar {
  iso: string;
  day: number;
  minutes: number;
  /** 0–100 bar height; rest days stay at 0. */
  heightPct: number;
}

export interface WeightLinePoint {
  iso: string;
  weightLb: number;
  x: number;
  y: number;
}

export interface WeightTrendSummary {
  points: WeightLinePoint[];
  linePath: string;
  areaPath: string;
  startLb: number | null;
  endLb: number | null;
  deltaLb: number | null;
  weighInCount: number;
}

export interface StreakStats {
  currentStreak: number;
  bestStreakMonth: number;
}

export interface PersistenceStory {
  weightLine: string;
  consistencyLine: string;
  insight: string | null;
}

function kgToLb(kg: number): number {
  return kg * LB_PER_KG;
}

function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

function isoForDay(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function parseIsoParts(iso: string): { y: number; m: number; d: number } | null {
  if (!iso || iso.length < 10) {
    return null;
  }
  const y = Number(iso.slice(0, 4));
  const m = Number(iso.slice(5, 7));
  const d = Number(iso.slice(8, 10));
  if (!Number.isFinite(y) || !Number.isFinite(m) || !Number.isFinite(d)) {
    return null;
  }
  return { y, m, d };
}

function addDaysIso(iso: string, delta: number): string {
  const p = parseIsoParts(iso);
  if (!p) {
    return iso;
  }
  const dt = new Date(p.y, p.m - 1, p.d + delta);
  return isoForDay(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}

/** Resolve display lb from a body-weight log (prefer stored lb). */
export function bodyWeightLogToLb(log: BodyWeightLog): number | null {
  if (log.weightLb != null && Number.isFinite(Number(log.weightLb))) {
    return Number(log.weightLb);
  }
  if (log.weightKg != null && Number.isFinite(Number(log.weightKg))) {
    return kgToLb(Number(log.weightKg));
  }
  return null;
}

/**
 * Map of yyyy-MM-dd → lb for the given month.
 * Prefers `listBodyWeight` logs; falls back to calendar `bodyWeightKgByDay`.
 */
export function buildMonthWeightLbByDay(
  year: number,
  month: number,
  bodyWeightLogs: BodyWeightLog[],
  calendarKgByDay?: Record<string, number> | null,
): Map<string, number> {
  const prefix = `${year}-${String(month).padStart(2, '0')}-`;
  const map = new Map<string, number>();

  for (const log of bodyWeightLogs) {
    const on = log.loggedOn?.slice(0, 10);
    if (!on || !on.startsWith(prefix)) {
      continue;
    }
    const lb = bodyWeightLogToLb(log);
    if (lb == null) {
      continue;
    }
    // Keep the first log for a day if duplicates (API is newest-first; later overwrite = older).
    if (!map.has(on)) {
      map.set(on, lb);
    }
  }

  if (map.size === 0 && calendarKgByDay) {
    for (const [iso, kg] of Object.entries(calendarKgByDay)) {
      if (!iso.startsWith(prefix)) {
        continue;
      }
      const n = Number(kg);
      if (!Number.isFinite(n)) {
        continue;
      }
      map.set(iso, kgToLb(n));
    }
  }

  return map;
}

export function buildWeightTrendSummary(weightLbByDay: Map<string, number>): WeightTrendSummary {
  const entries = [...weightLbByDay.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const weighInCount = entries.length;
  if (!weighInCount) {
    return {
      points: [],
      linePath: '',
      areaPath: '',
      startLb: null,
      endLb: null,
      deltaLb: null,
      weighInCount: 0,
    };
  }

  const values = entries.map(([, lb]) => lb);
  const startLb = values[0];
  const endLb = values[values.length - 1];
  const deltaLb = endLb - startLb;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const idxSpan = Math.max(entries.length - 1, 1);

  const points: WeightLinePoint[] = entries.map(([iso, weightLb], i) => ({
    iso,
    weightLb,
    x: (i / idxSpan) * 100,
    y: 100 - ((weightLb - min) / span) * 88 - 6,
  }));

  const linePath =
    points.length === 1
      ? `M ${points[0].x} ${points[0].y} L ${points[0].x} ${points[0].y}`
      : points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ');

  const areaPath =
    points.length === 0
      ? ''
      : `${linePath} L ${points[points.length - 1].x.toFixed(2)} 100 L ${points[0].x.toFixed(2)} 100 Z`;

  return { points, linePath, areaPath, startLb, endLb, deltaLb, weighInCount };
}

export function buildDurationBars(
  year: number,
  month: number,
  durationByDay?: Record<string, number> | null,
): DurationBar[] {
  const last = daysInMonth(year, month);
  const map = durationByDay ?? {};
  const minutesList: number[] = [];
  for (let d = 1; d <= last; d++) {
    const iso = isoForDay(year, month, d);
    const raw = Number(map[iso] ?? 0);
    minutesList.push(Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 0);
  }
  const max = minutesList.reduce((m, v) => Math.max(m, v), 0);
  const scale = max > 0 ? max : 1;

  return minutesList.map((minutes, i) => {
    const day = i + 1;
    return {
      iso: isoForDay(year, month, day),
      day,
      minutes,
      heightPct: minutes > 0 ? Math.max(8, (minutes / scale) * 100) : 0,
    };
  });
}

export function sumDurationMinutes(durationByDay?: Record<string, number> | null): number {
  if (!durationByDay) {
    return 0;
  }
  return Object.values(durationByDay).reduce((sum, v) => {
    const n = Number(v);
    return Number.isFinite(n) && n > 0 ? sum + Math.floor(n) : sum;
  }, 0);
}

/** Longest consecutive run of ISO dates within the month's active set. */
export function bestStreakInMonth(activeIsos: string[], year: number, month: number): number {
  const set = new Set(activeIsos);
  const last = daysInMonth(year, month);
  let best = 0;
  let run = 0;
  for (let d = 1; d <= last; d++) {
    const iso = isoForDay(year, month, d);
    if (set.has(iso)) {
      run += 1;
      best = Math.max(best, run);
    } else {
      run = 0;
    }
  }
  return best;
}

/**
 * Current streak: consecutive exercise days ending at today (or last day of a past month).
 * Allows one grace day if the anchor day itself has no log (e.g. today not logged yet).
 */
export function currentExerciseStreak(
  activeIsos: string[],
  year: number,
  month: number,
  todayIso: string,
): number {
  const set = new Set(activeIsos);
  const first = isoForDay(year, month, 1);
  const last = isoForDay(year, month, daysInMonth(year, month));

  if (todayIso < first) {
    return 0;
  }

  let anchor = todayIso > last ? last : todayIso;
  if (!set.has(anchor)) {
    const prev = addDaysIso(anchor, -1);
    if (prev >= first && set.has(prev)) {
      anchor = prev;
    } else {
      return 0;
    }
  }

  let streak = 0;
  let cursor = anchor;
  while (cursor >= first && set.has(cursor)) {
    streak += 1;
    cursor = addDaysIso(cursor, -1);
  }
  return streak;
}

export function computeStreakStats(
  activeIsos: string[],
  year: number,
  month: number,
  todayIso: string,
): StreakStats {
  return {
    currentStreak: currentExerciseStreak(activeIsos, year, month, todayIso),
    bestStreakMonth: bestStreakInMonth(activeIsos, year, month),
  };
}

/** Weekdays (Mon–Fri) from month start through `throughIso` (inclusive), clipped to month. */
export function weekdayCountsThrough(
  year: number,
  month: number,
  throughIso: string,
  activeIsos: string[],
): { weekdaysSoFar: number; exerciseWeekdaysSoFar: number } {
  const set = new Set(activeIsos);
  const first = isoForDay(year, month, 1);
  const last = isoForDay(year, month, daysInMonth(year, month));
  const end = throughIso < first ? first : throughIso > last ? last : throughIso;

  let weekdaysSoFar = 0;
  let exerciseWeekdaysSoFar = 0;
  let cursor = first;
  while (cursor <= end) {
    const p = parseIsoParts(cursor)!;
    const dow = new Date(p.y, p.m - 1, p.d).getDay();
    if (dow !== 0 && dow !== 6) {
      weekdaysSoFar += 1;
      if (set.has(cursor)) {
        exerciseWeekdaysSoFar += 1;
      }
    }
    cursor = addDaysIso(cursor, 1);
  }
  return { weekdaysSoFar, exerciseWeekdaysSoFar };
}

/** Exercise days in the rolling last 7 calendar days ending at todayIso (clipped to month optional). */
export function exerciseDaysInLast7(activeIsos: string[], todayIso: string): number {
  const set = new Set(activeIsos);
  let count = 0;
  for (let i = 0; i < 7; i++) {
    const iso = addDaysIso(todayIso, -i);
    if (set.has(iso)) {
      count += 1;
    }
  }
  return count;
}

function formatLb(n: number): string {
  return Math.abs(n).toFixed(1);
}

function formatWeightDeltaLine(deltaLb: number | null, weighInCount: number): string {
  if (weighInCount < 1) {
    return 'No weigh-ins yet this month';
  }
  if (weighInCount === 1 || deltaLb == null) {
    return 'One weigh-in so far — log again to see the trend';
  }
  if (Math.abs(deltaLb) < 0.15) {
    return 'Holding steady since the first weigh-in this month';
  }
  if (deltaLb < 0) {
    return `Down ${formatLb(deltaLb)} lb since the first weigh-in this month`;
  }
  return `Up ${formatLb(deltaLb)} lb since the first weigh-in this month`;
}

export function buildPersistenceStory(input: {
  year: number;
  month: number;
  todayIso: string;
  exerciseDays: number;
  activeIsos: string[];
  weighInCount: number;
  weightDeltaLb: number | null;
  currentStreak: number;
  bestStreakMonth: number;
}): PersistenceStory {
  const { weekdaysSoFar, exerciseWeekdaysSoFar } = weekdayCountsThrough(
    input.year,
    input.month,
    input.todayIso,
    input.activeIsos,
  );
  const weightLine = formatWeightDeltaLine(input.weightDeltaLb, input.weighInCount);

  const consistencyParts: string[] = [];
  if (weekdaysSoFar > 0) {
    consistencyParts.push(`Exercised ${exerciseWeekdaysSoFar} of ${weekdaysSoFar} weekdays`);
  } else if (input.exerciseDays > 0) {
    consistencyParts.push(`Exercised ${input.exerciseDays} day${input.exerciseDays === 1 ? '' : 's'} this month`);
  } else {
    consistencyParts.push('No exercise days logged this month yet');
  }
  if (input.currentStreak > 0) {
    consistencyParts.push(`${input.currentStreak}-day streak`);
  }
  const consistencyLine = consistencyParts.join(' · ');

  const emptyMonth = input.exerciseDays === 0 && input.weighInCount === 0;
  const insight = emptyMonth
    ? null
    : buildPersistenceInsight({
        exerciseDays: input.exerciseDays,
        weighInCount: input.weighInCount,
        weightDeltaLb: input.weightDeltaLb,
        currentStreak: input.currentStreak,
        bestStreakMonth: input.bestStreakMonth,
        last7: exerciseDaysInLast7(input.activeIsos, input.todayIso),
        todayIso: input.todayIso,
        activeIsos: input.activeIsos,
      });

  return { weightLine, consistencyLine, insight };
}

export function buildPersistenceInsight(input: {
  exerciseDays: number;
  weighInCount: number;
  weightDeltaLb: number | null;
  currentStreak: number;
  bestStreakMonth: number;
  last7: number;
  todayIso: string;
  activeIsos: string[];
}): string | null {
  const set = new Set(input.activeIsos);
  let daysSinceExercise: number | null = null;
  for (let i = 0; i <= 14; i++) {
    if (set.has(addDaysIso(input.todayIso, -i))) {
      daysSinceExercise = i;
      break;
    }
  }

  // Strongest positive: weight down with exercise present
  if (
    input.weightDeltaLb != null &&
    input.weightDeltaLb < -0.15 &&
    input.exerciseDays > 0 &&
    input.weighInCount >= 2
  ) {
    return `Weight is down ${formatLb(input.weightDeltaLb)} lb this month while you kept showing up — keep that cadence.`;
  }

  if (input.currentStreak >= 3) {
    return `You're on a ${input.currentStreak}-day streak — consistency beats intensity.`;
  }

  if (input.last7 >= 3) {
    return `Solid week: ${input.last7} exercise days in the last 7.`;
  }

  if (input.bestStreakMonth >= 5 && input.currentStreak < input.bestStreakMonth) {
    return `Your best streak this month is ${input.bestStreakMonth} days — you already know the rhythm.`;
  }

  // Gentle nudge when recently idle after having logged this month
  if (input.exerciseDays > 0 && daysSinceExercise != null && daysSinceExercise >= 2 && daysSinceExercise <= 3) {
    return 'Two rest days is fine — log tomorrow to keep the streak alive.';
  }

  if (input.exerciseDays > 0 && input.currentStreak === 1) {
    return 'Nice start — another session soon keeps the habit sticky.';
  }

  if (input.weighInCount > 0 && input.exerciseDays === 0) {
    return 'Weight is logged — add an exercise session to pair the trend with effort.';
  }

  if (input.exerciseDays > 0) {
    return 'Keep showing up: small, regular sessions add up more than rare long ones.';
  }

  return null;
}

export function activeExerciseIsos(cal: MonthActivityCalendarDto | null): string[] {
  if (!cal) {
    return [];
  }
  const fromStrength = cal.daysWithStrengthTraining ?? [];
  const fromActive = cal.activeDays ?? [];
  return [...new Set([...fromStrength, ...fromActive])].sort();
}
