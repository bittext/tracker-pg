/**
 * Web UI release label. Keep in sync with `web/package.json` version; align major/minor with `server/pom.xml` when you cut a release.
 */
export const WEB_RELEASE_VERSION = '13.0.5';

/** Format API `/api/version` buildTime for the header under the version. */
export function formatReleaseUpdatedAt(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return null;
  }
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}
