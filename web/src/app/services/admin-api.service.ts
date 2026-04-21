import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { ManagementDayOneTagDefDto } from '../models/management.models';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly adminRoot = `${environment.apiBaseUrl}/api/admin`;

  createDayOneTag(name: string) {
    return this.http.post<ManagementDayOneTagDefDto>(`${this.adminRoot}/day-one-tags`, { name });
  }

  deleteDayOneTag(id: number) {
    return this.http.delete<void>(`${this.adminRoot}/day-one-tags/${id}`);
  }
}
