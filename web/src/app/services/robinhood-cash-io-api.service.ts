import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  RobinhoodCashIoAccountDto,
  RobinhoodCashIoCalendarDto,
  RobinhoodCashIoDailyHistoryDto,
  RobinhoodCashIoEntryDto,
  RobinhoodCashIoImportDto,
  RobinhoodCashIoImportRequestDto,
  RobinhoodCashIoLedgerDto,
  RobinhoodCashIoRequestDto,
  RobinhoodCashIoYtdDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class RobinhoodCashIoApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/robinhood/cash-io';

  accounts() {
    return this.http.get<RobinhoodCashIoAccountDto[]>(`${this.root}/accounts`);
  }

  /** Names usable as an origin or receiving account: your accounts plus saved counterparties. */
  parties() {
    return this.http.get<string[]>(`${this.root}/parties`);
  }

  ledger(
    year: number,
    month?: number | null,
    accountSuffix?: string | null,
    origin?: string | null,
    receiver?: string | null,
  ) {
    return this.http.get<RobinhoodCashIoLedgerDto>(this.root, {
      params: this.periodParams(year, month, accountSuffix, origin, receiver),
    });
  }

  calendar(
    year: number,
    month?: number | null,
    accountSuffix?: string | null,
    origin?: string | null,
    receiver?: string | null,
  ) {
    return this.http.get<RobinhoodCashIoCalendarDto>(`${this.root}/calendar`, {
      params: this.periodParams(year, month, accountSuffix, origin, receiver),
    });
  }

  private periodParams(
    year: number,
    month?: number | null,
    accountSuffix?: string | null,
    origin?: string | null,
    receiver?: string | null,
  ): HttpParams {
    let params = new HttpParams().set('year', year);
    if (month != null) {
      params = params.set('month', month);
    }
    if (accountSuffix) {
      params = params.set('accountSuffix', accountSuffix);
    }
    if (origin) {
      params = params.set('origin', origin);
    }
    if (receiver) {
      params = params.set('receiver', receiver);
    }
    return params;
  }

  ytd(year: number, accountSuffix = '3370') {
    const params = new HttpParams().set('year', year).set('accountSuffix', accountSuffix);
    return this.http.get<RobinhoodCashIoYtdDto>(`${this.root}/ytd`, { params });
  }

  daily(year: number, accountSuffix = '3370') {
    const params = new HttpParams().set('year', year).set('accountSuffix', accountSuffix);
    return this.http.get<RobinhoodCashIoDailyHistoryDto>(`${this.root}/daily`, { params });
  }

  /** Shows what a Robinhood CSV would add, without saving anything. */
  importPreview(body: RobinhoodCashIoImportRequestDto) {
    return this.http.post<RobinhoodCashIoImportDto>(`${this.root}/import/preview`, body);
  }

  /** Adds the new transfers from a Robinhood CSV; rows already in the ledger are skipped. */
  importRows(body: RobinhoodCashIoImportRequestDto) {
    return this.http.post<RobinhoodCashIoImportDto>(`${this.root}/import`, body);
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
