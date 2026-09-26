import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { THEME_MODES, THEME_PRESETS, ThemeMode, ThemePreset } from '../../models/theme.models';
import { UiLayout } from '../../models/ui-layout.models';
import { ThemeService } from '../../services/theme.service';
import { UiLayoutService } from '../../services/ui-layout.service';

@Component({
  selector: 'app-theme-settings',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule],
  template: `
    <button
      mat-icon-button
      type="button"
      class="theme-trigger"
      [matMenuTriggerFor]="themeMenu"
      aria-label="Appearance settings"
      matTooltip="Appearance"
    >
      <mat-icon>{{ themeIcon }}</mat-icon>
    </button>

    <mat-menu #themeMenu="matMenu" panelClass="theme-settings-menu-panel" xPosition="before">
      <div class="theme-settings-menu" (click)="$event.stopPropagation()" role="presentation">
        <p class="theme-settings-menu__title">Appearance</p>

        <div class="theme-settings-menu__section">
          <span class="theme-settings-menu__label" id="theme-style-label">Style</span>
          <div class="theme-settings-menu__presets" role="listbox" aria-labelledby="theme-style-label">
            @for (p of presets; track p.id) {
              <button
                type="button"
                class="theme-settings-menu__preset"
                role="option"
                [attr.aria-selected]="theme.preset() === p.id"
                [class.theme-settings-menu__preset--active]="theme.preset() === p.id"
                (click)="onPresetChange(p.id)"
              >
                <span class="theme-settings-menu__preset-swatch" [attr.data-preset]="p.id" aria-hidden="true"></span>
                <span class="theme-settings-menu__preset-copy">
                  <span class="theme-settings-menu__preset-name">{{ p.label }}</span>
                  <span class="theme-settings-menu__preset-desc">{{ p.description }}</span>
                </span>
                @if (theme.preset() === p.id) {
                  <mat-icon class="theme-settings-menu__check" aria-hidden="true">check</mat-icon>
                }
              </button>
            }
          </div>
        </div>

        <div class="theme-settings-menu__section">
          <span class="theme-settings-menu__label" id="ui-layout-label">Layout</span>
          <div class="theme-settings-menu__modes" role="group" aria-labelledby="ui-layout-label">
            <button
              type="button"
              class="theme-settings-menu__mode"
              [class.theme-settings-menu__mode--active]="layout.layout() === 'current'"
              [attr.aria-pressed]="layout.layout() === 'current'"
              (click)="onLayoutChange('current')"
            >
              <mat-icon aria-hidden="true">view_agenda</mat-icon>
              <span>Current</span>
            </button>
            <button
              type="button"
              class="theme-settings-menu__mode"
              [class.theme-settings-menu__mode--active]="layout.isRedesign()"
              [attr.aria-pressed]="layout.isRedesign()"
              (click)="onLayoutChange('redesign')"
            >
              <mat-icon aria-hidden="true">space_dashboard</mat-icon>
              <span>Redesign</span>
            </button>
            <button
              type="button"
              class="theme-settings-menu__mode"
              [class.theme-settings-menu__mode--active]="layout.isSpine()"
              [attr.aria-pressed]="layout.isSpine()"
              (click)="onLayoutChange('spine')"
            >
              <mat-icon aria-hidden="true">view_timeline</mat-icon>
              <span>Spine</span>
            </button>
          </div>
        </div>

        <div class="theme-settings-menu__section">
          <span class="theme-settings-menu__label" id="theme-mode-label">Color mode</span>
          <div class="theme-settings-menu__modes" role="group" aria-labelledby="theme-mode-label">
            @for (m of modes; track m.id) {
              <button
                type="button"
                class="theme-settings-menu__mode"
                [class.theme-settings-menu__mode--active]="theme.mode() === m.id"
                [attr.aria-pressed]="theme.mode() === m.id"
                (click)="onModeChange(m.id)"
              >
                <mat-icon aria-hidden="true">{{ modeIcon(m.id) }}</mat-icon>
                <span>{{ m.label }}</span>
              </button>
            }
          </div>
        </div>
      </div>
    </mat-menu>
  `,
})
export class ThemeSettingsComponent {
  readonly theme = inject(ThemeService);
  readonly layout = inject(UiLayoutService);
  readonly presets = THEME_PRESETS;
  readonly modes = THEME_MODES;

  get themeIcon(): string {
    const mode = this.theme.resolvedMode();
    if (this.theme.mode() === 'system') {
      return 'brightness_auto';
    }
    return mode === 'dark' ? 'dark_mode' : 'light_mode';
  }

  modeIcon(id: ThemeMode): string {
    if (id === 'system') {
      return 'brightness_auto';
    }
    return id === 'dark' ? 'dark_mode' : 'light_mode';
  }

  onPresetChange(value: ThemePreset): void {
    if (value) {
      this.theme.setPreset(value);
    }
  }

  onModeChange(value: ThemeMode): void {
    if (value) {
      this.theme.setMode(value);
    }
  }

  onLayoutChange(value: UiLayout): void {
    this.layout.setLayout(value);
  }
}
