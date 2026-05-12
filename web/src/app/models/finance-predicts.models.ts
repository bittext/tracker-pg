export interface PredictsTickerDto {
  id: number;
  symbol: string;
  autoSeeded: boolean;
  sourcesEnabled: string[];
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PredictsTickerWriteBody {
  symbol: string;
  sourcesEnabled?: string[];
  note?: string | null;
}

export interface PredictsBucketPointDto {
  bucketStart: string;
  source: string;
  msgCount: number;
  uniqueAuthors: number;
  posCount: number;
  negCount: number;
  neuCount: number;
  engagementSum: number;
  sentimentAvg: number | null;
}

export interface PredictsTimeseriesDto {
  symbol: string;
  bucketSize: '5m' | '15m' | '1h' | '1d';
  source: string;
  from: string;
  to: string;
  points: PredictsBucketPointDto[];
}

export interface PredictsSourceSummaryDto {
  source: string;
  mentions24h: number;
  uniqueAuthors24h: number;
  posCount24h: number;
  negCount24h: number;
  neuCount24h: number;
  sentimentAvg24h: number;
  positivityPct: number;
  spikeZ: number;
  surgeZ: number;
}

export interface PredictsSymbolSummaryDto {
  symbol: string;
  latestMentionAt: string | null;
  mentions24h: number;
  uniqueAuthors24h: number;
  overallPositivityPct: number;
  overallSpikeZ: number;
  hotScore: number;
  sources: PredictsSourceSummaryDto[];
}

export interface PredictsLeaderboardEntryDto {
  rank: number;
  symbol: string;
  mentions24h: number;
  uniqueAuthors24h: number;
  positivityPct: number;
  spikeZ: number;
  hotScore: number;
}

export interface PredictsLeaderboardDto {
  type: 'hot' | 'positive' | 'surge' | string;
  generatedAt: string;
  entries: PredictsLeaderboardEntryDto[];
}

export interface PredictsMentionDto {
  id: number;
  symbol: string;
  source: string;
  bodyPreview: string;
  engagementScore: number;
  nativeSentiment: string | null;
  sentimentLabel: string | null;
  sentimentScore: number | null;
  postedAt: string;
  url: string | null;
}

export interface PredictsSourceHealthDto {
  source: string;
  enabled: boolean;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  lastErrorAt: string | null;
  lastErrorMessage: string | null;
  consecutiveFailures: number;
  mentionsIngested24h: number;
}
