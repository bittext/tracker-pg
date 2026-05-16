import type { ManagementNowCardType } from '../../../models/management.models';
import { NOW_CARD_TYPE_META, NOW_ROADMAP_CARD_TYPES, type NowRoadmapCardTypeMeta } from './management-now-data';

export function mergeNowCardTypeMeta(apiTypes: ManagementNowCardType[]): Map<string, NowRoadmapCardTypeMeta> {
  const m = new Map<string, NowRoadmapCardTypeMeta>();
  for (const [slug, meta] of Object.entries(NOW_CARD_TYPE_META)) {
    m.set(slug, meta);
  }
  for (const row of apiTypes) {
    m.set(row.slug, {
      label: row.label,
      badge: row.badge,
      color: row.colorHex,
    });
  }
  return m;
}

export function fallbackNowCardTypeMeta(slug: string): NowRoadmapCardTypeMeta {
  return { label: slug, badge: slug, color: '#64748b' };
}

export function resolveNowCardTypeMeta(
  metaBySlug: Map<string, NowRoadmapCardTypeMeta>,
  slug: string,
): NowRoadmapCardTypeMeta {
  return metaBySlug.get(slug) ?? fallbackNowCardTypeMeta(slug);
}

/** Built-in slugs first (fixed order), then other API slugs A–Z. */
export function orderedNowCardTypeSlugs(apiTypes: ManagementNowCardType[]): string[] {
  const builtin = [...NOW_ROADMAP_CARD_TYPES] as string[];
  const extra = apiTypes.map((t) => t.slug).filter((s) => !builtin.includes(s));
  extra.sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
  return [...builtin, ...extra];
}
