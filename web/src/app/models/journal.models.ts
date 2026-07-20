export interface JournalTagDefDto {
  id: number;
  name: string;
  createdAt?: string;
}

export interface JournalAttachmentDto {
  id: number;
  originalFilename: string;
  contentType?: string | null;
  sizeBytes?: number | null;
  downloadPath: string;
}

export interface JournalEntryDto {
  id: number;
  ownerUserId: number;
  loggedOn: string;
  bodyMarkdown: string;
  tags: JournalTagDefDto[];
  /** Populated in search; use with attachments for full detail. */
  attachmentCount?: number;
  attachments: JournalAttachmentDto[];
  createdAt?: string;
  updatedAt?: string;
}

export interface JournalEntryWriteBody {
  loggedOn: string;
  bodyMarkdown: string;
  tagIds?: number[] | null;
}

export interface JournalCalendarDayDto {
  date: string;
  entryCount: number;
  level: number;
}

export interface JournalSummaryDto {
  totalCount: number;
  byMonth: { yearMonth: string; count: number }[];
  byDay: { date: string; count: number }[];
}

export type JournalCourseStatus = 'INTEND' | 'IN_PROGRESS' | 'COMPLETED';

export interface JournalCourseDto {
  id: number;
  title: string;
  provider: string | null;
  status: JournalCourseStatus;
  url: string | null;
  notesMarkdown: string;
  startedOn: string | null;
  completedOn: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface JournalCourseWriteBody {
  title: string;
  provider?: string | null;
  status: JournalCourseStatus;
  url?: string | null;
  notesMarkdown?: string | null;
  startedOn?: string | null;
  completedOn?: string | null;
}

export type JournalBookStatus = 'TO_READ' | 'READING' | 'FINISHED';

export interface JournalBookDto {
  id: number;
  title: string;
  author: string | null;
  status: JournalBookStatus;
  url: string | null;
  notesMarkdown: string;
  startedOn: string | null;
  finishedOn: string | null;
  rating: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface JournalBookWriteBody {
  title: string;
  author?: string | null;
  status: JournalBookStatus;
  url?: string | null;
  notesMarkdown?: string | null;
  startedOn?: string | null;
  finishedOn?: string | null;
  rating?: number | null;
}
