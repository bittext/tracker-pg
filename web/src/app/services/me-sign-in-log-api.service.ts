import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AuthLoginEventDto } from '../models/auth-audit.models';

/** Current-user-only sign-in audit (not the global admin log). */
@Injectable({ providedIn: 'root' })
export class MeSignInLogApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/me/sign-in-log`;

  list(limit: number, q?: string) {
    let params: Record<string, string> = { limit: String(limit) };
    if (q != null && q.trim()) {
      params = { ...params, q: q.trim() };
    }
    return this.http.get<AuthLoginEventDto[]>(this.root, { params });
  }
}
