import { Injectable, computed, inject, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import {
  DEFAULT_UI_LAYOUT,
  UI_LAYOUT_CYCLE,
  UI_LAYOUT_STORAGE_KEY,
  UiLayout,
  parseUiLayout,
  uiLayoutLabel,
} from '../models/ui-layout.models';

@Injectable({ providedIn: 'root' })
export class UiLayoutService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly browser = isPlatformBrowser(this.platformId);
  private readonly layoutSignal = signal<UiLayout>(this.loadStored());

  readonly layout = this.layoutSignal.asReadonly();
  readonly isRedesign = computed(() => this.layoutSignal() === 'redesign');
  readonly isSpine = computed(() => this.layoutSignal() === 'spine');
  readonly usesInsightsRail = computed(() => {
    const layout = this.layoutSignal();
    return layout === 'redesign' || layout === 'spine';
  });

  init(): void {
    if (!this.browser) {
      return;
    }
    this.applyToDom(this.layoutSignal());
  }

  setLayout(layout: UiLayout): void {
    this.layoutSignal.set(layout);
    if (!this.browser) {
      return;
    }
    try {
      localStorage.setItem(UI_LAYOUT_STORAGE_KEY, layout);
    } catch {
      /* private browsing */
    }
    this.applyToDom(layout);
  }

  toggle(): void {
    const current = this.layoutSignal();
    const index = UI_LAYOUT_CYCLE.indexOf(current);
    this.setLayout(UI_LAYOUT_CYCLE[(index + 1) % UI_LAYOUT_CYCLE.length]);
  }

  layoutLabel(): string {
    return uiLayoutLabel(this.layoutSignal());
  }

  private loadStored(): UiLayout {
    if (!this.browser) {
      return DEFAULT_UI_LAYOUT;
    }
    try {
      return parseUiLayout(localStorage.getItem(UI_LAYOUT_STORAGE_KEY));
    } catch {
      return DEFAULT_UI_LAYOUT;
    }
  }

  private applyToDom(layout: UiLayout): void {
    document.documentElement.dataset['uiLayout'] = layout;
  }
}
