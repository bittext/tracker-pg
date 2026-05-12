export interface AdminPredictsStocktwitsConfig {
  enabled: boolean;
  baseUrl: string;
  maxMessagesPerSymbol: number;
  pollIntervalSeconds: number;
}

export interface AdminPredictsRedditConfig {
  enabled: boolean;
  userAgent: string;
  baseUrl: string;
  subreddits: string[];
  postsPerSubreddit: number;
  pollIntervalSeconds: number;
  credentialsConfigured: boolean;
}

export interface AdminPredictsXConfig {
  enabled: boolean;
  baseUrl: string;
  credentialsConfigured: boolean;
}

export interface AdminPredictsFinbertConfig {
  enabled: boolean;
  baseUrl: string;
  maxBatchSize: number;
  timeoutMs: number;
}

export interface AdminPredictsConfigDto {
  enabled: boolean;
  trackedTickerQuotaPerUser: number;
  baselineWindowDays: number;
  retentionMentionsDays: number;
  stocktwits: AdminPredictsStocktwitsConfig;
  reddit: AdminPredictsRedditConfig;
  x: AdminPredictsXConfig;
  finbert: AdminPredictsFinbertConfig;
}

export interface AdminPredictsPerSourceStat {
  source: string;
  mentionsTotal: number;
  mentions24h: number;
  uniqueSymbols24h: number;
  lastMentionAt: string | null;
}

export interface AdminPredictsStatsDto {
  generatedAt: string;
  mentionsTotal: number;
  mentions24h: number;
  uniqueSymbols: number;
  uniqueAuthors24h: number;
  bucketsTotal: number;
  baselinesTotal: number;
  trackedTickersTotal: number;
  trackedTickersAutoSeeded: number;
  perSource: AdminPredictsPerSourceStat[];
}

export interface AdminPredictsActionResultDto {
  action: string;
  ok: boolean;
  message: string;
  count: number;
  ranAt: string;
}

/**
 * Result of GET /api/admin/finance/predicts/diag/stocktwits — captures the raw shape of a
 * one-shot call against the StockTwits public stream so admins can distinguish unindexed
 * symbols from IP-level blocks at the egress.
 */
export interface AdminPredictsStocktwitsProbeDto {
  symbol: string;
  url: string | null;
  userAgent: string;
  status: number;
  elapsedMs: number;
  bodyPreview: string | null;
  messageCount: number | null;
  transportError: boolean;
  errorMessage: string | null;
}
