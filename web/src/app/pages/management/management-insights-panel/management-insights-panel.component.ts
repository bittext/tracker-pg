import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  ManagementAutoPaymentDto,
  ManagementDocumentDto,
  ManagementDueReportDto,
  ManagementDueReportRowDto,
  ManagementSubscriptionDto,
  TravelPlaceMapDto,
  TravelTripSummaryDto,
} from '../../../models/management.models';
import { ReportCalendarEntryDto, reportCalendarTypeLabel } from '../../../models/report-calendar.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { ReportCalendarApiService } from '../../../services/report-calendar-api.service';

type InsightTopic = 'overview' | 'due' | 'calendar' | 'accounts' | 'subscriptions' | 'autopay' | 'documents' | 'travel';
type InsightForm = 'brief' | 'list' | 'grouped';

interface SafeAccount {
  folder: string;
  itemName: string;
  username: string;
  website: string;
  notes: string;
  hasPassword: boolean;
  hasAuthenticator: boolean;
}

interface InsightGroup {
  title: string;
  lines: string[];
}

interface InsightSection {
  title: string;
  paragraphs: string[];
  stats: { label: string; value: string }[];
  columns: string[];
  rows: string[][];
  groups: InsightGroup[];
}

interface InsightBundle {
  due: ManagementDueReportDto | null;
  calendar: ReportCalendarEntryDto[];
  calendarLabels: ReadonlyArray<{ code: string; label: string }>;
  accounts: SafeAccount[];
  subscriptions: ManagementSubscriptionDto[];
  autopay: ManagementAutoPaymentDto[];
  documents: ManagementDocumentDto[];
  trips: TravelTripSummaryDto[];
  places: TravelPlaceMapDto[];
  failed: string[];
}

@Component({
  selector: 'app-management-insights-panel',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './management-insights-panel.component.html',
  styleUrl: './management-insights-panel.component.scss',
})
export class ManagementInsightsPanelComponent {
  private readonly api = inject(ManagementApiService);
  private readonly calendarApi = inject(ReportCalendarApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly topics: { id: InsightTopic; label: string }[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'due', label: 'Due' },
    { id: 'calendar', label: 'Calendar' },
    { id: 'accounts', label: 'Accounts' },
    { id: 'subscriptions', label: 'Subscriptions' },
    { id: 'autopay', label: 'Auto payments' },
    { id: 'documents', label: 'Documents' },
    { id: 'travel', label: 'Travel' },
  ];

  readonly forms: { id: InsightForm; label: string }[] = [
    { id: 'brief', label: 'Brief' },
    { id: 'list', label: 'List' },
    { id: 'grouped', label: 'Grouped' },
  ];

  year = new Date().getFullYear();
  topic: InsightTopic = 'overview';
  form: InsightForm = 'brief';
  loading = false;
  failed: string[] = [];

  private bundle: InsightBundle | null = null;

  reload(): void {
    const year = this.year;
    const month = year === new Date().getFullYear() ? new Date().getMonth() + 1 : 12;
    const from = `${year}-01-01`;
    const to = `${year}-12-31`;
    const failed: string[] = [];
    this.loading = true;
    forkJoin({
      due: this.api.dueReports(year, month).pipe(catchError(() => {
        failed.push('Due');
        return of(null);
      })),
      calendar: this.calendarApi.list(from, to, null).pipe(catchError(() => {
        failed.push('Calendar');
        return of([] as ReportCalendarEntryDto[]);
      })),
      types: this.api.listCalendarTypes().pipe(catchError(() => of([]))),
      accounts: this.api.listAccounts().pipe(catchError(() => {
        failed.push('Accounts');
        return of([]);
      })),
      subscriptions: this.api.listSubscriptions().pipe(catchError(() => {
        failed.push('Subscriptions');
        return of([]);
      })),
      autopay: this.api.listAutoPayments().pipe(catchError(() => {
        failed.push('Auto payments');
        return of([]);
      })),
      documents: this.api.listDocuments().pipe(catchError(() => {
        failed.push('Documents');
        return of([]);
      })),
      trips: this.api.listTravelTrips().pipe(catchError(() => {
        failed.push('Travel');
        return of([]);
      })),
      places: this.api.travelPlacesForMap(from, to).pipe(catchError(() => {
        if (!failed.includes('Travel')) {
          failed.push('Travel places');
        }
        return of([] as TravelPlaceMapDto[]);
      })),
    }).subscribe({
      next: (data) => {
        this.bundle = {
          due: data.due,
          calendar: data.calendar ?? [],
          calendarLabels: data.types ?? [],
          accounts: (data.accounts ?? []).map((row) => ({
            folder: row.folder || '',
            itemName: row.itemName || '',
            username: row.username || '',
            website: row.website || '',
            notes: row.notes || '',
            hasPassword: !!(row.password || '').trim(),
            hasAuthenticator: !!(row.authenticatorKey || '').trim(),
          })),
          subscriptions: data.subscriptions ?? [],
          autopay: data.autopay ?? [],
          documents: data.documents ?? [],
          trips: data.trips ?? [],
          places: data.places ?? [],
          failed,
        };
        this.failed = failed;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.failed = ['Management'];
        this.snackBar.open('The Management report could not be loaded.', 'Dismiss', { duration: 4000 });
      },
    });
  }

