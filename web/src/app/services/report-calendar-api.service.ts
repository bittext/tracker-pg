import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { ReportCalendarEntryDto, ReportCalendarEntryWriteBody, ReportCalendarType } from '../models/report-calendar.models';

@Injectable({ providedIn: 'root' })
export class ReportCalendarApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/report-calendar/entries`;

  list(from: string, to: string, calendarType: ReportCalendarType) {
    return this.http.get<ReportCalendarEntryDto[]>(this.root, {
      params: {
        from,
        to,
        calendarType,
      },
    });
  }

  create(body: ReportCalendarEntryWriteBody) {
    return this.http.post<ReportCalendarEntryDto>(this.root, body);
  }

  update(id: number, body: ReportCalendarEntryWriteBody) {
    return this.http.put<ReportCalendarEntryDto>(`${this.root}/${id}`, body);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }
}
