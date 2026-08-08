import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  FinanceCreditStandingDto,
  FinanceCreditStandingRequestDto,
} from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceCreditStandingApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/credit-standing';

  get() {
    return this.http.get<FinanceCreditStandingDto>(this.root);
  }

  upsert(body: FinanceCreditStandingRequestDto) {
    return this.http.put<FinanceCreditStandingDto>(this.root, body);
  }
}
