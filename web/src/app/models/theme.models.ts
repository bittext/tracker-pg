/** Visual preset — maps to token bundles in theme-tokens.config. */
export type ThemePreset = 'phoenix' | 'openai' | 'classic' | 'aura';

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
    description: 'Deep slate sidebar with soft sage accents',
  },
  {
    id: 'openai',
    label: 'OpenAI',
    description: 'Platform canvas with teal and gold accents',
  },
  {
    id: 'classic',
    label: 'Classic',
    description: 'Original teal accent and soft gradients',
  },
  {
    id: 'aura',
    label: 'Aura',
    description: 'Warm peach bloom into cool lavender mist',
  },
];

export const THEME_MODES: ThemeModeMeta[] = [
  { id: 'system', label: 'System' },
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
];

const PRESET_IDS = new Set<ThemePreset>(['phoenix', 'openai', 'classic', 'aura']);

export function parseThemeConfig(raw: unknown): ThemeConfig {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_THEME_CONFIG };
  }
  const o = raw as Record<string, unknown>;
  const preset =
    typeof o['preset'] === 'string' && PRESET_IDS.has(o['preset'] as ThemePreset)
      ? (o['preset'] as ThemePreset)
      : 'phoenix';
  const mode =
    o['mode'] === 'light' || o['mode'] === 'dark' || o['mode'] === 'system' ? o['mode'] : 'system';
  return { preset, mode };
}
