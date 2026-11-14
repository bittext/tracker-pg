import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { RhPredictDeskDto } from '../../../models/rh-predict.models';
import { RhPredictApiService } from '../../../services/rh-predict-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  centralDay,
  emptyBook,
  FreshBook,
  FreshClose,
  FreshDay,
  journeyCloses,
  journeyDays,
  sumBook,
} from './predict-fresh.util';

interface FreshPeriod {
  id: string;
  title: string;
  sub: string;
  book: FreshBook;
}

/** Where the journey begins: the amount in the account and the moment from which closes count. */
const START_AMOUNT = 100.7;
const START_AT = '2026-11-14T16:50';
const STORAGE_KEY = 'predict-fresh-v1';
const POLL_MS = 60_000;

@Component({
  selector: 'app-markets-predict-fresh',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatSnackBarModule,
    RouterLink,
  ],
  templateUrl: './markets-predict-fresh.component.html',
  styleUrl: './markets-predict-fresh.component.scss',
})
export class MarketsPredictFreshComponent implements OnInit, OnDestroy {
  private readonly api = inject(RhPredictApiService);
  private readonly snackBar = inject(MatSnackBar);
  private timer: ReturnType<typeof setInterval> | null = null;

  desk: RhPredictDeskDto | null = null;
  loading = false;
  refreshing = false;

  startAmount = START_AMOUNT;
  /** Local date and time in the `datetime-local` format. */
  startAt = START_AT;

  entries: FreshClose[] = [];
  days: FreshDay[] = [];
  periods: FreshPeriod[] = [];
  journey: FreshBook = emptyBook();
  balance = START_AMOUNT;

  ngOnInit(): void {
    this.restore();
    this.load();
    this.timer = setInterval(() => this.load(true), POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  get startDate(): Date {
    return new Date(this.startAt);
  }

  get newestFirst(): FreshClose[] {
    return [...this.entries].reverse();
  }

  get netOnStart(): number | null {
    return this.startAmount > 0 ? this.journey.net / this.startAmount : null;
  }

  load(quiet = false): void {
    if (!quiet) {
      this.loading = true;
    }
    this.api.load().subscribe({
      next: (desk) => {
        this.desk = desk;
        this.loading = false;
        this.build();
      },
      error: (err) => {
        this.loading = false;
        if (!quiet) {
          this.toastError(err);
        }
      },
    });
  }

  refresh(): void {
    if (this.refreshing) {
      return;
    }
    this.refreshing = true;
    this.api.refresh().subscribe({
      next: (desk) => {
        this.desk = desk;
        this.refreshing = false;
        this.build();
        this.snackBar.open('Predict book updated from Robinhood.', 'Dismiss', { duration: 3000 });
      },
      error: (err) => {
        this.refreshing = false;
        this.toastError(err);
      },
    });
  }

  /** Save the start amount or time and rebuild every total. */
  settingsChanged(): void {
    const amount = Number(this.startAmount);
    this.startAmount = Number.isFinite(amount) && amount >= 0 ? amount : START_AMOUNT;
    if (Number.isNaN(this.startDate.getTime())) {
      this.startAt = START_AT;
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ amount: this.startAmount, startAt: this.startAt }));
    } catch {
      // Storage can be blocked; the page still works for this visit.
    }
    this.build();
  }

  resetSettings(): void {
    this.startAmount = START_AMOUNT;
    this.startAt = START_AT;
    this.settingsChanged();
  }

  moneyClass(value: number | null | undefined): string {
    const n = value ?? 0;
    if (n > 0.004) {
      return 'gain';
    }
    if (n < -0.004) {
      return 'loss';
    }
    return '';
  }

  dayLabel(isoDay: string): string {
    const when = new Date(`${isoDay}T12:00:00`);
    return Number.isNaN(when.getTime())
      ? isoDay
      : when.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
  }

  winRate(book: FreshBook): number | null {
    return book.closes ? book.wins / book.closes : null;
  }

  netOnPrincipal(book: FreshBook): number | null {
    return book.principal > 0 ? book.net / book.principal : null;
  }

  private build(): void {
    this.entries = journeyCloses(this.desk?.closes ?? [], this.startDate, this.startAmount);
    this.days = journeyDays(this.entries);
    this.journey = sumBook(this.entries);
    this.balance = Math.round((this.startAmount + this.journey.net) * 100) / 100;

    const today = centralDay(new Date());
    const month = today.slice(0, 7);
    const year = today.slice(0, 4);
    const within = (prefix: string) => sumBook(this.entries.filter((item) => item.day.startsWith(prefix)));
    const when = new Date(`${today}T12:00:00`);
    this.periods = [
      { id: 'day', title: 'Today', sub: this.dayLabel(today), book: within(today) },
      {
        id: 'month',
        title: 'This month',
        sub: when.toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
        book: within(month),
      },
      { id: 'year', title: 'This year', sub: year, book: within(year) },
    ];
  }

  private restore(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return;
      }
      const saved = JSON.parse(raw) as { amount?: number; startAt?: string };
      if (typeof saved.amount === 'number' && Number.isFinite(saved.amount) && saved.amount >= 0) {
        this.startAmount = saved.amount;
      }
      if (typeof saved.startAt === 'string' && !Number.isNaN(new Date(saved.startAt).getTime())) {
        this.startAt = saved.startAt;
      }
    } catch {
      // A bad saved value falls back to the defaults.
    }
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
  }
}
