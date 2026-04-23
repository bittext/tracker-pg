export type ReportCalendarType = 'BIRTHDAY' | 'WORK' | 'PERSONAL';

export interface ReportCalendarEntryDto {
  id: number;
  entryDate: string;
  calendarType: ReportCalendarType;
  title: string | null;
  body: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReportCalendarEntryWriteBody {
  entryDate: string;
  calendarType: ReportCalendarType;
  title?: string | null;
  body?: string | null;
}
