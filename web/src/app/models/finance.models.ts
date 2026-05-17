/** GET /api/finance/robinhood/csv-import-upload-status */
export interface RobinhoodCsvUploadStatusDto {
  configured: boolean;
  importDirectory: string;
}

/** POST /api/finance/robinhood/import-csv-save-to-directory */
export interface RobinhoodCsvSavedImportDto {
  importDirectory: string;
  savedAbsolutePath: string;
  importResult: RobinhoodCsvImportResultDto;
}

export interface RobinhoodCsvImportResultDto {
  apply: boolean;
  fileName: string;
  csvRowCount: number;
  parsedRows: number;
  insertedRows: number;
  duplicateRowsSkipped: number;
  skippedRows: number;
  errorCount: number;
  errors: string[];
  detectedHeaders: string[];
  detectedInstruments: string[];
  previewRows: Record<string, string>[];
  tableTarget: string;
  note: string;
}

/** GET /api/finance/robinhood/account-status */
export interface RobinhoodAccountStatusDto {
  tableQueried: string;
  transactionRowCount: number;
  earliestActivity: string | null;
  latestActivity: string | null;
  csvImportDirectoryConfigured: boolean;
  csvImportDirectory: string;
}

/** GET /api/finance/robinhood/notebook-config */
export interface RobinhoodNotebookConfigDto {
  jupyterLabConfigured: boolean;
  jupyterLabUrl: string;
  notebookServiceConfigured: boolean;
  notebookServiceNote: string;
}

/** GET /api/finance/robinhood/notebook-bundle */
export interface RobinhoodNotebookBundleDto {
  year: number;
  filterInstrument: string | null;
  exportedAt: string;
  transactionRowCount: number;
  transactionsTruncated: boolean;
  transactions: Record<string, unknown>[];
  performanceReport: RobinhoodPerformanceReportDto;
  closedTrades: RobinhoodClosedTradeDto[];
  usageNote: string;
}

export type RobinhoodNotebookId = 'performance' | 'risk';

/** GET /api/finance/robinhood/notebook-render */
export interface RobinhoodNotebookRenderDto {
  year: number;
  html: string;
  source: string;
  note: string;
}

/** GET /api/finance/robinhood/performance-report */
export interface RobinhoodPerformanceReportDto {
  financialYear: number;
  filterInstrument: string | null;
  tableQueried: string;
  rowsAnalyzed: number;
  truncated: boolean;
  note: string;
  summary: RobinhoodPerformanceSummaryDto;
  dailyPnL: RobinhoodDailyPnLPointDto[];
  monthlyPnL: RobinhoodMonthlyPnLPointDto[];
  equityCurve: RobinhoodEquityCurvePointDto[];
  closedTrades: RobinhoodClosedTradeDto[];
  portfolio: RobinhoodPortfolioOverviewDto;
  unrealized: RobinhoodUnrealizedSectionDto;
  insights: RobinhoodPerformanceInsightsDto;
  tax: RobinhoodPerformanceTaxDto;
}

export interface RobinhoodPortfolioOverviewDto {
  asOfDate: string;
  source: string;
  portfolioValue: number;
  cash: number;
  todayPnL: number;
  todayPnLPercent: number | null;
  ytdTotalPnL: number;
  todayRealizedPnL: number;
  ytdRealizedPnL: number;
  openUnrealizedPnL: number;
  positions: RobinhoodPortfolioPositionDto[];
  note: string;
}

export interface RobinhoodPortfolioPositionDto {
  instrument: string;
  name: string;
  contract: string | null;
  assetClass: string;
  quantity: number;
  avgPrice: number | null;
  marketPrice: number | null;
  marketValue: number | null;
  openPnL: number | null;
  dayOpenPnL: number | null;
  dayOpenPnLPercent: number | null;
}

export interface RobinhoodUnrealizedSectionDto {
  asOfDate: string;
  totalCostBasis: number;
  totalMarketValue: number | null;
  totalUnrealizedPnL: number | null;
  openLotCount: number;
  quotedLotCount: number;
  truncated: boolean;
  note: string;
  openPositions: RobinhoodOpenPositionDto[];
}

export interface RobinhoodOpenPositionDto {
  instrument: string;
  contract: string;
  strategy: string;
  openedDate: string;
  holdDaysAsOf: number;
  quantity: number;
  avgPrice: number | null;
  costBasis: number;
  marketPrice: number | null;
  marketValue: number | null;
  unrealizedPnL: number | null;
  quoteAvailable: boolean;
}

