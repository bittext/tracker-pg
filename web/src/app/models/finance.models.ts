/** GET /api/finance/robinhood/transactions */
export interface RobinhoodTransactionsDto {
  rows: Record<string, unknown>[];
  returned: number;
  tableQueried: string;
  maxRowsCap: number;
  filterYear: number | null;
  filterMonth: number | null;
  filterLabel: string;
}

/** GET /api/finance/robinhood/stocks-summary */
export interface RobinhoodStocksSummaryDto {
  rows: RobinhoodStocksSummaryRow[];
  financialYear: number;
  filterInstrument: string | null;
  tableQueried: string;
  maxRowsCap: number;
  truncated: boolean;
  note: string;
}

export interface RobinhoodStocksSummaryRow {
  instrument: string;
  contract: string;
  financialYear: number;
  totalBuyQuantity: number;
  totalSellQuantity: number;
  totalBuyAmount: number;
  totalSellAmount: number;
  netAmount: number;
  firstBuyDate: string | null;
  lastBuyDate: string | null;
  firstSellDate: string | null;
  lastSellDate: string | null;
  buyLegCount: number;
  sellLegCount: number;
}

/** GET /api/finance/robinhood/news */
export interface StockNewsDto {
  requestedSymbol: string | null;
  requestedCompanyName: string | null;
  requestedLimit: number;
  returned: number;
  feed: string;
  fetchedAt: string;
  note: string;
  analysis: StockNewsAnalysisDto;
  items: StockNewsItemDto[];
}

export interface StockNewsAnalysisDto {
  overallSentiment: 'Positive' | 'Neutral' | 'Negative' | string;
  sentimentScore: number;
  projectedGrowthPercent: number;
  projectedGrowthLabel: 'Bullish' | 'Sideways' | 'Cautious' | string;
  stressSignals: StockNewsStressSignalsDto;
}

export interface StockNewsStressSignalsDto {
  mergerMentions: number;
  acquisitionMentions: number;
  dealMentions: number;
  permitMentions: number;
  sanctionMentions: number;
  emphasis: 'High' | 'Moderate' | 'Low' | string;
}

export interface StockNewsItemDto {
  title: string;
  source: string;
  publishedAt: string;
  url: string;
  summary: string;
}

/** GET /api/finance/robinhood/rising-52w-highs */
export interface Surge52WeekHighsDto {
  source: string;
  fetchedAt: string;
  returned: number;
  note: string;
  rows: Surge52WeekRowDto[];
}

export interface Surge52WeekRowDto {
  symbol: string;
  shortName: string;
  regularMarketPrice: number | null;
  regularMarketChangePercent: number | null;
  fiftyTwoWeekHigh: number | null;
  fiftyTwoWeekHighChangePercent: number | null;
  percentOf52WeekHigh: number;
  momentumScore: number;
  pastYearTradingDays: number;
  daysNearRolling52WeekHigh: number;
  pctPastYearNearRolling52WeekHigh: number;
  repeatedStayAtTop: boolean;
  fiftyTwoWeekGainPercent: number | null;
  pastSixMonthsTradingDays: number;
  daysNearRolling52WeekHighSixMonths: number;
  pctSixMonthsNearRolling52WeekHigh: number;
  marketCap: number | null;
  fiftyTwoWeekLow: number | null;
  averageDailyVolume3Month: number | null;
  trailingPe: number | null;
  forwardPe: number | null;
  growthOutlookLabel: string;
  growthProspectsSummary: string;
  externalDetailUrl: string;
}
