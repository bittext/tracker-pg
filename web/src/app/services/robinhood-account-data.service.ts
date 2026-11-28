import { Injectable, inject } from '@angular/core';
import { Observable, finalize, shareReplay, tap } from 'rxjs';
import {
  RobinhoodRhDailySnapshotDetailDto,
  RobinhoodRhDailyTrackerReportDto,
  RobinhoodRhPeriodBalancesDto,
} from '../models/finance.models';
import { AuthService } from './auth.service';
import { FinanceApiService } from './finance-api.service';

const PREFIX = 'tracker.rh-perf-cache.account.';

/**
 * Shared, cached source for the per-account pages (Accounts tabs and Milestones).
 *
 * - Last-known data is returned synchronously so a page can paint instantly, then refreshed in the background.
 * - Identical requests in flight are shared, so four Account tabs cost one network call, not four.
 * - A slimmed copy is kept in localStorage (per user) so the first paint after a full page reload is instant too.
 *   It is cleared on logout together with the Performance cache.
 */
@Injectable({ providedIn: 'root' })
export class RobinhoodAccountDataService {
  private readonly api = inject(FinanceApiService);
  private readonly auth = inject(AuthService);

  private readonly daily = new Map<number, RobinhoodRhDailyTrackerReportDto>();
  private readonly balances = new Map<number, RobinhoodRhPeriodBalancesDto>();
  private readonly details = new Map<number, RobinhoodRhDailySnapshotDetailDto>();
  private readonly inflight = new Map<string, Observable<unknown>>();

  dailyCached(year: number): RobinhoodRhDailyTrackerReportDto | null {
    return this.daily.get(year) ?? this.restore<RobinhoodRhDailyTrackerReportDto>('daily', year, this.daily);
  }

  balancesCached(year: number): RobinhoodRhPeriodBalancesDto | null {
    return this.balances.get(year) ?? this.restore<RobinhoodRhPeriodBalancesDto>('balances', year, this.balances);
  }

  detailCached(id: number): RobinhoodRhDailySnapshotDetailDto | null {
    return this.details.get(id) ?? null;
  }

  refreshDaily(year: number): Observable<RobinhoodRhDailyTrackerReportDto> {
    return this.shared(
      `daily:${year}`,
      this.api.robinhoodDailyTracker(year, null).pipe(
        tap((report) => {
          this.daily.set(year, report);
          this.persist('daily', year, this.slimDaily(report));
        }),
      ),
    );
  }

  refreshBalances(year: number): Observable<RobinhoodRhPeriodBalancesDto> {
    return this.shared(
      `balances:${year}`,
      this.api.robinhoodDailyTrackerPeriodBalances(year).pipe(
        tap((balances) => {
          this.balances.set(year, balances);
          this.persist('balances', year, balances);
        }),
      ),
    );
  }

  /** Snapshot detail (holdings, cash). A capture's detail is cached by id, so reopening a tab is instant. */
  snapshotDetail(id: number): Observable<RobinhoodRhDailySnapshotDetailDto> {
    return this.shared(
      `detail:${id}`,
      this.api.robinhoodDailyTrackerSnapshot(id).pipe(tap((detail) => this.details.set(id, detail))),
    );
  }

  private shared<T>(key: string, source: Observable<T>): Observable<T> {
    const hit = this.inflight.get(key) as Observable<T> | undefined;
    if (hit) {
      return hit;
    }
    const next = source.pipe(
      finalize(() => this.inflight.delete(key)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.inflight.set(key, next);
    return next;
  }

  /** Keeps only what the account pages read, so the stored copy stays small. */
  private slimDaily(report: RobinhoodRhDailyTrackerReportDto): RobinhoodRhDailyTrackerReportDto {
    return {
      ...report,
      sp500Benchmark: [],
      days: report.days.map((day) => ({
        ...day,
        trades: [],
        summaryNote: '',
        accounts: day.accounts.map((cell) => ({ ...cell, holdings: [], periodFlows: [] })),
        intradayCaptures: (day.intradayCaptures ?? []).slice(-1),
        manualCaptures: (day.manualCaptures ?? []).slice(-1),
      })),
    };
  }

  private key(kind: string, year: number): string {
    return `${PREFIX}${(this.auth.username ?? 'anon').toLowerCase()}.${kind}.${year}`;
  }

  private restore<T>(kind: string, year: number, into: Map<number, T>): T | null {
    try {
      const raw = localStorage.getItem(this.key(kind, year));
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as T;
      into.set(year, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  private persist(kind: string, year: number, value: unknown): void {
    try {
      localStorage.setItem(this.key(kind, year), JSON.stringify(value));
    } catch {
      /* storage full or unavailable — the in-memory copy still covers navigation within this session */
    }
  }
}
