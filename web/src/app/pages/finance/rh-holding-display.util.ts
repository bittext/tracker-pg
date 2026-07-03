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
  const current = h.currentUnitPrice;
  if (current != null && current > 0) {
    if (isOption(h) && current > 100) {
      return current / 100;
    }
    return current;
  }
  const qty = Math.abs(h.quantity ?? 0);
  if (qty > 0 && h.marketValue != null && h.marketValue > 0) {
    const implied = h.marketValue / qty;
    if (isOption(h) && implied > 100) {
      return implied / 100;
    }
    return implied;
  }
  return null;
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
