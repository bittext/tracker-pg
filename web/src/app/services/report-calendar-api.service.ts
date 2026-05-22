import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  ReportCalendarAttachmentDto,
  ReportCalendarEntryDto,
  ReportCalendarEntryWriteBody,
  ReportCalendarType,
} from '../models/report-calendar.models';

@Injectable({ providedIn: 'root' })
export class ReportCalendarApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/report-calendar/entries`;
  private readonly filesRoot = `${environment.apiBaseUrl}/api/report-calendar`;

  /** When `calendarType` is omitted, the API returns entries for every type in the date range. */
  list(from: string, to: string, calendarType: ReportCalendarType | null) {
    let params = new HttpParams().set('from', from).set('to', to);
    if (calendarType != null) {
      params = params.set('calendarType', calendarType);
    }
    return this.http.get<ReportCalendarEntryDto[]>(this.root, { params });
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

  uploadAttachment(entryId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<ReportCalendarAttachmentDto>(`${this.root}/${entryId}/attachments`, fd);
  }

  deleteAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.filesRoot}/attachments/${attachmentId}`);
  }

  getAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.filesRoot}/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }
}
