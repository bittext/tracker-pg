/**
 * Central navigation label registry — shells, Insights tabs, and Admin tabs import from here.
 * {@link validateAppNavRegistry} enforces uniqueness rules (see app-nav.config.spec.ts).
 */

export type NavShell =
  | 'life-primary'
  | 'life-switch'
  | 'life-admin'
  | 'markets-primary'
  | 'markets-footer'
  | 'insights-tab'
  | 'admin-top';

export interface NavEntry {
  id: string;
  label: string;
  path?: string;
  icon?: string;
  shell: NavShell;
  /** Screen-reader hint when label alone is ambiguous. */
  ariaLabel?: string;
  exact?: boolean;
  adminOnly?: boolean;
}

/** Life sidebar — workspace destinations (unique within shell). */
export const LIFE_PRIMARY_NAV: NavEntry[] = [
  { id: 'home', label: 'Home', path: '/life/welcome', icon: 'home', shell: 'life-primary', exact: true },
  {
    id: 'exercise',
    label: 'Exercise',
    path: '/life/exercise',
    icon: 'fitness_center',
    shell: 'life-primary',
    exact: true,
  },
  {
    id: 'management',
    label: 'Management',
    path: '/life/management',
    icon: 'dashboard',
    shell: 'life-primary',
    exact: true,
  },
  { id: 'journal', label: 'Journal', path: '/life/journal', icon: 'menu_book', shell: 'life-primary', exact: true },
  {
    id: 'money',
    label: 'Money',
    path: '/life/money',
    icon: 'account_balance_wallet',
    shell: 'life-primary',
    exact: true,
  },
  {
    id: 'insights',
    label: 'Insights',
    path: '/life/insights',
    icon: 'bar_chart',
    shell: 'life-primary',
    exact: false,
    ariaLabel: 'Insights — reports and trends',
  },
  {
    id: 'settings',
    label: 'Settings',
    path: '/life/settings',
    icon: 'settings',
    shell: 'life-primary',
    exact: true,
  },
];

export const LIFE_SWITCH_NAV: NavEntry = {
  id: 'markets-switch',
  label: 'Markets',
  path: '/markets/overview',
  icon: 'candlestick_chart',
  shell: 'life-switch',
  ariaLabel: 'Switch to Markets workspace',
};

export const LIFE_ADMIN_NAV: NavEntry[] = [
  {
    id: 'admin',
    label: 'Admin',
    path: '/admin',
    icon: 'admin_panel_settings',
    shell: 'life-admin',
    exact: true,
    adminOnly: true,
  },
  {
    id: 'logs',
    label: 'Logs',
    path: '/logs',
    icon: 'article',
    shell: 'life-admin',
    exact: true,
    adminOnly: true,
  },
];

/** Markets sidebar — trading destinations. */
export const MARKETS_PRIMARY_NAV: NavEntry[] = [
  {
    id: 'overview',
    label: 'Overview',
    path: '/markets/overview',
    icon: 'insights',
    shell: 'markets-primary',
    exact: true,
  },
  {
    id: 'workspace',
    label: 'Workspace',
    path: '/markets/workspace',
    icon: 'work',
    shell: 'markets-primary',
    exact: true,
  },
  {
    id: 'analytics',
    label: 'Analytics',
    path: '/markets/analytics',
    icon: 'analytics',
    shell: 'markets-primary',
    exact: true,
  },
  {
    id: 'execution',
    label: 'Execution',
    path: '/markets/execution',
    icon: 'bolt',
    shell: 'markets-primary',
    exact: true,
  },
  {
    id: 'alert-rules',
    label: 'Alert rules',
    path: '/markets/alerts',
    icon: 'notifications',
    shell: 'markets-primary',
    exact: true,
    ariaLabel: 'Markets — alert rules overview',
  },
];

export const MARKETS_FOOTER_NAV: NavEntry[] = [
  {
    id: 'life-switch',
    label: 'Life',
    path: '/life/welcome',
    icon: 'home',
    shell: 'markets-footer',
    ariaLabel: 'Switch to Life workspace',
  },
  {
    id: 'settings',
    label: 'Settings',
    path: '/life/settings',
    icon: 'settings',
    shell: 'markets-footer',
    ariaLabel: 'Settings (Life)',
  },
];

/** Insights page tabs — must not duplicate Life primary labels verbatim. */
export const INSIGHTS_TAB_NAV: NavEntry[] = [
  {
    id: 'trends',
    label: 'Trends',
    shell: 'insights-tab',
    ariaLabel: 'Insights — exercise trends',
  },
  {
    id: 'tasks-now',
    label: 'Tasks & Now',
    shell: 'insights-tab',
    ariaLabel: 'Insights — task reports and Now roadmap',
  },
  {
    id: 'banking',
    label: 'Banking',
    shell: 'insights-tab',
    ariaLabel: 'Insights — banking money-flow reports',
  },
  {
    id: 'search',
    label: 'Search',
    shell: 'insights-tab',
    ariaLabel: 'Insights — journal search',
  },
];

