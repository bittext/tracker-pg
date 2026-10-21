export interface RhPredictCloseDto {
  id: number;
  accountSuffix: string;
  accountLabel: string;
  closedAt: string;
  quantity: number;
  price: number;
  realized: number;
  label: string | null;
}

export interface Btc15WindowDto {
  ticker: string;
  label: string;
  windowStart: string;
  windowEnd: string;
  target: number | null;
  startPrice: number | null;
  endPrice: number | null;
  startGap: number | null;
  endGap: number | null;
  /** Settled Kalshi outcome only; null while unsettled. */
  aboveTarget: boolean | null;
  /** Live Coinbase print while unsettled. */
  currentPrice: number | null;
  currentGap: number | null;
  /** Provisional above/below from the live print. */
  liveAbove: boolean | null;
  unsettled: boolean;
}

export interface Btc15HourDto {
  label: string;
  settled: number;
  above: number;
  aboveRate: number | null;
  windows: Btc15WindowDto[];
}

export interface Btc15SharpDto {
  label: string;
  direction: 'up' | 'down' | string;
  lastFive: number;
  firstTen: number;
  reversed: boolean;
}

export interface Btc15DeskDto {
  date: string;
  settled: number;
  above: number;
  aboveRate: number | null;
  hours: Btc15HourDto[];
  sharpUp: number;
  sharpDown: number;
  sharpThreshold: number;
  sharps: Btc15SharpDto[];
}

export interface Btc15StreakRunDto {
  date: string;
  dayLabel: string;
  length: number;
  followed: number;
  fromLabel: string;
  toLabel: string;
}

export interface Btc15StreakDayDto {
  date: string;
  label: string;
  windows: number;
  below: number;
  followed: number;
  consecutiveRuns: number;
  singles: number;
  run2: number;
  run3: number;
  run4: number;
  run5: number;
  run6: number;
  run7: number;
}

export interface Btc15StreakDeskDto {
  series: string;
  title: string;
  from: string;
  to: string;
  windows: number;
  below: number;
  above: number;
  followed: number;
  consecutiveRuns: number;
  singles: number;
  run2: number;
  run3: number;
  run4: number;
  run5: number;
  run6: number;
  run7: number;
  longest: number;
  belowRate: number | null;
  followRate: number | null;
  pattern: 'coin-flip' | 'bunched' | 'reverses' | 'empty' | string;
  headline: string;
  notes: string[];
  days: Btc15StreakDayDto[];
  runs: Btc15StreakRunDto[];
}

export interface RhPredictNoteDto {
  id: number;
  body: string;
  createdAt: string;
}

export interface RhPredictFeedbackDto {
  headline: string;
  points: string[];
  watch: string;
  updatedAt: string;
}

export interface RhPredictNotesDeskDto {
  notes: RhPredictNoteDto[];
  feedback: RhPredictFeedbackDto | null;
}

export interface RhPredictDeskDto {
  lastSyncedAt: string | null;
  openValue: number | null;
  openCost: number | null;
  realizedAll: number;
  realizedWeek: number;
  closeCount: number;
  warnings: string[];
  closes: RhPredictCloseDto[];
}
