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
  aboveTarget: boolean | null;
}

export interface Btc15HourDto {
  label: string;
  settled: number;
  above: number;
  aboveRate: number | null;
  windows: Btc15WindowDto[];
}

export interface Btc15DeskDto {
  date: string;
  settled: number;
  above: number;
  aboveRate: number | null;
  hours: Btc15HourDto[];
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
