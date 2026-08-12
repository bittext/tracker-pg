import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  RobinhoodCashIoAccountDto,
  RobinhoodCashIoCalendarDto,
  RobinhoodCashIoEntryDto,
  RobinhoodCashIoLedgerDto,
  RobinhoodCashIoRequestDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class RobinhoodCashIoApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/robinhood/cash-io';

  accounts() {
    return this.http.get<RobinhoodCashIoAccountDto[]>(`${this.root}/accounts`);
  }

  ledger(year: number, month?: number | null, accountSuffix?: string | null) {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    if (accountSuffix) {
      params = params.set('accountSuffix', accountSuffix);
    }
    return this.http.get<RobinhoodCashIoLedgerDto>(this.root, { params });
  }

  calendar(year: number, month?: number | null, accountSuffix?: string | null) {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    if (accountSuffix) {
      params = params.set('accountSuffix', accountSuffix);
    }
    return this.http.get<RobinhoodCashIoCalendarDto>(`${this.root}/calendar`, { params });
  }

  create(body: RobinhoodCashIoRequestDto) {
    return this.http.post<RobinhoodCashIoEntryDto>(this.root, body);
  }

  update(id: number, body: RobinhoodCashIoRequestDto) {
    return this.http.put<RobinhoodCashIoEntryDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
