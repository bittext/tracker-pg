import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { THEME_MODES, THEME_PRESETS, ThemeMode, ThemePreset } from '../../models/theme.models';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-theme-settings',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
  ],
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
      <div class="theme-settings-menu" (click)="$event.stopPropagation()">
        <p class="theme-settings-menu__title">Appearance</p>

        <div class="theme-settings-menu__section">
          <span class="theme-settings-menu__label">Style</span>
          <mat-button-toggle-group
            [value]="theme.preset()"
            (change)="onPresetChange($event.value)"
            hideSingleSelectionIndicator
          >
            @for (p of presets; track p.id) {
              <mat-button-toggle [value]="p.id">{{ p.label }}</mat-button-toggle>
            }
          </mat-button-toggle-group>
          <p class="theme-settings-menu__hint">{{ activePresetDescription }}</p>
        </div>

        <div class="theme-settings-menu__section">
          <span class="theme-settings-menu__label">Color mode</span>
          <mat-button-toggle-group
            [value]="theme.mode()"
            (change)="onModeChange($event.value)"
            hideSingleSelectionIndicator
          >
            @for (m of modes; track m.id) {
              <mat-button-toggle [value]="m.id">{{ m.label }}</mat-button-toggle>
            }
          </mat-button-toggle-group>
        </div>
      </div>
    </mat-menu>
  `,
})
export class ThemeSettingsComponent {
  readonly theme = inject(ThemeService);
  readonly presets = THEME_PRESETS;
  readonly modes = THEME_MODES;

  get themeIcon(): string {
    const mode = this.theme.resolvedMode();
    if (this.theme.mode() === 'system') {
      return 'brightness_auto';
    }
    return mode === 'dark' ? 'dark_mode' : 'light_mode';
  }

  get activePresetDescription(): string {
    return this.presets.find((p) => p.id === this.theme.preset())?.description ?? '';
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
}
