import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TradingJournalNavService } from '../../services/trading-journal-nav.service';
import { UiLayoutService } from '../../services/ui-layout.service';

@Component({
  selector: 'app-insights-redesign-rail',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    @if (layout.usesInsightsRail()) {
      <div class="insights-rail" role="search" aria-label="Insights rail">
        <div class="insights-rail__accounts" role="group" aria-label="Account">
          @for (a of accounts; track a.suffix) {
            <button
              type="button"
              class="insights-rail__acct"
              [class.insights-rail__acct--active]="journalNav.insightsAccountSuffix() === a.suffix"
              (click)="setAccount(a.suffix)"
            >
              {{ a.label }}
            </button>
          }
        </div>

        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="insights-rail__year">
          <mat-label>Year</mat-label>
          <mat-select [value]="journalNav.insightsYear()" (selectionChange)="setYear($event.value)">
            @for (y of years; track y) {
              <mat-option [value]="y">{{ y }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" subscriptSizing="dynamic" class="insights-rail__jump">
          <mat-label>Jump ticker</mat-label>
          <input
            matInput
            name="insightsJump"
            [ngModel]="jumpDraft"
            (ngModelChange)="jumpDraft = $event"
            (keydown.enter)="jump()"
            placeholder="MRNA"
          />
        </mat-form-field>
        <button type="button" class="insights-rail__view" (click)="jump()">Open</button>

        <span class="insights-rail__accounts" role="group" aria-label="View">
          <button
            type="button"
            class="insights-rail__view"
            [class.insights-rail__view--active]="journalNav.analyticsTabIndex() === 1"
            (click)="journalNav.openDailyTracker()"
          >
            Month
          </button>
          <button
            type="button"
            class="insights-rail__view"
            [class.insights-rail__view--active]="journalNav.analyticsTabIndex() === 4"
            (click)="journalNav.openOwnershipHistory()"
          >
            Ownership
          </button>
          <button
            type="button"
            class="insights-rail__view"
            [class.insights-rail__view--active]="journalNav.analyticsTabIndex() === 2"
            (click)="journalNav.openExecutedTrades()"
          >
            Ledger
          </button>
          <button
            type="button"
            class="insights-rail__view"
            [class.insights-rail__view--active]="journalNav.analyticsTabIndex() === 5"
            (click)="journalNav.openJournal()"
          >
            Journal
          </button>
        </span>
      </div>
    }
  `,
})
export class InsightsRedesignRailComponent {
  readonly layout = inject(UiLayoutService);
  readonly journalNav = inject(TradingJournalNavService);
  jumpDraft = '';

  readonly accounts = [
    { suffix: '3370', label: 'Individual' },
    { suffix: '3550', label: 'Agentic' },
    { suffix: '8696', label: 'Ammu' },
    { suffix: '4123', label: 'Managed' },
  ] as const;

  readonly years = [new Date().getFullYear(), new Date().getFullYear() - 1, new Date().getFullYear() - 2];

  setAccount(suffix: string): void {
    const next = this.journalNav.insightsAccountSuffix() === suffix ? '' : suffix;
    this.journalNav.insightsAccountSuffix.set(next);
  }

  setYear(year: number): void {
    if (year) {
      this.journalNav.insightsYear.set(year);
    }
  }

  jump(): void {
    const sym = this.jumpDraft.trim().toUpperCase();
    if (!sym) {
      return;
    }
    this.journalNav.insightsSymbol.set(sym);
    this.journalNav.openOwnershipHistory();
  }
}
