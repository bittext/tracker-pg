import { ManagementTaskDto } from '../models/management.models';

/** Visual bucket for task rows and calendar cells (compare at local-date midnight). */
export type MgmtTaskDueVisual =
  | 'completed'
  | 'open_no_due'
  | 'open_due_future'
  | 'open_due_today'
  | 'overdue_1_7'
  | 'overdue_8_30'
  | 'overdue_31_plus';

/** Normalize API due date to yyyy-MM-dd or null. */
export function normalizeMgmtDueIso(d: string | null | undefined): string | null {
  if (d == null || String(d).trim() === '') {
    return null;
  }
  const s = String(d).trim();
  if (s.length >= 10) {
    return s.slice(0, 10);
  }
  return null;
}

/** Whole days between two calendar dates (from ≤ to). */
export function daysBetweenIsoDates(fromIso: string, toIso: string): number {
  const a = new Date(fromIso + 'T12:00:00');
  const b = new Date(toIso + 'T12:00:00');
  return Math.round((b.getTime() - a.getTime()) / 86400000);
}

/**
 * Classify an open task relative to `todayIso` (yyyy-MM-dd).
 * Completed tasks always return `completed`.
 */
export function mgmtTaskDueVisual(task: ManagementTaskDto, todayIso: string): MgmtTaskDueVisual {
  if (task.completed) {
    return 'completed';
  }
  const due = normalizeMgmtDueIso(task.dueDate);
  if (!due) {
    return 'open_no_due';
  }
  if (due > todayIso) {
    return 'open_due_future';
  }
  if (due === todayIso) {
    return 'open_due_today';
  }
  const days = daysBetweenIsoDates(due, todayIso);
  if (days <= 7) {
    return 'overdue_1_7';
  }
  if (days <= 30) {
    return 'overdue_8_30';
  }
  return 'overdue_31_plus';
}

/** CSS class suffix for table rows / chips (prefix with `mgmt-due-row--`). */
export function mgmtTaskDueRowClass(task: ManagementTaskDto, todayIso: string): string {
  const v = mgmtTaskDueVisual(task, todayIso);
  return `mgmt-due-row--${v}`;
}

/** For calendar day cells: `iso` is the day, tasks are those due that day. */
export function mgmtCalendarDayDueVisual(
  iso: string,
  tasksDueThatDay: ManagementTaskDto[],
  todayIso: string,
): MgmtTaskDueVisual | null {
  const open = tasksDueThatDay.filter((t) => !t.completed);
  if (open.length === 0) {
    return null;
  }
  if (iso > todayIso) {
    return 'open_due_future';
  }
  if (iso === todayIso) {
    return 'open_due_today';
  }
  const days = daysBetweenIsoDates(iso, todayIso);
  if (days <= 7) {
    return 'overdue_1_7';
  }
  if (days <= 30) {
    return 'overdue_8_30';
  }
  return 'overdue_31_plus';
}
