import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  AdminPredictsActionResultDto,
  AdminPredictsConfigDto,
  AdminPredictsStatsDto,
  AdminPredictsStocktwitsProbeDto,
} from '../models/admin-predicts.models';
import { PredictsSourceHealthDto } from '../models/finance-predicts.models';

/**
 * Client for /api/admin/finance/predicts/**. Mirrors the public Predicts read endpoints with extra
 * admin-only triggers that synchronously kick the scheduled jobs.
 */
@Injectable({ providedIn: 'root' })
export class AdminPredictsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/finance/predicts`;

  config() {
    return this.http.get<AdminPredictsConfigDto>(`${this.root}/config`);
  }

  stats() {
    return this.http.get<AdminPredictsStatsDto>(`${this.root}/stats`);
  }

  sources() {
    return this.http.get<PredictsSourceHealthDto[]>(`${this.root}/sources`);
  }

  pollStocktwits() {
    return this.http.post<AdminPredictsActionResultDto>(`${this.root}/actions/poll-stocktwits`, {});
  }

  pollReddit() {
    return this.http.post<AdminPredictsActionResultDto>(`${this.root}/actions/poll-reddit`, {});
  }

  recomputeBaselines() {
    return this.http.post<AdminPredictsActionResultDto>(`${this.root}/actions/recompute-baselines`, {});
  }

  purgeMentions() {
    return this.http.post<AdminPredictsActionResultDto>(`${this.root}/actions/purge-mentions`, {});
  }

  autoSeed() {
    return this.http.post<AdminPredictsActionResultDto>(`${this.root}/actions/auto-seed`, {});
  }

  probeStocktwits(symbol: string) {
    const params = symbol ? { symbol } : undefined;
    return this.http.get<AdminPredictsStocktwitsProbeDto>(`${this.root}/diag/stocktwits`, { params });
  }
}
