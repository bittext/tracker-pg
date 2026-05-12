import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  DailyActivityPointDto,
  FeatureUsageDto,
  MemberUsageDto,
  SignInDailyPointDto,
  UsageSummaryDto,
} from '../models/admin-usage.models';

@Injectable({ providedIn: 'root' })
export class AdminUsageApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/usage`;

  summary() {
    return this.http.get<UsageSummaryDto>(`${this.root}/summary`);
  }

  featureUsage(days: number) {
    return this.http.get<FeatureUsageDto[]>(`${this.root}/feature-usage`, {
      params: { days: String(days) },
    });
  }

  activityTimeseries(days: number) {
    return this.http.get<DailyActivityPointDto[]>(`${this.root}/activity-timeseries`, {
      params: { days: String(days) },
    });
  }

  signInsTimeseries(days: number) {
    return this.http.get<SignInDailyPointDto[]>(`${this.root}/sign-ins-timeseries`, {
      params: { days: String(days) },
    });
  }

  members(days: number) {
    return this.http.get<MemberUsageDto[]>(`${this.root}/members`, {
      params: { days: String(days) },
    });
  }
}
