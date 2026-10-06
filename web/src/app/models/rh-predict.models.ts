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

export interface RhPredictDeskDto {
  lastSyncedAt: string | null;
  openValue: number | null;
  realizedAll: number;
  realizedWeek: number;
  closeCount: number;
  warnings: string[];
  closes: RhPredictCloseDto[];
}
