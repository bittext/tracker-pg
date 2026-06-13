import { BankingTransactionDto } from '../models/finance.models';

export interface BankingFlowTotals {
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowByInstitutionRow {
  institutionId: number;
  institutionName: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowByMonthRow {
  yearMonth: string;
  monthLabel: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
  /** Net as a percentage of income; null when no credits in the month. */
  savingsRatePct: number | null;
}

export interface BankingFlowByTypeRow {
  institutionTypeId: number | null;
  typeLabel: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowBySourceRow {
  sourceFormat: string;
  creditTotal: number;
  debitTotal: number;
  net: number;
  txnCount: number;
}

export interface BankingFlowByDescriptionRow {
  description: string;
  debitTotal: number;
  txnCount: number;
}

function txnAmount(t: BankingTransactionDto): number | null {
  const a = Number(t.amount);
  return Number.isFinite(a) ? a : null;
}

export function computeBankingFlowTotals(txns: BankingTransactionDto[]): BankingFlowTotals {
  let creditTotal = 0;
  let debitTotal = 0;
  for (const t of txns) {
    const a = txnAmount(t);
    if (a == null) {
      continue;
    }
    if (a > 0) {
      creditTotal += a;
    } else if (a < 0) {
      debitTotal += -a;
    }
  }
  return {
    creditTotal,
    debitTotal,
    net: creditTotal - debitTotal,
    txnCount: txns.length,
  };
}

export function buildBankingFlowByInstitution(txns: BankingTransactionDto[]): BankingFlowByInstitutionRow[] {
  const m = new Map<number, { name: string; credit: number; debit: number; n: number }>();
  for (const t of txns) {
    const a = txnAmount(t);
    if (a == null) {
      continue;
    }
    const id = t.institutionId;
    const name = t.institutionName || `Institution ${id}`;
    const cur = m.get(id) ?? { name, credit: 0, debit: 0, n: 0 };
    cur.name = name;
    if (a > 0) {
      cur.credit += a;
    } else if (a < 0) {
      cur.debit += -a;
    }
    cur.n += 1;
    m.set(id, cur);
  }
  return [...m.entries()]
    .map(([institutionId, v]) => ({
      institutionId,
      institutionName: v.name,
      creditTotal: v.credit,
      debitTotal: v.debit,
      net: v.credit - v.debit,
      txnCount: v.n,
    }))
    .sort((a, b) => a.institutionName.localeCompare(b.institutionName, undefined, { sensitivity: 'base' }));
}

export function buildBankingFlowByType(txns: BankingTransactionDto[]): BankingFlowByTypeRow[] {
  const m = new Map<number | null, { label: string; credit: number; debit: number; n: number }>();
  for (const t of txns) {
    const a = txnAmount(t);
    if (a == null) {
      continue;
    }
    const tid = t.institutionTypeId != null && Number.isFinite(t.institutionTypeId) ? t.institutionTypeId : null;
    const label =
      tid != null && (t.institutionTypeName ?? '').trim()
        ? (t.institutionTypeName as string).trim()
        : 'Untyped';
    const cur = m.get(tid) ?? { label, credit: 0, debit: 0, n: 0 };
    cur.label = label;
    if (a > 0) {
      cur.credit += a;
    } else if (a < 0) {
      cur.debit += -a;
    }
    cur.n += 1;
    m.set(tid, cur);
  }
  return [...m.entries()]
    .map(([institutionTypeId, v]) => ({
      institutionTypeId,
      typeLabel: v.label,
      creditTotal: v.credit,
      debitTotal: v.debit,
      net: v.credit - v.debit,
      txnCount: v.n,
    }))
    .sort((a, b) => {
      if (a.institutionTypeId == null) {
        return 1;
      }
      if (b.institutionTypeId == null) {
        return -1;
      }
      return a.typeLabel.localeCompare(b.typeLabel, undefined, { sensitivity: 'base' });
    });
}

export function formatBankingYearMonthLabel(ym: string): string {
  const y = Number(ym.slice(0, 4));
  const mo = Number(ym.slice(5, 7));
  if (!Number.isFinite(y) || !Number.isFinite(mo) || mo < 1 || mo > 12) {
    return ym;
  }
  return new Date(y, mo - 1, 1).toLocaleString(undefined, { month: 'short', year: 'numeric' });
}

export function buildBankingFlowByMonth(txns: BankingTransactionDto[]): BankingFlowByMonthRow[] {
  const m = new Map<string, { credit: number; debit: number; n: number }>();
  for (const t of txns) {
    const ym = (t.txnDate ?? '').slice(0, 7);
    if (ym.length !== 7) {
      continue;
    }
    const a = txnAmount(t);
    if (a == null) {
      continue;
    }
    const cur = m.get(ym) ?? { credit: 0, debit: 0, n: 0 };
    if (a > 0) {
      cur.credit += a;
    } else if (a < 0) {
      cur.debit += -a;
    }
    cur.n += 1;
    m.set(ym, cur);
  }
  return [...m.entries()]
    .map(([yearMonth, v]) => {
      const net = v.credit - v.debit;
      return {
        yearMonth,
        monthLabel: formatBankingYearMonthLabel(yearMonth),
        creditTotal: v.credit,
        debitTotal: v.debit,
        net,
        txnCount: v.n,
        savingsRatePct: v.credit > 0 ? (net / v.credit) * 100 : null,
      };
    })
    .sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
}

export function buildBankingFlowBySource(txns: BankingTransactionDto[]): BankingFlowBySourceRow[] {
  const m = new Map<string, { credit: number; debit: number; n: number }>();
  for (const t of txns) {
    const key = (t.sourceFormat ?? '').trim() || '—';
    const a = txnAmount(t);
    if (a == null) {
      continue;
    }
    const cur = m.get(key) ?? { credit: 0, debit: 0, n: 0 };
    if (a > 0) {
      cur.credit += a;
    } else if (a < 0) {
      cur.debit += -a;
    }
    cur.n += 1;
    m.set(key, cur);
  }
  return [...m.entries()]
    .map(([sourceFormat, v]) => ({
      sourceFormat,
      creditTotal: v.credit,
      debitTotal: v.debit,
      net: v.credit - v.debit,
      txnCount: v.n,
    }))
    .sort((a, b) => Math.abs(b.net) - Math.abs(a.net));
}

export function filterBankingCreditTransactions(txns: BankingTransactionDto[]): BankingTransactionDto[] {
  return txns
    .filter((t) => {
      const a = txnAmount(t);
      return a != null && a > 0;
    })
    .sort((a, b) => Number(b.amount) - Number(a.amount));
}

export function filterBankingDebitTransactions(txns: BankingTransactionDto[]): BankingTransactionDto[] {
  return txns
    .filter((t) => {
      const a = txnAmount(t);
      return a != null && a < 0;
    })
    .sort((a, b) => Number(a.amount) - Number(b.amount));
}

/** Top outflows grouped by transaction description (exact match). */
export function buildBankingSpendingByDescription(
  txns: BankingTransactionDto[],
  limit = 25,
): BankingFlowByDescriptionRow[] {
  const m = new Map<string, { debit: number; n: number }>();
  for (const t of txns) {
    const a = txnAmount(t);
    if (a == null || a >= 0) {
      continue;
    }
    const key = (t.description ?? '').trim() || '—';
    const cur = m.get(key) ?? { debit: 0, n: 0 };
    cur.debit += -a;
    cur.n += 1;
    m.set(key, cur);
  }
  return [...m.entries()]
    .map(([description, v]) => ({
      description,
      debitTotal: v.debit,
      txnCount: v.n,
    }))
    .sort((a, b) => b.debitTotal - a.debitTotal)
    .slice(0, limit);
}
