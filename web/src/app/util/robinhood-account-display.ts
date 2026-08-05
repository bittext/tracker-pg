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
    default:
      return `Account (...${s})`;
  }
}
