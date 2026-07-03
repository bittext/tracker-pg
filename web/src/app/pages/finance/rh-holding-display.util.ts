import { RobinhoodRhHoldingDto } from '../../models/finance.models';

function isOption(h: RobinhoodRhHoldingDto): boolean {
  return (h.positionType ?? '').toLowerCase() === 'option';
}

/** Per-share option average when Robinhood MCP stored per-contract premium (e.g. 1000 → 10). */
export function rhHoldingAverageBuyPrice(h: RobinhoodRhHoldingDto): number {
  const avg = h.averageBuyPrice ?? 0;
  if (isOption(h) && avg > 100) {
    return avg / 100;
  }
  return avg;
}

/** Current per-share price (equities: per share; options: per share of underlying). */
export function rhHoldingCurrentUnitPrice(h: RobinhoodRhHoldingDto): number | null {
  const qty = Math.abs(h.quantity ?? 0);
  if (isOption(h)) {
    // Option market_value is total contract dollars; per-share = MV / (qty × 100).
    if (qty > 0 && h.marketValue != null && h.marketValue > 0) {
      return h.marketValue / (qty * 100);
    }
    const current = h.currentUnitPrice;
    if (current != null && current > 0) {
      return normalizeLegacyOptionPerShare(current, h);
    }
    return null;
  }
  const current = h.currentUnitPrice;
  if (current != null && current > 0) {
    return current;
  }
  if (qty > 0 && h.marketValue != null && h.marketValue > 0) {
    return h.marketValue / qty;
  }
  return null;
}

/** Legacy snapshots may store contract premium or premium×100 as unit price. */
function normalizeLegacyOptionPerShare(value: number, h: RobinhoodRhHoldingDto): number {
  const avg = rhHoldingAverageBuyPrice(h);
  let v = value;
  if (v > 100) {
    v = v / 100;
  }
  if (v > 100 && avg > 0 && v / avg > 25) {
    v = v / 100;
  }
  return v;
}

/** Unrealized P&L percent vs cost basis. */
export function rhHoldingPnlPercent(h: RobinhoodRhHoldingDto): number | null {
  if (h.unrealizedPnLPercent != null && !Number.isNaN(h.unrealizedPnLPercent)) {
    return h.unrealizedPnLPercent;
  }
  const cost = h.costBasis;
  if (cost == null || cost === 0) {
    return null;
  }
  return (h.unrealizedPnL / cost) * 100;
}
