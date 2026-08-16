/** Visual preset — maps to token bundles in theme-tokens.config. */
export type ThemePreset =
  | 'phoenix'
  | 'openai'
  | 'classic'
  | 'aura'
  | 'aether'
  | 'lumen'
  | 'obsidian'
  | 'velvet'
  | 'atelier'
  | 'solace'
  | 'eclipse'
  | 'opal'
  | 'archive'
  | 'ember';

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
  {
    id: 'aether',
    label: 'Aether',
    description: 'Airy sky canvas with cool steel-blue accents',
  },
  {
    id: 'lumen',
    label: 'Lumen',
    description: 'High-key cream light with warm gold highlights',
  },
  {
    id: 'obsidian',
    label: 'Obsidian',
    description: 'Sharp ink contrast on a volcanic glass field',
  },
  {
    id: 'velvet',
    label: 'Velvet',
    description: 'Deep plum and wine with a tactile hush',
  },
  {
    id: 'atelier',
    label: 'Atelier',
    description: 'Studio paper, warm gray, and charcoal ink',
  },
  {
    id: 'solace',
    label: 'Solace',
    description: 'Quiet stone and olive for an unhurried read',
  },
  {
    id: 'eclipse',
    label: 'Eclipse',
    description: 'Near-void field with a thin cream corona',
  },
  {
    id: 'opal',
    label: 'Opal',
    description: 'Iridescent mint, rose, and sky pastels',
  },
  {
    id: 'archive',
    label: 'Archive',
    description: 'Library parchment with sepia and serif titles',
  },
  {
    id: 'ember',
    label: 'Ember',
    description: 'Charcoal coals with a warm orange glow',
  },
];

export const THEME_MODES: ThemeModeMeta[] = [
  { id: 'system', label: 'System' },
  { id: 'light', label: 'Light' },
  { id: 'dark', label: 'Dark' },
];

const PRESET_IDS = new Set<ThemePreset>(THEME_PRESETS.map((p) => p.id));

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
