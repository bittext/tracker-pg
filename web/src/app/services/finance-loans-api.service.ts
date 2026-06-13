import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  FinanceLoanDto,
  FinanceLoanOptionsDto,
  FinanceLoanRequestDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceLoansApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/loans';

  options() {
    return this.http.get<FinanceLoanOptionsDto>(`${this.root}/options`);
  }

  list() {
    return this.http.get<FinanceLoanDto[]>(this.root);
  }

  get(id: number) {
    return this.http.get<FinanceLoanDto>(`${this.root}/${id}`);
  }

  create(body: FinanceLoanRequestDto) {
    return this.http.post<FinanceLoanDto>(this.root, body);
  }

  update(id: number, body: FinanceLoanRequestDto) {
    return this.http.put<FinanceLoanDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
