export interface TrackerMonthNoteCalendarDto {
  year: number;
  months: { month: number; noteCount: number }[];
}

export interface TrackerMonthNoteAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface TrackerMonthNoteDto {
  id: number;
  ownerUserId: number;
  year: number;
  month: number;
  subject: string;
  body: string;
  attachments: TrackerMonthNoteAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface TrackerMonthNoteWriteBody {
  year: number;
  month: number;
  subject: string;
  body: string;
}
