import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  LifeMonthNoteAttachmentDto,
  LifeMonthNoteCalendarDto,
  LifeMonthNoteDto,
  LifeMonthNoteWriteBody,
} from '../models/life.models';

@Injectable({ providedIn: 'root' })
export class LifeApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/life/notes`;

  notesCalendar(year: number) {
    return this.http.get<LifeMonthNoteCalendarDto>(`${this.root}/calendar`, {
      params: { year: String(year) },
    });
  }

  listMonthNotes(year: number, month?: number | null) {
    const p: Record<string, string> = { year: String(year) };
    if (month != null) {
      p['month'] = String(month);
    }
    return this.http.get<LifeMonthNoteDto[]>(this.root, { params: p });
  }

  createMonthNote(body: LifeMonthNoteWriteBody) {
    return this.http.post<LifeMonthNoteDto>(this.root, body);
  }

  updateMonthNote(id: number, body: LifeMonthNoteWriteBody) {
    return this.http.put<LifeMonthNoteDto>(`${this.root}/${id}`, body);
  }

  deleteMonthNote(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }

  uploadMonthNoteAttachment(noteId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file, file.name);
    return this.http.post<LifeMonthNoteAttachmentDto>(`${this.root}/${noteId}/attachments`, fd);
  }

  deleteMonthNoteAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.root}/attachments/${attachmentId}`);
  }

  getMonthNoteAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    return this.http.get(`${this.root}/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params: { disposition },
    });
  }
}
