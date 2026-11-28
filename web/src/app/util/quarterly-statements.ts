import { RobinhoodQuarterDto, RobinhoodStatementDto } from '../models/finance.models';

export type StatementLineKind = 'row' | 'total' | 'heading' | 'pct';

export interface StatementLine {
  label: string;
  value: number | null;
  kind: StatementLineKind;
  /** Shown instead of a dash when the value is not known. */
  hint?: string;
}

export interface StatementSection {
  title: string;
  lines: StatementLine[];
}

const nz = (v: number | null | undefined): number => v ?? 0;

function row(label: string, value: number | null, hint?: string): StatementLine {
  return { label, value, kind: 'row', hint };
}

function total(label: string, value: number | null, hint?: string): StatementLine {
  return { label, value, kind: 'total', hint };
}

/** The three statements for one quarter, from the same numbers the server sends. */
export function buildStatements(s: RobinhoodStatementDto): StatementSection[] {
  const hasTransfers = s.transfersIn !== 0 || s.transfersOut !== 0;
  const realizedKnown = s.realized != null;
  const netInvestmentIncome = nz(s.realized) + nz(s.income);

  const income: StatementLine[] = [
    row('Realized gains and losses', s.realized, 'Not available'),
    row('Interest income', s.income),
    total(realizedKnown ? 'Net investment income' : 'Net investment income (interest only)', netInvestmentIncome),
    row('Unrealized change and other', s.unrealizedOther, 'Needs opening value'),
    total('Total return from markets', s.marketChange, 'Needs opening value'),
    { label: 'Return on opening value (approx.)', value: s.returnPct, kind: 'pct', hint: 'Needs opening value' },
  ];

  const pnl: StatementLine[] = [
    row('Opening value', s.openingValue, 'Not tracked yet'),
    row('Deposits', s.deposits),
    row('Withdrawals', -s.withdrawals),
  ];
  if (hasTransfers) {
    pnl.push(row('Moved in from my other accounts', s.transfersIn));
    pnl.push(row('Moved out to my other accounts', -s.transfersOut));
  }
  if (realizedKnown) {
    pnl.push(row('Realized gains and losses', s.realized));
  }
  pnl.push(row('Interest income', s.income));
  pnl.push(row(realizedKnown ? 'Unrealized change and other' : 'Market change and other', realizedKnown ? s.unrealizedOther : nz(s.marketChange) - s.income, 'Needs opening value'));
  pnl.push(total('Closing value', s.closingValue, 'No close yet'));

  const netChange = s.openingCash != null && s.closingCash != null ? s.closingCash - s.openingCash : null;
  const cash: StatementLine[] = [
    row('Opening cash', s.openingCash, 'Not tracked yet'),
    { label: 'Operating', value: null, kind: 'heading' },
    row('Interest received', s.income),
    row('Net cash from trading and other', s.tradingCash, 'Needs opening cash'),
    { label: 'Financing', value: null, kind: 'heading' },
    row('Deposits', s.deposits),
    row('Withdrawals', -s.withdrawals),
  ];
  if (hasTransfers) {
    cash.push(row('Moved in from my other accounts', s.transfersIn));
    cash.push(row('Moved out to my other accounts', -s.transfersOut));
  }
  cash.push(total('Net change in cash', netChange, 'Needs opening cash'));
  cash.push(total('Closing cash', s.closingCash, 'No close yet'));

  return [
    { title: 'Income statement', lines: income },
    { title: 'Profit and loss (account value)', lines: pnl },
    { title: 'Cash flow statement', lines: cash },
  ];
}

export function pickStatement(q: RobinhoodQuarterDto, suffix: string): RobinhoodStatementDto | null {
  if (suffix === 'ALL') {
    return q.combined;
  }
  return q.accounts.find((a) => a.suffix === suffix) ?? null;
}

/** True when there is nothing to report for this account in this quarter. */
export function isEmptyStatement(s: RobinhoodStatementDto): boolean {
  return (
    s.closingValue == null &&
    s.deposits === 0 &&
    s.withdrawals === 0 &&
    s.transfersIn === 0 &&
    s.transfersOut === 0 &&
    s.income === 0
  );
}

