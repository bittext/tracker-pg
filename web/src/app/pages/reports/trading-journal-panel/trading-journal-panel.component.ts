import { CommonModule } from '@angular/common';
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
  RobinhoodRhDailyTrackerDayDto,
  TradingJournalAttachmentDto,
  TradingJournalDayDetailDto,
  TradingJournalEntrySummaryDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { TradingJournalNavService } from '../../../services/trading-journal-nav.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  TradingJournalImageGalleryDialogComponent,
  TradingJournalImageGalleryData,
} from './trading-journal-image-gallery-dialog.component';

@Component({
  selector: 'app-trading-journal-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
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
  detail: TradingJournalDayDetailDto | null = null;
  selectedDate: string | null = null;

  listLoading = false;
  detailLoading = false;
  saving = false;
  uploading = false;

  /** Authenticated thumbnail blob URLs keyed by attachment id. */
  readonly imagePreviewUrls = new Map<number, string>();

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

  ngOnDestroy(): void {
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
    const files = input.files ? Array.from(input.files) : [];
    if (!files.length || !this.selectedDate) {
      input.value = '';
      return;
    }
    this.ensureEntry(() => {
      this.uploading = true;
      let i = 0;
      const step = (): void => {
        if (i >= files.length) {
          this.uploading = false;
          input.value = '';
          this.openDay(this.selectedDate!, false);
          this.loadList();
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
            this.openDay(this.selectedDate!, false);
            this.loadList();
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
        if (this.selectedDate) {
          this.openDay(this.selectedDate, false);
          this.loadList();
        }
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
    return (this.detail?.entry?.attachments ?? []).filter((a) => this.isImageAttachment(a));
  }

  otherAttachments(): TradingJournalAttachmentDto[] {
    return (this.detail?.entry?.attachments ?? []).filter((a) => !this.isImageAttachment(a));
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
    this.syncImagePreviews(e?.attachments ?? []);
  }

  private syncImagePreviews(atts: TradingJournalAttachmentDto[]): void {
    const keep = new Set(atts.filter((a) => this.isImageAttachment(a)).map((a) => a.id));
    for (const id of [...this.imagePreviewUrls.keys()]) {
      if (!keep.has(id)) {
        this.revokePreview(id);
      }
    }
    for (const att of atts) {
      if (!this.isImageAttachment(att) || this.imagePreviewUrls.has(att.id)) {
        continue;
      }
      this.api.tradingJournalAttachmentBlob(att.id, 'inline').subscribe({
        next: (blob) => {
          if (!this.detail?.entry?.attachments?.some((a) => a.id === att.id)) {
            return;
          }
          this.imagePreviewUrls.set(att.id, URL.createObjectURL(blob));
        },
      });
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
