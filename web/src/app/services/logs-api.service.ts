import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { ServerLogsDto } from '../models/logs.models';

@Injectable({ providedIn: 'root' })
export class LogsApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/admin/logs`;

  tail(limit = 800) {
    return this.http.get<ServerLogsDto>(this.url, { params: { limit: String(limit) } });
  }
}