  shiftYear(delta: number): void {
    this.year += delta;
    this.reload();
  }

  setTopic(topic: InsightTopic): void {
    this.topic = topic;
  }

  setForm(form: InsightForm): void {
    this.form = form;
  }

  get sections(): InsightSection[] {
    return this.bundle ? this.buildSections(this.bundle) : [];
  }

  get activeSections(): InsightSection[] {
    if (this.topic === 'overview') {
      return this.sections;
    }
    return this.sections.filter((section) => section.title === this.topicTitle(this.topic));
  }

  get reportTitle(): string {
    return this.topic === 'overview' ? `Management in ${this.year}` : `${this.topicTitle(this.topic)} in ${this.year}`;
  }

  get groupHint(): string {
    switch (this.topic) {
      case 'due':
        return 'Grouped by month.';
      case 'calendar':
        return 'Grouped by calendar type.';
      case 'accounts':
        return 'Grouped by folder. Passwords and authenticator keys stay off this report.';
      case 'subscriptions':
        return 'Grouped by status.';
      case 'autopay':
        return 'Grouped by status.';
      case 'documents':
        return 'Grouped by document type.';
      case 'travel':
        return 'Grouped by trip.';
      default:
        return 'Each area in its own section.';
    }
  }

  downloadCsv(): void {
    const sections = this.activeSections;
    if (!sections.length) {
      return;
    }
    const lines: string[] = [];
    if (this.topic === 'overview') {
      lines.push(csvLine(['Area', 'On file', 'In short']));
      for (const section of sections) {
        lines.push(csvLine([section.title, section.stats.map((stat) => `${stat.label}: ${stat.value}`).join('; '), section.paragraphs[0] ?? '']));
      }
    } else {
      const section = sections[0];
      lines.push(csvLine(section.columns));
      for (const row of section.rows) {
        lines.push(csvLine(row));
      }
    }
    saveText(`management-insights-${this.topic}-${this.year}.csv`, lines.join('\n'), 'text/csv;charset=utf-8');
  }

  downloadText(): void {
    const parts = [this.reportTitle, ''];
    for (const section of this.activeSections) {
      if (this.topic === 'overview') {
        parts.push(section.title);
      }
      parts.push(...section.paragraphs, '');
    }
    if (this.topic === 'accounts') {
      parts.push('Passwords and authenticator keys are left off this reading.');
    }
    saveText(`management-insights-${this.topic}-${this.year}.txt`, parts.join('\n').trim() + '\n', 'text/plain;charset=utf-8');
  }

  printReport(): void {
    const popup = window.open('', '_blank', 'noopener,noreferrer');
    if (!popup) {
      this.snackBar.open('Allow pop-ups to print this report.', 'Dismiss', { duration: 4000 });
      return;
    }
    popup.document.write(this.printHtml());
    popup.document.close();
    popup.focus();
    popup.print();
  }

  private topicTitle(topic: InsightTopic): string {
    return this.topics.find((item) => item.id === topic)?.label ?? 'Management';
  }

