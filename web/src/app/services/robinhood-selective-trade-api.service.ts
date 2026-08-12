import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  RhDailyTrackerAiInsightStatusDto,
  RobinhoodSelectiveTradeAiInsightDto,
  RobinhoodSelectiveTradeCalendarDto,
  RobinhoodSelectiveTradeEntryDto,
  RobinhoodSelectiveTradeLedgerDto,
  RobinhoodSelectiveTradeRequestDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class RobinhoodSelectiveTradeApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/robinhood/selective-trades';

  ledger(year: number, month?: number | null) {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    return this.http.get<RobinhoodSelectiveTradeLedgerDto>(this.root, { params });
  }

  calendar(year: number, month?: number | null) {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    return this.http.get<RobinhoodSelectiveTradeCalendarDto>(`${this.root}/calendar`, { params });
  }

  aiStatus() {
    return this.http.get<RhDailyTrackerAiInsightStatusDto>(`${this.root}/ai-status`);
  }

  aiAnalyze(year: number, month?: number | null) {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    return this.http.post<RobinhoodSelectiveTradeAiInsightDto>(`${this.root}/ai-analyze`, null, { params });
  }

  create(body: RobinhoodSelectiveTradeRequestDto) {
    return this.http.post<RobinhoodSelectiveTradeEntryDto>(this.root, body);
  }

  update(id: number, body: RobinhoodSelectiveTradeRequestDto) {
    return this.http.put<RobinhoodSelectiveTradeEntryDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
