import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  MarketsJourneyDto,
  MarketsJourneyEntryDto,
  MarketsJourneyEntryWriteRequest,
  MarketsJourneyWriteRequest,
  MarketsRoadmapSlapPointsDto,
} from '../models/markets-journey.models';

@Injectable({ providedIn: 'root' })
export class MarketsJourneyApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/markets/journeys';
  private readonly roadmapRoot = '/api/markets/roadmap';

  list() {
    return this.http.get<MarketsJourneyDto[]>(this.root);
  }

  get(id: number) {
    return this.http.get<MarketsJourneyDto>(`${this.root}/${id}`);
  }

  create(body?: MarketsJourneyWriteRequest) {
    return this.http.post<MarketsJourneyDto>(this.root, body ?? {});
  }

  update(id: number, body: MarketsJourneyWriteRequest) {
    return this.http.put<MarketsJourneyDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }

  upsertEntry(journeyId: number, body: MarketsJourneyEntryWriteRequest) {
    return this.http.put<MarketsJourneyEntryDto>(`${this.root}/${journeyId}/entries`, body);
  }

  deleteEntry(journeyId: number, entryId: number) {
    return this.http.delete<void>(`${this.root}/${journeyId}/entries/${entryId}`);
  }

  slapPoints(accountSuffix = '3370', stepAmount = 50000) {
    const params = new HttpParams()
      .set('accountSuffix', accountSuffix)
      .set('stepAmount', stepAmount);
    return this.http.get<MarketsRoadmapSlapPointsDto>(`${this.roadmapRoot}/slap-points`, { params });
  }
}