/** Admin top-level tab labels (config views, not member workspaces). */
export const ADMIN_TOP_TAB_NAV: NavEntry[] = [
  { id: 'sign-in-log', label: 'Sign-in log', shell: 'admin-top' },
  { id: 'users', label: 'Users', shell: 'admin-top', adminOnly: true },
  { id: 'create-user', label: 'Create user', shell: 'admin-top', adminOnly: true },
  { id: 'my-profile', label: 'My profile', shell: 'admin-top' },
  { id: 'exercise-config', label: 'Exercise config', shell: 'admin-top' },
  { id: 'journal-config', label: 'Journal config', shell: 'admin-top' },
  { id: 'finance-config', label: 'Finance config', shell: 'admin-top' },
  { id: 'management-config', label: 'Management config', shell: 'admin-top' },
  { id: 'usage', label: 'Usage', shell: 'admin-top', adminOnly: true },
  { id: 'features', label: 'Features', shell: 'admin-top', adminOnly: true },
  { id: 'repository', label: 'Repository (GitHub)', shell: 'admin-top', adminOnly: true },
];

export const APP_NAV_REGISTRY: NavEntry[] = [
  ...LIFE_PRIMARY_NAV,
  LIFE_SWITCH_NAV,
  ...LIFE_ADMIN_NAV,
  ...MARKETS_PRIMARY_NAV,
  ...MARKETS_FOOTER_NAV,
  ...INSIGHTS_TAB_NAV,
  ...ADMIN_TOP_TAB_NAV,
];

/** Life primary labels that Insights tabs must not reuse exactly. */
const LIFE_PRIMARY_LABELS = new Set(LIFE_PRIMARY_NAV.map((e) => e.label));

/** Cross-shell duplicates allowed when path matches (e.g. Settings → /life/settings). */
const CROSS_SHELL_DUPLICATE_ALLOWLIST = new Set(['Settings']);

export interface NavValidationIssue {
  code: string;
  message: string;
}

/** Returns validation issues; empty array means the registry is valid. */
export function validateAppNavRegistry(entries: NavEntry[] = APP_NAV_REGISTRY): NavValidationIssue[] {
  const issues: NavValidationIssue[] = [];

  const byShell = new Map<NavShell, NavEntry[]>();
  for (const entry of entries) {
    const list = byShell.get(entry.shell) ?? [];
    list.push(entry);
    byShell.set(entry.shell, list);
  }

  for (const [shell, group] of byShell) {
    const seen = new Map<string, NavEntry[]>();
    for (const entry of group) {
      const bucket = seen.get(entry.label) ?? [];
      bucket.push(entry);
      seen.set(entry.label, bucket);
    }
    for (const [label, dupes] of seen) {
      if (dupes.length > 1) {
        issues.push({
          code: 'duplicate-label-in-shell',
          message: `Shell "${shell}" has duplicate label "${label}" (${dupes.map((d) => d.id).join(', ')})`,
        });
      }
    }
  }

  for (const tab of INSIGHTS_TAB_NAV) {
    if (LIFE_PRIMARY_LABELS.has(tab.label)) {
      issues.push({
        code: 'insights-collides-with-life',
        message: `Insights tab "${tab.label}" duplicates a Life primary nav label`,
      });
    }
  }

  const settingsEntries = entries.filter((e) => e.label === 'Settings');
  if (settingsEntries.length > 1) {
    const paths = new Set(settingsEntries.map((e) => e.path).filter(Boolean));
    if (paths.size > 1) {
      issues.push({
        code: 'settings-path-mismatch',
        message: 'Settings appears on multiple paths; expected only /life/settings',
      });
    }
  }

  const marketsPrimaryLabels = new Set(MARKETS_PRIMARY_NAV.map((e) => e.label));
  for (const life of LIFE_PRIMARY_NAV) {
    if (marketsPrimaryLabels.has(life.label) && !CROSS_SHELL_DUPLICATE_ALLOWLIST.has(life.label)) {
      issues.push({
        code: 'markets-collides-with-life',
        message: `Markets primary label "${life.label}" duplicates Life primary nav`,
      });
    }
  }

  return issues;
}

export function assertAppNavRegistryValid(entries: NavEntry[] = APP_NAV_REGISTRY): void {
  const issues = validateAppNavRegistry(entries);
  if (issues.length) {
    throw new Error(issues.map((i) => i.message).join('\n'));
  }
}

/** Convenience lookups for templates. */
export const INSIGHTS_TAB_LABELS = {
  trends: INSIGHTS_TAB_NAV[0].label,
  tasksAndNow: INSIGHTS_TAB_NAV[1].label,
  banking: INSIGHTS_TAB_NAV[2].label,
  search: INSIGHTS_TAB_NAV[3].label,
} as const;

export const ADMIN_TAB_LABELS = {
  signInLog: ADMIN_TOP_TAB_NAV[0].label,
  users: ADMIN_TOP_TAB_NAV[1].label,
  createUser: ADMIN_TOP_TAB_NAV[2].label,
  myProfile: ADMIN_TOP_TAB_NAV[3].label,
  exerciseConfig: ADMIN_TOP_TAB_NAV[4].label,
  journalConfig: ADMIN_TOP_TAB_NAV[5].label,
  financeConfig: ADMIN_TOP_TAB_NAV[6].label,
  managementConfig: ADMIN_TOP_TAB_NAV[7].label,
  usage: ADMIN_TOP_TAB_NAV[8].label,
  features: ADMIN_TOP_TAB_NAV[9].label,
  repository: ADMIN_TOP_TAB_NAV[10].label,
} as const;

export const MARKETS_ALERT_RULES_PAGE = {
  title: 'Alert rules',
  workspaceAlertsTab: 'Alerts',
} as const;
