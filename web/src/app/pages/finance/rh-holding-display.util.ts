import { RobinhoodRhHoldingDto } from '../../models/finance.models';

/** True for explicit option rows and legacy snapshots missing positionType. */
export function isOptionHolding(h: RobinhoodRhHoldingDto): boolean {
  const type = (h.positionType ?? '').trim().toLowerCase();
  if (type === 'option') {
    return true;
  }
  const qty = Math.abs(h.quantity ?? 0);
  const avg = h.averageBuyPrice ?? 0;
  // Legacy rows: few contracts with MCP average_price stored as contract premium (e.g. 1000).
  return qty > 0 && qty <= 20 && avg > 500 && avg < 100_000;
}

/** Per-share option average when Robinhood MCP stored per-contract premium (e.g. 1000 → 10). */
export function rhHoldingAverageBuyPrice(h: RobinhoodRhHoldingDto): number {
  const avg = h.averageBuyPrice ?? 0;
  if (isOptionHolding(h) && avg > 100) {
    return avg / 100;
  }
  return avg;
}

/** Per-share price from total option market value (contract dollars). */
function optionPerShareFromMarketValue(h: RobinhoodRhHoldingDto): number | null {
  const qty = Math.abs(h.quantity ?? 0);
  if (qty <= 0) {
    return null;
  }
  let mv = h.marketValue ?? 0;
  if (mv <= 0) {
    return null;
  }
  const avg = rhHoldingAverageBuyPrice(h);
  const cost = h.costBasis ?? 0;
  const unrealized = h.unrealizedPnL ?? 0;
  if (unrealized !== 0 && cost > 0 && Math.abs(mv - cost) < 0.05) {
    mv = cost + unrealized;
  }
  let perShare = mv / (qty * 100);
  while (avg > 0 && perShare > avg * 25) {
    perShare = perShare / 100;
  }
  return perShare > 0 ? perShare : null;
}

/** Legacy snapshots may store contract premium or premium×100 as unit price. */
function normalizeLegacyOptionPerShare(value: number, h: RobinhoodRhHoldingDto): number {
  if (value <= 0) {
    return 0;
  }
  const avg = rhHoldingAverageBuyPrice(h);
  let v = value;
  if (v > 100) {
    v = v / 100;
  }
  while (avg > 0 && v > avg * 25) {
    v = v / 100;
  }
  return v;
}

/** Current per-share price (equities: per share; options: per share of underlying). */
export function rhHoldingCurrentUnitPrice(h: RobinhoodRhHoldingDto): number | null {
  if (isOptionHolding(h)) {
    const avg = rhHoldingAverageBuyPrice(h);
    const fromCurrent = normalizeLegacyOptionPerShare(h.currentUnitPrice ?? 0, h);
    const fromMv = optionPerShareFromMarketValue(h);

    if (fromCurrent > 0 && fromMv != null && fromMv > 0) {
      // MV often equals cost when quotes failed; currentUnitPrice may still hold the mark (e.g. 1068 → 10.68).
      if (avg > 0 && Math.abs(fromMv - avg) < 0.05 && fromCurrent > avg + 0.001) {
        return fromCurrent;
      }
      if (fromMv > avg * 50 && fromCurrent > 0) {
        return fromCurrent;
      }
      return fromMv;
    }
    if (fromCurrent > 0) {
      return fromCurrent;
    }
    return fromMv;
  }

  const qty = Math.abs(h.quantity ?? 0);
  const current = h.currentUnitPrice;
  if (current != null && current > 0) {
    return current;
  }
  if (qty > 0 && h.marketValue != null && h.marketValue > 0) {
    return h.marketValue / qty;
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
