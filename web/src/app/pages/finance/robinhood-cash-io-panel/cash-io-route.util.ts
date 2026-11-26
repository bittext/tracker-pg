export interface CashIoAccountRef {
  suffix: string;
  label: string;
}

/** One ledger row to save: the effect of a transfer on one of your own accounts. */
export interface CashIoMove {
  accountSuffix: string;
  direction: 'IN' | 'OUT';
  /** The account on the other side of the move. */
  counterparty: string;
}

export type CashIoRoutePlan = { ok: true; moves: CashIoMove[] } | { ok: false; error: string };

export function accountLabelFor(suffix: string, accounts: CashIoAccountRef[]): string {
  return accounts.find((a) => a.suffix === suffix)?.label ?? `Account ••••${suffix}`;
}

/**
 * Recognizes one of your own Robinhood accounts from typed text: its full label
 * ("Individual ••••3370") or just its last digits ("3370" / "••••3370"). Anything else is an outside account.
 */
export function ownSuffix(text: string, accounts: CashIoAccountRef[]): string | null {
  const t = text.trim();
  if (!t) {
    return null;
  }
  const byLabel = accounts.find((a) => a.label.toLowerCase() === t.toLowerCase());
  if (byLabel) {
    return byLabel.suffix;
  }
  const digits = t.replace(/[•\s]/g, '');
  return /^\d{3,8}$/.test(digits) ? digits : null;
}

/**
 * Turns a From / To pair into the ledger rows to save.
 * - From is yours, To is outside: one output on From.
 * - From is outside, To is yours: one input on To.
 * - Both are yours: an output on From and an input on To.
 */
export function planRoute(fromText: string, toText: string, accounts: CashIoAccountRef[]): CashIoRoutePlan {
  const from = fromText.trim();
  const to = toText.trim();
  if (!from || !to) {
    return { ok: false, error: 'Enter both the From account and the To account.' };
  }
  const fromOwn = ownSuffix(from, accounts);
  const toOwn = ownSuffix(to, accounts);
  if (!fromOwn && !toOwn) {
    return { ok: false, error: 'One side must be one of your Robinhood accounts.' };
  }
  if (fromOwn && fromOwn === toOwn) {
    return { ok: false, error: 'From and To are the same account.' };
  }
  if (fromOwn && toOwn) {
    return {
      ok: true,
      moves: [
        { accountSuffix: fromOwn, direction: 'OUT', counterparty: accountLabelFor(toOwn, accounts) },
        { accountSuffix: toOwn, direction: 'IN', counterparty: accountLabelFor(fromOwn, accounts) },
      ],
    };
  }
  if (fromOwn) {
    return { ok: true, moves: [{ accountSuffix: fromOwn, direction: 'OUT', counterparty: to }] };
  }
  return { ok: true, moves: [{ accountSuffix: toOwn as string, direction: 'IN', counterparty: from }] };
}

/** One-line description of what saving will record. */
export function describeRoute(fromText: string, toText: string, accounts: CashIoAccountRef[]): string {
  const plan = planRoute(fromText, toText, accounts);
  if (!plan.ok) {
    return '';
  }
  return plan.moves
    .map((m) => {
      const label = accountLabelFor(m.accountSuffix, accounts);
      return m.direction === 'OUT' ? `Output from ${label}` : `Input to ${label}`;
    })
    .join(' + ');
}
