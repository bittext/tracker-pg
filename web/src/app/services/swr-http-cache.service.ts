import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';

/** Prefix shared with the logout cleanup in AuthService (everything under it is dropped on sign-out). */
export const SWR_STORAGE_PREFIX = 'tracker.rh-perf-cache.swr.';
/** Largest payload (characters) written to localStorage; bigger ones stay in memory for the session. */
const MAX_PERSIST_CHARS = 1_500_000;

/**
 * Last good response per cacheable GET, per user. Backs the stale-while-revalidate interceptor so report tabs
 * paint instantly from what was shown last time while the fresh response loads behind it.
 */
@Injectable({ providedIn: 'root' })
export class SwrHttpCacheService {
  private readonly auth = inject(AuthService);
  private readonly memory = new Map<string, unknown>();

  get(url: string): unknown | null {
    const key = this.key(url);
    if (this.memory.has(key)) {
      return this.memory.get(key) ?? null;
    }
    try {
      const raw = localStorage.getItem(key);
      if (raw) {
        const parsed = JSON.parse(raw) as unknown;
        this.memory.set(key, parsed);
        return parsed;
      }
    } catch {
      /* unreadable entry — treat as a miss */
    }
    return null;
  }

  set(url: string, body: unknown): void {
    const key = this.key(url);
    this.memory.set(key, body);
    try {
      const raw = JSON.stringify(body);
      if (raw.length <= MAX_PERSIST_CHARS) {
        localStorage.setItem(key, raw);
      } else {
        localStorage.removeItem(key);
      }
    } catch {
      /* storage full or unavailable — memory copy still serves this session */
    }
  }

  /** Drops every cached response whose path contains the given fragment (after a write to that area). */
  invalidate(pathFragment: string): void {
    const marker = this.key('');
    for (const key of [...this.memory.keys()]) {
      if (key.startsWith(marker) && key.includes(pathFragment)) {
        this.memory.delete(key);
      }
    }
    try {
      for (const key of Object.keys(localStorage)) {
        if (key.startsWith(marker) && key.includes(pathFragment)) {
          localStorage.removeItem(key);
        }
      }
    } catch {
      /* ignore */
    }
  }

  private key(url: string): string {
    return `${SWR_STORAGE_PREFIX}${(this.auth.username ?? 'anon').toLowerCase()}|${url}`;
  }
}
