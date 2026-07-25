import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  RobinhoodRhDailyTrackerDayDto,
  TradingJournalDayDetailDto,
  TradingJournalEntrySummaryDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-trading-journal-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
  ],
  templateUrl: './trading-journal-panel.component.html',
  styleUrl: './trading-journal-panel.component.scss',
})
export class TradingJournalPanelComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly nav = inject(TradingJournalNavService);
  private readonly snackBar = inject(MatSnackBar);

  reportYear = new Date().getFullYear();
  reportMonth = new Date().getMonth() + 1;
  searchQuery = '';

  entries: TradingJournalEntrySummaryDto[] = [];
  journalDates: string[] = [];
  detail: TradingJournalDayDetailDto | null = null;
  selectedDate: string | null = null;

  listLoading = false;
  detailLoading = false;
  saving = false;

  titleDraft = '';
  bodyDraft = '';
  tagsDraft = '';
  processGrade: number | null = null;
  riskGrade: number | null = null;

  refKind: 'SYMBOL' | 'URL' | 'NOTE' = 'SYMBOL';
  refSymbol = '';
  refUrl = '';
  refLabel = '';

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

  readonly gradeChoices = [1, 2, 3, 4, 5];

  ngOnInit(): void {
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
      this.openDay(this.entries[0].snapshotDate, false);
    }
  }

  openDay(date: string, create: boolean): void {
    this.selectedDate = date;
    this.detailLoading = true;
    const req = create ? this.api.tradingJournalOpenDay(date) : this.api.tradingJournalGetDay(date);
    req.subscribe({
      next: (d) => {
        this.applyDetail(d);
        this.detailLoading = false;
        if (!d.entry && create === false) {
          // no entry yet — offer create via open
        }
      },
      error: (err) => {
        this.detailLoading = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 });
      },
    });
  }

  createOrOpenSelected(): void {
    const date = this.selectedDate || this.todayIso();
    this.openDay(date, true);
    this.loadList();
  }

  openToday(): void {
    this.openDay(this.todayIso(), true);
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
        processGrade: this.processGrade,
        riskGrade: this.riskGrade,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.snackBar.open('Journal saved', 'OK', { duration: 2500 });
          this.openDay(this.selectedDate!, false);
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
        next: () => {
          this.snackBar.open('Call summary imported', 'OK', { duration: 3000 });
          this.openDay(this.selectedDate!, false);
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
        next: () => {
          this.snackBar.open('9 PM CT close pinned', 'OK', { duration: 3000 });
          this.openDay(this.selectedDate!, false);
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
            this.openDay(this.selectedDate!, false);
            this.loadList();
          },
          error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
        });
    });
  }

  deleteRef(id: number): void {
    this.api.tradingJournalDeleteRef(id).subscribe({
      next: () => {
        if (this.selectedDate) {
          this.openDay(this.selectedDate, false);
          this.loadList();
        }
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.selectedDate) {
      return;
    }
    this.ensureEntry(() => {
      this.api.tradingJournalAddAttachment(this.selectedDate!, file).subscribe({
        next: () => {
          this.openDay(this.selectedDate!, false);
          this.loadList();
        },
        error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
      });
    });
  }

  deleteAttachment(id: number): void {
    this.api.tradingJournalDeleteAttachment(id).subscribe({
      next: () => {
        if (this.selectedDate) {
          this.openDay(this.selectedDate, false);
          this.loadList();
        }
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  attachmentHref(path: string): string {
    if (path.startsWith('http')) {
      return path;
    }
    return `${environment.apiBaseUrl}${path}`;
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
    this.api.tradingJournalDelete(this.selectedDate).subscribe({
      next: () => {
        this.selectedDate = null;
        this.detail = null;
        this.loadList();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  calendarDays(): { date: string; hasJournal: boolean; isSelected: boolean }[] {
    const month = this.reportMonth === 0 ? new Date().getMonth() + 1 : this.reportMonth;
    const year = this.reportYear;
    const daysInMonth = new Date(year, month, 0).getDate();
    const journal = new Set(this.journalDates);
    const out: { date: string; hasJournal: boolean; isSelected: boolean }[] = [];
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      out.push({
        date,
        hasJournal: journal.has(date),
        isSelected: this.selectedDate === date,
      });
    }
    return out;
  }

  wrapAccounts(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined) {
    return wrap?.accounts ?? [];
  }

  wrapTrades(wrap: RobinhoodRhDailyTrackerDayDto | null | undefined) {
    return wrap?.trades ?? [];
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
        this.applyDetail(d);
        then();
      },
      error: (err) => this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 8000 }),
    });
  }

  private applyDetail(d: TradingJournalDayDetailDto): void {
    this.detail = d;
    this.selectedDate = d.snapshotDate;
    const e = d.entry;
    this.titleDraft = e?.title ?? '';
    this.bodyDraft = e?.bodyMarkdown ?? '';
    this.tagsDraft = (e?.tags ?? []).join(', ');
    this.processGrade = e?.processGrade ?? null;
    this.riskGrade = e?.riskGrade ?? null;
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
