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

export interface PredictHourRollup extends PredictPeriodTotals {
  key: string;
  label: string;
}

export interface PredictDayRollup extends PredictPeriodTotals {
  key: string;
  label: string;
  hours: PredictHourRollup[];
}

export interface PredictMonthRollup extends PredictPeriodTotals {
  key: string;
  label: string;
  days: PredictDayRollup[];
}

export interface PredictYearRollup extends PredictPeriodTotals {
  year: number;
  months: PredictMonthRollup[];
}

type HourBucket = Map<number, RhPredictCloseDto[]>;
type DayBucket = Map<number, HourBucket>;
type MonthBucket = Map<number, DayBucket>;

export function rollupPredictCloses(closes: RhPredictCloseDto[]): PredictYearRollup[] {
  const byYear = new Map<number, MonthBucket>();
  for (const row of closes) {
    const closed = new Date(row.closedAt);
    if (Number.isNaN(closed.getTime())) {
      continue;
    }
    const year = closed.getFullYear();
    const month = closed.getMonth();
    const day = closed.getDate();
    const hour = closed.getHours();
    let months = byYear.get(year);
    if (!months) {
      months = new Map();
      byYear.set(year, months);
    }
    let days = months.get(month);
    if (!days) {
      days = new Map();
      months.set(month, days);
    }
    let hours = days.get(day);
    if (!hours) {
      hours = new Map();
      days.set(day, hours);
    }
    const rows = hours.get(hour) ?? [];
    rows.push(row);
    hours.set(hour, rows);
  }

  return [...byYear.keys()]
    .sort((a, b) => b - a)
    .map((year) => {
      const months = [...(byYear.get(year)?.entries() ?? [])]
        .sort((a, b) => b[0] - a[0])
        .map(([month, days]) => rollupMonth(year, month, days));
      return { year, months, ...sumNestedYear(byYear.get(year)) };
    });
}

function rollupMonth(year: number, month: number, days: DayBucket): PredictMonthRollup {
  const dayRollups = [...days.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([day, hours]) => rollupDay(year, month, day, hours));
  return {
    key: `${year}-${month}`,
    label: new Date(year, month, 1).toLocaleString('en-US', { month: 'long' }),
    days: dayRollups,
    ...sumPredictCloses(flattenHours(days)),
  };
}

function rollupDay(year: number, month: number, day: number, hours: HourBucket): PredictDayRollup {
  const hourRollups = [...hours.entries()]
    .sort((a, b) => b[0] - a[0])
    .map(([hour, rows]) => ({
      key: `${year}-${month}-${day}-${hour}`,
      label: new Date(year, month, day, hour).toLocaleString('en-US', { hour: 'numeric' }),
      ...sumPredictCloses(rows),
    }));
  return {
    key: `${year}-${month}-${day}`,
    label: new Date(year, month, day).toLocaleString('en-US', { month: 'short', day: 'numeric' }),
    hours: hourRollups,
    ...sumPredictCloses([...hours.values()].flat()),
  };
}

function flattenHours(days: DayBucket): RhPredictCloseDto[] {
  return [...days.values()].flatMap((hours) => [...hours.values()].flat());
}

function sumNestedYear(months: MonthBucket | undefined): PredictPeriodTotals {
  if (!months) {
    return { closes: 0, invested: 0, payback: 0, returned: 0 };
  }
  return sumPredictCloses([...months.values()].flatMap((days) => flattenHours(days)));
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
