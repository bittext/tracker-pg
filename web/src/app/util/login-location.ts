/** Client context sent with sign-in / sign-out for MFA trusted-location fingerprinting and audit display. */
export interface LoginLocationContext {
  locationFingerprintSource: string;
  locationLabel: string;
}

/** Stable browser signals for fingerprinting; audit label is IANA timezone only (e.g. Asia/Kolkata). */
export function buildLoginLocationContext(): LoginLocationContext {
  if (typeof navigator === 'undefined') {
    return { locationFingerprintSource: '-', locationLabel: '' };
  }

  const tz =
    typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone?.trim() || '' : '';
  const lang = navigator.language?.trim() || '';
  const platform = navigator.platform?.trim() || '';
  const screenSize =
    typeof screen !== 'undefined' ? `${screen.width}x${screen.height}` : '';

  const locationFingerprintSource = [tz, lang, platform, screenSize].filter(Boolean).join('|');

  return { locationFingerprintSource, locationLabel: tz };
}
