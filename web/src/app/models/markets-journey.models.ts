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

export interface MarketsJourneyDto {
  id: number;
  title: string;
  milestoneAmount: number;
  sortOrder: number;
  entryCount: number;
  latestActual: number | null;
  progressPct: number | null;
  entries: MarketsJourneyEntryDto[];
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
