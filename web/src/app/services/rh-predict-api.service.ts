import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Btc15DeskDto, RhPredictCloseDto, RhPredictDeskDto } from '../models/rh-predict.models';

@Injectable({ providedIn: 'root' })
export class RhPredictApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/markets/predict';

  load() {
    return this.http.get<RhPredictDeskDto>(this.root);
  }

  btc15(date: string) {
    return this.http.get<Btc15DeskDto>(`${this.root}/btc-15`, { params: { date } });
  }

  refresh() {
    return this.http.post<RhPredictDeskDto>(`${this.root}/refresh`, {});
  }

  rename(id: number, label: string) {
    return this.http.put<RhPredictCloseDto>(`${this.root}/closes/${id}`, { label });
  }

  setOpenCost(amount: number | null) {
    return this.http.put<RhPredictDeskDto>(`${this.root}/open-cost`, { amount });
  }
}
