import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

const CHUNK_RELOAD_KEY = 'tracker_pg_chunk_reload_once';

function isLazyChunkLoadFailure(reason: unknown): boolean {
  const msg = reason instanceof Error ? reason.message : String(reason ?? '');
  return (
    msg.includes('dynamically imported module') ||
    msg.includes('Failed to fetch dynamically imported module') ||
    msg.includes('Loading chunk') ||
    msg.includes('ChunkLoadError')
  );
}

/** One hard reload after post-deploy stale bundles (pairs with nginx not serving HTML for missing .js). */
window.addEventListener('unhandledrejection', (event) => {
  if (!isLazyChunkLoadFailure(event.reason)) {
    return;
  }
  if (sessionStorage.getItem(CHUNK_RELOAD_KEY)) {
    return;
  }
  sessionStorage.setItem(CHUNK_RELOAD_KEY, '1');
  event.preventDefault();
  window.location.reload();
});

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
