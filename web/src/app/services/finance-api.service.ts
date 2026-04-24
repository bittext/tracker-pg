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
  RobinhoodStocksSummaryDto,
  RobinhoodTransactionsDto,
  StockNewsDto,
  Surge52WeekHighsDto,
} from '../models/finance.models';

export type FinancePeriod = 'all' | 'year' | 'month';

@Injectable({ providedIn: 'root' })
export class FinanceApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/finance/robinhood`;
  private readonly adminNotificationsRoot = `${environment.apiBaseUrl}/api/admin/finance/notifications`;

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

  /** Recent 52w-high names that have climbed through the year. */
  robinhoodRising52WeekHighs(limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<Surge52WeekHighsDto>(`${this.root}/rising-52w-highs`, { params });
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
}
