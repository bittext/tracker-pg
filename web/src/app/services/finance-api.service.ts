import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  FinanceAlertEvaluationDto,
  FinanceAlertEventDto,
  FinanceCrawlSnapshotDto,
  FinanceNotificationSettingsDto,
  FinanceNotificationSettingsRequestDto,
  FinanceNotificationTestResultDto,
  FinanceStockAlertDto,
  FinanceStockAlertRequestDto,
  FinanceTax1040ReturnDto,
  MarketOverviewDto,
  RobinhoodAccountStatusDto,
  RobinhoodCsvImportResultDto,
  RobinhoodNotebookBundleDto,
  RobinhoodNotebookConfigDto,
  RobinhoodNotebookId,
  RobinhoodNotebookRenderDto,
  RobinhoodPerformanceReportDto,
  RobinhoodStocksSummaryDto,
  RobinhoodTransactionsDto,
  StockNewsDto,
  Surge52WeekHighsDto,
  BreakoutCandidatesDto,
  RobinhoodCsvSavedImportDto,
  RobinhoodCsvUploadStatusDto,
  RobinhoodAgenticStatusDto,
  RobinhoodAgenticPositionsDto,
  RobinhoodAgenticSyncResultDto,
  RobinhoodAgenticSettingsDto,
  RobinhoodAgenticOrderDto,
  RobinhoodAgenticOrdersDto,
  BankingImportResultDto,
  BankingInstitutionDto,
  BankingInstitutionTypeDto,
  BankingLedgerDto,
  BankingLedgerRange,
  BankingPlaidExchangeResponseDto,
  BankingPlaidLinkTokenResponseDto,
  BankingPlaidStatusDto,
  BankingPlaidSyncRequestDto,
  BankingPlaidSyncResponseDto,
  BankingImportFileDto,
} from '../models/finance.models';

export type FinancePeriod = 'all' | 'year' | 'month';

