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
export interface ManagementWriteupAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface ManagementWriteupDto {
  id: number;
  ownerUserId: number;
  year: number;
  topic: string;
  highlight: string;
  body: string;
  attachments: ManagementWriteupAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ManagementWriteupWriteBody {
  year: number;
  topic: string;
  highlight?: string | null;
  body: string;
}

/** Management → Work tab: file attached to a work log entry (same storage as month notes). */
export interface ManagementWorkLogAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

/** Management → Work tab: day-scoped work log (Markdown body). */
export interface ManagementWorkLogEntryDto {
  id: number;
  ownerUserId: number;
  entryDate: string;
  loggedAt: string;
  subject: string;
  body: string;
  createdAt: string;
  updatedAt: string;
  attachments: ManagementWorkLogAttachmentDto[];
}

export interface ManagementWorkLogEntryWriteBody {
  entryDate: string;
  subject: string;
  body: string;
}

export interface ManagementWorkLogCalendarDto {
  year: number;
  days: { date: string; count: number }[];
}

/** Management → Travel tab */
export type TravelTripStatus = 'PLANNING' | 'ACTIVE' | 'COMPLETED';
export type TravelPlaceStatus = 'PLANNED' | 'VISITED';

export interface TravelTripSummaryDto {
  id: number;
  title: string;
  startDate: string;
  endDate: string | null;
  status: TravelTripStatus;
  colorHex: string | null;
  placeCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface TravelPlacePhotoDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

export interface TravelPlaceDto {
  id: number;
  tripId: number;
  tripTitle: string;
  name: string;
  latitude: number;
  longitude: number;
  address: string | null;
  placeStatus: TravelPlaceStatus;
  visitDate: string | null;
  notes: string;
  sortOrder: number;
  photos: TravelPlacePhotoDto[];
  createdAt: string;
  updatedAt: string;
}

export interface TravelTripDetailDto {
  id: number;
  ownerUserId: number;
  title: string;
  summary: string;
  startDate: string;
  endDate: string | null;
  status: TravelTripStatus;
  colorHex: string | null;
  places: TravelPlaceDto[];
  createdAt: string;
  updatedAt: string;
}

export interface TravelTripWriteBody {
  title: string;
  summary?: string;
  startDate: string;
  endDate?: string | null;
  status: TravelTripStatus;
  colorHex?: string | null;
}

export interface TravelPlaceWriteBody {
  name: string;
  latitude: number;
  longitude: number;
  address?: string | null;
  placeStatus: TravelPlaceStatus;
  visitDate?: string | null;
  notes?: string;
  sortOrder: number;
}

export interface TravelPlaceMapDto {
  id: number;
  tripId: number;
  tripTitle: string;
  tripColorHex: string | null;
  name: string;
  latitude: number;
  longitude: number;
  placeStatus: TravelPlaceStatus;
  visitDate: string | null;
}
