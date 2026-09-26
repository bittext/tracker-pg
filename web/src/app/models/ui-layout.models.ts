/** Site chrome: current v16, ledger redesign, or the month spine. */
export type UiLayout = 'current' | 'redesign' | 'spine';

export const UI_LAYOUT_STORAGE_KEY = 'tracker.ui-layout.v1';

export const DEFAULT_UI_LAYOUT: UiLayout = 'current';

export const UI_LAYOUT_CYCLE: UiLayout[] = ['current', 'redesign', 'spine'];

export function parseUiLayout(raw: unknown): UiLayout {
  if (raw === 'redesign' || raw === 'spine') {
    return raw;
  }
  return 'current';
}

export function uiLayoutLabel(layout: UiLayout): string {
  if (layout === 'redesign') {
    return 'Redesign';
  }
  if (layout === 'spine') {
    return 'Spine';
  }
  return 'Current';
}
