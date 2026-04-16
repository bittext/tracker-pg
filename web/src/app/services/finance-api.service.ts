import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
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

  /** Recent 52w-high names that have climbed through the year. */
  robinhoodRising52WeekHighs(limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<Surge52WeekHighsDto>(`${this.root}/rising-52w-highs`, { params });
  }
}
