/**
 * Management → Now: static roadmap copy. Edit this file to update the page—no API or database.
 * Keep cards short; use milestone for quarters, months, or ISO dates.
 * Card `id` values are stable keys for localStorage (order / lane); do not rename casually.
 */
export const NOW_ROADMAP_CARD_TYPES = ['product', 'finance', 'platform', 'experience', 'content'] as const;
export type NowRoadmapCardType = (typeof NOW_ROADMAP_CARD_TYPES)[number];

export interface NowRoadmapCardTypeMeta {
  readonly label: string;
  /** Small badge text (lowercase). */
  readonly badge: string;
  /** Accent color for badge and chips. */
  readonly color: string;
}

export const NOW_CARD_TYPE_META: Record<NowRoadmapCardType, NowRoadmapCardTypeMeta> = {
  product: { label: 'Product', badge: 'product', color: '#6366f1' },
  finance: { label: 'Finance', badge: 'finance', color: '#0d9488' },
  platform: { label: 'Platform', badge: 'platform', color: '#7c3aed' },
  experience: { label: 'Experience', badge: 'experience', color: '#ea580c' },
  content: { label: 'Content', badge: 'content', color: '#db2777' },
};

export interface NowRoadmapCard {
  readonly id: string;
  readonly type: NowRoadmapCardType;
  readonly title: string;
  readonly body?: string;
  /** e.g. "Q3 2026", "May 2026", "2026-06-01" */
  readonly milestone?: string;
}

export type NowRoadmapLane = 'planned' | 'active' | 'done';

export const NOW_ROADMAP_META = {
  /** Shown in the hero line (human-readable). */
  lastUpdatedLabel: 'May 2026',
  /** Short positioning line under the title. */
  tagline:
    'A lightweight roadmap: what is coming, what we are building, and what recently shipped. Update the data file to keep this page honest with almost no upkeep.',
} as const;

/** Ideas and commitments not yet started. */
export const NOW_ROADMAP_PLANNED: readonly NowRoadmapCard[] = [
  {
    id: 'now-roadmap-export',
    type: 'product',
    title: 'Roadmap export',
    body: 'Optional PDF or Markdown export of this board for sharing outside the app.',
    milestone: 'TBD',
  },
  {
    id: 'now-notification-digests',
    type: 'product',
    title: 'Notification digests',
    body: 'Weekly email summary of finance alerts and open management tasks when outbound mail is configured.',
    milestone: 'Q3 2026',
  },
  {
    id: 'now-mobile-polish',
    type: 'experience',
    title: 'Deeper mobile polish',
    body: 'Touch targets and document previews tuned for small screens across Finance and Management.',
    milestone: '2026',
  },
];

/** Actively in flight—adjust as reality changes. */
export const NOW_ROADMAP_ACTIVE: readonly NowRoadmapCard[] = [
  {
    id: 'now-finance-predicts',
    type: 'finance',
    title: 'Finance → Predicts',
    body: 'Community sentiment (e.g. StockTwits) scored with FinBERT; per-ticker attention and positivity trends.',
    milestone: 'In progress',
  },
  {
    id: 'now-mgmt-documents',
    type: 'product',
    title: 'Management → Documents',
    body: 'Member-scoped vault with metadata, preview, and download—living beside Tasks, Travel, and Work.',
    milestone: 'Shipped · iterating',
  },
  {
    id: 'now-banking-inst-type',
    type: 'finance',
    title: 'Banking by institution type',
    body: 'Admin-defined types, institution links, and roll-ups in Reports → Finance → Banking.',
    milestone: 'In progress',
  },
];

/** Shipped highlights—trim or archive over time to keep the column readable. */
export const NOW_ROADMAP_DONE: readonly NowRoadmapCard[] = [
  {
    id: 'now-welcome-quotes',
    type: 'content',
    title: 'Welcome → Thought for today',
    body: 'Curated inspirational quotes on the home hero with shuffle control.',
    milestone: 'May 2026',
  },
  {
    id: 'now-trading-screeners',
    type: 'finance',
    title: 'Screeners (Trading)',
    body: 'NASDAQ-focused lists, 52-week momentum, and watchlist headline sweep.',
    milestone: '2026',
  },
  {
    id: 'now-travel-map',
    type: 'product',
    title: 'Travel map + geocode',
    body: 'Trips, pins, optional Nominatim geocode, and place photos in Management.',
    milestone: '2026',
  },
  {
    id: 'now-mgmt-vault',
    type: 'platform',
    title: 'Per-user Management vault',
    body: 'Tasks, work log, month notes, write-ups, accounts, and calendar scoped to the signed-in member.',
    milestone: '2026',
  },
];

/** Single list for reports / catalog lookups. */
export const NOW_ROADMAP_ALL: readonly NowRoadmapCard[] = [
  ...NOW_ROADMAP_PLANNED,
  ...NOW_ROADMAP_ACTIVE,
  ...NOW_ROADMAP_DONE,
];

export function nowRoadmapDefaultLane(cardId: string): NowRoadmapLane {
  if (NOW_ROADMAP_PLANNED.some((c) => c.id === cardId)) {
    return 'planned';
  }
  if (NOW_ROADMAP_ACTIVE.some((c) => c.id === cardId)) {
    return 'active';
  }
  if (NOW_ROADMAP_DONE.some((c) => c.id === cardId)) {
    return 'done';
  }
  return 'planned';
}

export function nowRoadmapCardById(): Map<string, NowRoadmapCard> {
  const m = new Map<string, NowRoadmapCard>();
  for (const c of NOW_ROADMAP_ALL) {
    m.set(c.id, c);
  }
  return m;
}
