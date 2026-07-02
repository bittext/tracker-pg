import { RobinhoodRhHoldingDto } from '../../models/finance.models';

/** Current per-unit market price in the same units as averageBuyPrice. */
export function rhHoldingCurrentUnitPrice(h: RobinhoodRhHoldingDto): number | null {
  const qty = Math.abs(h.quantity ?? 0);
  if (qty > 0 && h.marketValue != null && h.marketValue > 0) {
    return h.marketValue / qty;
  }
  const current = h.currentUnitPrice;
  if (current != null && current > 0) {
    return current;
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
