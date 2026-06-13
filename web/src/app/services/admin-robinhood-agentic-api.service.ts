import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  AdminRobinhoodAgenticActionResultDto,
  AdminRobinhoodAgenticConfigDto,
  AdminRobinhoodAgenticDefaultsDto,
  AdminRobinhoodAgenticDefaultsRequest,
  AdminRobinhoodAgenticStatsDto,
  AdminRobinhoodAgenticTrackerDto,
} from '../models/admin-robinhood-agentic.models';
import { RobinhoodAgenticOrderDto, RobinhoodAgenticSettingsDto } from '../models/finance.models';

@Injectable({ providedIn: 'root' })
export class AdminRobinhoodAgenticApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/admin/finance/agentic';

  config() {
    return this.http.get<AdminRobinhoodAgenticConfigDto>(`${this.root}/config`);
  }

  stats() {
    return this.http.get<AdminRobinhoodAgenticStatsDto>(`${this.root}/stats`);
  }

  tracker() {
    return this.http.get<AdminRobinhoodAgenticTrackerDto>(`${this.root}/tracker`);
  }

  defaults() {
    return this.http.get<AdminRobinhoodAgenticDefaultsDto>(`${this.root}/defaults`);
  }

  saveDefaults(body: AdminRobinhoodAgenticDefaultsRequest) {
    return this.http.put<AdminRobinhoodAgenticDefaultsDto>(`${this.root}/defaults`, body);
  }

  evaluateAll() {
    return this.http.post<AdminRobinhoodAgenticActionResultDto>(`${this.root}/actions/evaluate-all`, {});
  }

  evaluateUser(userId: number) {
    return this.http.post<AdminRobinhoodAgenticActionResultDto>(`${this.root}/actions/evaluate/${userId}`, {});
  }

  applyDefaultsToUser(userId: number) {
    return this.http.post<AdminRobinhoodAgenticActionResultDto>(`${this.root}/users/${userId}/defaults/apply`, {});
  }

  approveOrder(userId: number, orderId: number) {
    return this.http.post<RobinhoodAgenticOrderDto>(
      `${this.root}/users/${userId}/orders/${orderId}/approve`,
      {},
    );
  }

  rejectOrder(userId: number, orderId: number) {
    return this.http.post<RobinhoodAgenticOrderDto>(`${this.root}/users/${userId}/orders/${orderId}/reject`, {});
  }

  settingsForUser(userId: number) {
    return this.http.get<RobinhoodAgenticSettingsDto>(`${this.root}/users/${userId}/settings`);
  }
}
