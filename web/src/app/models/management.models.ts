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

/** Daily journal entry (Management → Day One, Reports → Management). */
export interface ManagementDayOneTagDefDto {
  id: number;
  name: string;
  createdAt?: string;
}

export interface ManagementDayOneAttachmentDto {
  id: number;
  originalFilename: string;
  contentType?: string | null;
  sizeBytes?: number | null;
  downloadPath: string;
}

/** Daily journal entry (Management → Day One, Reports → Management). */
export interface ManagementDayOneLogDto {
  id: number;
  ownerUserId?: number;
  loggedOn: string;
  entryText: string;
  locationText?: string | null;
  weatherText?: string | null;
  tags?: ManagementDayOneTagDefDto[];
  attachments?: ManagementDayOneAttachmentDto[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ManagementDayOneWriteBody {
  loggedOn: string;
  entryText: string;
  locationText?: string | null;
  weatherText?: string | null;
  tagIds?: number[] | null;
}
