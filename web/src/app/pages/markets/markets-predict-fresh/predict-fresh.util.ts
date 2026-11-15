import { RhPredictCloseDto } from '../../../models/rh-predict.models';
import {
  predictFees,
  predictInvested,
  predictNet,
  predictPayback,
} from '../markets-predict/rh-predict-rollups';

/** One close, with the money that went in, came back, and was lost to fees. */
export interface FreshClose {
  row: RhPredictCloseDto;
  /** Central calendar day, YYYY-MM-DD. */
  day: string;
  principal: number;
  /** Gross received: contracts times the exit price. */
  payback: number;
  gross: number;
  fees: number;
  /** Net received: gross received less fees. */
  netReceived: number;
  /** Profit or loss: net received less the principal put in. */
  net: number;
  /** Journey balance right after this close. */
  balance: number;
}

/** Totals for a set of closes. */
export interface FreshBook {
  closes: number;
  wins: number;
  losses: number;
  principal: number;
  /** Gross received. */
  payback: number;
  /** Robinhood's reported profit or loss, before fees. */
  gross: number;
  fees: number;
  /** Net received: gross received less fees. */
  netReceived: number;
  /** Profit or loss after fees. */
  net: number;
}

export interface FreshDay extends FreshBook {
  day: string;
  /** Journey balance at the end of the day. */
  balance: number;
}

const CENTRAL = 'America/Chicago';

/** Central calendar day (YYYY-MM-DD) for an ISO instant, or '' when it does not parse. */
export function centralDay(iso: string | Date): string {
  const when = iso instanceof Date ? iso : new Date(iso);
  if (Number.isNaN(when.getTime())) {
    return '';
  }
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: CENTRAL,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(when);
}

export function emptyBook(): FreshBook {
  return { closes: 0, wins: 0, losses: 0, principal: 0, payback: 0, gross: 0, fees: 0, netReceived: 0, net: 0 };
}

function cents(value: number): number {
  return Math.round(value * 100) / 100;
}

export function sumBook(items: FreshClose[]): FreshBook {
  const book = emptyBook();
  for (const item of items) {
    book.closes += 1;
    book.wins += item.gross > 0 ? 1 : 0;
    book.losses += item.gross < 0 ? 1 : 0;
    book.principal += item.principal;
    book.payback += item.payback;
    book.gross += item.gross;
    book.fees += item.fees;
  }
  book.principal = cents(book.principal);
  book.payback = cents(book.payback);
  book.gross = cents(book.gross);
  book.fees = cents(book.fees);
  book.netReceived = cents(book.payback - book.fees);
  book.net = cents(book.gross - book.fees);
  return book;
}

/**
 * Closes at or after the start, oldest first, each carrying the journey balance after it.
 * The balance is the starting amount plus net return (gross less fees) so far.
 */
export function journeyCloses(
  closes: RhPredictCloseDto[],
  startAt: Date,
  startAmount: number,
): FreshClose[] {
  const from = startAt.getTime();
  const rows = closes
    .filter((row) => {
      const at = new Date(row.closedAt).getTime();
      return Number.isFinite(at) && at >= from;
    })
    .sort((a, b) => new Date(a.closedAt).getTime() - new Date(b.closedAt).getTime());
  let balance = startAmount;
  return rows.map((row) => {
    const net = predictNet(row);
    balance += net;
    return {
      row,
      day: centralDay(row.closedAt),
      principal: predictInvested(row),
      payback: predictPayback(row),
      gross: row.realized,
      fees: predictFees(row),
      netReceived: cents(predictPayback(row) - predictFees(row)),
      net,
      balance: cents(balance),
    };
  });
}

/** One entry per Central day that has closes, newest first. */
export function journeyDays(items: FreshClose[]): FreshDay[] {
  const byDay = new Map<string, FreshClose[]>();
  for (const item of items) {
    const list = byDay.get(item.day) ?? [];
    list.push(item);
    byDay.set(item.day, list);
  }
  return [...byDay.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([day, list]) => ({ day, ...sumBook(list), balance: list[list.length - 1].balance }));
}