@Injectable({ providedIn: 'root' })
export class FinanceApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/finance/robinhood`;
  private readonly tax1040Root = `${environment.apiBaseUrl}/api/finance/tax/1040`;
  private readonly adminNotificationsRoot = `${environment.apiBaseUrl}/api/admin/finance/notifications`;
  private readonly bankingRoot = `${environment.apiBaseUrl}/api/finance/banking`;
  private readonly bankingPlaidRoot = `${environment.apiBaseUrl}/api/finance/banking/plaid`;

  /**
   * Rows from configured Robinhood table with optional period filter (year / year+month query params).
   * @param period all = no date filter; year = calendar year; month = year + month
   */
  robinhoodTransactions(period: FinancePeriod, year: number, month: number, symbol?: string | null) {
    let params = new HttpParams();
    if (period === 'year' || period === 'month') {
      params = params.set('year', String(year));
    }
    if (period === 'month') {
      params = params.set('month', String(month));
    }
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    return this.http.get<RobinhoodTransactionsDto>(`${this.root}/transactions`, { params });
  }

  /** Distinct values from the configured stock column (e.g. instrument), server-capped. */
  robinhoodStockSymbols() {
    return this.http.get<string[]>(`${this.root}/symbols`);
  }

  /** Imported Robinhood row count and activity date range for the signed-in user. */
  robinhoodAccountStatus() {
    return this.http.get<RobinhoodAccountStatusDto>(`${this.root}/account-status`);
  }

  /** Robinhood Agentic MCP connection status (Phase 1). */
  robinhoodAgenticStatus() {
    return this.http.get<RobinhoodAgenticStatusDto>(`${this.root}/agentic/status`);
  }

  robinhoodAgenticSaveTokens(accessToken: string, refreshToken?: string | null) {
    return this.http.post<RobinhoodAgenticStatusDto>(`${this.root}/agentic/tokens`, {
      accessToken,
      refreshToken: refreshToken ?? '',
    });
  }

  robinhoodAgenticSync() {
    return this.http.post<RobinhoodAgenticSyncResultDto>(`${this.root}/agentic/sync`, {});
  }

  robinhoodAgenticPositions() {
    return this.http.get<RobinhoodAgenticPositionsDto>(`${this.root}/agentic/positions`);
  }

  robinhoodAgenticDisconnect() {
    return this.http.delete<void>(`${this.root}/agentic/connection`);
  }

  robinhoodAgenticSettings() {
    return this.http.get<RobinhoodAgenticSettingsDto>(`${this.root}/agentic/settings`);
  }

  robinhoodAgenticSaveSettings(body: {
    requireApproval?: boolean;
    maxOrderNotional?: number | null;
    allowedSymbols?: string;
  }) {
    return this.http.put<RobinhoodAgenticSettingsDto>(`${this.root}/agentic/settings`, body);
  }

  robinhoodAgenticOrders() {
    return this.http.get<RobinhoodAgenticOrdersDto>(`${this.root}/agentic/orders`);
  }

  robinhoodAgenticReviewOrder(body: {
    symbol: string;
    side: string;
    type: string;
    quantity?: number | null;
    amount?: number | null;
    limitPrice?: number | null;
    timeInForce?: string;
  }) {
    return this.http.post<RobinhoodAgenticOrderDto>(`${this.root}/agentic/orders/review`, body);
  }

  robinhoodAgenticApproveOrder(orderId: number) {
    return this.http.post<RobinhoodAgenticOrderDto>(`${this.root}/agentic/orders/${orderId}/approve`, {});
  }

  robinhoodAgenticRejectOrder(orderId: number) {
    return this.http.post<RobinhoodAgenticOrderDto>(`${this.root}/agentic/orders/${orderId}/reject`, {});
  }

  /** FIFO realized P&amp;L report (daily P&amp;L, equity curve, win/loss) for a calendar year. */
  robinhoodPerformanceReport(year: number, symbol?: string | null) {
    let params = new HttpParams().set('year', String(year));
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    return this.http.get<RobinhoodPerformanceReportDto>(`${this.root}/performance-report`, { params });
  }

  /** JupyterLab + notebook sidecar hints for Reports → Robinhood. */
  robinhoodNotebookConfig() {
    return this.http.get<RobinhoodNotebookConfigDto>(`${this.root}/notebook-config`);
  }

  /** JSON export for Jupyter / pandas workflows. */
  robinhoodNotebookBundle(year: number, symbol?: string | null) {
    let params = new HttpParams().set('year', String(year));
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    return this.http.get<RobinhoodNotebookBundleDto>(`${this.root}/notebook-bundle`, { params });
  }

  /** Server-rendered notebook HTML (requires robinhood-notebook-svc). */
  robinhoodNotebookRender(year: number, symbol?: string | null, notebook: RobinhoodNotebookId = 'performance') {
    let params = new HttpParams().set('year', String(year)).set('notebook', notebook);
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    return this.http.get<RobinhoodNotebookRenderDto>(`${this.root}/notebook-render`, { params });
  }

  /** Upload Robinhood CSV directly (dry-run unless apply=true). */
  robinhoodImportCsv(file: File, apply: boolean) {
    const form = new FormData();
    form.append('file', file, file.name);
    const params = new HttpParams().set('apply', apply ? 'true' : 'false');
    return this.http.post<RobinhoodCsvImportResultDto>(`${this.root}/import-csv`, form, { params });
  }

  /** Buy/sell rollups by instrument + contract for a calendar year; optional instrument filter. */
  robinhoodStocksSummary(year: number, symbol?: string | null) {
    let params = new HttpParams().set('year', String(year));
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    return this.http.get<RobinhoodStocksSummaryDto>(`${this.root}/stocks-summary`, { params });
  }

  /** Latest trusted-source stock news for an instrument and/or company name. */
  robinhoodStockNews(symbol?: string | null, companyName?: string | null, limit?: number) {
    let params = new HttpParams();
    const sym = symbol?.trim();
    if (sym) {
      params = params.set('symbol', sym);
    }
    const company = companyName?.trim();
    if (company) {
      params = params.set('companyName', company);
    }
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<StockNewsDto>(`${this.root}/news`, { params });
  }

  /** Finance “Crawler” tab: topic news + watchlist + index marks. */
  financeCrawlSnapshot() {
    return this.http.get<FinanceCrawlSnapshotDto>(`${this.root}/crawl-snapshot`);
  }

  /** Finance → Market: global + US futures, composites, headline indexes (day / MTD / YTD). */
  financeMarketOverview() {
    return this.http.get<MarketOverviewDto>(`${this.root}/market-overview`);
  }

  /** Recent 52w-high names that have climbed through the year. */
  robinhoodRising52WeekHighs(limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<Surge52WeekHighsDto>(`${this.root}/rising-52w-highs`, { params });
  }

  /** NASDAQ-listed mid-cap (~$2B–$10B USD) names from merged Yahoo screeners + quote filters. */
  robinhoodNasdaqMidCapScreener(limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<Surge52WeekHighsDto>(`${this.root}/nasdaq-mid-cap-screener`, { params });
  }

  /** Trading “Break outs”: heuristic resistance + volume + volatility contraction scan. */
  robinhoodBreakoutCandidates(limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<BreakoutCandidatesDto>(`${this.root}/breakout-candidates`, { params });
  }

  /** Whether robinhood CSV import directory is configured (for UI uploads). */
  robinhoodCsvImportUploadStatus() {
    return this.http.get<RobinhoodCsvUploadStatusDto>(`${this.root}/csv-import-upload-status`);
  }

  /** Save CSV to configured import folder then run import pipeline (same as import-csv). */
  robinhoodCsvSaveToImportDirectory(file: File, apply: boolean) {
    const form = new FormData();
    form.append('file', file, file.name);
    const params = new HttpParams().set('apply', apply ? 'true' : 'false');
    return this.http.post<RobinhoodCsvSavedImportDto>(`${this.root}/import-csv-save-to-directory`, form, { params });
  }

  financeAlerts() {
    return this.http.get<FinanceStockAlertDto[]>(`${this.root}/alerts`);
  }

  createFinanceAlert(body: FinanceStockAlertRequestDto) {
    return this.http.post<FinanceStockAlertDto>(`${this.root}/alerts`, body);
  }

  updateFinanceAlert(id: number, body: FinanceStockAlertRequestDto) {
    return this.http.put<FinanceStockAlertDto>(`${this.root}/alerts/${id}`, body);
  }

  deleteFinanceAlert(id: number) {
    return this.http.delete<void>(`${this.root}/alerts/${id}`);
  }

  evaluateFinanceAlerts() {
    return this.http.post<FinanceAlertEvaluationDto>(`${this.root}/alerts/evaluate`, {});
  }

  financeAlertEvents(limit = 50) {
    const params = new HttpParams().set('limit', String(Math.floor(limit)));
    return this.http.get<FinanceAlertEventDto[]>(`${this.root}/alerts/events`, { params });
  }

  financeNotificationSettings() {
    return this.http.get<FinanceNotificationSettingsDto>(this.adminNotificationsRoot);
  }

  saveFinanceNotificationSettings(body: FinanceNotificationSettingsRequestDto) {
    return this.http.put<FinanceNotificationSettingsDto>(this.adminNotificationsRoot, body);
  }

  testFinanceNotificationSettings(email: boolean, sms: boolean) {
    return this.http.post<FinanceNotificationTestResultDto>(`${this.adminNotificationsRoot}/test`, { email, sms });
  }

  /** Form 1040 PDFs per tax year; list omits full extract unless fullText=true. */
  listTax1040Returns(fullText = false) {
    const params = new HttpParams().set('fullText', fullText ? 'true' : 'false');
    return this.http.get<FinanceTax1040ReturnDto[]>(`${this.tax1040Root}/returns`, { params });
  }

  getTax1040Return(id: number, fullText = true) {
    const params = new HttpParams().set('fullText', fullText ? 'true' : 'false');
    return this.http.get<FinanceTax1040ReturnDto>(`${this.tax1040Root}/returns/${id}`, { params });
  }

  uploadTax1040Return(taxYear: number, file: File) {
    const form = new FormData();
    form.append('taxYear', String(Math.floor(taxYear)));
    form.append('file', file, file.name);
    return this.http.post<FinanceTax1040ReturnDto>(`${this.tax1040Root}/returns`, form);
  }

  deleteTax1040Return(id: number) {
    return this.http.delete<void>(`${this.tax1040Root}/returns/${id}`);
  }

  downloadTax1040Blob(downloadPath: string) {
    const u = downloadPath.startsWith('http') ? downloadPath : `${environment.apiBaseUrl}${downloadPath}`;
    return this.http.get(u, { responseType: 'blob' });
  }

  listBankingInstitutions() {
    return this.http.get<BankingInstitutionDto[]>(`${this.bankingRoot}/institutions`);
  }

  listBankingInstitutionTypes() {
    return this.http.get<BankingInstitutionTypeDto[]>(`${this.bankingRoot}/institution-types`);
  }

  createBankingInstitutionType(body: { name: string; sortOrder?: number | null }) {
    return this.http.post<BankingInstitutionTypeDto>(`${this.bankingRoot}/institution-types`, body);
  }

  deleteBankingInstitutionType(id: number) {
    return this.http.delete<void>(`${this.bankingRoot}/institution-types/${id}`);
  }

  createBankingInstitution(name: string, institutionTypeId?: number | null) {
    return this.http.post<BankingInstitutionDto>(`${this.bankingRoot}/institutions`, {
      name,
      institutionTypeId: institutionTypeId ?? null,
    });
  }

  updateBankingInstitution(id: number, body: { name: string; institutionTypeId?: number | null }) {
    return this.http.put<BankingInstitutionDto>(`${this.bankingRoot}/institutions/${id}`, {
      name: body.name,
      institutionTypeId: body.institutionTypeId ?? null,
    });
  }

  /** Imports in a calendar date span (Admin); same owner scope as ledger. */
  bankingImportFiles(
    fromIsoDate: string,
    toIsoDate: string,
    institutionId?: number | null,
    institutionTypeId?: number | null,
  ) {
    let params = new HttpParams().set('from', fromIsoDate).set('to', toIsoDate);
    if (institutionId != null && institutionId > 0) {
      params = params.set('institutionId', String(institutionId));
    }
    if (institutionTypeId != null && institutionTypeId > 0) {
      params = params.set('institutionTypeId', String(institutionTypeId));
    }
    return this.http.get<BankingImportFileDto[]>(`${this.bankingRoot}/import-files`, { params });
  }

  bankingLedger(
    range: BankingLedgerRange,
    year: number,
    month?: number | null,
    quarter?: number | null,
    institutionId?: number | null,
    institutionTypeId?: number | null,
  ) {
    let params = new HttpParams().set('range', range).set('year', String(year));
    if (month != null) {
      params = params.set('month', String(month));
    }
    if (quarter != null) {
      params = params.set('quarter', String(quarter));
    }
    if (institutionId != null && institutionId > 0) {
      params = params.set('institutionId', String(institutionId));
    }
    if (institutionTypeId != null && institutionTypeId > 0) {
      params = params.set('institutionTypeId', String(institutionTypeId));
    }
    return this.http.get<BankingLedgerDto>(`${this.bankingRoot}/ledger`, { params });
  }

  importBankingFile(institutionId: number, file: File) {
    const form = new FormData();
    form.append('institutionId', String(institutionId));
    form.append('file', file, file.name);
    return this.http.post<BankingImportResultDto>(`${this.bankingRoot}/imports`, form);
  }

  downloadBankingFile(id: number) {
    return this.http.get(`${this.bankingRoot}/files/${id}/download`, {
      responseType: 'blob',
      observe: 'response',
    });
  }

  deleteBankingImportFile(id: number) {
    return this.http.delete<void>(`${this.bankingRoot}/files/${id}`);
  }

  bankingPlaidStatus(institutionId: number) {
    return this.http.get<BankingPlaidStatusDto>(`${this.bankingPlaidRoot}/status`, {
      params: { institutionId: String(institutionId) },
    });
  }

  bankingPlaidLinkToken(institutionId: number) {
    return this.http.post<BankingPlaidLinkTokenResponseDto>(`${this.bankingPlaidRoot}/link-token`, null, {
      params: { institutionId: String(institutionId) },
    });
  }

  bankingPlaidExchange(institutionId: number, publicToken: string) {
    return this.http.post<BankingPlaidExchangeResponseDto>(`${this.bankingPlaidRoot}/exchange`, {
      institutionId,
      publicToken,
    });
  }

  bankingPlaidSync(body: BankingPlaidSyncRequestDto) {
    return this.http.post<BankingPlaidSyncResponseDto>(`${this.bankingPlaidRoot}/sync`, body);
  }

  /** Removes stored Plaid Item credentials for this institution (does not delete imported ledger rows). */
  bankingPlaidUnlink(institutionId: number) {
    return this.http.delete<void>(`${this.bankingPlaidRoot}/link`, {
      params: { institutionId: String(institutionId) },
    });
  }
}
