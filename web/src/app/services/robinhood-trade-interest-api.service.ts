import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RobinhoodTradeInterestDto, RobinhoodTradeInterestRequestDto } from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class RobinhoodTradeInterestApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/robinhood/trade-interests';

  list(status?: string | null) {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<RobinhoodTradeInterestDto[]>(this.root, { params });
  }

  create(body: RobinhoodTradeInterestRequestDto) {
    return this.http.post<RobinhoodTradeInterestDto>(this.root, body);
  }

  update(id: number, body: RobinhoodTradeInterestRequestDto) {
    return this.http.put<RobinhoodTradeInterestDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
