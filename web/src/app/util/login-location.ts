/** Client context sent with sign-in / sign-out for MFA trusted-location fingerprinting and audit display. */
export interface LoginLocationContext {
  locationFingerprintSource: string;
  locationLabel: string;
}

/** Stable browser signals + human-readable label (browser, OS, timezone). */
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
  const locationLabel = formatLocationLabel(navigator.userAgent, platform, tz, lang);

  return { locationFingerprintSource, locationLabel };
}

function formatLocationLabel(userAgent: string, platform: string, tz: string, lang: string): string {
  const browser = detectBrowser(userAgent);
  const os = detectOs(userAgent, platform);
  const locale = lang ? lang.replace('-', ' · ') : '';
  return [browser, os, tz, locale].filter(Boolean).join(' · ');
}

function detectBrowser(ua: string): string {
  if (/Edg\//i.test(ua)) {
    return 'Edge';
  }
  if (/Chrome\//i.test(ua) && !/Chromium/i.test(ua)) {
    return 'Chrome';
  }
  if (/Safari\//i.test(ua) && !/Chrome/i.test(ua)) {
    return 'Safari';
  }
  if (/Firefox\//i.test(ua)) {
    return 'Firefox';
  }
  return 'Browser';
}

function detectOs(ua: string, platform: string): string {
  if (/Windows NT/i.test(ua)) {
    return 'Windows';
  }
  if (/Mac OS X|Macintosh/i.test(ua)) {
    return 'macOS';
  }
  if (/Android/i.test(ua)) {
    return 'Android';
  }
  if (/iPhone|iPad|iPod/i.test(ua)) {
    return 'iOS';
  }
  if (/Linux/i.test(ua)) {
    return 'Linux';
  }
  return platform || '';
}
