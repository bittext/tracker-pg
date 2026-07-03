import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  RobinhoodAgenticBankingStatusDto,
  RobinhoodAgenticBankingSyncResultDto,
  RobinhoodAgenticBankingTransactionsDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceAgenticBankingApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/credit/agentic';

  status() {
    return this.http.get<RobinhoodAgenticBankingStatusDto>(`${this.root}/status`);
  }

  saveTokens(accessToken: string, refreshToken: string) {
    return this.http.post<RobinhoodAgenticBankingStatusDto>(`${this.root}/tokens`, {
      accessToken,
      refreshToken,
    });
  }

  disconnect() {
    return this.http.delete<void>(`${this.root}/connection`);
  }

  sync() {
    return this.http.post<RobinhoodAgenticBankingSyncResultDto>(`${this.root}/sync`, {});
  }

  transactions() {
    return this.http.get<RobinhoodAgenticBankingTransactionsDto>(`${this.root}/transactions`);
  }
}
