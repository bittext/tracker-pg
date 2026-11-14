import { RhPredictCloseDto } from '../../../models/rh-predict.models';
import { centralDay, journeyCloses, journeyDays, sumBook } from './predict-fresh.util';

function close(id: number, closedAt: string, quantity: number, price: number, realized: number): RhPredictCloseDto {
  return { id, accountSuffix: '3370', accountLabel: 'Individual', closedAt, quantity, price, realized, label: null };
}

describe('predict fresh start', () => {
  const start = new Date('2026-11-14T22:50:00Z');

  it('leaves out closes before the start', () => {
    const rows = journeyCloses(
      [close(1, '2026-11-14T21:00:00Z', 100, 1, 20), close(2, '2026-11-14T23:30:00Z', 100, 1, 20)],
      start,
      100.7,
    );
    expect(rows.length).toBe(1);
    expect(rows[0].row.id).toBe(2);
  });

  it('keeps principal, payback, and gross consistent and takes fees from net', () => {
    const [item] = journeyCloses([close(1, '2026-11-14T23:30:00Z', 100, 1, 40)], start, 100.7);
    expect(item.payback).toBe(100);
    expect(item.principal).toBe(60);
    expect(item.gross).toBe(40);
    expect(item.fees).toBeGreaterThan(0);
    expect(item.net).toBeCloseTo(item.gross - item.fees, 6);
    expect(item.balance).toBeCloseTo(100.7 + item.net, 2);
  });

  it('carries the balance forward and totals each Central day', () => {
    const rows = journeyCloses(
      [
        close(1, '2026-11-14T23:30:00Z', 100, 1, 40),
        close(2, '2026-11-15T01:00:00Z', 100, 0, -50),
        close(3, '2026-11-15T20:00:00Z', 10, 1, 4),
      ],
      start,
      100.7,
    );
    expect(rows[1].balance).toBeCloseTo(rows[0].balance + rows[1].net, 2);
    const days = journeyDays(rows);
    expect(days.map((d) => d.day)).toEqual(['2026-11-15', '2026-11-14']);
    expect(days[1].closes).toBe(2);
    expect(days[1].losses).toBe(1);
    expect(days[0].balance).toBe(rows[2].balance);
    const all = sumBook(rows);
    expect(all.gross).toBeCloseTo(-6, 6);
    expect(all.net).toBeCloseTo(all.gross - all.fees, 6);
  });

  it('names the Central day, not the UTC day', () => {
    expect(centralDay('2026-11-15T03:30:00Z')).toBe('2026-11-14');
  });
});
