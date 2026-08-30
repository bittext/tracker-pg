/** Sidebar grouping for Management write-ups that share an explicit topic group. */

export const WRITEUP_UNGROUPED_KEY = '__ungrouped__';

export interface WriteupTopicGroup<T extends { topic: string; updatedAt?: string }> {
  key: string;
  label: string;
  entries: T[];
  ungrouped: boolean;
  /** Manual group order (topicGroupRank of its entries). 0 for the synthetic "Ungrouped" bucket. */
  rank: number;
}

export function normalizeWriteupTopic(topic: string): string {
  return (topic || '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .replace(/[\s.,;:!?]+$/g, '');
}

export function writeupDraftFingerprint(draft: {
  year: number;
  topic: string;
  topicGroup?: string | null;
  highlight: string;
  body: string;
}): string {
  const topic = (draft.topic || '').trim() || 'Untitled';
  const group = (draft.topicGroup || '').trim();
  return `${draft.year}|${topic}|${group}|${draft.highlight || ''}|${draft.body || ''}`;
}

export function writeupDropListId(key: string): string {
  return `wu-drop-${key || WRITEUP_UNGROUPED_KEY}`;
}

/** Child title with the group name stripped so it is not repeated under the header. */
export function writeupChildLabel(topic: string, groupLabel: string): string {
  const t = (topic || '').trim();
  const g = (groupLabel || '').trim();
  if (!t) {
    return 'Overview';
  }
  if (!g) {
    return t;
  }
  if (normalizeWriteupTopic(t) === normalizeWriteupTopic(g)) {
    return 'Overview';
  }
  const escaped = g.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const rest = t.replace(new RegExp(`^${escaped}\\s+`, 'i'), '').trim();
  return rest || 'Overview';
}

function sortWriteupEntries<
  T extends { topicGroupSort?: number | null; updatedAt?: string; id?: number },
>(a: T, b: T): number {
  const as = a.topicGroupSort ?? 0;
  const bs = b.topicGroupSort ?? 0;
  if (as !== bs) {
    return as - bs;
  }
  const au = a.updatedAt || '';
  const bu = b.updatedAt || '';
  if (au !== bu) {
    return bu.localeCompare(au);
  }
  return (b.id ?? 0) - (a.id ?? 0);
}

export function groupWriteupsByRelatedTopic<
  T extends {
    topic: string;
    topicGroup?: string | null;
    topicGroupSort?: number | null;
    topicGroupRank?: number | null;
    updatedAt?: string;
    id?: number;
  },
>(rows: T[]): WriteupTopicGroup<T>[] {
  const buckets = new Map<string, { label: string; rank: number; entries: T[] }>();
  const ungrouped: T[] = [];

  for (const row of rows) {
    const raw = (row.topicGroup || '').trim();
    if (!raw) {
      ungrouped.push(row);
      continue;
    }
    const key = normalizeWriteupTopic(raw) || WRITEUP_UNGROUPED_KEY;
    if (key === WRITEUP_UNGROUPED_KEY) {
      ungrouped.push(row);
      continue;
    }
    const rank = row.topicGroupRank ?? 0;
    const existing = buckets.get(key);
    if (existing) {
      existing.entries.push(row);
    } else {
      buckets.set(key, { label: raw, rank, entries: [row] });
    }
  }

  const groups: WriteupTopicGroup<T>[] = [];
  for (const [key, bucket] of buckets) {
    bucket.entries.sort(sortWriteupEntries);
    groups.push({
      key,
      label: bucket.label,
      entries: bucket.entries,
      ungrouped: false,
      rank: bucket.rank,
    });
  }
  groups.sort((a, b) => {
    if (a.rank !== b.rank) {
      return a.rank - b.rank;
    }
    return a.label.localeCompare(b.label, undefined, { sensitivity: 'base' });
  });

  ungrouped.sort(sortWriteupEntries);
  groups.push({
    key: WRITEUP_UNGROUPED_KEY,
    label: 'Ungrouped',
    entries: ungrouped,
    ungrouped: true,
    rank: Number.MAX_SAFE_INTEGER,
  });
  return groups;
}
