import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatOption, MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { forkJoin } from 'rxjs';
import {
  AdminCronJobDto,
  AdminCronJobRunnerDto,
  CronJobFormModel,
  emptyCronJobForm,
  formFromJob,
  formToUpsert,
} from '../../../models/admin-cron-jobs.models';
import { AdminCronJobsApiService } from '../../../services/admin-cron-jobs-api.service';
import { AuthService } from '../../../services/auth.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-admin-cron-jobs-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatOption,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatChipsModule,
  ],
  templateUrl: './admin-cron-jobs-panel.component.html',
  styleUrl: './admin-cron-jobs-panel.component.scss',
})
export class AdminCronJobsPanelComponent implements OnInit {
  private readonly api = inject(AdminCronJobsApiService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  readonly isAdmin = this.auth.isAdmin.bind(this.auth);

  readonly jobs = signal<AdminCronJobDto[]>([]);
  readonly runners = signal<AdminCronJobRunnerDto[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly runningJobKey = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly editingJobKey = signal<string | null>(null);

  form: CronJobFormModel = emptyCronJobForm();

  readonly columns = [
    'name',
    'category',
    'schedule',
    'enabled',
    'lastRun',
    'nextRun',
    'status',
    'actions',
  ];

  readonly formTitle = computed(() =>
    this.editingJobKey() ? `Edit job: ${this.editingJobKey()}` : 'Add scheduled job',
  );

  ngOnInit(): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.reload();
  }

  reload(): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({ jobs: this.api.listJobs(), runners: this.api.listRunners() }).subscribe({
      next: ({ jobs, runners }) => {
        this.jobs.set(jobs);
        this.runners.set(runners);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(formatHttpErrorDetail(err));
      },
    });
  }

  startCreate(): void {
    this.editingJobKey.set(null);
    this.form = emptyCronJobForm();
    const first = this.runners()[0];
    if (first) {
      this.form.runnerKey = first.runnerKey;
      this.form.category = first.category;
      this.form.displayName = first.label;
    }
  }

  startEdit(job: AdminCronJobDto): void {
    this.editingJobKey.set(job.jobKey);
    this.form = formFromJob(job);
  }

  cancelForm(): void {
    this.editingJobKey.set(null);
    this.form = emptyCronJobForm();
  }

  onRunnerChange(): void {
    const runner = this.runners().find((r) => r.runnerKey === this.form.runnerKey);
    if (!runner) {
      return;
    }
    if (!this.editingJobKey()) {
      this.form.displayName = runner.label;
      this.form.category = runner.category;
    }
  }

  saveForm(): void {
    if (!this.form.displayName.trim() || !this.form.runnerKey) {
      this.snackBar.open('Name and runner are required.', undefined, { duration: 3500 });
      return;
    }
    this.saving.set(true);
    const body = formToUpsert(this.form);
    const key = this.editingJobKey();
    const req = key ? this.api.updateJob(key, body) : this.api.createJob(body);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.snackBar.open(key ? 'Job updated.' : 'Job created.', undefined, { duration: 3000 });
        this.cancelForm();
        this.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.snackBar.open(formatHttpErrorDetail(err), undefined, { duration: 6000 });
      },
    });
  }

  toggleEnabled(job: AdminCronJobDto, enabled: boolean): void {
    const body = formToUpsert(formFromJob({ ...job, enabled }));
    this.api.updateJob(job.jobKey, body).subscribe({
      next: () => this.reload(),
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), undefined, { duration: 6000 }),
    });
  }

  runNow(job: AdminCronJobDto): void {
    this.runningJobKey.set(job.jobKey);
    this.api.runNow(job.jobKey).subscribe({
      next: (res) => {
        this.runningJobKey.set(null);
        this.snackBar.open(res.message || 'Job started.', undefined, { duration: 3500 });
        setTimeout(() => this.reload(), 1500);
      },
      error: (err) => {
        this.runningJobKey.set(null);
        this.snackBar.open(formatHttpErrorDetail(err), undefined, { duration: 6000 });
      },
    });
  }

  deleteJob(job: AdminCronJobDto): void {
    if (job.builtIn) {
      return;
    }
    if (!confirm(`Delete custom job "${job.displayName}"?`)) {
      return;
    }
    this.api.deleteJob(job.jobKey).subscribe({
      next: () => {
        this.snackBar.open('Job deleted.', undefined, { duration: 3000 });
        this.reload();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), undefined, { duration: 6000 }),
    });
  }

  formatInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
  }

  statusClass(status: string | null | undefined): string {
    if (status === 'OK') {
      return 'cron-jobs__status--ok';
    }
    if (status === 'ERROR') {
      return 'cron-jobs__status--error';
    }
    return 'cron-jobs__status--neutral';
  }
}
