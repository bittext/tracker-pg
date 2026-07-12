/** Visual preset — maps to token bundles in src/styles/themes/. */
export type ThemePreset = 'phoenix' | 'openai' | 'classic';

/** Color scheme; `system` follows OS preference. */
export type ThemeMode = 'light' | 'dark' | 'system';

/** Light or dark after resolving `system` against OS preference. */
export type ResolvedThemeMode = 'light' | 'dark';

export interface ThemeConfig {
  preset: ThemePreset;
  mode: ThemeMode;
}

export const THEME_STORAGE_KEY = 'tracker.theme.v1';

export const DEFAULT_THEME_CONFIG: ThemeConfig = {
  preset: 'phoenix',
  mode: 'system',
};

export interface ThemePresetMeta {
  id: ThemePreset;
  label: string;
  description: string;
}

export interface ThemeModeMeta {
  id: ThemeMode;
  label: string;
}

export const THEME_PRESETS: ThemePresetMeta[] = [
  {
    id: 'phoenix',
    label: 'Phoenix',
    description: 'Deep slate sidebar, soft sage accent — Life & Markets default',
  },
  {
    id: 'openai',
    label: 'OpenAI Platform',
    description: 'platform.openai.com — sidebar nav, #f7f7f8 canvas, teal & gold accents',
  },
  {
    id: 'classic',
    label: 'Classic',
    description: 'Original teal accent with soft gradients',
  },
];

export const THEME_MODES: ThemeModeMeta[] = [
  { id: 'system', label: 'System' },
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
];

export function parseThemeConfig(raw: unknown): ThemeConfig {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_THEME_CONFIG };
  }
  const o = raw as Record<string, unknown>;
  const preset =
    o['preset'] === 'classic' ? 'classic' : o['preset'] === 'openai' ? 'openai' : 'phoenix';
  const mode =
    o['mode'] === 'light' || o['mode'] === 'dark' || o['mode'] === 'system' ? o['mode'] : 'system';
  return { preset, mode };
}
