/**
 * Management → Now: static roadmap copy. Edit this file to update the page—no API or database.
 * Keep cards short; use milestone for quarters, months, or ISO dates.
 */
export interface NowRoadmapCard {
  readonly title: string;
  readonly body?: string;
  /** e.g. "Q3 2026", "May 2026", "2026-06-01" */
  readonly milestone?: string;
}

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
    title: 'Roadmap export',
    body: 'Optional PDF or Markdown export of this board for sharing outside the app.',
    milestone: 'TBD',
  },
  {
    title: 'Notification digests',
    body: 'Weekly email summary of finance alerts and open management tasks when outbound mail is configured.',
    milestone: 'Q3 2026',
  },
  {
    title: 'Deeper mobile polish',
    body: 'Touch targets and document previews tuned for small screens across Finance and Management.',
    milestone: '2026',
  },
];

/** Actively in flight—adjust as reality changes. */
export const NOW_ROADMAP_ACTIVE: readonly NowRoadmapCard[] = [
  {
    title: 'Finance → Predicts',
    body: 'Community sentiment (e.g. StockTwits) scored with FinBERT; per-ticker attention and positivity trends.',
    milestone: 'In progress',
  },
  {
    title: 'Management → Documents',
    body: 'Member-scoped vault with metadata, preview, and download—living beside Tasks, Travel, and Work.',
    milestone: 'Shipped · iterating',
  },
  {
    title: 'Banking by institution type',
    body: 'Admin-defined types, institution links, and roll-ups in Reports → Finance → Banking.',
    milestone: 'In progress',
  },
];

/** Shipped highlights—trim or archive over time to keep the column readable. */
export const NOW_ROADMAP_DONE: readonly NowRoadmapCard[] = [
  {
    title: 'Welcome → Thought for today',
    body: 'Curated inspirational quotes on the home hero with shuffle control.',
    milestone: 'May 2026',
  },
  {
    title: 'Screeners (Trading)',
    body: 'NASDAQ-focused lists, 52-week momentum, and watchlist headline sweep.',
    milestone: '2026',
  },
  {
    title: 'Travel map + geocode',
    body: 'Trips, pins, optional Nominatim geocode, and place photos in Management.',
    milestone: '2026',
  },
  {
    title: 'Per-user Management vault',
    body: 'Tasks, work log, month notes, write-ups, accounts, and calendar scoped to the signed-in member.',
    milestone: '2026',
  },
];
