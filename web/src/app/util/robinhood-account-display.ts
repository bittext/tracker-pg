/** Friendly RH account labels — keep in sync with RobinhoodRhDailyTrackerAccountPolicy.displayLabel. */
export function robinhoodAccountDisplayLabel(suffix: string | null | undefined): string {
  const s = (suffix ?? '').trim();
  if (!s) {
    return 'Account';
  }
  switch (s) {
    case '3370':
      return 'Individual a/c (...3370)';
    case '3550':
      return 'Agentic a/c (...3550)';
    case '4123':
      return 'Managed a/c (...4123)';
    case '8696':
      return "Ammu's a/c (...8696)";
    case '4190':
      return 'Individual a/c (...4190)';
    case '7581':
      return 'Agentic a/c (...7581)';
    case '2835':
      return 'Roth IRA (...2835)';
    case '0440':
      return 'Short Term a/c (...0440)';
    default:
      return `Account (...${s})`;
  }
}

/** Compact name + last-4 for tight tables. */
export function robinhoodAccountParts(suffix: string | null | undefined): { name: string; last4: string } {
  const last4 = (suffix ?? '').trim();
  switch (last4) {
    case '3370':
    case '4190':
      return { name: 'Individual', last4 };
    case '3550':
    case '7581':
      return { name: 'Agentic', last4 };
    case '4123':
      return { name: 'Managed', last4 };
    case '8696':
      return { name: 'Ammu', last4 };
    case '2835':
      return { name: 'Roth IRA', last4 };
    case '0440':
      return { name: 'Short Term', last4 };
    default:
      return { name: last4 ? 'Account' : 'Account', last4 };
  }
}

/** Name with the masked number, such as "Roth IRA ••••2835" (matches the labels the server sends). */
export function robinhoodAccountNamed(suffix: string | null | undefined): string {
  const { name, last4 } = robinhoodAccountParts(suffix);
  return last4 ? `${name === 'Ammu' ? "Ammu's" : name} ••••${last4}` : name;
}
