import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementMailAccountDto,
  ManagementMailDigestDto,
  ManagementMailProvider,
  ManagementMailSlot,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-management-mail-panel',
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
  ],
  templateUrl: './management-mail-panel.component.html',
  styleUrl: './management-mail-panel.component.scss',
})
export class ManagementMailPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly slots: { id: ManagementMailSlot; label: string; when: string }[] = [
    { id: 'MORNING', label: 'Morning', when: '7 PM – 7 AM' },
    { id: 'MIDDAY', label: 'Midday', when: '7 AM – 1 PM' },
    { id: 'EVENING', label: 'Evening', when: '1 PM – 7 PM' },
  ];

  readonly providers: { id: ManagementMailProvider; label: string }[] = [
    { id: 'GMAIL', label: 'Gmail' },
    { id: 'HOTMAIL', label: 'Hotmail' },
    { id: 'YAHOO', label: 'Yahoo' },
  ];

  private readonly monthNames = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];

  private readonly weekdayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  readonly weekLabels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  accounts: ManagementMailAccountDto[] = [];
  shown: ManagementMailDigestDto[] = [];
  shownDate = '';
  readingDates = new Set<string>();
  private anchorYear = new Date().getFullYear();
  private anchorMonth = Math.floor(new Date().getMonth() / 3) * 3;
  running = false;
  saving = false;
  showSetup = false;

  provider: ManagementMailProvider = 'GMAIL';
  emailAddress = '';
  appPassword = '';

  ngOnInit(): void {
    this.refreshAll();
  }

  refreshAll(): void {
    this.api.listMailAccounts().subscribe({
      next: (rows) => {
        this.accounts = rows;
        if (rows.length === 0) {
          this.showSetup = true;
        }
      },
      error: (e) => this.err('Could not load mailboxes', e),
    });
    this.loadDates();
    this.loadDay(this.shownDate || undefined);
  }

  loadDates(): void {
    this.api.mailDigestDates().subscribe({
      next: (rows) => {
        this.readingDates = new Set(rows);
      },
      error: (e) => this.err('Could not load mail history', e),
    });
  }

  loadDay(on?: string): void {
    this.api.mailDay(on).subscribe({
      next: (rows) => {
        this.shown = rows;
        this.shownDate = rows[0]?.digestOn ?? on ?? '';
        if (this.shownDate) {
          this.revealDate(this.shownDate);
        }
      },
      error: (e) => this.err('Could not load the mail summary', e),
    });
  }

  digestFor(slot: ManagementMailSlot): ManagementMailDigestDto | undefined {
    return this.shown.find((row) => row.slot === slot);
  }

  summaryParagraphs(summary: string): string[] {
    const parts = (summary || '')
      .split(/\n\s*\n/)
      .map((part) => part.trim())
      .filter((part) => part.length > 0);
    return parts.length ? parts : [summary];
  }

  dayTitle(): string {
    const now = new Date();
    const iso = this.shownDate || this.formatIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
    const [year, month, day] = iso.split('-').map(Number);
    const weekday = new Date(year, month - 1, day).getDay();
    return `${this.weekdayNames[weekday]}, ${this.monthNames[month - 1]} ${day}, ${year}`;
  }

  calendarMonths(): { key: string; title: string; weeks: { iso: string; label: string; inMonth: boolean }[][] }[] {
    return [0, 1, 2].map((offset) => this.monthGrid(this.anchorYear, this.anchorMonth + offset));
  }

  shiftDay(direction: -1 | 1): void {
    const now = new Date();
    const base = this.shownDate || this.formatIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
    const [year, month, day] = base.split('-').map(Number);
    const next = new Date(year, month - 1, day + direction);
    this.loadDay(this.formatIso(next.getFullYear(), next.getMonth() + 1, next.getDate()));
  }

  hasReading(iso: string): boolean {
    return this.readingDates.has(iso);
  }

  isToday(iso: string): boolean {
    const now = new Date();
    return iso === this.formatIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
  }

  save(): void {
    this.saving = true;
    this.api
      .saveMailAccount({
        provider: this.provider,
        emailAddress: this.emailAddress.trim(),
        appPassword: this.appPassword,
        enabled: true,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.appPassword = '';
          this.snackBar.open('Mailbox saved', 'Dismiss', { duration: 3000 });
          this.refreshAll();
        },
        error: (e) => {
          this.saving = false;
          this.err('Could not save the mailbox', e);
        },
      });
  }

  remove(account: ManagementMailAccountDto): void {
    this.api.deleteMailAccount(account.id).subscribe({
      next: () => this.refreshAll(),
      error: (e) => this.err('Could not remove the mailbox', e),
    });
  }

  removeDigest(digest: ManagementMailDigestDto): void {
    this.api.deleteMailDigest(digest.id).subscribe({
      next: () => {
        this.loadDates();
        this.loadDay(this.shownDate || undefined);
      },
      error: (e) => this.err('Could not remove that summary', e),
    });
  }

  test(account: ManagementMailAccountDto): void {
    this.api.testMailAccount(account.id).subscribe({
      next: (result) => this.snackBar.open(result.detail, 'Dismiss', { duration: 6000 }),
      error: (e) => this.err('Could not reach the mailbox', e),
    });
  }

  runNow(): void {
    this.running = true;
    this.api.runMailDigest().subscribe({
      next: (digest) => {
        this.running = false;
        this.shownDate = digest.digestOn;
        this.revealDate(digest.digestOn);
        this.loadDay(digest.digestOn);
        this.loadDates();
      },
      error: (e) => {
        this.running = false;
        this.err('Could not summarize mail', e);
      },
    });
  }

  providerLabel(provider: string): string {
    return this.providers.find((item) => item.id === provider)?.label ?? provider;
  }

  private revealDate(iso: string): void {
    const [year, month] = iso.split('-').map(Number);
    const point = year * 12 + (month - 1);
    const start = this.anchorYear * 12 + this.anchorMonth;
    if (point < start || point >= start + 3) {
      this.anchorYear = year;
      this.anchorMonth = Math.floor((month - 1) / 3) * 3;
    }
  }

  private monthGrid(year: number, month: number): {
    key: string;
    title: string;
    weeks: { iso: string; label: string; inMonth: boolean }[][];
  } {
    const cursor = new Date(year, month, 1);
    const gridYear = cursor.getFullYear();
    const gridMonth = cursor.getMonth();
    const firstWeekday = cursor.getDay();
    const daysInMonth = new Date(gridYear, gridMonth + 1, 0).getDate();
    const cells: { iso: string; label: string; inMonth: boolean }[] = [];
    for (let pad = firstWeekday; pad > 0; pad -= 1) {
      const day = new Date(gridYear, gridMonth, 1 - pad);
      cells.push({ iso: this.formatIso(day.getFullYear(), day.getMonth() + 1, day.getDate()), label: String(day.getDate()), inMonth: false });
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
      cells.push({ iso: this.formatIso(gridYear, gridMonth + 1, day), label: String(day), inMonth: true });
    }
    let extra = 1;
    // Always six week rows so the calendar keeps one height from month to month.
    while (cells.length < 42) {
      const day = new Date(gridYear, gridMonth, daysInMonth + extra);
      cells.push({ iso: this.formatIso(day.getFullYear(), day.getMonth() + 1, day.getDate()), label: String(day.getDate()), inMonth: false });
      extra += 1;
    }
    const weeks = [];
    for (let index = 0; index < cells.length; index += 7) {
      weeks.push(cells.slice(index, index + 7));
    }
    return {
      key: `${gridYear}-${gridMonth}`,
      title: `${this.monthNames[gridMonth]} ${gridYear}`,
      weeks,
    };
  }

  private formatIso(year: number, month: number, day: number): string {
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }

  private err(prefix: string, error: unknown): void {
    this.snackBar.open(`${prefix}. ${formatHttpErrorDetail(error)}`, 'Dismiss', { duration: 7000 });
  }
}
