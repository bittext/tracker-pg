/** Sidebar grouping for Management write-ups that share a topic family. */

export interface WriteupTopicGroup<T extends { topic: string; updatedAt?: string }> {
  key: string;
  label: string;
  entries: T[];
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
  highlight: string;
  body: string;
}): string {
  const topic = (draft.topic || '').trim() || 'Untitled';
  return `${draft.year}|${topic}|${draft.highlight || ''}|${draft.body || ''}`;
}

function topicStem(normalized: string, candidates: string[]): string {
  const matches = candidates.filter(
    (stem) => stem.length >= 2 && (normalized === stem || normalized.startsWith(`${stem} `)),
  );
  if (!matches.length) {
    return normalized || 'untitled';
  }
  return matches.reduce((shortest, next) => (next.length < shortest.length ? next : shortest));
}

export function groupWriteupsByRelatedTopic<T extends { topic: string; updatedAt?: string }>(
  rows: T[],
): WriteupTopicGroup<T>[] {
  const normalized = rows.map((row) => normalizeWriteupTopic(row.topic));
  const stems = [...new Set(normalized.filter(Boolean))].sort(
    (a, b) => a.length - b.length || a.localeCompare(b),
  );

  const buckets = new Map<string, T[]>();
  rows.forEach((row, i) => {
    const key = topicStem(normalized[i] || '', stems);
    const list = buckets.get(key) ?? [];
    list.push(row);
    buckets.set(key, list);
  });

  const groups: WriteupTopicGroup<T>[] = [];
  for (const [key, entries] of buckets) {
    entries.sort((a, b) => (b.updatedAt || '').localeCompare(a.updatedAt || ''));
    const shortest = [...entries].sort(
      (a, b) => (a.topic || '').trim().length - (b.topic || '').trim().length,
    )[0];
    groups.push({
      key,
      label: (shortest?.topic || '').trim() || 'Untitled',
      entries,
    });
  }
  groups.sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }));
  return groups;
}
