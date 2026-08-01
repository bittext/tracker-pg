export interface LifeMonthNoteCalendarDto {
  year: number;
  months: { month: number; noteCount: number }[];
}

export interface LifeMonthNoteAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface LifeMonthNoteDto {
  id: number;
  ownerUserId: number;
  year: number;
  month: number;
  subject: string;
  body: string;
  attachments: LifeMonthNoteAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface LifeMonthNoteWriteBody {
  year: number;
  month: number;
  subject: string;
  body: string;
}
