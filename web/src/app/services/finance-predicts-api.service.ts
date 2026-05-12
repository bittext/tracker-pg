import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  PredictsLeaderboardDto,
  PredictsMentionDto,
  PredictsSourceHealthDto,
  PredictsSymbolSummaryDto,
  PredictsTickerDto,
  PredictsTickerWriteBody,
  PredictsTimeseriesDto,
} from '../models/finance-predicts.models';

/**
 * Client for /api/finance/predicts/**. Ticker CRUD is per-user; everything else (summary, time series,
 * mentions, leaderboard, sources) is shared and returned for the requested symbol.
 */
@Injectable({ providedIn: 'root' })
export class FinancePredictsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/finance/predicts`;

  /** Tracked tickers for the current user (auto-seeded + manual). */
  listTickers() {
    return this.http.get<PredictsTickerDto[]>(`${this.root}/tickers`);
  }

  addTicker(body: PredictsTickerWriteBody) {
    return this.http.post<PredictsTickerDto>(`${this.root}/tickers`, body);
  }

  updateTicker(id: number, body: PredictsTickerWriteBody) {
    return this.http.put<PredictsTickerDto>(`${this.root}/tickers/${id}`, body);
  }

  deleteTicker(id: number) {
    return this.http.delete<void>(`${this.root}/tickers/${id}`);
  }

  /** Status cards for each source (last attempt/success/error, 24h ingested). */
  listSourceHealth() {
    return this.http.get<PredictsSourceHealthDto[]>(`${this.root}/sources`);
  }

  /** "hot" | "positive" | "surge" leaderboard. */
  leaderboard(type: 'hot' | 'positive' | 'surge', limit?: number) {
    let params = new HttpParams().set('type', type);
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<PredictsLeaderboardDto>(`${this.root}/leaderboard`, { params });
  }

  /** Latest 24h rollup for one symbol across enabled sources. */
  summary(symbol: string) {
    return this.http.get<PredictsSymbolSummaryDto>(`${this.root}/${encodeURIComponent(symbol)}/summary`);
  }

  /** Bucket time series for charting. */
  timeseries(symbol: string, window: '5m' | '15m' | '1h' | '1d', source: string, days: number) {
    const params = new HttpParams().set('window', window).set('source', source).set('days', String(days));
    return this.http.get<PredictsTimeseriesDto>(`${this.root}/${encodeURIComponent(symbol)}/timeseries`, { params });
  }

  /** Recent mentions for the drilldown list. */
  mentions(symbol: string, limit?: number) {
    let params = new HttpParams();
    if (limit != null && Number.isFinite(limit) && limit > 0) {
      params = params.set('limit', String(Math.floor(limit)));
    }
    return this.http.get<PredictsMentionDto[]>(`${this.root}/${encodeURIComponent(symbol)}/mentions`, { params });
  }
}
