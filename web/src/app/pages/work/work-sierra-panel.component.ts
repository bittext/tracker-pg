import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SafeMarkdownPipe } from '../../pipes/safe-markdown.pipe';
import { SIERRA_CATALOG, SierraModule } from './work-sierra-catalog';

@Component({
  selector: 'app-work-sierra-panel',
  standalone: true,
  imports: [CommonModule, SafeMarkdownPipe],
  templateUrl: './work-sierra-panel.component.html',
  styleUrl: './work-sierra-panel.component.scss',
})
export class WorkSierraPanelComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly catalog = SIERRA_CATALOG;
  selected: SierraModule = SIERRA_CATALOG[0].modules[0];
  body = '';
  loading = false;
  error = '';

  ngOnInit(): void {
    this.open(this.selected);
  }

  open(mod: SierraModule): void {
    this.selected = mod;
    this.loading = true;
    this.error = '';
    this.http.get(mod.assetPath, { responseType: 'text' }).subscribe({
      next: (text) => {
        this.body = text;
        this.loading = false;
      },
      error: () => {
        this.body = '';
        this.loading = false;
        this.error = 'Could not load this Sierra module.';
      },
    });
  }

  isActive(mod: SierraModule): boolean {
    return this.selected.path === mod.path;
  }
}
