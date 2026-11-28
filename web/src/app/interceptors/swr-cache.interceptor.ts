import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, concat, of, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { SwrHttpCacheService } from '../services/swr-http-cache.service';

/**
 * Report endpoints that are slow to compute but fine to show from the last visit first.
 * Journal day detail is deliberately left out: it feeds an editor, so a second emission must not overwrite typing.
 * Polling hints and snapshot detail are left out as well.
 */
const CACHEABLE_PATHS: RegExp[] = [
  /\/api\/finance\/robinhood\/ytd-check$/,
  /\/api\/finance\/robinhood\/executed-trades$/,
  /\/api\/finance\/robinhood\/daily-tracker$/,
  /\/api\/finance\/robinhood\/daily-tracker\/period-balances$/,
  /\/api\/finance\/robinhood\/quarterly-statements$/,
  /\/api\/finance\/robinhood\/ownership-history$/,
  /\/api\/finance\/robinhood\/margin-watch$/,
  /\/api\/markets\/trading-journal$/,
  /\/api\/markets\/trading-journal\/dates$/,
];

/** Areas whose cached reports are dropped when something is written there. */
const WRITE_AREAS: { match: RegExp; fragment: string }[] = [
  { match: /\/api\/finance\/robinhood\//, fragment: '/api/finance/robinhood/' },
  { match: /\/api\/markets\/trading-journal/, fragment: '/api/markets/trading-journal' },
];

function pathOf(urlWithParams: string): string {
  return urlWithParams.split('?')[0];
}

function isCacheable(req: { url: string; params: { get(name: string): string | null } }): boolean {
  const path = pathOf(req.url);
  if (!CACHEABLE_PATHS.some((re) => re.test(path))) {
    return false;
  }
  // A margin-watch request with sync=true pulls live data from the broker; never serve that from cache.
  return !(path.endsWith('/margin-watch') && req.params.get('sync') === 'true');
}

/**
 * Stale-while-revalidate for report GETs: when a previous response exists it is emitted immediately and the
 * live response follows (so subscribers see up to two emissions). If the live request then fails, the cached
 * data stays on screen instead of being replaced by an error.
 */
export const swrCacheInterceptor: HttpInterceptorFn = (req, next) => {
  const cache = inject(SwrHttpCacheService);

  if (req.method !== 'GET') {
    return next(req).pipe(
      tap((event) => {
        if (event instanceof HttpResponse && event.ok) {
          const area = WRITE_AREAS.find((a) => a.match.test(req.url));
          if (area) {
            cache.invalidate(area.fragment);
          }
        }
      }),
    );
  }
  if (!isCacheable(req)) {
    return next(req);
  }

  const key = req.urlWithParams;
  const cached = cache.get(key);
  const network$: Observable<HttpEvent<unknown>> = next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse && event.ok && event.body != null) {
        cache.set(key, event.body);
      }
    }),
  );
  if (cached == null) {
    return network$;
  }
  const stale$ = of(new HttpResponse({ body: cached, status: 200, statusText: 'OK (cached)', url: req.url }));
  return concat(
    stale$,
    network$.pipe(
      catchError((err: unknown) =>
        // Keep what is already on screen for transient failures; let auth failures through.
        err instanceof HttpErrorResponse && err.status === 401 ? throwError(() => err) : of<HttpEvent<unknown>>(),
      ),
    ),
  );
};
