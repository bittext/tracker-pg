export interface AdminCronJobRunnerDto {
  runnerKey: string;
  label: string;
  description: string;
  category: string;
}

export interface AdminCronJobDto {
  jobKey: string;
  displayName: string;
  description: string | null;
  category: string;
  scheduleType: 'CRON' | 'FIXED_DELAY';
  cronExpression: string | null;
  fixedDelayMs: number | null;
  initialDelayMs: number;
  zoneId: string;
  enabled: boolean;
  builtIn: boolean;
  runnerKey: string;
  runnerLabel: string;
  lastRunAt: string | null;
  lastRunStatus: string | null;
  lastRunMessage: string | null;
  nextRunAt: string | null;
  scheduleSummary: string;
  runnerAvailable: boolean;
}

export interface AdminCronJobUpsertRequest {
  displayName: string;
  description?: string | null;
  category?: string;
  scheduleType: 'CRON' | 'FIXED_DELAY';
  cronExpression?: string | null;
  fixedDelayMs?: number | null;
  initialDelayMs?: number | null;
  zoneId?: string | null;
  enabled?: boolean | null;
  runnerKey: string;
}

export interface AdminCronJobActionResultDto {
  ok: boolean;
  jobKey: string;
  message: string;
  ranAt: string;
}

export interface CronJobFormModel {
  displayName: string;
  description: string;
  category: string;
  scheduleType: 'CRON' | 'FIXED_DELAY';
  cronExpression: string;
  fixedDelayMinutes: number;
  initialDelaySeconds: number;
  zoneId: string;
  enabled: boolean;
  runnerKey: string;
}

export function emptyCronJobForm(): CronJobFormModel {
  return {
    displayName: '',
    description: '',
    category: '',
    scheduleType: 'FIXED_DELAY',
    cronExpression: '0 0 * * * *',
    fixedDelayMinutes: 60,
    initialDelaySeconds: 60,
    zoneId: 'UTC',
    enabled: true,
    runnerKey: '',
  };
}

export function formFromJob(job: AdminCronJobDto): CronJobFormModel {
  return {
    displayName: job.displayName,
    description: job.description ?? '',
    category: job.category,
    scheduleType: job.scheduleType,
    cronExpression: job.cronExpression ?? '0 0 * * * *',
    fixedDelayMinutes: job.fixedDelayMs ? Math.max(1, Math.round(job.fixedDelayMs / 60_000)) : 60,
    initialDelaySeconds: Math.round(job.initialDelayMs / 1000),
    zoneId: job.zoneId,
    enabled: job.enabled,
    runnerKey: job.runnerKey,
  };
}

export function formToUpsert(form: CronJobFormModel): AdminCronJobUpsertRequest {
  return {
    displayName: form.displayName.trim(),
    description: form.description.trim() || null,
    category: form.category.trim() || undefined,
    scheduleType: form.scheduleType,
    cronExpression: form.scheduleType === 'CRON' ? form.cronExpression.trim() : null,
    fixedDelayMs: form.scheduleType === 'FIXED_DELAY' ? Math.max(1, form.fixedDelayMinutes) * 60_000 : null,
    initialDelayMs: Math.max(0, form.initialDelaySeconds) * 1000,
    zoneId: form.zoneId.trim() || 'UTC',
    enabled: form.enabled,
    runnerKey: form.runnerKey,
  };
}