function csvCell(v: string): string {
  return /[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
}

export function statementsToCsv(q: RobinhoodQuarterDto, s: RobinhoodStatementDto): string {
  const lines: string[] = [];
  lines.push(['Quarter', q.label].map(csvCell).join(','));
  lines.push(['Account', s.label].map(csvCell).join(','));
  lines.push(['Period', `${q.start} to ${q.end}${q.partial ? ' (quarter in progress)' : ''}`].map(csvCell).join(','));
  lines.push(['Measured from close', s.openingDate ?? 'not tracked'].map(csvCell).join(','));
  lines.push(['Measured to close', s.closingDate ?? 'none'].map(csvCell).join(','));
  lines.push('');
  lines.push('Statement,Line,Amount');
  for (const section of buildStatements(s)) {
    for (const l of section.lines) {
      if (l.kind === 'heading') {
        continue;
      }
      const value = l.value == null ? '' : l.value.toFixed(2);
      lines.push([section.title, l.label, value].map(csvCell).join(','));
    }
  }
  return lines.join('\r\n') + '\r\n';
}

const escapeHtml = (t: string): string =>
  t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function fmt(l: StatementLine): string {
  if (l.value == null) {
    return l.hint ? escapeHtml(l.hint) : '—';
  }
  if (l.kind === 'pct') {
    return `${l.value.toFixed(2)}%`;
  }
  const abs = Math.abs(l.value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return l.value < 0 ? `-$${abs}` : `$${abs}`;
}

/** A standalone page that prints (or saves as PDF) cleanly. */
export function statementsToPrintHtml(q: RobinhoodQuarterDto, s: RobinhoodStatementDto, notes: string[]): string {
  const sections = buildStatements(s)
    .map((section) => {
      const body = section.lines
        .map((l) => {
          if (l.kind === 'heading') {
            return `<tr class="h"><td colspan="2">${escapeHtml(l.label)}</td></tr>`;
          }
          const cls = l.kind === 'total' ? 't' : '';
          return `<tr class="${cls}"><td>${escapeHtml(l.label)}</td><td class="n">${fmt(l)}</td></tr>`;
        })
        .join('');
      return `<section><h2>${escapeHtml(section.title)}</h2><table>${body}</table></section>`;
    })
    .join('');
  const noteHtml = notes.map((n) => `<li>${escapeHtml(n)}</li>`).join('');
  return `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(q.label)} statements - ${escapeHtml(s.label)}</title>
<style>
body{font:13px/1.45 -apple-system,Segoe UI,Helvetica,Arial,sans-serif;color:#0f172a;margin:28px}
h1{font-size:20px;margin:0 0 2px}h2{font-size:14px;margin:22px 0 6px;border-bottom:2px solid #0f172a;padding-bottom:3px}
.sub{color:#475569;margin:0 0 6px}table{width:100%;border-collapse:collapse}
td{padding:4px 2px;border-bottom:1px solid #e2e8f0}td.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
tr.t td{font-weight:700;border-top:1px solid #0f172a}tr.h td{font-weight:600;color:#475569;padding-top:8px}
ul{color:#475569;font-size:11px;padding-left:18px}
section{break-inside:avoid}
</style></head><body>
<h1>${escapeHtml(q.label)} statements</h1>
<p class="sub">${escapeHtml(s.label)} · ${escapeHtml(q.start)} to ${escapeHtml(q.end)}${q.partial ? ' (quarter in progress)' : ''}</p>
<p class="sub">Measured from the ${s.openingDate ? escapeHtml(s.openingDate) + ' close' : 'start (no earlier close tracked)'} to the ${s.closingDate ? escapeHtml(s.closingDate) + ' close' : 'latest data'}.</p>
${sections}
<ul>${noteHtml}</ul>
<script>window.addEventListener('load',function(){setTimeout(function(){window.print()},250)})</script>
</body></html>`;
}
