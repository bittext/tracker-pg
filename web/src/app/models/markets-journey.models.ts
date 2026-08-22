export type JourneyDirection = 'ABOVE' | 'ON' | 'BELOW' | 'UNKNOWN';

export interface MarketsJourneyEntryDto {
  id: number;
  periodDate: string;
  periodLabel: string;
  targetAmount: number | null;
  actualAmount: number | null;
  targetNote: string;
  actualNote: string;
  variance: number | null;
  direction: JourneyDirection;
  createdAt: string;
  updatedAt: string;
}

export interface MarketsJourneyLiveAccountDto {
  accountSuffix: string;
  label: string;
  totalAccountValue: number;
  dayChange?: number | null;
}

export interface MarketsJourneyLiveSeriesPointDto {
  date: string;
  total: number;
  dayChange: number | null;
  dayChangePct: number | null;
}

export interface MarketsJourneyLiveNetDto {
  asOfDate: string;
  total: number;
  remaining: number;
  progressPct: number;
  priorTotal?: number | null;
  dayChange?: number | null;
  dayChangePct?: number | null;
  accounts: MarketsJourneyLiveAccountDto[];
  series?: MarketsJourneyLiveSeriesPointDto[];
  note: string;
}

export interface MarketsJourneyDto {
  id: number;
  title: string;
  milestoneAmount: number;
  sortOrder: number;
  entryCount: number;
  latestActual: number | null;
  progressPct: number | null;
  entries: MarketsJourneyEntryDto[];
  liveNet?: MarketsJourneyLiveNetDto | null;
  createdAt: string;
  updatedAt: string;
}

export interface MarketsJourneyWriteRequest {
  title?: string | null;
  milestoneAmount?: number | null;
  sortOrder?: number | null;
}

export interface MarketsJourneyEntryWriteRequest {
  periodDate: string;
  periodLabel?: string | null;
  targetAmount?: number | null;
  actualAmount?: number | null;
  targetNote?: string | null;
  actualNote?: string | null;
}

/** GET /api/markets/roadmap/slap-points */
export interface MarketsRoadmapSlapSeriesPointDto {
  date: string;
  totalAccountValue: number;
}

export interface MarketsRoadmapSlapCrossingDto {
  threshold: number;
  crossedOn: string;
  totalOnDay: number;
  priorTotal: number | null;
}

export interface MarketsRoadmapSlapCashNoteDto {
  id: number;
  activityDate: string;
  direction: 'IN' | 'OUT' | string;
  amount: number;
  note: string | null;
}

export interface MarketsRoadmapSlapPointsDto {
  accountSuffix: string;
  accountLabel: string;
  stepAmount: number;
  latestTotal: number | null;
  latestDate: string | null;
  fromDate: string | null;
  toDate: string | null;
  series: MarketsRoadmapSlapSeriesPointDto[];
  guideLevels: number[];
  crossings: MarketsRoadmapSlapCrossingDto[];
  cashNotes: MarketsRoadmapSlapCashNoteDto[];
}
