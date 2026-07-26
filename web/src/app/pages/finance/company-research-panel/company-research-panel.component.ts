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
import { RouterLink } from '@angular/router';
import { concat, of } from 'rxjs';
import { catchError, toArray } from 'rxjs/operators';
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

interface EarningsCalendarCell {
  type: 'pad' | 'day';
  trackKey: string;
  date: string;
  dayNumber: number | null;
  events: CompanyEarningsEventDto[];
  isToday: boolean;
  isSelected: boolean;
  hasWatched: boolean;
  hasBought: boolean;
}

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
    RouterLink,
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
  largeCapOnly = true;
  addSymbol = '';

  calendar: CompanyEarningsCalendarDto | null = null;
  calendarMonth = this.monthStartIso(new Date());
  selectedCalendarDate = this.todayIso();
  cards: CompanyResearchCardDto[] = [];
  detail: CompanyResearchDetailDto | null = null;
  selectedSymbol: string | null = null;

  calendarLoading = false;
  /** Soft refresh after a buffered/partial paint — calendar stays interactive. */
  calendarRefreshing = false;
  listLoading = false;
  detailLoading = false;
  saving = false;
  newsTab: 'all' | 'yahoo' = 'all';

  thesisDraft = '';
  tagsDraft = '';
  noteDraft = '';
  noteTagsDraft = '';
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;
  /** Client-side month buffer keyed by month|largeCap. */
  private readonly calendarBuffer = new Map<string, CompanyEarningsCalendarDto>();
  private calendarLoadGen = 0;

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.loadCalendar();
    this.loadList();
  }

  runSearch(): void {
    this.loadList();
  }

  onSearchInput(): void {
    if (this.searchDebounce) {
      clearTimeout(this.searchDebounce);
    }
    this.searchDebounce = setTimeout(() => this.loadList(), 350);
  }

  loadCalendar(): void {
    const gen = ++this.calendarLoadGen;
    const key = this.calendarBufferKey();
    const buffered = this.calendarBuffer.get(key);
    const minCap = this.largeCapOnly ? 1_000_000_000 : null;
    const days = this.daysInCalendarMonth();
    const month = this.calendarMonth;

    if (buffered) {
      this.applyCalendar(buffered, false);
      this.calendarLoading = false;
      this.calendarRefreshing = true;
    } else {
      this.calendarLoading = true;
      this.calendarRefreshing = false;
    }

    // Fast path: paint whatever the server already has buffered, then refresh in the background.
    this.api.companyResearchEarningsCalendar(month, days, minCap, true).subscribe({
      next: (c) => {
        if (gen !== this.calendarLoadGen) {
          return;
        }
        if (c.events?.length) {
          this.calendarBuffer.set(key, c);
          this.applyCalendar(c, false);
          this.calendarLoading = false;
          this.calendarRefreshing = true;
        }
      },
      error: () => {
        /* full load below is authoritative */
      },
    });

    this.api.companyResearchEarningsCalendar(month, days, minCap, false).subscribe({
      next: (c) => {
        if (gen !== this.calendarLoadGen) {
          return;
        }
        this.calendarBuffer.set(key, c);
        this.applyCalendar(c, true);
        this.calendarLoading = false;
        this.calendarRefreshing = false;
        this.prefetchAdjacentMonths(minCap);
      },
      error: (err) => {
        if (gen !== this.calendarLoadGen) {
          return;
        }
        this.calendarLoading = false;
        this.calendarRefreshing = false;
        if (!this.calendar) {
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        }
      },
    });
  }

  private applyCalendar(c: CompanyEarningsCalendarDto, preferSelectionFix: boolean): void {
    this.calendar = c;
    if (
      preferSelectionFix
      || !this.selectedCalendarDate.startsWith(this.calendarMonth.slice(0, 7))
    ) {
      if (!this.selectedCalendarDate.startsWith(this.calendarMonth.slice(0, 7))) {
        this.selectedCalendarDate = this.firstEventDate() ?? this.calendarMonth;
      }
    }
  }

  private calendarBufferKey(): string {
    return `${this.calendarMonth}|${this.largeCapOnly ? 'lg' : 'all'}`;
  }

  private prefetchAdjacentMonths(minCap: number | null): void {
    const [year, month] = this.calendarMonth.split('-').map(Number);
    for (const offset of [-1, 1]) {
      const d = new Date(year, month - 1 + offset, 1);
      const from = this.monthStartIso(d);
      const days = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
      const key = `${from}|${this.largeCapOnly ? 'lg' : 'all'}`;
      if (this.calendarBuffer.has(key)) {
        continue;
      }
      this.api.companyResearchEarningsCalendar(from, days, minCap, false).subscribe({
        next: (c) => this.calendarBuffer.set(key, c),
        error: () => undefined,
      });
    }
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
    this.newsTab = 'all';
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

  activeNews() {
    if (this.newsTab === 'yahoo') {
      return this.detail?.yahooNews ?? null;
    }
    return this.detail?.news ?? null;
  }

  yahooNewsUrl(symbol: string): string {
    return `https://finance.yahoo.com/quote/${encodeURIComponent(symbol.trim().toUpperCase())}/news`;
  }

  /** Near-earnings chip: open Watch detail and snap the calendar to that report date. */
  openNearEarnings(card: CompanyResearchCardDto, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    if (card.nextEarningsDate) {
      const earnMonth = card.nextEarningsDate.slice(0, 7) + '-01';
      if (earnMonth !== this.calendarMonth) {
        this.calendarMonth = earnMonth;
        this.selectedCalendarDate = card.nextEarningsDate;
        this.loadCalendar();
      } else {
        this.selectedCalendarDate = card.nextEarningsDate;
      }
    }
    this.openDetail(card.symbol);
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

  /** Companies on the selected calendar day that are not already on Watch. */
  unwatchedSelectedDayEvents(): CompanyEarningsEventDto[] {
    return this.selectedCalendarEvents().filter((e) => !e.onWatchlist);
  }

  /** Add every unwatched company from the selected day into Your Watch. */
  addAllFromSelectedDay(): void {
    const toAdd = this.unwatchedSelectedDayEvents();
    if (!toAdd.length) {
      this.snackBar.open('All companies on this day are already on Watch', 'OK', { duration: 3000 });
      return;
    }
    this.saving = true;
    concat(
      ...toAdd.map((event) =>
        this.api
          .companyResearchUpsert({
            symbol: event.symbol,
            companyName: event.companyName,
            decisionStatus: 'WATCHING',
          })
          .pipe(
            catchError((err) => {
              this.snackBar.open(
                `${event.symbol}: ${formatHttpErrorDetail(err)}`,
                'Dismiss',
                { duration: 6000 },
              );
              return of(null);
            }),
          ),
      ),
    )
      .pipe(toArray())
      .subscribe({
        next: (results) => {
          this.saving = false;
          const added = results.filter((r) => r != null).length;
          const failed = toAdd.length - added;
          const msg =
            failed > 0
              ? `Added ${added} of ${toAdd.length} to Watch (${failed} failed)`
              : `Added ${added} compan${added === 1 ? 'y' : 'ies'} to Watch`;
          this.snackBar.open(msg, 'OK', { duration: 4000 });
          const last = [...results].reverse().find((r) => r != null);
          this.loadList(last?.symbol ?? null);
          this.loadCalendar();
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
          this.loadList();
          this.loadCalendar();
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
        this.loadCalendar();
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  isBoughtEvent(event: CompanyEarningsEventDto): boolean {
    return (event.decisionStatus ?? '').toUpperCase() === 'BOUGHT';
  }

  calendarEventIcon(event: CompanyEarningsEventDto): string {
    if (this.isBoughtEvent(event)) {
      return 'shopping_bag';
    }
    return event.onWatchlist ? 'visibility' : 'add_circle_outline';
  }

  formatMarketCap(event: CompanyEarningsEventDto): string {
    const v = event.marketCapValue;
    if (v == null || Number.isNaN(Number(v)) || v <= 0) {
      return event.marketCap?.trim() || '—';
    }
    if (v >= 1e12) {
      return this.formatMarketCapUnit(v / 1e12, 'T');
    }
    if (v >= 1e9) {
      return this.formatMarketCapUnit(v / 1e9, 'B');
    }
    if (v >= 1e6) {
      return this.formatMarketCapUnit(v / 1e6, 'M');
    }
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(v);
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
    const events = this.filteredCalendarEvents();
    if (!events.length) {
      return [];
    }
    const map = new Map<string, CompanyEarningsEventDto[]>();
    for (const e of events) {
      const list = map.get(e.reportDate) ?? [];
      list.push(e);
      map.set(e.reportDate, list);
    }
    return [...map.entries()].map(([date, dayEvents]) => ({ date, events: dayEvents }));
  }

  calendarTitle(): string {
    return new Date(`${this.calendarMonth}T12:00:00`).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  previousCalendarMonth(): void {
    this.moveCalendarMonth(-1);
  }

  nextCalendarMonth(): void {
    this.moveCalendarMonth(1);
  }

  currentCalendarMonth(): void {
    this.calendarMonth = this.monthStartIso(new Date());
    this.selectedCalendarDate = this.todayIso();
    this.loadCalendar();
  }

  selectCalendarDate(date: string): void {
    this.selectedCalendarDate = date;
  }

  selectedCalendarEvents(): CompanyEarningsEventDto[] {
    return this.eventsForDate(this.selectedCalendarDate);
  }

  calendarRows(): EarningsCalendarCell[][] {
    const [year, month] = this.calendarMonth.split('-').map(Number);
    const firstDow = new Date(year, month - 1, 1).getDay();
    const days = new Date(year, month, 0).getDate();
    const today = this.todayIso();
    const flat: EarningsCalendarCell[] = [];

    for (let i = 0; i < firstDow; i++) {
      flat.push({
        type: 'pad',
        trackKey: `lead-${i}`,
        date: '',
        dayNumber: null,
        events: [],
        isToday: false,
        isSelected: false,
        hasWatched: false,
        hasBought: false,
      });
    }

    for (let day = 1; day <= days; day++) {
      const date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const events = this.eventsForDate(date);
      flat.push({
        type: 'day',
        trackKey: date,
        date,
        dayNumber: day,
        events,
        isToday: date === today,
        isSelected: date === this.selectedCalendarDate,
        hasWatched: events.some((e) => e.onWatchlist),
        hasBought: events.some((e) => this.isBoughtEvent(e)),
      });
    }

    let tail = 0;
    while (flat.length % 7 !== 0) {
      flat.push({
        type: 'pad',
        trackKey: `tail-${tail++}`,
        date: '',
        dayNumber: null,
        events: [],
        isToday: false,
        isSelected: false,
        hasWatched: false,
        hasBought: false,
      });
    }

    const rows: EarningsCalendarCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    return rows;
  }

  filteredCalendarCount(): number {
    return this.filteredCalendarEvents().length;
  }

  filteredCalendarEvents(): CompanyEarningsEventDto[] {
    const all = this.calendar?.events ?? [];
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      return all;
    }
    return all.filter((e) => this.calendarEventMatches(e, q));
  }

  filteredCards(): CompanyResearchCardDto[] {
    return this.cards;
  }

  nearEarningsCards(): CompanyResearchCardDto[] {
    const today = this.todayIso();
    const end = this.addDaysIso(today, this.earningsWindowDays || 14);
    return this.cards
      .filter((c) => c.nextEarningsDate && c.nextEarningsDate >= today && c.nextEarningsDate <= end)
      .sort((a, b) => {
        const byDate = (a.nextEarningsDate ?? '').localeCompare(b.nextEarningsDate ?? '');
        return byDate !== 0 ? byDate : a.symbol.localeCompare(b.symbol);
      });
  }

  isOpenPosition(card: CompanyResearchCardDto): boolean {
    return (card.decisionStatus ?? '').toUpperCase() === 'BOUGHT';
  }

  externalLinks(symbol: string): { label: string; url: string; icon: string; hint: string }[] {
    const s = encodeURIComponent((symbol ?? '').trim().toUpperCase());
    return [
      {
        label: 'Yahoo chart',
        url: `https://finance.yahoo.com/chart/${s}`,
        icon: 'show_chart',
        hint: 'Interactive price chart on Yahoo Finance',
      },
      {
        label: 'Yahoo quote',
        url: `https://finance.yahoo.com/quote/${s}`,
        icon: 'assessment',
        hint: 'Quote, financials, and profile on Yahoo Finance',
      },
      {
        label: 'Yahoo news',
        url: `https://finance.yahoo.com/quote/${s}/news`,
        icon: 'newspaper',
        hint: 'Latest Yahoo Finance headlines for this symbol',
      },
      {
        label: 'Finviz',
        url: `https://finviz.com/quote.ashx?t=${s}`,
        icon: 'insights',
        hint: 'Technical chart and peer comparison on Finviz',
      },
      {
        label: 'Sector heatmap',
        url: 'https://finviz.com/map.ashx?t=sec',
        icon: 'grid_view',
        hint: 'Market heatmap — every sector shaded by relative performance',
      },
      {
        label: 'Sector ranking',
        url: 'https://finviz.com/groups.ashx?g=sector&v=210&o=-perfytd',
        icon: 'leaderboard',
        hint: 'Sectors ranked against each other by performance',
      },
    ];
  }

  /** In-app Finviz Elite research panel for this symbol (options prefilled). */
  finvizElitePanelLink(symbol: string): { commands: string[]; queryParams: Record<string, string> } {
    const t = (symbol ?? '').trim().toUpperCase();
    return {
      commands: ['/markets/research'],
      queryParams: { tab: 'finviz', t },
    };
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

  private calendarEventMatches(e: CompanyEarningsEventDto, q: string): boolean {
    const haystack = [
      e.symbol,
      e.companyName,
      e.timing,
      e.epsForecast,
      e.lastYearEps,
      e.marketCap,
      e.fiscalQuarterEnding,
      e.lastYearReportDate,
      e.decisionStatus,
      e.reportDate,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(q);
  }

  private eventsForDate(date: string): CompanyEarningsEventDto[] {
    return this.filteredCalendarEvents()
      .filter((event) => event.reportDate === date)
      .sort((a, b) => (b.marketCapValue ?? 0) - (a.marketCapValue ?? 0));
  }

  private formatMarketCapUnit(value: number, unit: 'T' | 'B' | 'M'): string {
    const digits = unit === 'M' ? 1 : 2;
    return new Intl.NumberFormat(undefined, { maximumFractionDigits: digits }).format(value) + unit;
  }

  private firstEventDate(): string | null {
    return this.filteredCalendarEvents()[0]?.reportDate ?? null;
  }

  private moveCalendarMonth(offset: number): void {
    const [year, month] = this.calendarMonth.split('-').map(Number);
    this.calendarMonth = this.monthStartIso(new Date(year, month - 1 + offset, 1));
    this.selectedCalendarDate = this.calendarMonth;
    this.loadCalendar();
  }

  private daysInCalendarMonth(): number {
    const [year, month] = this.calendarMonth.split('-').map(Number);
    return new Date(year, month, 0).getDate();
  }

  private monthStartIso(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`;
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
