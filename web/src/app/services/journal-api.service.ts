import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  JournalAttachmentDto,
  JournalCalendarDayDto,
  JournalEntryDto,
  JournalEntryWriteBody,
  JournalSummaryDto,
  JournalTagDefDto,
} from '../models/journal.models';

@Injectable({ providedIn: 'root' })
export class JournalApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/journal`;

  listTagDefinitions() {
    return this.http.get<JournalTagDefDto[]>(`${this.root}/tag-definitions`);
  }

  createTag(name: string) {
    return this.http.post<JournalTagDefDto>(`${this.root}/tag-definitions`, { name });
  }

  deleteTag(id: number) {
    return this.http.delete<void>(`${this.root}/tag-definitions/${id}`);
  }

  calendar(year: number, month: number, ownerUserId?: number | null) {
    let p = new HttpParams().set('year', String(year)).set('month', String(month));
    if (ownerUserId != null) {
      p = p.set('ownerUserId', String(ownerUserId));
    }
    return this.http.get<JournalCalendarDayDto[]>(`${this.root}/calendar`, { params: p });
  }

  listEntriesForDay(dateIso: string, ownerUserId?: number | null) {
    let p = new HttpParams().set('date', dateIso);
    if (ownerUserId != null) {
      p = p.set('ownerUserId', String(ownerUserId));
    }
    return this.http.get<JournalEntryDto[]>(`${this.root}/entries/day`, { params: p });
  }

  search(
    from: string,
    to: string,
    q?: string | null,
    tagIds?: number[] | null,
    ownerUserId?: number | null,
  ) {
    let p = new HttpParams().set('from', from).set('to', to);
    if (q) {
      p = p.set('q', q);
    }
    (tagIds ?? []).forEach((id) => {
      p = p.append('tagIds', String(id));
    });
    if (ownerUserId != null) {
      p = p.set('ownerUserId', String(ownerUserId));
    }
    return this.http.get<JournalEntryDto[]>(`${this.root}/entries/search`, { params: p });
  }

  summary(
    from: string,
    to: string,
    q?: string | null,
    tagIds?: number[] | null,
    ownerUserId?: number | null,
  ) {
    let p = new HttpParams().set('from', from).set('to', to);
    if (q) {
      p = p.set('q', q);
    }
    (tagIds ?? []).forEach((id) => {
      p = p.append('tagIds', String(id));
    });
    if (ownerUserId != null) {
      p = p.set('ownerUserId', String(ownerUserId));
    }
    return this.http.get<JournalSummaryDto>(`${this.root}/summary`, { params: p });
  }

  getEntry(id: number) {
    return this.http.get<JournalEntryDto>(`${this.root}/entries/${id}`);
  }

  createEntry(body: JournalEntryWriteBody) {
    return this.http.post<JournalEntryDto>(`${this.root}/entries`, body);
  }

  updateEntry(id: number, body: JournalEntryWriteBody) {
    return this.http.put<JournalEntryDto>(`${this.root}/entries/${id}`, body);
  }

  deleteEntry(id: number) {
    return this.http.delete<void>(`${this.root}/entries/${id}`);
  }

  uploadAttachment(entryId: number, file: File) {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<JournalAttachmentDto>(`${this.root}/entries/${entryId}/attachments`, fd);
  }

  deleteAttachment(attachmentId: number) {
    return this.http.delete<void>(`${this.root}/attachments/${attachmentId}`);
  }

  /** URL path for download/preview; prefix with origin or api base in components. */
  attachmentFilePath(attachmentId: number) {
    return `${environment.apiBaseUrl}/api/journal/attachments/${attachmentId}/file`;
  }
}
