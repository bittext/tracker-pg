export type ReportCalendarType =
  | 'BIRTHDAY'
  | 'WORK'
  | 'PERSONAL'
  | 'TRADES'
  | 'BANKING'
  | 'PAYMENTS'
  | 'OPINION_STRATEGIES';

/** UI filter: all calendar types vs one. */
export type ReportCalendarTypeFilter = 'ALL' | ReportCalendarType;

export const REPORT_CALENDAR_TYPE_OPTIONS: ReadonlyArray<{ value: ReportCalendarType; label: string }> = [
  { value: 'BIRTHDAY', label: 'Birthday' },
  { value: 'WORK', label: 'Work' },
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'TRADES', label: 'Trades' },
  { value: 'BANKING', label: 'Banking' },
  { value: 'PAYMENTS', label: 'Payments' },
  { value: 'OPINION_STRATEGIES', label: 'Opinion & strategies' },
];

export const REPORT_CALENDAR_FILTER_OPTIONS: ReadonlyArray<{ value: ReportCalendarTypeFilter; label: string }> = [
  { value: 'ALL', label: 'All types' },
  ...REPORT_CALENDAR_TYPE_OPTIONS,
];

export function reportCalendarTypeLabel(t: ReportCalendarType): string {
  const o = REPORT_CALENDAR_TYPE_OPTIONS.find((x) => x.value === t);
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
  attachments: ReportCalendarAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ReportCalendarEntryWriteBody {
  entryDate: string;
  calendarType: ReportCalendarType;
  title?: string | null;
  body?: string | null;
}
