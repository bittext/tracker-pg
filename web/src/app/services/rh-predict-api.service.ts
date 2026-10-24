import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  Btc15DeskDto,
  Btc15StreakDeskDto,
  RhPredictCloseDto,
  RhPredictDailyDto,
  RhPredictDeskDto,
  RhPredictNotesDeskDto,
} from '../models/rh-predict.models';

@Injectable({ providedIn: 'root' })
export class RhPredictApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/markets/predict';

  load() {
    return this.http.get<RhPredictDeskDto>(this.root);
  }

  daily() {
    return this.http.get<RhPredictDailyDto[]>(`${this.root}/daily`);
  }

  btc15(date: string) {
    return this.quarter15('btc', date);
  }

  eth15(date: string) {
    return this.quarter15('eth', date);
  }

  doge15(date: string) {
    return this.quarter15('doge', date);
  }

  sol15(date: string) {
    return this.quarter15('sol', date);
  }

  quarter15(asset: 'btc' | 'eth' | 'doge' | 'sol', date: string) {
    return this.http.get<Btc15DeskDto>(`${this.root}/${asset}-15`, { params: { date } });
  }

  streaks(series: string, from: string, to: string) {
    return this.http.get<Btc15StreakDeskDto>(`${this.root}/streaks`, { params: { series, from, to } });
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

  notes() {
    return this.http.get<RhPredictNotesDeskDto>(`${this.root}/notes`);
  }

  addNote(body: string) {
    return this.http.post<RhPredictNotesDeskDto>(`${this.root}/notes`, { body });
  }

  deleteNote(id: number) {
    return this.http.delete<RhPredictNotesDeskDto>(`${this.root}/notes/${id}`);
  }

  noteFeedback() {
    return this.http.post<RhPredictNotesDeskDto>(`${this.root}/notes/feedback`, {});
  }

  clearFeedback() {
    return this.http.delete<RhPredictNotesDeskDto>(`${this.root}/notes/feedback`);
  }
}
