import { ResolvedThemeMode, ThemePreset } from '../models/theme.models';

/**
 * Semantic design tokens for the tracker-pg UI.
 * Values for the OpenAI preset mirror platform.openai.com (Home dashboard).
 */
export interface AppThemeTokens {
  fontDisplay: string;
  fontBody: string;

  bg: string;
  bgGradient: string;
  surface: string;
  surfaceMuted: string;
  surfaceTint: string;
  surfaceHover: string;
  /** Left navigation rail (OpenAI sidebar). */
  surfaceSidebar: string;
  /** Active nav item pill background. */
  surfaceNavActive: string;

  text: string;
  textMuted: string;
  textSubtle: string;

  border: string;
  borderStrong: string;

  accent: string;
  accentSoft: string;
  accentOn: string;

  /** Teal progress / positive metrics (#10a37f on platform). */
  colorSuccess: string;
  /** Gold “Add credits” CTA (~#b08d07). */
  colorWarning: string;
  /** Magenta usage chart accent. */
  colorChartSecondary: string;
  /** Pastel hero / promo card gradient. */
  heroGradient: string;

  toolbarBg: string;
  toolbarBorder: string;
  focusRing: string;

  shellMaxWidth: string;
  frameShadow: string;
  sidebarWidth: string;

  radiusLg: string;
  radius: string;
  radiusSm: string;
  radiusPill: string;

  shadowSm: string;
  shadowMd: string;

  tableStripe: string;

  calStrength: string;
  calWeight: string;
  calBoth: string;

  /** PWA / mobile browser chrome color. */
  themeColor: string;
}

/** Maps {@link AppThemeTokens} keys to `--app-*` CSS custom properties. */
export function themeTokensToCssVars(tokens: AppThemeTokens): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [key, value] of Object.entries(tokens)) {
    const kebab = key.replace(/([A-Z])/g, '-$1').toLowerCase();
    out[`--app-${kebab}`] = value;
  }
  return out;
}

export function getThemeTokens(preset: ThemePreset, mode: ResolvedThemeMode): AppThemeTokens {
  return THEME_TOKEN_REGISTRY[preset][mode];
}

/** Phoenix — deep slate sidebar, soft sage accent, warm neutral canvas. */
const PHOENIX_LIGHT: AppThemeTokens = {
  fontDisplay: "'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
  fontBody: "'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",

  bg: '#eef1ef',
  bgGradient: 'linear-gradient(165deg, #eef1ef 0%, #e8ece9 48%, #eef0ed 100%)',
  surface: '#ffffff',
  surfaceMuted: '#f3f5f4',
  surfaceTint: '#f8faf9',
  surfaceHover: '#e4e9e6',
  surfaceSidebar: '#1e2933',
  surfaceNavActive: 'rgba(134, 168, 143, 0.22)',

  text: '#1a2421',
  textMuted: '#5c6b65',
  textSubtle: '#7a8a83',

  border: '#d5ddd8',
  borderStrong: '#bcc9c2',

  accent: '#4a7c59',
  accentSoft: 'rgba(74, 124, 89, 0.12)',
  accentOn: '#ffffff',

  colorSuccess: '#3d8b63',
  colorWarning: '#b8860b',
  colorChartSecondary: '#5b8a72',
  heroGradient:
    'linear-gradient(135deg, rgba(74, 124, 89, 0.18) 0%, rgba(134, 168, 143, 0.22) 45%, rgba(238, 241, 239, 0.9) 100%)',

  toolbarBg: '#eef1ef',
  toolbarBorder: '#d5ddd8',
  focusRing: 'rgba(74, 124, 89, 0.4)',

  shellMaxWidth: 'none',
  frameShadow: 'none',
  sidebarWidth: '248px',

  radiusLg: '12px',
  radius: '8px',
  radiusSm: '6px',
  radiusPill: '9999px',

  shadowSm: '0 1px 2px rgba(26, 36, 33, 0.05)',
  shadowMd: '0 2px 8px rgba(26, 36, 33, 0.07)',

  tableStripe: 'rgba(74, 124, 89, 0.04)',

  calStrength: 'rgba(74, 124, 89, 0.16)',
  calWeight: 'rgba(91, 138, 114, 0.14)',
  calBoth: 'rgba(30, 41, 51, 0.08)',

  themeColor: '#1e2933',
};

