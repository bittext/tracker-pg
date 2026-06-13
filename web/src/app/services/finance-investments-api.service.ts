import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  FinanceInvestmentDto,
  FinanceInvestmentOptionsDto,
  FinanceInvestmentRequestDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceInvestmentsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/investments';

  options() {
    return this.http.get<FinanceInvestmentOptionsDto>(`${this.root}/options`);
  }

  list() {
    return this.http.get<FinanceInvestmentDto[]>(this.root);
  }

  get(id: number) {
    return this.http.get<FinanceInvestmentDto>(`${this.root}/${id}`);
  }

  create(body: FinanceInvestmentRequestDto) {
    return this.http.post<FinanceInvestmentDto>(this.root, body);
  }

  update(id: number, body: FinanceInvestmentRequestDto) {
    return this.http.put<FinanceInvestmentDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
