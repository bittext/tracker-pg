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

/** Types for Management → Now roadmap cards (Admin → Management → Now). */
export interface ManagementNowCardType {
  id?: number;
  slug: string;
  label: string;
  badge: string;
  colorHex: string;
  sortIndex: number;
  createdAt?: string;
}

export interface ManagementNowCardTypeWriteBody {
  slug: string;
  label: string;
  badge: string;
  colorHex: string;
  sortIndex?: number | null;
}

/** Types for Management → Calendar (Admin → Management → Calendar). */
export interface ManagementCalendarType {
  id?: number;
  code: string;
  label: string;
  sortIndex: number;
  createdAt?: string;
}

export interface ManagementCalendarTypeWriteBody {
  code: string;
  label: string;
  sortIndex?: number | null;
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
  topicGroup: string;
  topicGroupSort: number;
  topicGroupRank: number;
  highlight: string;
  body: string;
  attachments: ManagementWriteupAttachmentDto[];
  createdAt: string;
  updatedAt: string;
}

export interface ManagementWriteupWriteBody {
  year: number;
  topic: string;
  topicGroup?: string | null;
  topicGroupSort?: number | null;
  highlight?: string | null;
  body: string;
}

export interface ManagementWriteupPlacementItem {
  id: number;
  topicGroup: string | null;
  topicGroupSort: number;
}

export interface ManagementWriteupGroupOrderRequest {
  year: number;
  groupLabels: string[];
}

/** Life → Work: file attached to a work log entry (same storage as month notes). */
export interface ManagementWorkLogAttachmentDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  downloadPath: string;
}

/** Life → Work: day-scoped work log (Markdown body). */
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

/** Management → Recordings: Just Press Record library (local path + cached transcript/summary). */
export interface ManagementRecordingDayDto {
  day: string;
  recordingCount: number;
}

export interface ManagementRecordingItemDto {
  path: string;
  displayName: string;
  recordedDay: string | null;
  fileSizeBytes: number;
  hasTranscript: boolean;
  hasSummary: boolean;
  processingStatus: 'IDLE' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | null;
  processingError: string | null;
}

export interface ManagementRecordingUploadResultDto {
  recordings: ManagementRecordingItemDto[];
  imageCount: number;
}

export interface ManagementRecordingListDto {
  enabled: boolean;
  storageMode: string;
  note: string | null;
  days: ManagementRecordingDayDto[];
  recordings: ManagementRecordingItemDto[];
}

export interface ManagementRecordingDetailDto {
  path: string;
  displayName: string;
  recordedDay: string | null;
  fileSizeBytes: number;
  transcript: string | null;
  transcriptSource: string | null;
  transcribedAt: string | null;
  summary: string | null;
  summarizedAt: string | null;
  segments: ManagementRecordingTranscriptSegmentDto[] | null;
  images: ManagementRecordingImageDto[] | null;
  processingStatus: 'IDLE' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | null;
  processingError: string | null;
}

export interface ManagementRecordingImageDto {
  id: number;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number;
  createdAt: string | null;
}

export interface ManagementRecordingTranscriptSegmentDto {
  speaker: string | null;
  text: string;
  startSeconds: number | null;
  endSeconds: number | null;
}

export interface ManagementRecordingReprocessDto {
  clearedCount: number;
}

export type ManagementDueSide = 'PAYABLE' | 'RECEIVABLE';

export interface ManagementDueOccurrenceDto {
  itemId: number;
  occurrenceId: number | null;
  side: ManagementDueSide;
  counterparty: string;
  recurring: boolean;
  dayOfMonth: number | null;
  oneOffDate: string | null;
  occurrenceDate: string;
  amountOverride: number | null;
  estimatedAmount: number | null;
  displayAmount: number | null;
  amountSource: 'settled' | 'override' | 'history' | 'none' | string;
  notes: string;
  settled: boolean;
  settledAmount: number | null;
}

export type ManagementDueSuggestionKind = 'MONTHLY' | 'BIG_DEBIT' | string;

export interface ManagementDueSuggestionDto {
  side: ManagementDueSide;
  counterparty: string;
  typicalDay: number;
  estimatedAmount: number | null;
  sampleCount: number;
  kind?: ManagementDueSuggestionKind;
  detail?: string;
}

export interface ManagementDueDayDto {
  date: string;
  payableCount: number;
  receivableCount: number;
  payableTotal: number;
  receivableTotal: number;
  items: ManagementDueOccurrenceDto[];
}

export interface ManagementDueMonthDto {
  year: number;
  month: number;
  paidTotal: number;
  receivedTotal: number;
  netTotal: number;
  yearPaidTotal: number;
  yearReceivedTotal: number;
  yearNetTotal: number;
  days: ManagementDueDayDto[];
  suggestions: ManagementDueSuggestionDto[];
}

