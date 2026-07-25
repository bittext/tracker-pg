import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  CompanyEarningsCalendarDto,
  CompanyEarningsEventDto,
  CompanyResearchCardDto,
  CompanyResearchDecisionStatus,
  CompanyResearchDetailDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

const STATUSES: { value: CompanyResearchDecisionStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'WATCHING', label: 'Watching' },
  { value: 'CONSIDERING', label: 'Considering' },
  { value: 'BOUGHT', label: 'Bought' },
  { value: 'PASSED', label: 'Passed' },
  { value: 'REVISIT', label: 'Revisit' },
];

@Component({
  selector: 'app-company-research-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './company-research-panel.component.html',
  styleUrl: './company-research-panel.component.scss',
})
export class CompanyResearchPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly statusChoices = STATUSES;
  readonly decisionChoices = STATUSES.filter((s) => s.value !== 'ALL') as {
    value: CompanyResearchDecisionStatus;
    label: string;
  }[];

  searchQuery = '';
  statusFilter: CompanyResearchDecisionStatus | 'ALL' = 'ALL';
  earningsWindowDays = 14;
  calendarDays = 7;
  largeCapOnly = true;
  addSymbol = '';

  calendar: CompanyEarningsCalendarDto | null = null;
  cards: CompanyResearchCardDto[] = [];
  detail: CompanyResearchDetailDto | null = null;
  selectedSymbol: string | null = null;

  calendarLoading = false;
  listLoading = false;
  detailLoading = false;
  saving = false;

  thesisDraft = '';
  tagsDraft = '';
  noteDraft = '';
  noteTagsDraft = '';

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.loadCalendar();
    this.loadList();
  }

  loadCalendar(): void {
    this.calendarLoading = true;
    const minCap = this.largeCapOnly ? 1_000_000_000 : null;
    this.api.companyResearchEarningsCalendar(null, this.calendarDays, minCap).subscribe({
      next: (c) => {
        this.calendar = c;
        this.calendarLoading = false;
      },
      error: (err) => {
        this.calendarLoading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  loadList(selectSymbol?: string | null): void {
    this.listLoading = true;
    this.api
      .companyResearchList(
        this.searchQuery,
        this.statusFilter === 'ALL' ? null : this.statusFilter,
        null,
      )
      .subscribe({
        next: (res) => {
          this.cards = res.cards ?? [];
          this.listLoading = false;
          const pick = selectSymbol ?? this.selectedSymbol;
          if (pick && this.cards.some((c) => c.symbol === pick)) {
            this.openDetail(pick);
          } else if (!this.selectedSymbol && this.cards.length) {
            this.openDetail(this.cards[0].symbol);
          } else if (this.selectedSymbol && !this.cards.some((c) => c.symbol === this.selectedSymbol)) {
            this.selectedSymbol = null;
            this.detail = null;
          }
        },
        error: (err) => {
          this.listLoading = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  openDetail(symbol: string): void {
    this.selectedSymbol = symbol;
    this.detailLoading = true;
    this.api.companyResearchDetail(symbol, true).subscribe({
      next: (d) => {
        this.detail = d;
        this.thesisDraft = d.card.thesis ?? '';
        this.tagsDraft = (d.card.tags ?? []).join(', ');
        this.detailLoading = false;
      },
      error: (err) => {
        this.detailLoading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  addFromInput(): void {
    const symbol = this.addSymbol.trim().toUpperCase();
    if (!symbol) {
      return;
    }
    this.saving = true;
    this.api.companyResearchUpsert({ symbol, decisionStatus: 'WATCHING' }).subscribe({
      next: (card) => {
        this.saving = false;
        this.addSymbol = '';
        this.snackBar.open(`Added ${card.symbol} to Watch`, 'OK', { duration: 3000 });
        this.loadList(card.symbol);
        this.loadCalendar();
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  addFromCalendar(event: CompanyEarningsEventDto): void {
    if (event.onWatchlist) {
      this.openDetail(event.symbol);
      return;
    }
    this.saving = true;
    this.api
      .companyResearchUpsert({
        symbol: event.symbol,
        companyName: event.companyName,
        decisionStatus: 'WATCHING',
      })
      .subscribe({
        next: (card) => {
          this.saving = false;
          this.snackBar.open(`Watching ${card.symbol}`, 'OK', { duration: 3000 });
          this.loadList(card.symbol);
          this.loadCalendar();
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  setDecision(status: CompanyResearchDecisionStatus): void {
    if (!this.selectedSymbol) {
      return;
    }
    this.saving = true;
    this.api.companyResearchUpdate(this.selectedSymbol, { decisionStatus: status }).subscribe({
      next: () => {
        this.saving = false;
        this.loadList(this.selectedSymbol);
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  saveThesisAndTags(): void {
    if (!this.selectedSymbol) {
      return;
    }
    this.saving = true;
    this.api
      .companyResearchUpdate(this.selectedSymbol, {
        thesis: this.thesisDraft,
        tags: this.parseTags(this.tagsDraft),
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.snackBar.open('Saved thesis & tags', 'OK', { duration: 2500 });
          this.loadList(this.selectedSymbol);
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  addNote(): void {
    if (!this.selectedSymbol || !this.noteDraft.trim()) {
      return;
    }
    this.saving = true;
    this.api
      .companyResearchAddNote(this.selectedSymbol, {
        noteText: this.noteDraft.trim(),
        tags: this.parseTags(this.noteTagsDraft),
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.noteDraft = '';
          this.noteTagsDraft = '';
          this.openDetail(this.selectedSymbol!);
          this.loadList(this.selectedSymbol);
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  deleteNote(noteId: number): void {
    if (!confirm('Delete this note?')) {
      return;
    }
    this.api.companyResearchDeleteNote(noteId).subscribe({
      next: () => {
        if (this.selectedSymbol) {
          this.openDetail(this.selectedSymbol);
          this.loadList(this.selectedSymbol);
        }
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  removeFromWatch(): void {
    if (!this.selectedSymbol) {
      return;
    }
    if (!confirm(`Remove ${this.selectedSymbol} from Watch? Notes will be deleted.`)) {
      return;
    }
    this.api.companyResearchDelete(this.selectedSymbol).subscribe({
      next: () => {
        this.selectedSymbol = null;
        this.detail = null;
        this.loadList();
        this.loadCalendar();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  eventsByDate(): { date: string; events: CompanyEarningsEventDto[] }[] {
    if (!this.calendar?.events?.length) {
      return [];
    }
    const map = new Map<string, CompanyEarningsEventDto[]>();
    for (const e of this.calendar.events) {
      const list = map.get(e.reportDate) ?? [];
      list.push(e);
      map.set(e.reportDate, list);
    }
    return [...map.entries()].map(([date, events]) => ({ date, events }));
  }

  filteredCards(): CompanyResearchCardDto[] {
    return this.cards;
  }

  nearEarningsCards(): CompanyResearchCardDto[] {
    const today = this.todayIso();
    const end = this.addDaysIso(today, this.earningsWindowDays || 14);
    return this.cards.filter(
      (c) => c.nextEarningsDate && c.nextEarningsDate >= today && c.nextEarningsDate <= end,
    );
  }

  statusLabel(status: string | null | undefined): string {
    const found = this.statusChoices.find((s) => s.value === status);
    return found?.label ?? status ?? '—';
  }

  quoteDeltaClass(delta: string | null | undefined): string {
    if (!delta) {
      return '';
    }
    if (delta === 'up') {
      return 'cr-delta--up';
    }
    if (delta === 'down') {
      return 'cr-delta--down';
    }
    return '';
  }

  private parseTags(raw: string): string[] {
    return raw
      .split(/[,|]/)
      .map((t) => t.trim())
      .filter(Boolean);
  }

  private todayIso(): string {
    const d = new Date();
    return d.toISOString().slice(0, 10);
  }

  private addDaysIso(iso: string, days: number): string {
    const d = new Date(iso + 'T12:00:00');
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
  }
}