export interface RobinhoodPerformanceInsightsDto {
  bestPerformingStocks: RobinhoodInstrumentPerformanceDto[];
  worstTrades: RobinhoodClosedTradeDto[];
  averageHoldDays: number;
  medianHoldDays: number;
  tradingFrequency: RobinhoodTradingFrequencyDto;
  strategyPerformance: RobinhoodStrategyPerformanceDto[];
}

export interface RobinhoodInstrumentPerformanceDto {
  instrument: string;
  totalRealizedPnL: number;
  closedLots: number;
  winCount: number;
  lossCount: number;
}

export interface RobinhoodClosedTradeDto {
  instrument: string;
  contract: string;
  strategy: string;
  buyDate: string;
  sellDate: string;
  holdDays: number;
  quantity: number;
  realizedPnL: number;
}

export interface RobinhoodTradingFrequencyDto {
  totalClosedLots: number;
  tradingDays: number;
  averageClosesPerWeek: number;
  averageClosesPerMonth: number;
  busiestMonthLabel: string;
  busiestMonthCloses: number;
}

export interface RobinhoodStrategyPerformanceDto {
  strategy: string;
  totalRealizedPnL: number;
  closedLots: number;
  winRate: number;
}

export interface RobinhoodPerformanceTaxDto {
  quarterlyGains: RobinhoodQuarterlyGainDto[];
  yearRealizedGain: number;
  estimatedTaxRate: number;
  estimatedTaxOwed: number;
  disclaimer: string;
}

export interface RobinhoodQuarterlyGainDto {
  quarter: number;
  quarterLabel: string;
  realizedGain: number;
  estimatedTax: number;
}

export interface RobinhoodPerformanceSummaryDto {
  totalRealizedPnL: number;
  winCount: number;
  lossCount: number;
  breakevenCount: number;
  winRate: number;
  tradingDays: number;
  bestDay: string | null;
  bestDayPnL: number;
  worstDay: string | null;
  worstDayPnL: number;
}

export interface RobinhoodDailyPnLPointDto {
  date: string;
  realizedPnL: number;
  closedLots: number;
}

export interface RobinhoodMonthlyPnLPointDto {
  yearMonth: string;
  monthLabel: string;
  realizedPnL: number;
}

export interface RobinhoodEquityCurvePointDto {
  date: string;
  cumulativePnL: number;
}

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

/** GET /api/finance/robinhood/breakout-candidates */
export interface BreakoutCandidatesDto {
  source: string;
  fetchedAt: string;
  returned: number;
  note: string;
  rows: BreakoutCandidateRowDto[];
}

export interface BreakoutCandidateRowDto {
  symbol: string;
  shortName: string;
  regularMarketPrice: number | null;
  regularMarketChangePercent: number | null;
  percentOf52WeekHigh: number | null;
  breakoutScore: number;
  patternLabel: string;
  rationale: string;
  pctOfRecentResistance: number | null;
  volumeRatioVs20d: number | null;
  atrCompressionRatio: number | null;
  pctVsSma50: number | null;
  externalDetailUrl: string;
}

/** GET /api/finance/robinhood/crawl-snapshot */
export interface FinanceCrawlSnapshotDto {
  fetchedAt: string;
  sourceNote: string;
  crawlHeadlineLimit: number;
  generalNews: StockNewsDto;
  financialNews: StockNewsDto;
  majorIndexes: IndexSnapshotDto[];
  watchlist: CrawlerWatchItemDto[];
  /** Present when API includes swing screeners (newer server). */
  swingStocks?: SwingStocksSectionDto;
}

export interface SwingStocksSectionDto {
  source: string;
  note: string;
  fetchedAt: string;
  swingRowsRequested: number;
  rows: SwingStockDetailDto[];
}

export interface YahooExtendedQuoteDto {
  symbol: string;
  shortName: string;
  longName: string;
  regularMarketPrice: number | null;
  regularMarketChangePercent: number | null;
  regularMarketVolume: number | null;
  averageDailyVolume3Month: number | null;
  marketCap: number | null;
  fiftyTwoWeekHigh: number | null;
  fiftyTwoWeekLow: number | null;
  trailingPE: number | null;
  sector: string;
  industry: string;
}

export interface SectorPeerMoveDto {
  symbol: string;
  shortName: string;
  regularMarketChangePercent: number | null;
  regularMarketPrice: number | null;
  sector: string;
}

export interface SwingStockDetailDto {
  quote: YahooExtendedQuoteDto;
  news: StockNewsDto;
  performanceReport: string;
  kpiNarrative: string;
  sectorPeers: SectorPeerMoveDto[];
  warnings: string[];
  /** Heuristic chip — not analyst consensus (newer API). */
  nearTermOutlookTilt?: string;
  /** Explains the rule blend and empirical caveats (newer API). */
  nearTermOutlookNarrative?: string;
}

