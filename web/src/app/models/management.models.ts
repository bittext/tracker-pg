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

/** Forward-geocode result (OpenStreetMap Nominatim via API). */
export interface TravelGeocodeResultDto {
  latitude: number;
  longitude: number;
  displayName: string;
  country: string;
  region: string;
  locality: string;
}

/** Management → Account: server-backed vault row (one per item). */
export interface ManagementAccountDto {
  id: number;
  itemName: string;
  folder: string;
  username: string;
  password: string;
  authenticatorKey: string;
  website: string;
  notes: string;
  createdAt: string;
  updatedAt: string;
}

export interface ManagementAccountWriteBody {
  itemName: string;
  folder?: string;
  username?: string;
  password?: string;
  authenticatorKey?: string;
  website?: string;
  notes?: string;
}

export interface ManagementAccountImportResultDto {
  submitted: number;
  inserted: number;
  skippedDuplicates: number;
}

/** Management → Documents: member-scoped uploads (metadata + blob). */
export interface ManagementDocumentDto {
  id: number;
  displayName: string;
  docType: string;
  originalFilename: string | null;
  contentType: string | null;
  byteSize: number;
  downloadPath: string;
  createdAt: string;
  updatedAt: string;
}

export interface ManagementDocumentWriteBody {
  displayName: string;
  docType: string;
}
