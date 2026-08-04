import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  Observable,
  Subject,
  Subscription,
  catchError,
  finalize,
  of,
  switchMap,
  tap,
} from 'rxjs';
import {
  RobinhoodRhDailyTrackerDayDto,
  TradingJournalAttachmentDto,
  TradingJournalCalendarDayDto,
  TradingJournalDayDetailDto,
  TradingJournalEntryDto,
  TradingJournalEntrySummaryDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  TradingJournalImageGalleryDialogComponent,
  TradingJournalImageGalleryData,
} from './trading-journal-image-gallery-dialog.component';
import { SafeMarkdownPipe } from '../../../pipes/safe-markdown.pipe';

export interface TradingJournalCalCell {
  type: 'pad' | 'day';
  trackKey: string;
  date: string;
  label: string;
  hasJournal: boolean;
  isSelected: boolean;
  isToday: boolean;
  tone: 'up' | 'down' | null;
  heat: number;
  tooltip: string;
}

@Component({
  selector: 'app-trading-journal-panel',
  standalone: true,
  imports: [
    CommonModule,
    NgTemplateOutlet,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    SafeMarkdownPipe,
  ],
  templateUrl: './trading-journal-panel.component.html',
  styleUrl: './trading-journal-panel.component.scss',
})
export class TradingJournalPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(FinanceApiService);
  private readonly nav = inject(TradingJournalNavService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  reportYear = new Date().getFullYear();
  reportMonth = new Date().getMonth() + 1;
  searchQuery = '';

  entries: TradingJournalEntrySummaryDto[] = [];
  journalDates: string[] = [];
  /** Month Δ prior close cells from Daily Tracker scheduled closes. */
  monthCloseDays: TradingJournalCalendarDayDto[] = [];
  detail: TradingJournalDayDetailDto | null = null;
  selectedDate: string | null = null;

  listLoading = false;
  detailLoading = false;
  saving = false;
  uploading = false;

  /** Authenticated thumbnail blob URLs keyed by attachment id. */
  readonly imagePreviewUrls = new Map<number, string>();

  /** Fast calendar traversal — avoid refetching days already opened this session. */
  private readonly dayCache = new Map<string, TradingJournalDayDetailDto>();
  private readonly dayLoad$ = new Subject<{ date: string; create: boolean; force: boolean }>();
  private dayLoadSub: Subscription | null = null;
  private previewLoadToken = 0;
  private readonly calendarCache = new Map<string, TradingJournalCalCell[][]>();

  titleDraft = '';
  bodyDraft = '';
  tagsDraft = '';
  /** When false, show rendered journal; when true, show markdown editor. */
  editingJournal = false;

  refKind: 'SYMBOL' | 'URL' | 'NOTE' = 'SYMBOL';
  refSymbol = '';
  refUrl = '';
  refLabel = '';

  readonly weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  readonly yearMonthIndexes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

  readonly monthChoices: { value: number; label: string }[] = [
    { value: 0, label: 'All months' },
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' },
  ];

  /** Built-in Example 1 – Stock Trade body template. */
  static readonly STOCK_TRADE_TEMPLATE = `## Stock trade

| Field | Detail |
|:------|:-------|
| **Asset** | Company name & Symbol |
| **Strategy** | Breakout after consolidation |
| **Entry** | |
| **Stop-Loss** | |
| **Exit** | |
| **Notes** | Entered when price broke resistance; aligned with strong CPI data. |
| **Result** | |

`;

  ngOnInit(): void {
    this.dayLoadSub = this.dayLoad$
      .pipe(
        switchMap(({ date, create, force }) => {
          this.selectedDate = date;
          if (!force && !create) {
            const cached = this.dayCache.get(date);
            if (cached) {
              this.applyDetail(cached, true);
              this.detailLoading = false;
              this.prefetchAdjacent(date);
              return of(cached);
            }
          }
          this.detailLoading = true;
          const req: Observable<TradingJournalDayDetailDto> = create
            ? this.api.tradingJournalOpenDay(date)
            : this.api.tradingJournalGetDay(date);
          return req.pipe(
            tap((d) => {
              this.dayCache.set(d.snapshotDate, d);
              this.applyDetail(d, true);
              this.prefetchAdjacent(d.snapshotDate);
            }),
            catchError((err) => {
              this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
              return of(null);
            }),
            finalize(() => {
              this.detailLoading = false;
            }),
          );
        }),
      )
      .subscribe();

    const requested = this.nav.consumeRequestedDate();
    if (requested) {
      this.selectedDate = requested;
      const d = new Date(requested + 'T12:00:00');
      this.reportYear = d.getFullYear();
      this.reportMonth = d.getMonth() + 1;
      this.loadList(() => this.openDay(requested, true));
    } else {
      this.loadList(() => this.openTodayIfNeeded());
    }
  }

  ngOnDestroy(): void {
    this.dayLoadSub?.unsubscribe();
    this.revokeAllPreviews();
  }

  yearChoices(): number[] {
    const y = new Date().getFullYear();
    return [y, y - 1, y - 2];
  }

  loadList(after?: () => void): void {
    this.listLoading = true;
    const month = this.reportMonth === 0 ? null : this.reportMonth;
    this.api.tradingJournalList(this.reportYear, month, this.searchQuery).subscribe({
      next: (res) => {
        this.entries = res.entries ?? [];
        this.journalDates = res.journalDates ?? [];
        this.monthCloseDays = res.calendarDays ?? [];
        this.calendarCache.clear();
        this.listLoading = false;
        after?.();
      },
      error: (err) => {
        this.listLoading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  openTodayIfNeeded(): void {
    if (this.selectedDate) {
      return;
    }
    if (this.entries.length) {
      // List is oldest→newest; open the most recent entry by default.
      this.openDay(this.entries[this.entries.length - 1].snapshotDate, false);
    }
  }

  openDay(date: string, create: boolean, force = false): void {
    if (force) {
      this.invalidateDay(date);
    }
    this.dayLoad$.next({ date, create, force });
  }

  createOrOpenSelected(): void {
    const date = this.selectedDate || this.todayIso();
    this.invalidateDay(date);
    this.openDay(date, true, true);
    this.loadList();
  }

  openToday(): void {
    const date = this.todayIso();
    this.invalidateDay(date);
    this.openDay(date, true, true);
    this.loadList();
  }

  saveEntry(): void {
    if (!this.selectedDate || !this.detail?.entry) {
      if (this.selectedDate) {
        this.api.tradingJournalOpenDay(this.selectedDate).subscribe({
          next: () => this.persistUpdate(),
          error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
        });
        return;
      }
      return;
    }
    this.persistUpdate();
  }

  private persistUpdate(): void {
    if (!this.selectedDate) {
      return;
    }
    this.saving = true;
    this.api
      .tradingJournalUpdate(this.selectedDate, {
        title: this.titleDraft,
        bodyMarkdown: this.bodyDraft,
        tags: this.parseTags(this.tagsDraft),
      })
      .subscribe({
        next: (entry) => {
          this.saving = false;
          this.editingJournal = false;
          this.snackBar.open('Journal saved', 'OK', { duration: 2500 });
          this.patchEntry(entry);
          this.loadList();
        },
        error: (err) => {
          this.saving = false;
          this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
        },
      });
  }

  importSummary(): void {
    if (!this.selectedDate) {
      return;
    }
    this.ensureEntry(() => {
      this.api.tradingJournalImportSummary(this.selectedDate!).subscribe({
        next: (entry) => {
          this.snackBar.open('Call summary imported', 'OK', { duration: 3000 });
          this.patchEntry(entry);
          this.loadList();
        },
        error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
      });
    });
  }

  pinClose(): void {
    if (!this.selectedDate) {
      return;
    }
    this.ensureEntry(() => {
      this.api.tradingJournalPinClose(this.selectedDate!).subscribe({
        next: (entry) => {
          this.snackBar.open('9 PM CT close pinned', 'OK', { duration: 3000 });
          this.patchEntry(entry);
          this.loadList();
        },
        error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
      });
    });
  }

  generateAiDraft(): void {
    if (!this.selectedDate) {
      return;
    }
    this.saving = true;
    this.api.tradingJournalAiDraft(this.selectedDate).subscribe({
      next: (r) => {
        this.saving = false;
        const draft = r.draftMarkdown?.trim() ?? '';
        if (!draft) {
          return;
        }
        this.bodyDraft = this.bodyDraft.trim()
          ? `${this.bodyDraft.trim()}\n\n## AI wrap draft\n\n${draft}\n`
          : `## AI wrap draft\n\n${draft}\n`;
        this.snackBar.open('AI draft appended — review and save', 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  addRef(): void {
    if (!this.selectedDate) {
      return;
    }
    this.ensureEntry(() => {
      this.api
        .tradingJournalAddRef(this.selectedDate!, {
          kind: this.refKind,
          symbol: this.refSymbol,
          url: this.refUrl,
          label: this.refLabel,
        })
        .subscribe({
          next: () => {
            this.refSymbol = '';
            this.refUrl = '';
            this.refLabel = '';
            this.refreshDayKeepList();
          },
          error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
        });
    });
  }

  deleteRef(id: number): void {
    this.api.tradingJournalDeleteRef(id).subscribe({
      next: () => this.refreshDayKeepList(),
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    if (!files.length || !this.selectedDate) {
      input.value = '';
      return;
    }
    files.sort((a, b) => {
      const ta = Number.isFinite(a.lastModified) ? a.lastModified : 0;
      const tb = Number.isFinite(b.lastModified) ? b.lastModified : 0;
      if (ta !== tb) {
        return ta - tb;
      }
      return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
    });
    this.ensureEntry(() => {
      this.uploading = true;
      let i = 0;
      const step = (): void => {
        if (i >= files.length) {
          this.uploading = false;
          input.value = '';
          this.refreshDayKeepList();
          return;
        }
        this.api.tradingJournalAddAttachment(this.selectedDate!, files[i]).subscribe({
          next: () => {
            i += 1;
            step();
          },
          error: (err) => {
            this.uploading = false;
            input.value = '';
            this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
            this.refreshDayKeepList();
          },
        });
      };
      step();
    });
  }

  deleteAttachment(id: number): void {
    this.api.tradingJournalDeleteAttachment(id).subscribe({
      next: () => {
        this.revokePreview(id);
        this.refreshDayKeepList();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  isImageAttachment(att: TradingJournalAttachmentDto | null | undefined): boolean {
    if (!att) {
      return false;
    }
    const ct = att.contentType?.toLowerCase() ?? '';
    if (ct.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|bmp|svg|heic|heif)$/i.test(att.originalFilename);
  }

  imageAttachments(): TradingJournalAttachmentDto[] {
    return this.sortAttachmentsByCreatedAsc(
      (this.detail?.entry?.attachments ?? []).filter((a) => this.isImageAttachment(a)),
    );
  }

  otherAttachments(): TradingJournalAttachmentDto[] {
    return this.sortAttachmentsByCreatedAsc(
      (this.detail?.entry?.attachments ?? []).filter((a) => !this.isImageAttachment(a)),
    );
  }

  attachmentCapturedLabel(att: TradingJournalAttachmentDto): string {
    const raw = att.capturedAt || att.createdAt;
    if (!raw) {
      return '';
    }
    // Finder-style: “Monday, July 27, 2026 at 9:13 PM” (America/Chicago).
    const formatted = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Chicago',
      weekday: 'long',
      month: 'long',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
    }).format(new Date(raw));
    return formatted.replace(/, (\d{1,2}:\d{2}\s*[AP]M)$/i, ' at $1');
  }

  private sortAttachmentsByCreatedAsc(atts: TradingJournalAttachmentDto[]): TradingJournalAttachmentDto[] {
    return [...atts].sort((a, b) => {
      const ta = Date.parse(a.capturedAt || a.createdAt || '') || 0;
      const tb = Date.parse(b.capturedAt || b.createdAt || '') || 0;
      if (ta !== tb) {
        return ta - tb;
      }
      return a.id - b.id;
    });
  }

  previewUrl(att: TradingJournalAttachmentDto): string | null {
    return this.imagePreviewUrls.get(att.id) ?? null;
  }

  openImageGallery(att: TradingJournalAttachmentDto): void {
    const images = this.imageAttachments();
    const startIndex = Math.max(0, images.findIndex((a) => a.id === att.id));
    this.dialog.open<TradingJournalImageGalleryDialogComponent, TradingJournalImageGalleryData>(
      TradingJournalImageGalleryDialogComponent,
      {
        data: { images, startIndex },
        maxWidth: '96vw',
        width: 'min(52rem, 96vw)',
        autoFocus: false,
      },
    );
  }

  downloadAttachment(att: TradingJournalAttachmentDto): void {
    this.api.tradingJournalAttachmentBlob(att.id, 'attachment').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = att.originalFilename || 'attachment';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  openInDailyTracker(): void {
    if (this.selectedDate) {
      this.nav.openDailyTracker(this.selectedDate);
    }
  }

  openWatchForSymbol(symbol: string): void {
    // Research Watch lives under /markets/research — deep-link via query not wired; copy hint.
    this.snackBar.open(`Open Research → Watch and search ${symbol}`, 'OK', { duration: 5000 });
  }

  deleteEntry(): void {
    if (!this.selectedDate || !confirm(`Delete journal for ${this.selectedDate}?`)) {
      return;
    }
    const date = this.selectedDate;
    this.api.tradingJournalDelete(date).subscribe({
      next: () => {
        this.invalidateDay(date);
        this.selectedDate = null;
        this.detail = null;
        this.loadList();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  isYearView(): boolean {
    return this.reportMonth === 0;
  }

  calendarTitle(): string {
    const month = this.isYearView() ? new Date().getMonth() + 1 : this.reportMonth;
    return new Date(this.reportYear, month - 1, 1).toLocaleString(undefined, {
      month: 'long',
      year: 'numeric',
    });
  }

  monthCardTitle(month: number): string {
    return new Date(this.reportYear, month - 1, 1).toLocaleString(undefined, {
      month: 'short',
    });
  }

  /** Single-month rail calendar. */
  calendarRows(): TradingJournalCalCell[][] {
    const month = this.isYearView() ? new Date().getMonth() + 1 : this.reportMonth;
    return this.rowsForMonth(this.reportYear, month);
  }

  /** Year-view mini calendar for one month (1–12). */
  yearMonthRows(month: number): TradingJournalCalCell[][] {
    return this.rowsForMonth(this.reportYear, month);
  }

  private rowsForMonth(year: number, month: number): TradingJournalCalCell[][] {
    const today = this.todayIso();
    const key = `${year}-${month}-${this.selectedDate ?? ''}-${today}-${this.journalDates.join(',')}-${this.monthCloseDays
      .filter((d) => d.snapshotDate.startsWith(`${year}-${String(month).padStart(2, '0')}`))
      .map((d) => `${d.snapshotDate}:${d.changeFromPrevious}`)
      .join('|')}`;
    const cached = this.calendarCache.get(key);
    if (cached) {
      return cached;
    }

    const daysInMonth = new Date(year, month, 0).getDate();
    const firstDow = new Date(year, month - 1, 1).getDay();
    const journal = new Set(this.journalDates);
    const monthPrefix = `${year}-${String(month).padStart(2, '0')}`;
    const monthDays = this.monthCloseDays.filter((d) => d.snapshotDate.startsWith(monthPrefix));
    const byDate = new Map(monthDays.map((d) => [d.snapshotDate, d]));
    let maxAbs = 0;
    for (const d of monthDays) {
      if (!d.hasPreviousScheduledSnapshot) {
        continue;
      }
      maxAbs = Math.max(maxAbs, Math.abs(d.changeFromPrevious ?? 0));
    }

    const flat: TradingJournalCalCell[] = [];
    for (let i = 0; i < firstDow; i++) {
      flat.push(this.padCell(`pad-lead-${year}-${month}-${i}`));
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${monthPrefix}-${String(d).padStart(2, '0')}`;
      const cell = byDate.get(date);
      let tone: 'up' | 'down' | null = null;
      let heat = 0;
      let tooltip = date;
      if (cell?.hasPreviousScheduledSnapshot) {
        const chg = cell.changeFromPrevious ?? 0;
        if (chg > 0) {
          tone = 'up';
        } else if (chg < 0) {
          tone = 'down';
        }
        heat = maxAbs > 0 ? Math.round(18 + (Math.abs(chg) / maxAbs) * 62) : 28;
        const amount = chg.toLocaleString(undefined, {
          style: 'currency',
          currency: 'USD',
          maximumFractionDigits: 0,
          signDisplay: 'always',
        });
        const arrow = chg > 0 ? '▲' : chg < 0 ? '▼' : '●';
        tooltip = `${date} · ${arrow} ${amount}`;
      }
      flat.push({
        type: 'day',
        trackKey: `d-${date}`,
        date,
        label: String(d),
        hasJournal: journal.has(date),
        isSelected: this.selectedDate === date,
        isToday: today === date,
        tone,
        heat,
        tooltip,
      });
    }
    let pad = 0;
    while (flat.length % 7 !== 0) {
      flat.push(this.padCell(`pad-tail-${year}-${month}-${pad++}`));
    }
    const rows: TradingJournalCalCell[][] = [];
    for (let i = 0; i < flat.length; i += 7) {
      rows.push(flat.slice(i, i + 7));
    }
    this.calendarCache.set(key, rows);
    return rows;
  }

  private padCell(trackKey: string): TradingJournalCalCell {
    return {
      type: 'pad',
      trackKey,
      date: '',
      label: '',
      hasJournal: false,
      isSelected: false,
      isToday: false,
      tone: null,
      heat: 0,
      tooltip: '',
    };
  }

  private static readonly JOURNAL_FOCUS_ACCOUNT_SUFFIXES = new Set(['3370', '3550', '8696']);

  wrapAccounts(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined) {
    return wrap?.accounts ?? [];
  }

  wrapTrades(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined) {
    return (wrap?.trades ?? []).filter((t) => {
      const suffix = (t.accountSuffix ?? '').trim();
      return TradingJournalPanelComponent.JOURNAL_FOCUS_ACCOUNT_SUFFIXES.has(suffix);
    });
  }

  /** Deposits / withdrawals since prior 9 PM CT on focus accounts (3370, 3550, 8696). */
  wrapCashFlows(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined) {
    return (wrap?.accounts ?? []).filter((a) => {
      const suffix = (a.accountSuffix ?? '').trim();
      if (!TradingJournalPanelComponent.JOURNAL_FOCUS_ACCOUNT_SUFFIXES.has(suffix)) {
        return false;
      }
      const added = Number(a.periodAdded) || 0;
      const removed = Number(a.periodRemoved) || 0;
      return a.hasFlowActivity || added > 0 || removed > 0;
    });
  }

  wrapCashFlowTotals(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined): {
    deposited: number;
    withdrawn: number;
  } {
    let deposited = 0;
    let withdrawn = 0;
    for (const a of this.wrapCashFlows(wrap)) {
      deposited += Number(a.periodAdded) || 0;
      withdrawn += Number(a.periodRemoved) || 0;
    }
    return { deposited, withdrawn };
  }

  dayHoldings() {
    return this.detail?.holdings ?? [];
  }

  private ensureEntry(then: () => void): void {
    if (!this.selectedDate) {
      return;
    }
    if (this.detail?.entry) {
      then();
      return;
    }
    this.api.tradingJournalOpenDay(this.selectedDate).subscribe({
      next: (d) => {
        this.dayCache.set(d.snapshotDate, d);
        this.applyDetail(d, true);
        then();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  private refreshDayKeepList(): void {
    if (!this.selectedDate) {
      return;
    }
    this.invalidateDay(this.selectedDate);
    this.openDay(this.selectedDate, false, true);
    this.loadList();
  }

  private patchEntry(entry: TradingJournalEntryDto): void {
    if (!this.detail || this.detail.snapshotDate !== entry.snapshotDate) {
      return;
    }
    const next: TradingJournalDayDetailDto = {
      ...this.detail,
      entry,
    };
    this.dayCache.set(entry.snapshotDate, next);
    this.applyDetail(next, true);
  }

  private invalidateDay(date: string): void {
    this.dayCache.delete(date);
  }

  private prefetchAdjacent(date: string): void {
    // Defer so the visible day paints first; adjacent loads used to triple cold-path work.
    window.setTimeout(() => {
      if (this.selectedDate !== date) {
        return;
      }
      const base = new Date(date + 'T12:00:00');
      for (const delta of [-1, 1]) {
        const d = new Date(base);
        d.setDate(d.getDate() + delta);
        const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
        if (this.dayCache.has(iso)) {
          continue;
        }
        this.api.tradingJournalGetDay(iso).subscribe({
          next: (detail) => this.dayCache.set(detail.snapshotDate, detail),
        });
      }
    }, 1500);
  }

  private applyDetail(d: TradingJournalDayDetailDto, reloadPreviews: boolean): void {
    this.detail = d;
    this.selectedDate = d.snapshotDate;
    const e = d.entry;
    this.titleDraft = e?.title ?? '';
    this.bodyDraft = e?.bodyMarkdown ?? '';
    this.tagsDraft = (e?.tags ?? []).join(', ');
    // Empty day → start in editor; existing content → read-only preview.
    this.editingJournal = !(e?.bodyMarkdown ?? '').trim() && !(e?.title ?? '').trim();
    if (reloadPreviews) {
      this.scheduleImagePreviews(e?.attachments ?? []);
    }
  }

  startEditingJournal(): void {
    this.editingJournal = true;
  }

  cancelEditingJournal(): void {
    const e = this.detail?.entry;
    this.titleDraft = e?.title ?? '';
    this.bodyDraft = e?.bodyMarkdown ?? '';
    this.tagsDraft = (e?.tags ?? []).join(', ');
    this.editingJournal = false;
  }

  /** Insert Example 1 – Stock Trade template into the journal body. */
  applyStockTradeTemplate(): void {
    const tpl = TradingJournalPanelComponent.STOCK_TRADE_TEMPLATE;
    this.editingJournal = true;
    if (!this.titleDraft.trim()) {
      this.titleDraft = 'Stock trade';
    }
    if (!this.bodyDraft.trim()) {
      this.bodyDraft = tpl;
    } else if (!this.bodyDraft.includes('## Stock trade')) {
      this.bodyDraft = `${this.bodyDraft.trim()}\n\n${tpl}`;
    } else {
      this.snackBar.open('Stock trade section already in body — edit it in place', 'OK', {
        duration: 3500,
      });
      return;
    }
    if (!this.tagsDraft.toLowerCase().includes('stock')) {
      this.tagsDraft = this.tagsDraft.trim()
        ? `${this.tagsDraft.trim()}, stock-trade`
        : 'stock-trade';
    }
    this.snackBar.open('Stock trade template ready — fill in Entry / Stop / Exit / Result', 'OK', {
      duration: 4000,
    });
  }

  private scheduleImagePreviews(atts: TradingJournalAttachmentDto[]): void {
    const token = ++this.previewLoadToken;
    const images = atts.filter((a) => this.isImageAttachment(a));
    const keep = new Set(images.map((a) => a.id));
    for (const id of [...this.imagePreviewUrls.keys()]) {
      if (!keep.has(id)) {
        this.revokePreview(id);
      }
    }
    const pending = images.filter((a) => !this.imagePreviewUrls.has(a.id));
    if (!pending.length) {
      return;
    }
    const run = (): void => {
      if (token !== this.previewLoadToken) {
        return;
      }
      let i = 0;
      const step = (): void => {
        if (token !== this.previewLoadToken || i >= pending.length) {
          return;
        }
        const att = pending[i++];
        this.api.tradingJournalAttachmentBlob(att.id, 'inline').subscribe({
          next: (blob) => {
            if (token !== this.previewLoadToken) {
              return;
            }
            if (!this.detail?.entry?.attachments?.some((a) => a.id === att.id)) {
              return;
            }
            this.imagePreviewUrls.set(att.id, URL.createObjectURL(blob));
            step();
          },
          error: () => step(),
        });
      };
      step();
    };
    if (typeof requestIdleCallback === 'function') {
      requestIdleCallback(() => run(), { timeout: 1200 });
    } else {
      setTimeout(run, 0);
    }
  }

  private revokePreview(id: number): void {
    const url = this.imagePreviewUrls.get(id);
    if (url) {
      URL.revokeObjectURL(url);
      this.imagePreviewUrls.delete(id);
    }
  }

  private revokeAllPreviews(): void {
    for (const id of [...this.imagePreviewUrls.keys()]) {
      this.revokePreview(id);
    }
  }

  private parseTags(raw: string): string[] {
    return raw
      .split(/[,|]/)
      .map((t) => t.trim())
      .filter(Boolean);
  }

  private todayIso(): string {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'America/Chicago',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(new Date());
    const y = parts.find((p) => p.type === 'year')?.value;
    const m = parts.find((p) => p.type === 'month')?.value;
    const d = parts.find((p) => p.type === 'day')?.value;
    return `${y}-${m}-${d}`;
  }
}