export interface ManagementDueItemWriteBody {
  side: ManagementDueSide;
  counterparty: string;
  recurring: boolean;
  dayOfMonth?: number | null;
  oneOffDate?: string | null;
  amountOverride?: number | null;
  notes?: string | null;
  startYear?: number | null;
  startMonth?: number | null;
}

export interface ManagementDueSettleBody {
  year: number;
  month: number;
  settled: boolean;
  settledAmount?: number | null;
}

export interface ManagementDueReportPeriodDto {
  paid: number;
  received: number;
  net: number;
  openPayable: number;
  openReceivable: number;
  openPayableCount: number;
  openReceivableCount: number;
  settledCount: number;
  firstSettledOn: string | null;
  biggestOutName: string | null;
  biggestOutAmount: number | null;
  biggestInName: string | null;
  biggestInAmount: number | null;
}

export interface ManagementDueReportMonthBarDto {
  year: number;
  month: number;
  paid: number;
  received: number;
  net: number;
  openPayable: number;
  openReceivable: number;
  settledCount: number;
  openCount: number;
}

export interface ManagementDueReportDayDto {
  date: string;
  paid: number;
  received: number;
  net: number;
  itemCount: number;
}

export interface ManagementDueReportRowDto {
  itemId: number;
  occurrenceId: number | null;
  year: number;
  month: number;
  date: string;
  side: ManagementDueSide;
  counterparty: string;
  recurring: boolean;
  notes: string;
  settled: boolean;
  amount: number | null;
  amountSource: string;
}

export interface ManagementDueReportWhoDto {
  counterparty: string;
  side: ManagementDueSide;
  paid: number;
  received: number;
  openAmount: number;
  count: number;
  lastDate: string | null;
}

export interface ManagementDueReportDto {
  year: number;
  month: number;
  lifetime: ManagementDueReportPeriodDto;
  yearTotals: ManagementDueReportPeriodDto;
  monthTotals: ManagementDueReportPeriodDto;
  months: ManagementDueReportMonthBarDto[];
  days: ManagementDueReportDayDto[];
  rows: ManagementDueReportRowDto[];
  who: ManagementDueReportWhoDto[];
}

export type ManagementSubscriptionStatus = 'ACTIVE' | 'TRIAL' | 'CANCELLED' | 'EXPIRED';
export type ManagementSubscriptionBillingCycle = 'WEEKLY' | 'MONTHLY' | 'ANNUAL' | 'OTHER';

/** Life → Management → Subscriptions. */
export interface ManagementSubscriptionDto {
  id: number;
  name: string;
  vendor: string;
  category: string;
  plan: string;
  billingCycle: ManagementSubscriptionBillingCycle;
  amount: number | null;
  currency: string;
  enrolledOn: string | null;
  renewsOn: string | null;
  trialEndsOn: string | null;
  cancelledOn: string | null;
  status: ManagementSubscriptionStatus;
  autoRenew: boolean;
  website: string;
  accountEmail: string;
  notes: string;
  nextRenewalOn: string | null;
  daysUntilRenewal: number | null;
  refundWindowOpen: boolean;
  refundWindowEndsOn: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ManagementSubscriptionWriteBody {
  name: string;
  vendor?: string;
  category?: string;
  plan?: string;
  billingCycle?: ManagementSubscriptionBillingCycle;
  amount?: number | null;
  currency?: string;
  enrolledOn?: string | null;
  renewsOn?: string | null;
  trialEndsOn?: string | null;
  cancelledOn?: string | null;
  status?: ManagementSubscriptionStatus;
  autoRenew?: boolean;
  website?: string;
  accountEmail?: string;
  notes?: string;
}

export type ManagementAutoPaymentStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';
export type ManagementAutoPaymentFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'ANNUAL' | 'OTHER';
export type ManagementAutoPaymentMethod = 'ACH' | 'CARD' | 'BILL_PAY' | 'OTHER';

/** Life → Management → Auto Payments. */
export interface ManagementAutoPaymentDto {
  id: number;
  name: string;
  payee: string;
  category: string;
  paymentMethod: ManagementAutoPaymentMethod;
  frequency: ManagementAutoPaymentFrequency;
  amount: number | null;
  currency: string;
  startedOn: string | null;
  nextPaymentOn: string | null;
  dayOfMonth: number | null;
  endedOn: string | null;
  status: ManagementAutoPaymentStatus;
  fundingAccount: string;
  confirmationRef: string;
  website: string;
  notes: string;
  nextDebitOn: string | null;
  daysUntilDebit: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ManagementAutoPaymentWriteBody {
  name: string;
  payee?: string;
  category?: string;
  paymentMethod?: ManagementAutoPaymentMethod;
  frequency?: ManagementAutoPaymentFrequency;
  amount?: number | null;
  currency?: string;
  startedOn?: string | null;
  nextPaymentOn?: string | null;
  dayOfMonth?: number | null;
  endedOn?: string | null;
  status?: ManagementAutoPaymentStatus;
  fundingAccount?: string;
  confirmationRef?: string;
  website?: string;
  notes?: string;
}
