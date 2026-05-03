/** Matches server `BalanceUrgency`. */
export type BalanceUrgency = 'LOW' | 'MEDIUM' | 'HIGH';

export interface ManagementTaskCategory {
  id?: number;
  name: string;
  description?: string | null;
  createdAt?: string;
}

export interface ManagementTaskType {
  id?: number;
  name: string;
  notes?: string | null;
  createdAt?: string;
}

export interface ManagementTaskDto {
  id: number;
  title: string;
  notes?: string | null;
  dueDate?: string | null;
  urgency: BalanceUrgency;
  completed: boolean;
  categoryId?: number | null;
  categoryName?: string | null;
  taskTypeId?: number | null;
  taskTypeName?: string | null;
  createdAt?: string;
}

export interface TaskMonthCalendarDto {
  year: number;
  month: number;
  /** ISO date → tasks due that day */
  tasksByDay: Record<string, ManagementTaskDto[]>;
}

export interface ManagementTaskWriteBody {
  title: string;
  notes?: string;
  dueDate?: string | null;
  urgency: BalanceUrgency;
  categoryId?: number | null;
  taskTypeId?: number | null;
  completed?: boolean;
}

export interface ManagementMonthNoteCalendarDto {
  year: number;
  months: { month: number; noteCount: number }[];
}

export interface ManagementMonthNoteAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface ManagementMonthNoteDto {
  id: number;
  ownerUserId: number;
  year: number;
  month: number;
  subject: string;
  body: string;
  attachments: ManagementMonthNoteAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ManagementMonthNoteWriteBody {
  year: number;
  month: number;
  subject: string;
  body: string;
}

/** Year-scoped detailed reports / reviews (Management → Write-up). */
export interface ManagementWriteupDto {
  id: number;
  ownerUserId: number;
  year: number;
  topic: string;
  highlight: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface ManagementWriteupWriteBody {
  year: number;
  topic: string;
  highlight?: string | null;
  body: string;
}
