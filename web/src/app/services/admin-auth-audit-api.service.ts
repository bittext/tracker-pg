import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AuthLoginEventDto } from '../models/auth-audit.models';

@Injectable({ providedIn: 'root' })
export class AdminAuthAuditApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/auth/login-events`;

  listLoginEvents(limit: number, q?: string) {
    let params = new HttpParams().set('limit', String(limit));
    if (q != null && q.trim() !== '') {
      params = params.set('q', q.trim());
    }
    return this.http.get<AuthLoginEventDto[]>(this.root, { params });
  }
}
