import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { GithubRepositoryInsightsDto } from '../models/github-insights.models';

@Injectable({ providedIn: 'root' })
export class AdminGithubApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/github`;

  getRepositoryInsights() {
    return this.http.get<GithubRepositoryInsightsDto>(`${this.root}/repository-insights`);
  }
}