  private buildSections(bundle: InsightBundle): InsightSection[] {
    const documents = bundle.documents.filter((doc) => (doc.createdAt || '').startsWith(String(this.year)));
    const trips = bundle.trips.filter((trip) => overlapsYear(trip.startDate, trip.endDate, this.year));
    return [
      this.dueSection(bundle.due),
      this.calendarSection(bundle.calendar, bundle.calendarLabels),
      this.accountSection(bundle.accounts),
      this.subscriptionSection(bundle.subscriptions),
      this.autopaySection(bundle.autopay),
      this.documentSection(documents, bundle.documents.length - documents.length),
      this.travelSection(trips, bundle.places),
    ];
  }

  private dueSection(report: ManagementDueReportDto | null): InsightSection {
    const rows = [...(report?.rows ?? [])].sort((a, b) => a.date.localeCompare(b.date) || a.counterparty.localeCompare(b.counterparty));
    const year = report?.yearTotals;
    const paragraphs: string[] = [];
    if (!report || !year) {
      paragraphs.push(`Due for ${this.year} could not be read.`);
    } else if (!rows.length) {
      paragraphs.push(`Nothing is on the Due calendar for ${this.year}.`);
    } else {
      paragraphs.push(
        `${this.year} has ${countPhrase(rows.length, 'Due date')} on the calendar. ${money(year.paid)} is marked paid and ${money(year.received)} is marked received.`,
      );
      paragraphs.push(
        `${money(year.openPayable)} is still open to pay across ${countPhrase(year.openPayableCount, 'date')}, and ${money(year.openReceivable)} is still open to receive across ${countPhrase(year.openReceivableCount, 'date')}.`,
      );
      if (year.biggestOutName && year.biggestOutAmount != null) {
        paragraphs.push(`The largest amount paid is ${year.biggestOutName} at ${money(year.biggestOutAmount)}.`);
      }
      if (year.biggestInName && year.biggestInAmount != null) {
        paragraphs.push(`The largest amount received is ${year.biggestInName} at ${money(year.biggestInAmount)}.`);
      }
    }
    const byMonth = new Map<string, ManagementDueReportRowDto[]>();
    for (const row of rows) {
      const key = row.date.slice(0, 7);
      const list = byMonth.get(key) ?? [];
      list.push(row);
      byMonth.set(key, list);
    }
    return {
      title: 'Due',
      paragraphs,
      stats: [
        { label: 'Dates', value: String(rows.length) },
        { label: 'Paid', value: money(year?.paid) },
        { label: 'Received', value: money(year?.received) },
        { label: 'Still to pay', value: money(year?.openPayable) },
      ],
      columns: ['Date', 'Side', 'Who', 'Category', 'Amount', 'Status', 'Notes'],
      rows: rows.map((row) => [
        formatDay(row.date),
        row.side === 'RECEIVABLE' ? 'To receive' : 'To pay',
        dash(row.counterparty),
        dash(row.category),
        money(row.amount),
        row.settled ? 'Settled' : 'Open',
        plain(row.notes),
      ]),
      groups: [...byMonth.entries()].map(([key, list]) => ({
        title: monthTitle(key),
        lines: list.map((row) => `${formatDay(row.date)} · ${row.side === 'RECEIVABLE' ? 'To receive' : 'To pay'} · ${dash(row.counterparty)} · ${money(row.amount)} · ${row.settled ? 'Settled' : 'Open'}`),
      })),
    };
  }

