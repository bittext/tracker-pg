import { RobinhoodRhDailyTrackerDayDto } from '../../../models/finance.models';

/** One day’s FIFO sale tally used by the month money picture and spine. */
export interface RhDailySaleDay {
  gains: number;
  losses: number;
  net: number;
  count: number;
  symbols: string[];
}

/** Combined book, sale, and cash row for Daily Tracker overlays. */
export interface RhDailyMoneyPicturePoint {
  date: string;
  day: RobinhoodRhDailyTrackerDayDto | null;
  book: number | null;
  bookDelta: number | null;
  added: number;
  removed: number;
  sale: RhDailySaleDay;
  bookIfNoIo: number | null;
  x: number;
  bookY: number | null;
  noIoY: number | null;
  saleBarH: number;
  saleUp: boolean;
  addedBarH: number;
  removedBarH: number;
}

export interface RhDailyMoneyPicture {
  points: RhDailyMoneyPicturePoint[];
  eventRows: RhDailyMoneyPicturePoint[];
  latestBook: number | null;
  latestIfNoIo: number | null;
  yearIfNoIo: number | null;
  saleGains: number;
  saleLosses: number;
  saleNet: number;
  sellDays: number;
  added: number;
  removed: number;
  bookPath: string;
  noIoPath: string;
}
