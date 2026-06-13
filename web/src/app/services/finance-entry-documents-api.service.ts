import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { FinanceEntryDocumentDto, FinanceEntryEntityType } from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class FinanceEntryDocumentsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/finance/entry-documents';

  list(entityType: FinanceEntryEntityType, entityId: number) {
    const params = new HttpParams().set('entityType', entityType).set('entityId', String(entityId));
    return this.http.get<FinanceEntryDocumentDto[]>(this.root, { params });
  }

  upload(entityType: FinanceEntryEntityType, entityId: number, file: File, displayName?: string) {
    const form = new FormData();
    form.append('file', file, file.name);
    if (displayName?.trim()) {
      form.append('displayName', displayName.trim());
    }
    const params = new HttpParams().set('entityType', entityType).set('entityId', String(entityId));
    return this.http.post<FinanceEntryDocumentDto>(this.root, form, { params });
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.root}/${id}`);
  }

  downloadBlob(id: number, disposition: 'inline' | 'attachment' = 'attachment') {
    return this.http.get(`${this.root}/${id}/file`, {
      params: { disposition },
      responseType: 'blob',
    });
  }
}
