/** Site chrome: current v16, or the ledger redesign. */
export type UiLayout = 'current' | 'redesign';

export const UI_LAYOUT_STORAGE_KEY = 'tracker.ui-layout.v1';

export const DEFAULT_UI_LAYOUT: UiLayout = 'current';

export function parseUiLayout(raw: unknown): UiLayout {
  return raw === 'redesign' ? 'redesign' : 'current';
}
