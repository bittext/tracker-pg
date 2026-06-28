import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import {
  DEFAULT_THEME_CONFIG,
  THEME_STORAGE_KEY,
  ThemeConfig,
  ThemeMode,
  ThemePreset,
  parseThemeConfig,
} from '../models/theme.models';

/** Resolved light/dark after applying system preference. */
export type ResolvedThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly browser = isPlatformBrowser(this.platformId);
  private media: MediaQueryList | null = null;
  private mediaListener: ((e: MediaQueryListEvent) => void) | null = null;

  private readonly configSignal = signal<ThemeConfig>(this.loadStored());

  readonly config = this.configSignal.asReadonly();

  readonly preset = computed(() => this.configSignal().preset);

  readonly mode = computed(() => this.configSignal().mode);

  readonly resolvedMode = computed<ResolvedThemeMode>(() =>
    this.resolveMode(this.configSignal().mode),
  );

  /** Call once at startup (AppComponent ngOnInit). */
  init(): void {
    if (!this.browser) {
      return;
    }
    this.applyToDom(this.configSignal());
    this.bindSystemPreference();
  }

  setPreset(preset: ThemePreset): void {
    this.update({ ...this.configSignal(), preset });
  }

  setMode(mode: ThemeMode): void {
    this.update({ ...this.configSignal(), mode });
  }

  setConfig(config: ThemeConfig): void {
    this.update(config);
  }

  private update(config: ThemeConfig): void {
    this.configSignal.set(config);
    if (this.browser) {
      try {
        localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(config));
      } catch {
        /* private browsing */
      }
      this.applyToDom(config);
      this.bindSystemPreference();
    }
  }

  private loadStored(): ThemeConfig {
    if (!this.browser) {
      return { ...DEFAULT_THEME_CONFIG };
    }
    try {
      const raw = localStorage.getItem(THEME_STORAGE_KEY);
      if (!raw) {
        return { ...DEFAULT_THEME_CONFIG };
      }
      return parseThemeConfig(JSON.parse(raw));
    } catch {
      return { ...DEFAULT_THEME_CONFIG };
    }
  }

  private resolveMode(mode: ThemeMode): ResolvedThemeMode {
    if (!this.browser || mode !== 'system') {
      return mode === 'dark' ? 'dark' : 'light';
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private applyToDom(config: ThemeConfig): void {
    const root = document.documentElement;
    const resolved = this.resolveMode(config.mode);
    root.dataset['themePreset'] = config.preset;
    root.dataset['themeMode'] = resolved;
    root.style.colorScheme = resolved;
    this.updateMetaThemeColor(config.preset, resolved);
  }

  private updateMetaThemeColor(preset: ThemePreset, mode: ResolvedThemeMode): void {
    const meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      return;
    }
    if (preset === 'openai') {
      meta.setAttribute('content', mode === 'dark' ? '#0d0d0d' : '#ffffff');
    } else {
      meta.setAttribute('content', mode === 'dark' ? '#0b3d40' : '#0f6d73');
    }
  }

  private bindSystemPreference(): void {
    if (!this.browser) {
      return;
    }
    if (this.mediaListener && this.media) {
      this.media.removeEventListener('change', this.mediaListener);
      this.mediaListener = null;
      this.media = null;
    }
    if (this.configSignal().mode !== 'system') {
      return;
    }
    this.media = window.matchMedia('(prefers-color-scheme: dark)');
    this.mediaListener = () => this.applyToDom(this.configSignal());
    this.media.addEventListener('change', this.mediaListener);
  }
}