const PHOENIX_DARK: AppThemeTokens = {
  ...PHOENIX_LIGHT,
  bg: '#121816',
  bgGradient: 'linear-gradient(165deg, #0f1412 0%, #141a18 50%, #121816 100%)',
  surface: '#1a2220',
  surfaceMuted: '#222b28',
  surfaceTint: '#1e2623',
  surfaceHover: '#2a3531',
  surfaceSidebar: '#0f1412',
  surfaceNavActive: 'rgba(134, 168, 143, 0.18)',

  text: '#e8eee9',
  textMuted: '#a3b0a8',
  textSubtle: '#7d8d85',

  border: 'rgba(163, 176, 168, 0.14)',
  borderStrong: 'rgba(163, 176, 168, 0.22)',

  accent: '#86a88f',
  accentSoft: 'rgba(134, 168, 143, 0.16)',
  accentOn: '#0f1412',

  colorSuccess: '#6db88a',
  colorWarning: '#d4af37',
  colorChartSecondary: '#86a88f',
  heroGradient:
    'linear-gradient(135deg, rgba(30, 41, 51, 0.65) 0%, rgba(74, 124, 89, 0.25) 55%, rgba(18, 24, 22, 0.95) 100%)',

  toolbarBg: '#121816',
  toolbarBorder: 'rgba(163, 176, 168, 0.14)',
  focusRing: 'rgba(134, 168, 143, 0.45)',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.35)',
  shadowMd: '0 4px 14px rgba(0, 0, 0, 0.4)',

  tableStripe: 'rgba(134, 168, 143, 0.05)',

  calStrength: 'rgba(134, 168, 143, 0.22)',
  calWeight: 'rgba(109, 184, 138, 0.18)',
  calBoth: 'rgba(232, 238, 233, 0.08)',

  themeColor: '#0f1412',
};

/** platform.openai.com — light dashboard */
const OPENAI_LIGHT: AppThemeTokens = {
  fontDisplay: "'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
  fontBody: "'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",

  bg: '#f7f7f8',
  bgGradient: '#f7f7f8',
  surface: '#ffffff',
  surfaceMuted: '#f7f7f8',
  surfaceTint: '#ffffff',
  surfaceHover: '#ececed',
  surfaceSidebar: '#f2f2f2',
  surfaceNavActive: '#ececec',

  text: '#1a1a1a',
  textMuted: '#6e6e73',
  textSubtle: '#8e8e93',

  border: '#e5e5e5',
  borderStrong: '#d1d1d6',

  accent: '#1a1a1a',
  accentSoft: 'rgba(26, 26, 26, 0.06)',
  accentOn: '#ffffff',

  colorSuccess: '#10a37f',
  colorWarning: '#b08d07',
  colorChartSecondary: '#c026d3',
  heroGradient:
    'linear-gradient(135deg, rgba(196, 181, 253, 0.45) 0%, rgba(251, 207, 232, 0.4) 40%, rgba(254, 240, 138, 0.35) 100%)',

  toolbarBg: '#f7f7f8',
  toolbarBorder: '#e5e5e5',
  focusRing: 'rgba(26, 26, 26, 0.35)',

  shellMaxWidth: 'none',
  frameShadow: 'none',
  sidebarWidth: '240px',

  radiusLg: '12px',
  radius: '8px',
  radiusSm: '6px',
  radiusPill: '9999px',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.04)',
  shadowMd: '0 1px 3px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04)',

  tableStripe: 'rgba(0, 0, 0, 0.02)',

  calStrength: 'rgba(16, 163, 127, 0.14)',
  calWeight: 'rgba(192, 38, 211, 0.12)',
  calBoth: 'rgba(26, 26, 26, 0.08)',

  themeColor: '#f7f7f8',
};

/** platform.openai.com — dark (developer console). */
const OPENAI_DARK: AppThemeTokens = {
  ...OPENAI_LIGHT,
  bg: '#0d0d0d',
  bgGradient: '#0d0d0d',
  surface: '#171717',
  surfaceMuted: '#212121',
  surfaceTint: '#1a1a1a',
  surfaceHover: '#2a2a2a',
  surfaceSidebar: '#0d0d0d',
  surfaceNavActive: '#2a2a2a',

  text: '#ececec',
  textMuted: '#b4b4b4',
  textSubtle: '#8e8e8e',

  border: 'rgba(255, 255, 255, 0.1)',
  borderStrong: 'rgba(255, 255, 255, 0.16)',

  accent: '#ffffff',
  accentSoft: 'rgba(255, 255, 255, 0.1)',
  accentOn: '#0d0d0d',

  colorSuccess: '#10a37f',
  colorWarning: '#d4a017',
  colorChartSecondary: '#e879f9',
  heroGradient:
    'linear-gradient(135deg, rgba(88, 28, 135, 0.35) 0%, rgba(157, 23, 77, 0.25) 50%, rgba(113, 63, 18, 0.2) 100%)',

  toolbarBg: '#0d0d0d',
  toolbarBorder: 'rgba(255, 255, 255, 0.1)',
  focusRing: 'rgba(255, 255, 255, 0.45)',

  shadowSm: 'none',
  shadowMd: 'rgba(0, 0, 0, 0.45) 0px 4px 12px 0px',

  tableStripe: 'rgba(255, 255, 255, 0.03)',

  calStrength: 'rgba(16, 163, 127, 0.22)',
  calWeight: 'rgba(232, 121, 249, 0.18)',
  calBoth: 'rgba(255, 255, 255, 0.1)',

  themeColor: '#0d0d0d',
};

