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