  private calendarSection(
    entries: ReportCalendarEntryDto[],
    labels: ReadonlyArray<{ code: string; label: string }>,
  ): InsightSection {
    const sorted = [...entries].sort((a, b) => a.entryDate.localeCompare(b.entryDate) || (a.title || '').localeCompare(b.title || ''));
    const counts = new Map<string, number>();
    for (const entry of sorted) {
      const label = reportCalendarTypeLabel(entry.calendarType, labels);
      counts.set(label, (counts.get(label) ?? 0) + 1);
    }
    const mix = [...counts.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
    const paragraphs = sorted.length
      ? [
          `${this.year} has ${countPhrase(sorted.length, 'calendar entry', 'calendar entries')}.`,
          mix.map(([label, count]) => `${count} ${label}`).join(', ') + '.',
        ]
      : [`No calendar entries are saved for ${this.year}.`];
    const groups = mix.map(([label]) => ({
      title: label,
      lines: sorted
        .filter((entry) => reportCalendarTypeLabel(entry.calendarType, labels) === label)
        .map((entry) => `${formatDay(entry.entryDate)} · ${dash(entry.title)} · ${plain(entry.body || entry.details)}`),
    }));
    return {
      title: 'Calendar',
      paragraphs,
      stats: [
        { label: 'Entries', value: String(sorted.length) },
        { label: 'Types', value: String(mix.length) },
      ],
      columns: ['Date', 'Type', 'Title', 'Information', 'Files'],
      rows: sorted.map((entry) => [
        formatDay(entry.entryDate),
        reportCalendarTypeLabel(entry.calendarType, labels),
        dash(entry.title),
        plain(entry.body || entry.details),
        entry.attachments?.length ? String(entry.attachments.length) : '—',
      ]),
      groups,
    };
  }

  private accountSection(accounts: SafeAccount[]): InsightSection {
    const folders = new Map<string, SafeAccount[]>();
    for (const account of [...accounts].sort((a, b) => (a.folder || '').localeCompare(b.folder || '') || a.itemName.localeCompare(b.itemName))) {
      const folder = account.folder.trim() || 'No folder';
      const list = folders.get(folder) ?? [];
      list.push(account);
      folders.set(folder, list);
    }
    const withPassword = accounts.filter((account) => account.hasPassword).length;
    const withKey = accounts.filter((account) => account.hasAuthenticator).length;
    const withSite = accounts.filter((account) => account.website.trim()).length;
    const paragraphs = accounts.length
      ? [
          `The account library holds ${countPhrase(accounts.length, 'item')} in ${countPhrase(folders.size, 'folder')}. It is the full library, not only items added in ${this.year}.`,
          `${countPhrase(withSite, 'item')} ${withSite === 1 ? 'has' : 'have'} a website, ${countPhrase(withPassword, 'item')} ${withPassword === 1 ? 'has' : 'have'} a password stored, and ${countPhrase(withKey, 'item')} ${withKey === 1 ? 'has' : 'have'} an authenticator key. This report names the items and leaves the secrets off the page.`,
        ]
      : ['No accounts are saved yet.'];
    return {
      title: 'Accounts',
      paragraphs,
      stats: [
        { label: 'Items', value: String(accounts.length) },
        { label: 'Folders', value: String(folders.size) },
        { label: 'With a website', value: String(withSite) },
      ],
      columns: ['Folder', 'Item', 'Username', 'Website', 'Password stored', 'Authenticator stored', 'Notes'],
      rows: [...folders.values()].flat().map((account) => [
        account.folder.trim() || '—',
        dash(account.itemName),
        dash(account.username),
        dash(account.website),
        account.hasPassword ? 'Yes' : 'No',
        account.hasAuthenticator ? 'Yes' : 'No',
        plain(account.notes),
      ]),
      groups: [...folders.entries()].map(([folder, list]) => ({
        title: folder,
        lines: list.map((account) => `${account.itemName} · ${dash(account.username)} · ${dash(account.website)}`),
      })),
    };
  }

  private subscriptionSection(rows: ManagementSubscriptionDto[]): InsightSection {
    const sorted = [...rows].sort((a, b) => a.name.localeCompare(b.name));
    const active = sorted.filter((row) => row.status === 'ACTIVE' || row.status === 'TRIAL');
    const monthly = active.reduce((sum, row) => sum + monthlyAmount(row.amount, row.billingCycle), 0);
    const soon = active
      .filter((row) => row.daysUntilRenewal != null && row.daysUntilRenewal >= 0 && row.daysUntilRenewal <= 45)
      .sort((a, b) => (a.daysUntilRenewal ?? 0) - (b.daysUntilRenewal ?? 0));
    const activeCount = sorted.filter((row) => row.status === 'ACTIVE').length;
    const trialCount = sorted.filter((row) => row.status === 'TRIAL').length;
    const paragraphs = sorted.length
      ? [
          `${countPhrase(sorted.length, 'subscription')} ${sorted.length === 1 ? 'is' : 'are'} on file. ${activeCount} ${activeCount === 1 ? 'is' : 'are'} active and ${trialCount} ${trialCount === 1 ? 'is' : 'are'} in a trial.`,
          monthly > 0
            ? `Active and trial plans with an amount come to about ${money(monthly)} a month. Weekly amounts are spread across the month, and annual amounts are divided by 12. Plans marked Other stay out of that figure.`
            : 'No active or trial plan has an amount yet.',
          soon.length
            ? `Coming up in the next 45 days: ${soon.map((row) => `${row.name} on ${formatDay(row.nextRenewalOn)}`).join('; ')}.`
            : 'Nothing renews in the next 45 days.',
        ]
      : ['No subscriptions are saved yet.'];
    const statusOrder = ['ACTIVE', 'TRIAL', 'CANCELLED', 'EXPIRED'];
    return {
      title: 'Subscriptions',
      paragraphs,
      stats: [
        { label: 'Plans', value: String(sorted.length) },
        { label: 'Active or trial', value: String(active.length) },
        { label: 'About / month', value: money(monthly) },
      ],
      columns: ['Name', 'Vendor', 'Plan', 'Cycle', 'Amount', 'Status', 'Renews', 'Auto renew', 'Notes'],
      rows: sorted.map((row) => [
        dash(row.name),
        dash(row.vendor),
        dash(row.plan || row.category),
        cycleLabel(row.billingCycle),
        money(row.amount),
        statusLabel(row.status),
        formatDay(row.nextRenewalOn || row.renewsOn),
        row.autoRenew ? 'Yes' : 'No',
        plain(row.notes),
      ]),
      groups: statusOrder
        .map((status) => ({
          title: statusLabel(status),
          lines: sorted
            .filter((row) => row.status === status)
            .map((row) => `${row.name} · ${dash(row.vendor)} · ${money(row.amount)} ${cycleLabel(row.billingCycle).toLowerCase()} · renews ${formatDay(row.nextRenewalOn || row.renewsOn)}`),
        }))
        .filter((group) => group.lines.length),
    };
  }

  private autopaySection(rows: ManagementAutoPaymentDto[]): InsightSection {
    const sorted = [...rows].sort((a, b) => a.name.localeCompare(b.name));
    const active = sorted.filter((row) => row.status === 'ACTIVE');
    const monthly = active.reduce((sum, row) => sum + monthlyAmount(row.amount, row.frequency === 'BIWEEKLY' ? 'BIWEEKLY' : row.frequency), 0);
    const soon = active
      .filter((row) => row.daysUntilDebit != null && row.daysUntilDebit >= 0 && row.daysUntilDebit <= 45)
      .sort((a, b) => (a.daysUntilDebit ?? 0) - (b.daysUntilDebit ?? 0));
    const paragraphs = sorted.length
      ? [
          `${countPhrase(sorted.length, 'auto payment')} ${sorted.length === 1 ? 'is' : 'are'} on file. ${active.length} ${active.length === 1 ? 'is' : 'are'} active. These are the enrolled records, not a live bank feed.`,
          monthly > 0
            ? `Active autopays with an amount come to about ${money(monthly)} a month.`
            : 'No active autopay has an amount yet.',
          soon.length
            ? `Next debits in 45 days: ${soon.map((row) => `${row.name} on ${formatDay(row.nextDebitOn || row.nextPaymentOn)} from ${dash(row.fundingAccount)}`).join('; ')}.`
            : 'No active autopay is due in the next 45 days.',
        ]
      : ['No auto payments are saved yet.'];
    return {
      title: 'Auto payments',
      paragraphs,
      stats: [
        { label: 'Enrolled', value: String(sorted.length) },
        { label: 'Active', value: String(active.length) },
        { label: 'About / month', value: money(monthly) },
      ],
      columns: ['Name', 'Payee', 'Method', 'Frequency', 'Amount', 'Status', 'Next debit', 'Funding account', 'Notes'],
      rows: sorted.map((row) => [
        dash(row.name),
        dash(row.payee),
        methodLabel(row.paymentMethod),
        frequencyLabel(row.frequency),
        money(row.amount),
        payStatus(row.status),
        formatDay(row.nextDebitOn || row.nextPaymentOn),
        dash(row.fundingAccount),
        plain(row.notes),
      ]),
      groups: (['ACTIVE', 'PAUSED', 'CANCELLED'] as const)
        .map((status) => ({
          title: payStatus(status),
          lines: sorted
            .filter((row) => row.status === status)
            .map((row) => `${row.name} · ${dash(row.payee)} · ${money(row.amount)} ${frequencyLabel(row.frequency).toLowerCase()} · ${dash(row.fundingAccount)}`),
        }))
        .filter((group) => group.lines.length),
    };
  }

  private documentSection(yearDocs: ManagementDocumentDto[], otherYears: number): InsightSection {
    const sorted = [...yearDocs].sort((a, b) => (a.docType || '').localeCompare(b.docType || '') || a.displayName.localeCompare(b.displayName));
    const types = new Map<string, ManagementDocumentDto[]>();
    let bytes = 0;
    for (const doc of sorted) {
      bytes += doc.byteSize || 0;
      const type = doc.docType?.trim() || 'Unlabeled';
      const list = types.get(type) ?? [];
      list.push(doc);
      types.set(type, list);
    }
    const paragraphs = sorted.length
      ? [
          `${countPhrase(sorted.length, 'document')} ${sorted.length === 1 ? 'was' : 'were'} added in ${this.year}, about ${formatBytes(bytes)} across ${countPhrase(types.size, 'type')}.`,
          otherYears > 0 ? `${countPhrase(otherYears, 'other document')} ${otherYears === 1 ? 'sits' : 'sit'} in the library from another year. Change the year to read those.` : `Every saved document was added in ${this.year}.`,
        ]
      : [
          otherYears > 0
            ? `No documents were added in ${this.year}. ${countPhrase(otherYears, 'document')} ${otherYears === 1 ? 'is' : 'are'} saved in another year.`
            : 'No documents are saved yet.',
        ];
    return {
      title: 'Documents',
      paragraphs,
      stats: [
        { label: `Added in ${this.year}`, value: String(sorted.length) },
        { label: 'Types', value: String(types.size) },
        { label: 'Size', value: formatBytes(bytes) },
      ],
      columns: ['Name', 'Type', 'File', 'Size', 'Added'],
      rows: sorted.map((doc) => [
        dash(doc.displayName),
        dash(doc.docType),
        dash(doc.originalFilename),
        formatBytes(doc.byteSize || 0),
        formatDay(doc.createdAt),
      ]),
      groups: [...types.entries()].map(([type, list]) => ({
        title: type,
        lines: list.map((doc) => `${doc.displayName} · ${dash(doc.originalFilename)} · ${formatBytes(doc.byteSize || 0)} · added ${formatDay(doc.createdAt)}`),
      })),
    };
  }

  private travelSection(trips: TravelTripSummaryDto[], places: TravelPlaceMapDto[]): InsightSection {
    const sortedTrips = [...trips].sort((a, b) => a.startDate.localeCompare(b.startDate) || a.title.localeCompare(b.title));
    const visited = places.filter((place) => place.placeStatus === 'VISITED').length;
    const planned = places.filter((place) => place.placeStatus === 'PLANNED').length;
    const paragraphs = sortedTrips.length || places.length
      ? [
          `${countPhrase(sortedTrips.length, 'trip')} ${sortedTrips.length === 1 ? 'overlaps' : 'overlap'} ${this.year}. ${countPhrase(places.length, 'place')} ${places.length === 1 ? 'is' : 'are'} on the map for this year: ${visited} visited and ${planned} planned.`,
          sortedTrips.length
            ? sortedTrips.map((trip) => `${trip.title} (${tripStatus(trip.status)}, ${formatDay(trip.startDate)}${trip.endDate ? ' – ' + formatDay(trip.endDate) : ''})`).join('; ') + '.'
            : 'No trip record overlaps this year, but places are still on the map.',
        ]
      : [`No trips or places are entered for ${this.year}.`];
    const byTrip = new Map<string, TravelPlaceMapDto[]>();
    for (const place of [...places].sort((a, b) => (a.visitDate || '').localeCompare(b.visitDate || '') || a.name.localeCompare(b.name))) {
      const list = byTrip.get(place.tripTitle) ?? [];
      list.push(place);
      byTrip.set(place.tripTitle, list);
    }
    for (const trip of sortedTrips) {
      if (!byTrip.has(trip.title)) {
        byTrip.set(trip.title, []);
      }
    }
    const columns = ['Trip', 'Trip dates', 'Trip status', 'Place', 'Place status', 'Visit'];
    const rows: string[][] = [];
    for (const trip of sortedTrips) {
      const tripPlaces = byTrip.get(trip.title) ?? [];
      const dates = `${formatDay(trip.startDate)}${trip.endDate ? ' – ' + formatDay(trip.endDate) : ''}`;
      if (!tripPlaces.length) {
        rows.push([trip.title, dates, tripStatus(trip.status), '—', '—', '—']);
      }
      for (const place of tripPlaces) {
        rows.push([
          trip.title,
          dates,
          tripStatus(trip.status),
          place.name,
          place.placeStatus === 'VISITED' ? 'Visited' : 'Planned',
          formatDay(place.visitDate),
        ]);
      }
    }
    for (const [title, tripPlaces] of byTrip) {
      if (sortedTrips.some((trip) => trip.title === title)) {
        continue;
      }
      for (const place of tripPlaces) {
        rows.push([title, '—', '—', place.name, place.placeStatus === 'VISITED' ? 'Visited' : 'Planned', formatDay(place.visitDate)]);
      }
    }
    return {
      title: 'Travel',
      paragraphs,
      stats: [
        { label: 'Trips', value: String(sortedTrips.length) },
        { label: 'Places', value: String(places.length) },
        { label: 'Visited', value: String(visited) },
      ],
      columns,
      rows,
      groups: [...byTrip.entries()].map(([title, tripPlaces]) => ({
        title,
        lines: tripPlaces.length
          ? tripPlaces.map((place) => `${place.name} · ${place.placeStatus === 'VISITED' ? 'Visited' : 'Planned'} · ${formatDay(place.visitDate)}`)
          : ['No places on the map for this year.'],
      })),
    };
  }

  private printHtml(): string {
    const sections = this.activeSections;
    const overviewList = this.topic === 'overview' && this.form === 'list'
      ? `<table><thead><tr><th>Area</th><th>On file</th><th>In short</th></tr></thead><tbody>${sections
          .map((section) => `<tr><td>${escapeHtml(section.title)}</td><td>${escapeHtml(section.stats[0]?.value ?? '—')}</td><td>${escapeHtml(section.paragraphs[0] ?? '')}</td></tr>`)
          .join('')}</tbody></table>`
      : '';
    const body = overviewList || sections
      .map((section) => {
        const heading = this.topic === 'overview' ? `<h2>${escapeHtml(section.title)}</h2>` : '';
        if (this.form === 'list') {
          return `${heading}<table><thead><tr>${section.columns.map((column) => `<th>${escapeHtml(column)}</th>`).join('')}</tr></thead><tbody>${section.rows
            .map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`)
            .join('')}</tbody></table>`;
        }
        if (this.form === 'grouped') {
          return `${heading}${section.groups
            .map((group) => `<h3>${escapeHtml(group.title)}</h3><ul>${group.lines.map((line) => `<li>${escapeHtml(line)}</li>`).join('')}</ul>`)
            .join('')}`;
        }
        const stats = section.stats.map((stat) => `<span><strong>${escapeHtml(stat.value)}</strong> ${escapeHtml(stat.label)}</span>`).join('');
        return `${heading}<p class="stats">${stats}</p>${section.paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join('')}`;
      })
      .join('');
    const secret = this.topic === 'accounts' || this.topic === 'overview'
      ? '<p class="note">Account passwords and authenticator keys are left off this report.</p>'
      : '';
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${escapeHtml(this.reportTitle)}</title><style>
      body{font:15px/1.45 Georgia,serif;color:#1e293b;margin:2rem;max-width:52rem}
      h1{font-size:1.4rem;margin:0 0 .4rem} h2{font-size:1.05rem;margin:1.2rem 0 .3rem} h3{font-size:.95rem;margin:1rem 0 .2rem}
      p{margin:.35rem 0} .stats span{display:inline-block;margin:0 .8rem .4rem 0} .note{color:#64748b;font-size:.85rem}
      table{border-collapse:collapse;width:100%;margin:.6rem 0 1rem} th,td{border:1px solid #cbd5e1;padding:.35rem .45rem;text-align:left;vertical-align:top}
      th{background:#f8fafc}
    </style></head><body><h1>${escapeHtml(this.reportTitle)}</h1><p>${escapeHtml(this.formLabel())}</p>${body}${secret}</body></html>`;
  }

  private formLabel(): string {
    if (this.form === 'list') {
      return 'List of what is entered.';
    }
    if (this.form === 'grouped') {
      return this.groupHint;
    }
    return 'A short reading of what is entered.';
  }
}

function countPhrase(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

function money(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) {
    return '—';
  }
  return Number(value).toLocaleString(undefined, { style: 'currency', currency: 'USD' });
}

function monthlyAmount(amount: number | null, cycle: string): number {
  if (amount == null || Number.isNaN(Number(amount))) {
    return 0;
  }
  const value = Number(amount);
  if (cycle === 'WEEKLY') {
    return (value * 52) / 12;
  }
  if (cycle === 'BIWEEKLY') {
    return (value * 26) / 12;
  }
  if (cycle === 'ANNUAL') {
    return value / 12;
  }
  if (cycle === 'MONTHLY') {
    return value;
  }
  return 0;
}

function dash(value: string | null | undefined): string {
  const text = (value ?? '').trim();
  return text || '—';
}

function plain(value: string | null | undefined): string {
  const text = (value ?? '').replace(/\s+/g, ' ').trim();
  return text || '—';
}

function formatDay(iso: string | null | undefined): string {
  if (!iso || iso.length < 10) {
    return '—';
  }
  const year = Number(iso.slice(0, 4));
  const month = Number(iso.slice(5, 7));
  const day = Number(iso.slice(8, 10));
  if (!year || !month || !day) {
    return '—';
  }
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function monthTitle(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  if (!year || !month) {
    return yearMonth;
  }
  return new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
}

function formatBytes(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(size < 10 * 1024 ? 1 : 0)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function overlapsYear(start: string, end: string | null, year: number): boolean {
  if (!start) {
    return false;
  }
  const from = `${year}-01-01`;
  const to = `${year}-12-31`;
  if (start > to) {
    return false;
  }
  return !end || end >= from;
}

function statusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'TRIAL':
      return 'Trial';
    case 'CANCELLED':
      return 'Cancelled';
    case 'EXPIRED':
      return 'Expired';
    default:
      return status;
  }
}

function cycleLabel(cycle: string): string {
  switch (cycle) {
    case 'WEEKLY':
      return 'Weekly';
    case 'MONTHLY':
      return 'Monthly';
    case 'ANNUAL':
      return 'Annual';
    default:
      return 'Other';
  }
}

function payStatus(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'PAUSED':
      return 'Paused';
    case 'CANCELLED':
      return 'Cancelled';
    default:
      return status;
  }
}

function frequencyLabel(frequency: string): string {
  switch (frequency) {
    case 'WEEKLY':
      return 'Weekly';
    case 'BIWEEKLY':
      return 'Every two weeks';
    case 'MONTHLY':
      return 'Monthly';
    case 'ANNUAL':
      return 'Annual';
    default:
      return 'Other';
  }
}

function methodLabel(method: string): string {
  switch (method) {
    case 'ACH':
      return 'Bank transfer';
    case 'CARD':
      return 'Card';
    case 'BILL_PAY':
      return 'Bill pay';
    default:
      return 'Other';
  }
}

function tripStatus(status: string): string {
  switch (status) {
    case 'PLANNING':
      return 'Planning';
    case 'ACTIVE':
      return 'On the trip';
    case 'COMPLETED':
      return 'Completed';
    default:
      return status;
  }
}

function csvLine(cells: string[]): string {
  return cells.map((cell) => {
    const value = cell ?? '';
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
  }).join(',');
}

function saveText(filename: string, contents: string, type: string): void {
  const blob = new Blob([contents], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
