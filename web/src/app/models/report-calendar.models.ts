/** Calendar type code stored on entries (provisioned in Admin → Management → Calendar). */
export type ReportCalendarType = string;

/** UI filter: all calendar types vs one. */
export type ReportCalendarTypeFilter = 'ALL' | ReportCalendarType;

export const DEFAULT_REPORT_CALENDAR_TYPE_OPTIONS: ReadonlyArray<{ value: ReportCalendarType; label: string }> = [
  { value: 'BIRTHDAY', label: 'Birthday' },
  { value: 'WORK', label: 'Work' },
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'TRADES', label: 'Trades' },
  { value: 'BANKING', label: 'Banking' },
  { value: 'PAYMENTS', label: 'Payments' },
  { value: 'OPINION_STRATEGIES', label: 'Opinion & strategies' },
];

/** @deprecated Use types loaded from API; kept as fallback. */
export const REPORT_CALENDAR_TYPE_OPTIONS = DEFAULT_REPORT_CALENDAR_TYPE_OPTIONS;

export function reportCalendarTypeOptionsFromProvisioned(
  types: ReadonlyArray<{ code: string; label: string; sortIndex?: number }>,
): ReadonlyArray<{ value: ReportCalendarType; label: string }> {
  if (!types.length) {
    return DEFAULT_REPORT_CALENDAR_TYPE_OPTIONS;
  }
  return [...types]
    .sort((a, b) => {
      const si = (a.sortIndex ?? 0) - (b.sortIndex ?? 0);
      if (si !== 0) {
        return si;
      }
      return (a.code || '').localeCompare(b.code || '', undefined, { sensitivity: 'base' });
    })
    .map((t) => ({ value: t.code, label: t.label }));
}

export function reportCalendarFilterOptions(
  types: ReadonlyArray<{ code: string; label: string; sortIndex?: number }>,
): ReadonlyArray<{ value: ReportCalendarTypeFilter; label: string }> {
  return [{ value: 'ALL', label: 'All types' }, ...reportCalendarTypeOptionsFromProvisioned(types)];
}

export function reportCalendarTypeLabel(
  t: ReportCalendarType,
  types?: ReadonlyArray<{ code: string; label: string }>,
): string {
  const fromApi = types?.find((x) => x.code === t);
  if (fromApi) {
    return fromApi.label;
  }
  const o = DEFAULT_REPORT_CALENDAR_TYPE_OPTIONS.find((x) => x.value === t);
  return o?.label ?? t;
}

export interface ReportCalendarAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface ReportCalendarEntryDto {
  id: number;
  entryDate: string;
  calendarType: ReportCalendarType;
  title: string | null;
  body: string | null;
  details: string | null;
  attachments: ReportCalendarAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ReportCalendarEntryWriteBody {
  entryDate: string;
  calendarType: ReportCalendarType;
  title?: string | null;
  body?: string | null;
  details?: string | null;
}
