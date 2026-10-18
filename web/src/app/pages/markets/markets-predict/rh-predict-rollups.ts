import { RhPredictCloseDto } from '../../../models/rh-predict.models';

/** Cash received when the contract closed: contracts × exit price. */
export function predictPayback(row: Pick<RhPredictCloseDto, 'quantity' | 'price'>): number {
  return row.quantity * row.price;
}

/** Cash put in, implied by the exit and the reported P&L. */
export function predictInvested(row: Pick<RhPredictCloseDto, 'quantity' | 'price' | 'realized'>): number {
  return predictPayback(row) - row.realized;
}

export interface PredictPeriodTotals {
  closes: number;
  invested: number;
  payback: number;
  returned: number;
}

export interface PredictMonthRollup extends PredictPeriodTotals {
  key: string;
  label: string;
}

export interface PredictYearRollup extends PredictPeriodTotals {
  year: number;
  months: PredictMonthRollup[];
}

export function rollupPredictCloses(closes: RhPredictCloseDto[]): PredictYearRollup[] {
  const byYear = new Map<number, Map<number, RhPredictCloseDto[]>>();
  for (const row of closes) {
    const closed = new Date(row.closedAt);
    if (Number.isNaN(closed.getTime())) {
      continue;
    }
    const year = closed.getFullYear();
    const month = closed.getMonth();
    let months = byYear.get(year);
    if (!months) {
      months = new Map();
      byYear.set(year, months);
    }
    const rows = months.get(month) ?? [];
    rows.push(row);
    months.set(month, rows);
  }

  return [...byYear.keys()]
    .sort((a, b) => b - a)
    .map((year) => {
      const months = [...(byYear.get(year)?.entries() ?? [])]
        .sort((a, b) => b[0] - a[0])
        .map(([month, rows]) => ({
          key: `${year}-${month}`,
          label: new Date(year, month, 1).toLocaleString('en-US', { month: 'long' }),
          ...sumPredictCloses(rows),
        }));
      const yearRows = [...(byYear.get(year)?.values() ?? [])].flat();
      return { year, months, ...sumPredictCloses(yearRows) };
    });
}

export function sumPredictCloses(closes: RhPredictCloseDto[]): PredictPeriodTotals {
  let invested = 0;
  let payback = 0;
  let returned = 0;
  for (const row of closes) {
    invested += predictInvested(row);
    payback += predictPayback(row);
    returned += row.realized;
  }
  return { closes: closes.length, invested, payback, returned };
}
