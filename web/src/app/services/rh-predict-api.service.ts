import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RhPredictCloseDto, RhPredictDeskDto } from '../models/rh-predict.models';

@Injectable({ providedIn: 'root' })
export class RhPredictApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/markets/predict';

  load() {
    return this.http.get<RhPredictDeskDto>(this.root);
  }

  refresh() {
    return this.http.post<RhPredictDeskDto>(`${this.root}/refresh`, {});
  }

  rename(id: number, label: string) {
    return this.http.put<RhPredictCloseDto>(`${this.root}/closes/${id}`, { label });
  }
}