export type FinanceStockAlertTriggerType = 'PRICE_AT_OR_ABOVE' | 'SESSION_CHANGE_PERCENT_AT_OR_ABOVE';
export type FinanceStockAlertRepeatMode = 'ONCE' | 'REPEAT';
export type FinanceAlertDeliveryChannel = 'EMAIL' | 'SMS' | 'SYSTEM';
export type FinanceAlertDeliveryStatus = 'SENT' | 'SKIPPED' | 'FAILED';

export interface FinanceStockAlertDto {
  id: number;
  symbol: string;
  triggerType: FinanceStockAlertTriggerType;
  thresholdValue: number;
  repeatMode: FinanceStockAlertRepeatMode;
  cooldownMinutes: number;
  enabled: boolean;
  lastCheckedAt: string | null;
  lastTriggeredAt: string | null;
  lastRegularMarketPrice: number | null;
  lastRegularMarketChangePercent: number | null;
  fireCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface FinanceStockAlertRequestDto {
  symbol: string;
  triggerType: FinanceStockAlertTriggerType;
  thresholdValue: number;
  repeatMode: FinanceStockAlertRepeatMode;
  cooldownMinutes: number;
  enabled: boolean;
}

export interface FinanceAlertEventDto {
  id: number;
  alertId: number | null;
  symbol: string | null;
  triggerType: FinanceStockAlertTriggerType | null;
  thresholdValue: number | null;
  observedPrice: number | null;
  observedChangePercent: number | null;
  channel: FinanceAlertDeliveryChannel;
  status: FinanceAlertDeliveryStatus;
  message: string | null;
  providerResponse: string | null;
  createdAt: string;
}

export interface FinanceAlertEvaluationDto {
  evaluatedAt: string;
  checkedAlerts: number;
  triggeredAlerts: number;
  events: FinanceAlertEventDto[];
}

export interface FinanceNotificationSettingsDto {
  id: number | null;
  emailAddress: string;
  mobileE164: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  emailProviderConfigured: boolean;
  smsProviderConfigured: boolean;
  updatedAt: string | null;
}

export interface FinanceNotificationSettingsRequestDto {
  emailAddress: string;
  mobileE164: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
}

export interface FinanceNotificationTestResultDto {
  events: FinanceAlertEventDto[];
}

export interface IndexSnapshotDto {
  symbol: string;
  shortName: string;
  price: number | null;
  changePercent: number | null;
}

export interface YahooSimpleQuoteDto {
  symbol: string;
  shortName: string;
  regularMarketPrice: number | null;
  regularMarketChangePercent: number | null;
}

export interface CrawlerWatchItemDto {
  symbol: string;
  companyLabel: string;
  searchNote: string;
  news: StockNewsDto;
  quote: YahooSimpleQuoteDto | null;
  vsMarketSummary: string;
  analysisSummary: string;
  dataWarnings: string[];
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
  /** Yahoo quote exchange code when present (e.g. NMS). */
  exchangeId?: string | null;
  /** Yahoo full exchange name when present (e.g. NasdaqGS). */
  fullExchangeName?: string | null;
}

/** Parsed summary from Form 1040 PDF text (best effort; server may re-parse on each read). */
export interface Form1040ParsedSummary {
  likelyForm1040: boolean;
  parseNote?: string | null;
  parserVersion?: string | null;
  confidenceLabel?: 'HIGH' | 'MEDIUM' | 'LOW' | string | null;
  parsedAmountFieldCount?: number | null;
  parseWarnings?: string[] | null;
  fieldProvenance?: Record<string, Form1040FieldProvenance> | null;
  taxYearOnForm?: string | null;
  filingStatus?: string | null;
  wagesSalariesTips?: number | null;
  taxableInterest?: number | null;
  ordinaryDividends?: number | null;
  iraDistributionsTaxable?: number | null;
  pensionsTaxable?: number | null;
  socialSecurityTaxable?: number | null;
  totalIncome?: number | null;
  adjustedGrossIncome?: number | null;
  standardOrItemizedDeduction?: number | null;
  taxableIncome?: number | null;
  totalTax?: number | null;
  childAndOtherDependentsCredit?: number | null;
  totalTaxAfterCredits?: number | null;
  federalIncomeTaxWithheld?: number | null;
  estimatedTaxPayments?: number | null;
  totalPayments?: number | null;
  refund?: number | null;
  amountOwed?: number | null;
}

export interface Form1040FieldProvenance {
  sourcePass?: 'exact' | 'neighbor' | 'fallback' | string | null;
  matchedTokens?: string[] | null;
  confidence?: 'HIGH' | 'MEDIUM' | 'LOW' | string | null;
  note?: string | null;
}

export interface FinanceTax1040ReturnDto {
  id: number;
  taxYear: number;
  originalFilename: string;
  sizeBytes: number;
  downloadPath: string;
  summary: Form1040ParsedSummary;
  extractedTextPreview: string | null;
  extractedTextFull: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Banking imports (CSV, QFX, QIF, QBO, Excel, PDF) scoped to the logged-in user. */
export type BankingLedgerRange = 'MONTH' | 'QUARTER' | 'YEAR';

export interface BankingInstitutionTypeDto {
  id: number;
  name: string;
  sortOrder: number;
}

export interface BankingInstitutionDto {
  id: number;
  name: string;
  institutionTypeId?: number | null;
  institutionTypeName?: string | null;
}

export interface BankingImportFileDto {
  id: number;
  institutionId: number;
  institutionName: string;
  fileKind: string;
  originalFilename: string;
  contentType: string | null;
  sha256Hex: string;
  sizeBytes: number;
  skippedDuplicateFile: boolean;
  rowsInserted: number;
  rowsSkippedDuplicate: number;
  parseNote: string | null;
  /** Relative path under the configured banking import root (e.g. `plaid/1/2/uuid_file.qfx`). */
  storedRelativePath?: string | null;
  createdAt: string;
}

export interface BankingTransactionDto {
  id: number;
  institutionId: number;
  institutionName: string;
  institutionTypeId?: number | null;
  institutionTypeName?: string | null;
  importFileId: number;
  txnDate: string;
  amount: number;
  description: string;
  /** CREDIT = positive amount (inflow), DEBIT = negative (outflow), ZERO = zero. */
  debitCredit: string;
  /** Label derived from uploaded file type, e.g. CSV, QFX, Excel (XLSX). */
  sourceFormat: string;
}

export interface BankingLedgerDto {
  importDirectoryConfigured: boolean;
  importDirectory: string;
  rangeLabel: string;
  institutions: BankingInstitutionDto[];
  transactions: BankingTransactionDto[];
  importFiles: BankingImportFileDto[];
}

export interface BankingImportResultDto {
  success: boolean;
  skippedDuplicateFile: boolean;
  file: BankingImportFileDto | null;
  message: string;
}

export interface BankingPlaidLinkTokenResponseDto {
  linkToken: string;
  expiration: string;
}

export interface BankingPlaidStatusDto {
  plaidConfigured: boolean;
  linked: boolean;
  itemIdSuffix: string;
  /** Human-readable linked accounts from Plaid (after exchange). */
  connectionSummary?: string[] | null;
}

export interface BankingPlaidExchangeResponseDto {
  institutionId: number;
  institutionName: string;
  institutionRenamedFromPlaid: boolean;
  connectionSummary: string[];
  /** Banking institution ids created or updated for this Plaid Item (anchor first). */
  linkedInstitutionIds?: number[];
}

export interface BankingPlaidSyncRequestDto {
  institutionId: number;
  /** Inclusive ISO date (yyyy-MM-dd). */
  startDate: string;
  endDate: string;
  accountIds?: string[] | null;
}

export interface BankingPlaidSyncResponseDto {
  transactionsFetchedFromPlaid: number;
  ofxStatementRows: number;
  storedRelativePath: string;
  absoluteDirectoryUnderImportRoot: string;
  importResult: BankingImportResultDto;
}

/** GET /api/finance/robinhood/market-overview */
export interface MarketOverviewDto {
  source: string;
  fetchedAt: string;
  note: string;
  warnings: string[];
  summary: MarketOverviewSummaryDto;
  sections: MarketOverviewSectionDto[];
}

export interface MarketOverviewSummaryDto {
  narrative: string;
  vixLevel: number | null;
  vixChangePercentDay: number | null;
  sp500ChangePercentDay: number | null;
  nasdaqCompositeChangePercentDay: number | null;
  dowChangePercentDay: number | null;
  russell2000ChangePercentDay: number | null;
}

export interface MarketOverviewSectionDto {
  title: string;
  subtitle: string;
  rows: MarketOverviewInstrumentDto[];
}

export interface MarketOverviewInstrumentDto {
  symbol: string;
  displayName: string;
  regularMarketPrice: number | null;
  changePercentDay: number | null;
  changePercentMonthToDate: number | null;
  changePercentYearToDate: number | null;
  quoteUrl: string;
}
