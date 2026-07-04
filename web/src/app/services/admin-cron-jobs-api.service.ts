import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  AdminCronJobActionResultDto,
  AdminCronJobDto,
  AdminCronJobRunnerDto,
  AdminCronJobUpsertRequest,
} from '../models/admin-cron-jobs.models';

@Injectable({ providedIn: 'root' })
export class AdminCronJobsApiService {
  private readonly http = inject(HttpClient);
  private readonly root = '/api/admin/cron-jobs';

  listJobs() {
    return this.http.get<AdminCronJobDto[]>(this.root);
  }

  listRunners() {
    return this.http.get<AdminCronJobRunnerDto[]>(`${this.root}/runners`);
  }

  createJob(body: AdminCronJobUpsertRequest) {
    return this.http.post<AdminCronJobDto>(this.root, body);
  }

  updateJob(jobKey: string, body: AdminCronJobUpsertRequest) {
    return this.http.put<AdminCronJobDto>(`${this.root}/${encodeURIComponent(jobKey)}`, body);
  }

  deleteJob(jobKey: string) {
    return this.http.delete<void>(`${this.root}/${encodeURIComponent(jobKey)}`);
  }

  runNow(jobKey: string) {
    return this.http.post<AdminCronJobActionResultDto>(`${this.root}/${encodeURIComponent(jobKey)}/run`, {});
  }
}
