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

  calendar(year: number, month: number) {
    const p = new HttpParams().set('year', String(year)).set('month', String(month));
    return this.http.get<JournalCalendarDayDto[]>(`${this.root}/calendar`, { params: p });
  }

  listEntriesForDay(dateIso: string) {
    const p = new HttpParams().set('date', dateIso);
    return this.http.get<JournalEntryDto[]>(`${this.root}/entries/day`, { params: p });
  }

  search(
    from: string,
    to: string,
    q?: string | null,
    tagIds?: (number | string)[] | null,
  ) {
    let p = this.appendTagIdParams(
      new HttpParams().set('from', from).set('to', to),
      tagIds,
    );
    if (q) {
      p = p.set('q', q);
    }
    return this.http.get<JournalEntryDto[]>(`${this.root}/entries/search`, { params: p });
  }

  summary(
    from: string,
    to: string,
    q?: string | null,
    tagIds?: (number | string)[] | null,
  ) {
    let p = this.appendTagIdParams(
      new HttpParams().set('from', from).set('to', to),
      tagIds,
    );
    if (q) {
      p = p.set('q', q);
    }
    return this.http.get<JournalSummaryDto>(`${this.root}/summary`, { params: p });
  }

  /** Mat-select may yield string ids; Spring expects one query param per tag: tagIds=1&tagIds=2 */
  private appendTagIdParams(params: HttpParams, tagIds?: (number | string)[] | null): HttpParams {
    let p = params;
    for (const id of this.normalizeTagIds(tagIds)) {
      p = p.append('tagIds', String(id));
    }
    return p;
  }

  private normalizeTagIds(tagIds?: (number | string)[] | null): number[] {
    if (!tagIds?.length) {
      return [];
    }
    const nums = tagIds
      .map((id) => (typeof id === 'string' ? Number(id) : Number(id)))
      .filter((n) => Number.isFinite(n) && n > 0);
    return [...new Set(nums)];
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

  /**
   * Download attachment with JWT (use this from the app). Plain {@code <a href>} to the file URL
   * does not send the Bearer token and will get 401/403 from the API.
   */
  getAttachmentBlob(attachmentId: number, disposition: 'inline' | 'attachment' = 'inline') {
    const params = new HttpParams().set('disposition', disposition);
    return this.http.get(`${this.root}/attachments/${attachmentId}/file`, {
      responseType: 'blob',
      params,
    });
  }

  /**
   * Raw file URL (no auth). Do not use for user-facing {@code <a href>} — use {@link getAttachmentBlob} instead.
   */
  attachmentFilePath(attachmentId: number) {
    return `${environment.apiBaseUrl}/api/journal/attachments/${attachmentId}/file`;
  }
}
