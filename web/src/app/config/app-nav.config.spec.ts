import {
  ADMIN_TOP_TAB_NAV,
  APP_NAV_REGISTRY,
  INSIGHTS_TAB_NAV,
  LIFE_PRIMARY_NAV,
  MARKETS_PRIMARY_NAV,
  validateAppNavRegistry,
} from './app-nav.config';

describe('app-nav.config', () => {
  it('registry passes all validation rules', () => {
    expect(validateAppNavRegistry(APP_NAV_REGISTRY)).toEqual([]);
  });

  it('Life primary nav has 8 unique labels', () => {
    const labels = LIFE_PRIMARY_NAV.map((e) => e.label);
    expect(new Set(labels).size).toBe(labels.length);
    expect(labels.length).toBe(8);
  });

  it('Markets primary nav has 8 unique labels', () => {
    const labels = MARKETS_PRIMARY_NAV.map((e) => e.label);
    expect(new Set(labels).size).toBe(labels.length);
    expect(labels.length).toBe(8);
  });

  it('Insights tabs do not reuse Life primary labels', () => {
    const lifeLabels = new Set(LIFE_PRIMARY_NAV.map((e) => e.label));
    for (const tab of INSIGHTS_TAB_NAV) {
      expect(lifeLabels.has(tab.label)).withContext(`tab ${tab.id}`).toBeFalse();
    }
  });

  it('Insights tab labels are unique within the group', () => {
    const labels = INSIGHTS_TAB_NAV.map((e) => e.label);
    expect(new Set(labels).size).toBe(labels.length);
  });

  it('Admin top tabs do not reuse Life primary labels verbatim', () => {
    const lifeLabels = new Set(LIFE_PRIMARY_NAV.map((e) => e.label));
    for (const tab of ADMIN_TOP_TAB_NAV) {
      expect(lifeLabels.has(tab.label))
        .withContext(`admin tab ${tab.id} should not equal Life nav label`)
        .toBeFalse();
    }
  });

  it('detects duplicate labels within a shell', () => {
    const bad = [
      ...LIFE_PRIMARY_NAV,
      { id: 'dup', label: 'Home', path: '/x', shell: 'life-primary' as const },
    ];
    const issues = validateAppNavRegistry(bad);
    expect(issues.some((i) => i.code === 'duplicate-label-in-shell')).toBeTrue();
  });
});
