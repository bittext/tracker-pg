import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  ManagementMailAccountDto,
  ManagementMailDigestDto,
  ManagementMailDigestMessageDto,
  ManagementMailProvider,
  ManagementMailSlot,
} from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-management-mail-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSnackBarModule],
  templateUrl: './management-mail-panel.component.html',
  styleUrl: './management-mail-panel.component.scss',
})
export class ManagementMailPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly slots: { id: ManagementMailSlot; label: string; when: string }[] = [
    { id: 'MORNING', label: 'Morning', when: '7:00 AM' },
    { id: 'MIDDAY', label: 'Midday', when: '1:00 PM' },
    { id: 'EVENING', label: 'Evening', when: '7:00 PM' },
  ];

  readonly providers: { id: ManagementMailProvider; label: string }[] = [
    { id: 'GMAIL', label: 'Gmail' },
    { id: 'HOTMAIL', label: 'Hotmail' },
    { id: 'YAHOO', label: 'Yahoo' },
  ];

  accounts: ManagementMailAccountDto[] = [];
  history: ManagementMailDigestDto[] = [];
  shown: ManagementMailDigestDto[] = [];
  shownDate = '';
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
    this.api.listMailDigests().subscribe({
      next: (rows) => (this.history = rows),
      error: (e) => this.err('Could not load mail history', e),
    });
    this.loadDay(this.shownDate || undefined);
  }

  loadDay(on?: string): void {
    this.api.mailDay(on).subscribe({
      next: (rows) => {
        this.shown = rows;
        this.shownDate = rows[0]?.digestOn ?? on ?? '';
      },
      error: (e) => this.err('Could not load the mail summary', e),
    });
  }

  digestFor(slot: ManagementMailSlot): ManagementMailDigestDto | undefined {
    return this.shown.find((row) => row.slot === slot);
  }

  historyDates(): string[] {
    const dates: string[] = [];
    for (const row of this.history) {
      if (!dates.includes(row.digestOn)) {
        dates.push(row.digestOn);
      }
    }
    return dates;
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

  removeMessage(message: ManagementMailDigestMessageDto): void {
    this.api.deleteMailMessage(message.id).subscribe({
      next: () => this.loadDay(this.shownDate || undefined),
      error: (e) => this.err('Could not remove that message', e),
    });
  }

  removeDigest(digest: ManagementMailDigestDto): void {
    this.api.deleteMailDigest(digest.id).subscribe({
      next: () => {
        this.api.listMailDigests().subscribe({
          next: (rows) => {
            this.history = rows;
            const stillHere = rows.some((row) => row.digestOn === this.shownDate);
            this.shownDate = stillHere ? this.shownDate : (rows[0]?.digestOn ?? '');
            this.loadDay(this.shownDate || undefined);
          },
          error: (e) => this.err('Could not refresh mail history', e),
        });
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
        this.loadDay(digest.digestOn);
        this.api.listMailDigests().subscribe({ next: (rows) => (this.history = rows) });
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

  private err(prefix: string, error: unknown): void {
    this.snackBar.open(`${prefix}. ${formatHttpErrorDetail(error)}`, 'Dismiss', { duration: 7000 });
  }
}
