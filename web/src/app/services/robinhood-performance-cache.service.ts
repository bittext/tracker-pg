import { Injectable, inject } from '@angular/core';
import {
  RobinhoodExecutedTradesDto,
  RobinhoodRhCryptoTrackerReportDto,
  RobinhoodRhDailyTrackerReportDto,
  RobinhoodRhPeriodBalancesDto,
} from '../models/finance.models';
import { AuthService } from './auth.service';

export const RH_PERFORMANCE_CACHE_PREFIX = 'tracker.rh-perf-cache.';

export interface RobinhoodPerformanceSnapshot {
  daily: RobinhoodRhDailyTrackerReportDto | null;
  balances: RobinhoodRhPeriodBalancesDto | null;
  trades: RobinhoodExecutedTradesDto | null;
  crypto: RobinhoodRhCryptoTrackerReportDto | null;
}

/**
 * Last-known Performance data per year, kept in memory and in localStorage (keyed by user) so the
 * tab can paint immediately and refresh in the background instead of waiting on the network.
 */
@Injectable({ providedIn: 'root' })
export class RobinhoodPerformanceCacheService {
  private readonly auth = inject(AuthService);
  private readonly memory = new Map<string, RobinhoodPerformanceSnapshot>();

  read(year: number): RobinhoodPerformanceSnapshot | null {
    const key = this.key(year);
    const hit = this.memory.get(key);
    if (hit) {
      return hit;
    }
    try {
      const raw = localStorage.getItem(key);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as RobinhoodPerformanceSnapshot;
      this.memory.set(key, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  /** Merge freshly loaded parts into the cached snapshot for the year. */
  write(year: number, part: Partial<RobinhoodPerformanceSnapshot>): void {
    const key = this.key(year);
    const next: RobinhoodPerformanceSnapshot = {
      daily: null,
      balances: null,
      trades: null,
      crypto: null,
      ...(this.memory.get(key) ?? this.read(year) ?? {}),
      ...part,
    };
    this.memory.set(key, next);
    try {
      localStorage.setItem(key, JSON.stringify(next));
    } catch {
      /* storage full or unavailable — the in-memory copy still helps within this session */
    }
  }

  private key(year: number): string {
    return `${RH_PERFORMANCE_CACHE_PREFIX}${(this.auth.username ?? 'anon').toLowerCase()}.${year}`;
  }
}
