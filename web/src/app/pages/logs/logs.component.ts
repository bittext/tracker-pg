import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, filter, finalize, interval, merge, takeUntil } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { ServerLogsDto } from '../../models/logs.models';
import { LogsApiService } from '../../services/logs-api.service';

@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './logs.component.html',
  styleUrl: './logs.component.scss',
})
export class LogsComponent implements OnInit, OnDestroy {
  private readonly logsApi = inject(LogsApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroy$ = new Subject<void>();
  private readonly manualRefresh$ = new Subject<void>();

  lines: string[] = [];
  meta: ServerLogsDto | null = null;
  loading = false;
  lineLimit = 800;
  autoRefresh = true;
  readonly pollMs = 4000;
  /** Shown in-page when the logs API fails (proxy/port/CORS). */
  loadError: string | null = null;

  ngOnInit(): void {
    merge(interval(this.pollMs).pipe(filter(() => this.autoRefresh)), this.manualRefresh$)
      .pipe(
        takeUntil(this.destroy$),
        tap(() => {
          this.loading = true;
          this.loadError = null;
        }),
        switchMap(() =>
          this.logsApi.tail(this.lineLimit).pipe(finalize(() => (this.loading = false))),
        ),
      )
      .subscribe({
        next: (dto) => {
          this.meta = dto;
          const raw = dto.lines ?? [];
          // API returns oldest→newest; show newest at the top of the panel.
          this.lines = [...raw].reverse();
          this.loadError = null;
        },
        error: (e) => {
          const msg = this.errMsg(e);
          this.loadError = msg;
          this.snackBar.open(msg, 'Dismiss', { duration: 12_000 });
        },
      });
    this.refresh();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  refresh(): void {
    this.manualRefresh$.next();
  }

  logText(): string {
    return (this.lines ?? []).join('\n');
  }

  private errMsg(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      if (e.status === 0) {
        return 'Could not reach the API. Start Spring on :9091 and use ng serve (requests go to /api via the dev proxy).';
      }
      const body = e.error;
      if (body && typeof body === 'object' && 'message' in body) {
        const m = (body as { message?: unknown }).message;
        if (typeof m === 'string' && m.length) {
          return `Logs failed (${e.status}): ${m}`;
        }
      }
      return `Logs failed (${e.status}): ${typeof e.error === 'string' ? e.error : e.message}`;
    }
    return String(e);
  }
}
