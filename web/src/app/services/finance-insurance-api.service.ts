import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  FinanceInsuranceOptionsDto,
  FinanceInsurancePolicyDto,
  FinanceInsurancePolicyRequestDto,
  FinanceInsuranceSummaryDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceInsuranceApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/insurance-policies';

  options() {
    return this.http.get<FinanceInsuranceOptionsDto>(`${this.root}/options`);
  }

  summary() {
    return this.http.get<FinanceInsuranceSummaryDto>(`${this.root}/summary`);
  }

  list() {
    return this.http.get<FinanceInsurancePolicyDto[]>(this.root);
  }

  get(id: number) {
    return this.http.get<FinanceInsurancePolicyDto>(`${this.root}/${id}`);
  }

  create(body: FinanceInsurancePolicyRequestDto) {
    return this.http.post<FinanceInsurancePolicyDto>(this.root, body);
  }

  update(id: number, body: FinanceInsurancePolicyRequestDto) {
    return this.http.put<FinanceInsurancePolicyDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
