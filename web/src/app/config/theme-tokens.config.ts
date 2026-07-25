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

/**
 * Aura — soft side illumination: warm peach/rose bloom from the upper-right,
 * cooling into lavender mist (tab-switcher / frosted-glass atmosphere).
 */
const AURA_LIGHT: AppThemeTokens = {
  fontDisplay: "'SF Pro Display', 'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",
  fontBody: "'SF Pro Text', 'Inter', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif",

  bg: '#ebe6ef',
  bgGradient: [
    'radial-gradient(ellipse 90% 75% at 100% -5%, rgba(255, 198, 168, 0.95) 0%, transparent 58%)',
    'radial-gradient(ellipse 70% 55% at 92% 18%, rgba(244, 170, 186, 0.55) 0%, transparent 52%)',
    'radial-gradient(ellipse 55% 70% at -5% 95%, rgba(168, 162, 210, 0.42) 0%, transparent 50%)',
    'radial-gradient(ellipse 40% 35% at 40% 60%, rgba(255, 255, 255, 0.35) 0%, transparent 60%)',
    'linear-gradient(152deg, #e4e0eb 0%, #ece6ea 38%, #f3ebe6 72%, #efe8e4 100%)',
  ].join(', '),
  surface: 'rgba(255, 255, 255, 0.72)',
  surfaceMuted: 'rgba(255, 250, 248, 0.55)',
  surfaceTint: 'rgba(255, 255, 255, 0.45)',
  surfaceHover: 'rgba(255, 255, 255, 0.88)',
  surfaceSidebar: 'rgba(255, 252, 250, 0.55)',
  surfaceNavActive: 'rgba(255, 255, 255, 0.78)',

  text: '#2a2430',
  textMuted: '#6b6270',
  textSubtle: '#918898',

  border: 'rgba(70, 55, 75, 0.1)',
  borderStrong: 'rgba(70, 55, 75, 0.16)',

  accent: '#8b5a6b',
  accentSoft: 'rgba(139, 90, 107, 0.12)',
  accentOn: '#ffffff',

  colorSuccess: '#3d8b6e',
  colorWarning: '#c4893a',
  colorChartSecondary: '#7a6bb0',
  heroGradient:
    'linear-gradient(125deg, rgba(255, 186, 160, 0.55) 0%, rgba(236, 170, 190, 0.4) 42%, rgba(180, 170, 220, 0.35) 100%)',

  toolbarBg: 'rgba(255, 252, 250, 0.55)',
  toolbarBorder: 'rgba(70, 55, 75, 0.1)',
  focusRing: 'rgba(139, 90, 107, 0.4)',

  shellMaxWidth: 'none',
  frameShadow: 'none',
  sidebarWidth: '248px',

  radiusLg: '16px',
  radius: '12px',
  radiusSm: '8px',
  radiusPill: '9999px',

  shadowSm: '0 1px 2px rgba(60, 40, 55, 0.05)',
  shadowMd: '0 8px 28px rgba(80, 50, 70, 0.08)',

  tableStripe: 'rgba(255, 255, 255, 0.35)',

  calStrength: 'rgba(122, 107, 176, 0.16)',
  calWeight: 'rgba(61, 139, 110, 0.14)',
  calBoth: 'rgba(139, 90, 107, 0.14)',

  themeColor: '#e8c4b8',
};

const AURA_DARK: AppThemeTokens = {
  ...AURA_LIGHT,
  bg: '#16121a',
  bgGradient: [
    'radial-gradient(ellipse 85% 70% at 100% -8%, rgba(180, 90, 70, 0.55) 0%, transparent 55%)',
    'radial-gradient(ellipse 65% 50% at 95% 20%, rgba(140, 70, 100, 0.4) 0%, transparent 50%)',
    'radial-gradient(ellipse 50% 65% at -8% 100%, rgba(70, 60, 120, 0.45) 0%, transparent 48%)',
    'linear-gradient(155deg, #120f16 0%, #1a1520 45%, #18131c 100%)',
  ].join(', '),
  surface: 'rgba(30, 24, 36, 0.78)',
  surfaceMuted: 'rgba(38, 30, 46, 0.72)',
  surfaceTint: 'rgba(36, 28, 44, 0.65)',
  surfaceHover: 'rgba(48, 38, 58, 0.9)',
  surfaceSidebar: 'rgba(18, 14, 24, 0.72)',
  surfaceNavActive: 'rgba(255, 186, 160, 0.12)',

  text: '#f0e8ef',
  textMuted: '#b5a8b8',
  textSubtle: '#8a7e90',

  border: 'rgba(240, 220, 230, 0.1)',
  borderStrong: 'rgba(240, 220, 230, 0.16)',

  accent: '#e8a090',
  accentSoft: 'rgba(232, 160, 144, 0.14)',
  accentOn: '#1a1218',

  colorSuccess: '#6db88a',
  colorWarning: '#e0b060',
  colorChartSecondary: '#b8a0e0',
  heroGradient:
    'linear-gradient(125deg, rgba(180, 90, 70, 0.35) 0%, rgba(120, 60, 100, 0.3) 50%, rgba(60, 50, 110, 0.35) 100%)',

  toolbarBg: 'rgba(18, 14, 24, 0.65)',
  toolbarBorder: 'rgba(240, 220, 230, 0.1)',
  focusRing: 'rgba(232, 160, 144, 0.45)',

  shadowSm: '0 1px 2px rgba(0, 0, 0, 0.4)',
  shadowMd: '0 10px 32px rgba(0, 0, 0, 0.45)',

  tableStripe: 'rgba(255, 255, 255, 0.03)',

  calStrength: 'rgba(184, 160, 224, 0.22)',
  calWeight: 'rgba(109, 184, 138, 0.18)',
  calBoth: 'rgba(232, 160, 144, 0.16)',

  themeColor: '#16121a',
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
  aura: {
    light: AURA_LIGHT,
    dark: AURA_DARK,
  },
};

/** Boot-time defaults (matches {@link DEFAULT_THEME_CONFIG} + system fallback to light). */
export const THEME_BOOT_TOKENS = PHOENIX_LIGHT;