/** Original tracker-pg look. */
const CLASSIC_LIGHT: AppThemeTokens = {
  fontDisplay: "'Plus Jakarta Sans', 'Segoe UI', system-ui, sans-serif",
  fontBody: "'Plus Jakarta Sans', 'Roboto', 'Helvetica Neue', sans-serif",

  bg: '#e7eff0',
  bgGradient: 'linear-gradient(165deg, #e6eff2 0%, #edf4f5 42%, #e9f0f2 100%)',
  surface: '#ffffff',
  surfaceMuted: '#f5f7fb',
  surfaceTint: '#f0f4fc',
  surfaceHover: 'rgba(255, 255, 255, 0.65)',
  surfaceSidebar: '#e6eff2',
  surfaceNavActive: 'rgba(15, 109, 115, 0.12)',

  text: '#0f172a',
  textMuted: '#64748b',
  textSubtle: '#94a3b8',

  border: 'rgba(15, 23, 42, 0.08)',
  borderStrong: 'rgba(15, 23, 42, 0.12)',

  accent: '#0f6d73',
  accentSoft: 'rgba(15, 109, 115, 0.12)',
  accentOn: '#ffffff',

  colorSuccess: '#10b981',
  colorWarning: '#f59e0b',
  colorChartSecondary: '#6366f1',
  heroGradient: 'linear-gradient(135deg, rgba(15, 109, 115, 0.15) 0%, rgba(37, 99, 235, 0.1) 100%)',

  toolbarBg: 'rgba(255, 255, 255, 0.82)',
  toolbarBorder: 'rgba(15, 23, 42, 0.08)',
  focusRing: 'rgba(15, 109, 115, 0.45)',

  shellMaxWidth: '1200px',
  frameShadow: '0 2px 8px rgba(15, 23, 42, 0.06), 0 8px 24px rgba(15, 23, 42, 0.04)',
  sidebarWidth: '240px',

  radiusLg: '16px',
  radius: '12px',
  radiusSm: '10px',
  radiusPill: '9999px',

  shadowSm: '0 1px 2px rgba(15, 23, 42, 0.05)',
  shadowMd: '0 2px 8px rgba(15, 23, 42, 0.06), 0 8px 24px rgba(15, 23, 42, 0.04)',

  tableStripe: 'rgba(248, 250, 252, 0.65)',

  calStrength: 'rgba(99, 102, 241, 0.14)',
  calWeight: 'rgba(16, 185, 129, 0.14)',
  calBoth: 'rgba(139, 92, 246, 0.16)',

  themeColor: '#0f6d73',
};

const CLASSIC_DARK: AppThemeTokens = {
  ...CLASSIC_LIGHT,
  bg: '#0f1419',
  bgGradient: 'linear-gradient(165deg, #0c1117 0%, #111820 42%, #0e151c 100%)',
  surface: '#1a222d',
  surfaceMuted: '#232d3b',
  surfaceTint: '#1e2834',
  surfaceHover: 'rgba(255, 255, 255, 0.06)',
  surfaceSidebar: '#0f1419',
  surfaceNavActive: 'rgba(45, 212, 191, 0.14)',

  text: '#e2e8f0',
  textMuted: '#94a3b8',
  textSubtle: '#64748b',

  border: 'rgba(148, 163, 184, 0.14)',
  borderStrong: 'rgba(148, 163, 184, 0.22)',

  accent: '#2dd4bf',
  accentSoft: 'rgba(45, 212, 191, 0.14)',
  accentOn: '#042f2e',

  toolbarBg: 'rgba(15, 20, 25, 0.88)',
  toolbarBorder: 'rgba(148, 163, 184, 0.14)',
  focusRing: 'rgba(45, 212, 191, 0.45)',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.35)',
  shadowMd: '0 2px 8px rgba(0, 0, 0, 0.4), 0 8px 24px rgba(0, 0, 0, 0.25)',

  tableStripe: 'rgba(255, 255, 255, 0.03)',

  calStrength: 'rgba(129, 140, 248, 0.2)',
  calWeight: 'rgba(52, 211, 153, 0.18)',
  calBoth: 'rgba(167, 139, 250, 0.2)',

  themeColor: '#0b3d40',
};

export const THEME_TOKEN_REGISTRY: Record<
  ThemePreset,
  Record<ResolvedThemeMode, AppThemeTokens>
> = {
  phoenix: {
    light: PHOENIX_LIGHT,
    dark: PHOENIX_DARK,
  },
  openai: {
    light: OPENAI_LIGHT,
    dark: OPENAI_DARK,
  },
  classic: {
    light: CLASSIC_LIGHT,
    dark: CLASSIC_DARK,
  },
};

/** Boot-time defaults (matches {@link DEFAULT_THEME_CONFIG} + system fallback to light). */
export const THEME_BOOT_TOKENS = PHOENIX_LIGHT;
