import { HttpErrorResponse } from '@angular/common/http';

/** Text for snackbars and inline alerts; avoids `[object Object]` for {@link HttpErrorResponse}. */
export function formatHttpErrorDetail(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'Network error — API unreachable. Use `ng serve` with the dev proxy (tracker-pg API on :9091), or set apiBaseUrl.';
    }
    if (e.status === 413) {
      return '413: Upload too large (or proxy body limit). Check server limits: `tracker.journal.max-attachment-bytes` (currently 8MB), `spring.servlet.multipart.max-file-size`, and any nginx/Caddy body-size limits. If this is a dev HTML response, also verify `/api` proxying or `apiBaseUrl`.';
    }
    if (typeof e.error === 'string' && e.error.trim()) {
      const body = e.error;
      if (/^\s*</.test(body) || /<title>\s*404/i.test(body)) {
        return `${e.status}: HTML error page (not JSON from Spring). Usually /api is not proxied: run the API on :9091 and use ng serve, or set apiBaseUrl to http://127.0.0.1:9091.`;
      }
      const t = body.trim();
      return `${e.status}: ${t.length > 240 ? `${t.slice(0, 240)}…` : t}`;
    }
    if (e.error && typeof e.error === 'object' && 'message' in e.error) {
      const m = (e.error as { message?: unknown }).message;
      if (typeof m === 'string' && m.length) {
        return `${e.status}: ${m}`;
      }
    }
    if (e.error && typeof e.error === 'object') {
      try {
        const s = JSON.stringify(e.error);
        if (s !== '{}' && s.length < 500) {
          return `${e.status}: ${s}`;
        }
      } catch {
        /* ignore */
      }
    }
    const tail = e.statusText?.trim() || e.message || '';
    return tail ? `${e.status} ${tail}` : String(e.status);
  }
  if (e instanceof Error) {
    return e.message;
  }
  return String(e);
}
