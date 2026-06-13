import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { forkJoin } from 'rxjs';
import {
  AdminRobinhoodAgenticConfigDto,
  AdminRobinhoodAgenticDefaultsDto,
  AdminRobinhoodAgenticStatsDto,
  AdminRobinhoodAgenticTrackerDto,
} from '../../../models/admin-robinhood-agentic.models';
import { AdminRobinhoodAgenticApiService } from '../../../services/admin-robinhood-agentic-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-admin-robinhood-agentic-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  templateUrl: './admin-robinhood-agentic-panel.component.html',
  styleUrl: './admin-robinhood-agentic-panel.component.scss',
})
export class AdminRobinhoodAgenticPanelComponent implements OnInit {
  private readonly api = inject(AdminRobinhoodAgenticApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly config = signal<AdminRobinhoodAgenticConfigDto | null>(null);
  readonly stats = signal<AdminRobinhoodAgenticStatsDto | null>(null);
  readonly tracker = signal<AdminRobinhoodAgenticTrackerDto | null>(null);
  readonly loading = signal(false);
  readonly savingDefaults = signal(false);
  readonly busyAction = signal<string | null>(null);

  defaultsForm: AdminRobinhoodAgenticDefaultsDto | null = null;
  evaluateUserId: number | null = null;
  applyDefaultsUserId: number | null = null;

  readonly pendingColumns = ['when', 'user', 'source', 'symbol', 'side', 'notional', 'actions'];

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    forkJoin({
      config: this.api.config(),
      stats: this.api.stats(),
      tracker: this.api.tracker(),
      defaults: this.api.defaults(),
    }).subscribe({
      next: ({ config, stats, tracker, defaults }) => {
        this.config.set(config);
        this.stats.set(stats);
        this.tracker.set(tracker);
        this.defaultsForm = { ...defaults };
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.snackBar.open(`Load failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  saveDefaults(): void {
    if (!this.defaultsForm) {
      return;
    }
    this.savingDefaults.set(true);
    this.api.saveDefaults(this.defaultsForm).subscribe({
      next: (d) => {
        this.defaultsForm = { ...d };
        this.savingDefaults.set(false);
        this.snackBar.open('Default guardrails saved', undefined, { duration: 4500 });
      },
      error: (e) => {
        this.savingDefaults.set(false);
        this.snackBar.open(`Save failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  evaluateAll(): void {
    this.busyAction.set('evaluate-all');
    this.api.evaluateAll().subscribe({
      next: (r) => {
        this.busyAction.set(null);
        this.snackBar.open(r.message, undefined, { duration: 7000 });
        this.refresh();
      },
      error: (e) => {
        this.busyAction.set(null);
        this.snackBar.open(`Evaluate failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  evaluateUser(): void {
    if (this.evaluateUserId == null || this.evaluateUserId <= 0) {
      this.snackBar.open('Enter a valid user ID', undefined, { duration: 4500 });
      return;
    }
    this.busyAction.set('evaluate-user');
    this.api.evaluateUser(this.evaluateUserId).subscribe({
      next: (r) => {
        this.busyAction.set(null);
        this.snackBar.open(r.message, undefined, { duration: 7000 });
        this.refresh();
      },
      error: (e) => {
        this.busyAction.set(null);
        this.snackBar.open(`Evaluate failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  applyDefaultsToUser(): void {
    if (this.applyDefaultsUserId == null || this.applyDefaultsUserId <= 0) {
      this.snackBar.open('Enter a valid user ID', undefined, { duration: 4500 });
      return;
    }
    this.busyAction.set('apply-defaults');
    this.api.applyDefaultsToUser(this.applyDefaultsUserId).subscribe({
      next: (r) => {
        this.busyAction.set(null);
        this.snackBar.open(r.message, undefined, { duration: 6000 });
      },
      error: (e) => {
        this.busyAction.set(null);
        this.snackBar.open(`Apply failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  approve(row: { ownerUserId: number; order: { id: number } }): void {
    this.busyAction.set('approve-' + row.order.id);
    this.api.approveOrder(row.ownerUserId, row.order.id).subscribe({
      next: () => {
        this.busyAction.set(null);
        this.snackBar.open('Order approved and placed', undefined, { duration: 5000 });
        this.refresh();
      },
      error: (e) => {
        this.busyAction.set(null);
        this.snackBar.open(`Approve failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  reject(row: { ownerUserId: number; order: { id: number } }): void {
    this.busyAction.set('reject-' + row.order.id);
    this.api.rejectOrder(row.ownerUserId, row.order.id).subscribe({
      next: () => {
        this.busyAction.set(null);
        this.snackBar.open('Order rejected', undefined, { duration: 5000 });
        this.refresh();
      },
      error: (e) => {
        this.busyAction.set(null);
        this.snackBar.open(`Reject failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    return new Date(iso).toLocaleString();
  }
}
