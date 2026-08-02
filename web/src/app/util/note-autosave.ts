import { Observable, of } from 'rxjs';
import { catchError, finalize, tap } from 'rxjs/operators';

export type NoteSaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';

/**
 * Debounced + flush-on-demand autosave controller for month-note composers.
 * The persist callback should perform create/update and complete on success;
 * return a completed observable immediately to skip a no-op save.
 */
export class NoteAutosave {
  private timer: ReturnType<typeof setTimeout> | null = null;
  private dirty = false;
  private inFlight = false;
  private generation = 0;
  private readonly flushWaiters: Array<() => void> = [];
  private readonly debounceMs: number;
  private readonly persist: () => Observable<unknown>;
  private readonly onStatus: (status: NoteSaveStatus) => void;

  constructor(opts: {
    persist: () => Observable<unknown>;
    onStatus: (status: NoteSaveStatus) => void;
    debounceMs?: number;
  }) {
    this.persist = opts.persist;
    this.onStatus = opts.onStatus;
    this.debounceMs = opts.debounceMs ?? 2000;
  }

  get isDirty(): boolean {
    return this.dirty;
  }

  /** Mark draft dirty and schedule a debounced save. */
  markDirtyAndSchedule(): void {
    this.dirty = true;
    this.generation += 1;
    this.onStatus('dirty');
    this.schedule();
  }

  schedule(): void {
    if (this.timer != null) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      this.flush();
    }, this.debounceMs);
  }

  /**
   * Cancel pending timer and clear dirty without saving (Discard).
   * Does not abort an in-flight HTTP request.
   */
  cancel(): void {
    if (this.timer != null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.dirty = false;
    this.generation += 1;
    this.drainWaiters();
    this.onStatus('idle');
  }

  /**
   * Persist immediately if dirty. Invokes {@code done} after the draft is clean
   * (saved or skipped) or after a failed attempt so navigation is not blocked.
   */
  flush(done?: () => void): void {
    if (done) {
      this.flushWaiters.push(done);
    }
    if (this.timer != null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (!this.dirty) {
      this.drainWaiters();
      return;
    }
    if (this.inFlight) {
      return;
    }
    this.runPersist();
  }

  destroy(): void {
    if (this.timer != null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.dirty && !this.inFlight) {
      this.inFlight = true;
      this.persist().subscribe({
        error: () => undefined,
        complete: () => undefined,
      });
    }
  }

  private runPersist(): void {
    if (!this.dirty || this.inFlight) {
      if (!this.dirty) {
        this.drainWaiters();
      }
      return;
    }

    const startGen = this.generation;
    this.inFlight = true;
    this.onStatus('saving');
    let succeeded = false;

    this.persist()
      .pipe(
        tap(() => {
          succeeded = true;
          if (this.generation === startGen) {
            this.dirty = false;
            this.onStatus('saved');
          } else {
            this.dirty = true;
            this.onStatus('dirty');
          }
        }),
        catchError(() => {
          succeeded = false;
          this.onStatus('error');
          // Keep dirty so the user can retry via typing / blur / Save.
          return of(null);
        }),
        finalize(() => {
          this.inFlight = false;
          if (succeeded && this.dirty) {
            // Draft changed during the successful request — save again.
            this.runPersist();
          } else {
            // Clean, or failed (no auto-retry storm).
            this.drainWaiters();
          }
        }),
      )
      .subscribe();
  }

  private drainWaiters(): void {
    const waiters = this.flushWaiters.splice(0, this.flushWaiters.length);
    for (const w of waiters) {
      try {
        w();
      } catch {
        /* ignore waiter errors */
      }
    }
  }
}

/** Stable fingerprint for skipping identical quiet saves. */
export function noteDraftFingerprint(draft: {
  year: number;
  month: number;
  subject: string;
  body: string;
}): string {
  const subject = (draft.subject || '').trim() || 'Untitled';
  const body = draft.body || '';
  return `${draft.year}|${draft.month}|${subject}|${body}`;
}
