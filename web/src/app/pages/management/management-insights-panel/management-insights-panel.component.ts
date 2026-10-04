import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  BankingPlaidOpeningBalancesDto,
  RobinhoodExecutedTradeDto,
  RobinhoodRhAccountsTrackDto,
  RobinhoodRhPeriodBalancesDto,
} from '../../../models/finance.models';
import {
  ManagementAutoPaymentDto,
  ManagementDocumentDto,
  ManagementDueCategoryDto,
  ManagementDueReportRowDto,
  ManagementDueSide,
  ManagementSubscriptionDto,
} from '../../../models/management.models';
import { ReportCalendarEntryDto, reportCalendarTypeLabel } from '../../../models/report-calendar.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { ManagementApiService } from '../../../services/management-api.service';
import { ReportCalendarApiService } from '../../../services/report-calendar-api.service';
import {
  DueOpeningGroup,
  DueOpeningMonth,
  ManagementDueReportsComponent,
} from '../management-due-reports/management-due-reports.component';

type AreaId = 'documents' | 'calendar' | 'due' | 'accounts' | 'subscriptions' | 'autopay';
type ReportShape = 'summary' | 'ledger' | 'detail';
type SalesView = 'off' | 'with' | 'only';

interface SafeAccount {
  folder: string;
  itemName: string;
  username: string;
  website: string;
  notes: string;
  hasPassword: boolean;
  hasAuthenticator: boolean;
}

interface ReportLine {
  label: string;
  value: string;
}

interface ReportGroup {
  title: string;
  total: string;
  lines: ReportLine[];
}

interface ReportSheet {
  stats: { label: string; value: string }[];
  paragraphs: string[];
  columns: string[];
  rows: string[][];
  groups: ReportGroup[];
}

@Component({
  selector: 'app-management-insights-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTabsModule,
    ManagementDueReportsComponent,
  ],
  templateUrl: './management-insights-panel.component.html',
  styleUrl: './management-insights-panel.component.scss',
})
export class ManagementInsightsPanelComponent implements OnInit {
  private readonly api = inject(ManagementApiService);
  private readonly calendarApi = inject(ReportCalendarApiService);
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly areas: { id: AreaId; label: string }[] = [
    { id: 'documents', label: 'Documents' },
    { id: 'calendar', label: 'Calendar' },
    { id: 'due', label: 'Due' },
    { id: 'accounts', label: 'Account' },
    { id: 'subscriptions', label: 'Subscriptions' },
    { id: 'autopay', label: 'Auto Payments' },
  ];

  year = new Date().getFullYear();
  month = new Date().getMonth() + 1;
  shape: ReportShape = 'ledger';
  query = '';

  documentScope: 'year' | 'all' = 'year';
  documentGroup: 'type' | 'month' = 'type';
  calendarScope: 'month' | 'year' = 'year';
  calendarGroup: 'type' | 'month' = 'type';
  calendarType = '';
  accountFilter: '' | 'site' | 'password' | 'key' = '';
  subscriptionScope: 'all' | 'month' | 'year' = 'all';
  subscriptionGroup: 'status' | 'cycle' | 'category' = 'status';
  subscriptionStatus = '';
  autopayScope: 'all' | 'month' = 'all';
  autopayGroup: 'status' | 'frequency' | 'funding' | 'method' = 'status';
  autopayStatus = '';

  salesView: SalesView = 'off';
  showOpenings = false;
  dueRevision = 0;
  dueCategories: ManagementDueCategoryDto[] = [];
  salesReportRows: ManagementDueReportRowDto[] = [];
  openingReportMonths: DueOpeningMonth[] = [];

  loadingArea: AreaId | null = null;
  errorArea: AreaId | null = null;

  private documents: ManagementDocumentDto[] = [];
  private calendar: ReportCalendarEntryDto[] = [];
  private calendarLabels: { code: string; label: string }[] = [];
  private accounts: SafeAccount[] = [];
  private subscriptions: ManagementSubscriptionDto[] = [];
  private autopay: ManagementAutoPaymentDto[] = [];
  private readonly loaded = new Set<AreaId>();
  private salesYearLoaded: number | null = null;
  private openingYearLoaded: number | null = null;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    const areas = this.loaded.size ? [...this.loaded] : (['documents'] as AreaId[]);
    for (const area of areas) {
      this.pull(area);
    }
    this.dueRevision += 1;
  }

  onArea(index: number): void {
    const area = this.areas[index]?.id;
    if (!area || area === 'due') {
      if (area === 'due') {
        this.pull('due');
      }
      return;
    }
    if (!this.loaded.has(area)) {
      this.pull(area);
    }
  }

  shiftYear(delta: number): void {
    this.year += delta;
    this.dueRevision += 1;
    this.salesYearLoaded = null;
    this.openingYearLoaded = null;
    this.salesReportRows = [];
    this.openingReportMonths = [];
    if (this.salesView !== 'off') {
      this.ensureSales();
    }
    if (this.showOpenings) {
      this.ensureOpenings();
    }
    this.reloadDated();
  }

  shiftMonth(delta: number): void {
    const next = new Date(this.year, this.month - 1 + delta, 1);
    this.year = next.getFullYear();
    this.month = next.getMonth() + 1;
    this.dueRevision += 1;
    this.reloadDated();
  }

  setSales(view: SalesView): void {
    this.salesView = view;
    if (view !== 'off') {
      this.ensureSales();
    }
  }

  setOpenings(on: boolean): void {
    this.showOpenings = on;
    if (on) {
      this.ensureOpenings();
    }
  }

  pull(area: AreaId): void {
    if (area === 'due') {
      this.dueRevision += 1;
      this.loadingArea = 'due';
      this.api.listDueCategories().pipe(catchError(() => of([]))).subscribe((rows) => {
        this.dueCategories = rows ?? [];
        if (this.loadingArea === 'due') {
          this.loadingArea = null;
        }
      });
      if (this.salesView !== 'off') {
        this.salesYearLoaded = null;
        this.ensureSales();
      }
      if (this.showOpenings) {
        this.openingYearLoaded = null;
        this.ensureOpenings();
      }
      return;
    }
    this.loadingArea = area;
    this.errorArea = null;
    const done = () => {
      this.loaded.add(area);
      if (this.loadingArea === area) {
        this.loadingArea = null;
      }
    };
    const fail = () => {
      this.errorArea = area;
      if (this.loadingArea === area) {
        this.loadingArea = null;
      }
    };
    if (area === 'documents') {
      this.api.listDocuments().subscribe({ next: (rows) => { this.documents = rows ?? []; done(); }, error: fail });
    } else if (area === 'calendar') {
      forkJoin({
        entries: this.calendarApi.list(`${this.year}-01-01`, `${this.year}-12-31`, null),
        types: this.api.listCalendarTypes().pipe(catchError(() => of([]))),
      }).subscribe({
        next: ({ entries, types }) => {
          this.calendar = entries ?? [];
          this.calendarLabels = types ?? [];
          done();
        },
        error: fail,
      });
    } else if (area === 'accounts') {
      this.api.listAccounts().subscribe({
        next: (rows) => {
          this.accounts = (rows ?? []).map((row) => ({
            folder: row.folder || '',
            itemName: row.itemName || '',
            username: row.username || '',
            website: row.website || '',
            notes: row.notes || '',
            hasPassword: !!(row.password || '').trim(),
            hasAuthenticator: !!(row.authenticatorKey || '').trim(),
          }));
          done();
        },
        error: fail,
      });
    } else if (area === 'subscriptions') {
      this.api.listSubscriptions().subscribe({ next: (rows) => { this.subscriptions = rows ?? []; done(); }, error: fail });
    } else {
      this.api.listAutoPayments().subscribe({ next: (rows) => { this.autopay = rows ?? []; done(); }, error: fail });
    }
  }

  sheet(area: string): ReportSheet {
    if (area === 'documents') {
      return this.documentSheet();
    }
    if (area === 'calendar') {
      return this.calendarSheet();
    }
    if (area === 'accounts') {
      return this.accountSheet();
    }
    if (area === 'subscriptions') {
      return this.subscriptionSheet();
    }
    return this.autopaySheet();
  }

  monthLabel(): string {
    return new Date(this.year, this.month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  }

  calendarTypes(): string[] {
    const labels = new Set(this.calendar.map((entry) => reportCalendarTypeLabel(entry.calendarType, this.calendarLabels)));
    return [...labels].sort((a, b) => a.localeCompare(b));
  }

  download(area: AreaId): void {
    const view = this.sheet(area);
    const lines = [csvLine(view.columns), ...view.rows.map((row) => csvLine(row))];
    saveText(`management-${area}-${this.year}.csv`, lines.join('\n'), 'text/csv;charset=utf-8');
  }

  downloadText(area: AreaId): void {
    const view = this.sheet(area);
    const parts = [this.areas.find((item) => item.id === area)?.label ?? 'Management', this.monthLabel(), ''];
    parts.push(...view.paragraphs, '');
    for (const group of view.groups) {
      parts.push(group.title, ...group.lines.map((line) => `${line.label}  ${line.value}`), '');
    }
    saveText(`management-${area}-${this.year}.txt`, parts.join('\n').trim() + '\n', 'text/plain;charset=utf-8');
  }

  printSheet(area: AreaId): void {
    const view = this.sheet(area);
    const title = this.areas.find((item) => item.id === area)?.label ?? 'Management';
    const body = this.shape === 'detail'
      ? tableHtml(view.columns, view.rows)
      : this.shape === 'summary'
        ? view.paragraphs.map((paragraph) => `<p>${escapeHtml(paragraph)}</p>`).join('')
        : view.groups.map((group) => `<h2>${escapeHtml(group.title)} <span>${escapeHtml(group.total)}</span></h2><ul>${group.lines.map((line) => `<li><span>${escapeHtml(line.label)}</span><span>${escapeHtml(line.value)}</span></li>`).join('')}</ul>`).join('');
    openPrint(title, body + (area === 'accounts' ? '<p class="note">Passwords and authenticator keys are left off this report.</p>' : ''));
  }

  downloadDue(due: ManagementDueReportsComponent): void {
    const rows = due.filteredRows;
    const lines = [
      csvLine(['Date', 'Side', 'Who', 'Category', 'Amount', 'Status', 'Recurring', 'Notes']),
      ...rows.map((row) => csvLine([
        row.date,
        row.side === 'RECEIVABLE' ? 'To receive' : 'To pay',
        row.counterparty,
        row.category || '',
        row.amount == null ? '' : String(row.amount),
        row.settled ? 'Settled' : 'Open',
        row.recurring ? 'Yes' : 'No',
        plain(row.notes),
      ])),
    ];
    if (this.showOpenings) {
      lines.push(csvLine(['Opening date', 'Institute', 'Account', 'Amount', '', '', '', '']));
      for (const month of due.openingSections) {
        for (const group of month.groups) {
          for (const account of group.accounts) {
            lines.push(csvLine([month.iso, group.institution, account.label, String(account.amount), '', '', '', '']));
          }
        }
      }
    }
    saveText(`management-due-${this.year}.csv`, lines.join('\n'), 'text/csv;charset=utf-8');
  }

  downloadDueText(due: ManagementDueReportsComponent): void {
    const parts = [`Due ${due.scopeLabel}`, ''];
    parts.push(`Paid ${due.money(due.period.paid)}`, `Received ${due.money(due.period.received)}`, `Net ${due.signed(due.period.net)}`, '');
    for (const group of [...due.paidGroups, ...due.receivedGroups]) {
      parts.push(group.name, ...group.lines.map((line) => `${line.date}  ${line.label}  ${due.money(line.amount)}`), '');
    }
    saveText(`management-due-${this.year}.txt`, parts.join('\n').trim() + '\n', 'text/plain;charset=utf-8');
  }

  printDue(due: ManagementDueReportsComponent): void {
    const sections = [
      ['Paid', due.paidGroups],
      ['Received', due.receivedGroups],
    ] as const;
    const body = sections.map(([title, groups]) => `<h2>${title}</h2>${groups.map((group) => `<h3>${escapeHtml(group.name)} <span>${escapeHtml(due.money(group.total))}</span></h3><ul>${group.lines.map((line) => `<li><span>${escapeHtml(line.label)}</span><span>${escapeHtml(due.money(line.amount))}</span></li>`).join('')}</ul>`).join('')}`).join('');
    openPrint(`Due ${due.scopeLabel}`, body);
  }

  private reloadDated(): void {
    if (this.loaded.has('calendar')) {
      this.pull('calendar');
    }
  }

  private documentSheet(): ReportSheet {
    const q = this.query.trim().toLowerCase();
    const rows = this.documents.filter((doc) => {
      if (this.documentScope === 'year' && !(doc.createdAt || '').startsWith(String(this.year))) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${doc.displayName} ${doc.docType} ${doc.originalFilename || ''}`.toLowerCase().includes(q);
    });
    const keyOf = (doc: ManagementDocumentDto) => this.documentGroup === 'month'
      ? (doc.createdAt || '').slice(0, 7) || 'Undated'
      : (doc.docType || '').trim() || 'Unlabeled';
    return sheetFrom(
      rows,
      keyOf,
      (key) => this.documentGroup === 'month' && key.length === 7 ? monthTitle(key) : key,
      (doc) => ({ label: `${doc.displayName} · ${doc.originalFilename || 'file'}`, value: formatBytes(doc.byteSize || 0) }),
      (list) => formatBytes(list.reduce((sum, doc) => sum + (doc.byteSize || 0), 0)),
      ['Name', 'Type', 'File', 'Size', 'Added'],
      (doc) => [dash(doc.displayName), dash(doc.docType), dash(doc.originalFilename), formatBytes(doc.byteSize || 0), formatDay(doc.createdAt)],
      [
        { label: 'Documents', value: String(rows.length) },
        { label: 'Size', value: formatBytes(rows.reduce((sum, doc) => sum + (doc.byteSize || 0), 0)) },
      ],
      rows.length
        ? [`${rows.length} document${rows.length === 1 ? '' : 's'} in this report, grouped by ${this.documentGroup === 'month' ? 'the month they were added' : 'type'}.`]
        : ['No documents match this report.'],
    );
  }

  private calendarSheet(): ReportSheet {
    const q = this.query.trim().toLowerCase();
    const monthKey = `${this.year}-${String(this.month).padStart(2, '0')}`;
    const rows = this.calendar.filter((entry) => {
      const label = reportCalendarTypeLabel(entry.calendarType, this.calendarLabels);
      if (this.calendarScope === 'month' && !entry.entryDate.startsWith(monthKey)) {
        return false;
      }
      if (this.calendarType && label !== this.calendarType) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${label} ${entry.title || ''} ${entry.body || ''} ${entry.details || ''}`.toLowerCase().includes(q);
    });
    const keyOf = (entry: ReportCalendarEntryDto) => this.calendarGroup === 'month'
      ? entry.entryDate.slice(0, 7)
      : reportCalendarTypeLabel(entry.calendarType, this.calendarLabels);
    return sheetFrom(
      rows,
      keyOf,
      (key) => key.length === 7 ? monthTitle(key) : key,
      (entry) => ({ label: `${formatDay(entry.entryDate)} · ${dash(entry.title)}`, value: plain(entry.body || entry.details) }),
      (list) => String(list.length),
      ['Date', 'Type', 'Title', 'Information', 'Files'],
      (entry) => [
        formatDay(entry.entryDate),
        reportCalendarTypeLabel(entry.calendarType, this.calendarLabels),
        dash(entry.title),
        plain(entry.body || entry.details),
        entry.attachments?.length ? String(entry.attachments.length) : '—',
      ],
      [
        { label: 'Entries', value: String(rows.length) },
        { label: 'Range', value: this.calendarScope === 'month' ? this.monthLabel() : String(this.year) },
      ],
      rows.length
        ? [`${rows.length} calendar entr${rows.length === 1 ? 'y' : 'ies'} for ${this.calendarScope === 'month' ? this.monthLabel() : this.year}.`]
        : ['No calendar entries match this report.'],
    );
  }

  private accountSheet(): ReportSheet {
    const q = this.query.trim().toLowerCase();
    const rows = this.accounts.filter((account) => {
      if (this.accountFilter === 'site' && !account.website.trim()) {
        return false;
      }
      if (this.accountFilter === 'password' && !account.hasPassword) {
        return false;
      }
      if (this.accountFilter === 'key' && !account.hasAuthenticator) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${account.folder} ${account.itemName} ${account.username} ${account.website}`.toLowerCase().includes(q);
    });
    return sheetFrom(
      rows,
      (account) => account.folder.trim() || 'No folder',
      (key) => key,
      (account) => ({ label: `${account.itemName} · ${dash(account.username)}`, value: dash(account.website) }),
      (list) => String(list.length),
      ['Folder', 'Item', 'Username', 'Website', 'Password stored', 'Authenticator stored', 'Notes'],
      (account) => [
        dash(account.folder),
        dash(account.itemName),
        dash(account.username),
        dash(account.website),
        account.hasPassword ? 'Yes' : 'No',
        account.hasAuthenticator ? 'Yes' : 'No',
        plain(account.notes),
      ],
      [
        { label: 'Items', value: String(rows.length) },
        { label: 'With a website', value: String(rows.filter((account) => account.website.trim()).length) },
      ],
      [
        rows.length
          ? `${rows.length} account${rows.length === 1 ? '' : 's'} in this report, grouped by folder.`
          : 'No accounts match this report.',
        'Passwords and authenticator keys stay off the page. The report only says whether one is stored.',
      ],
    );
  }

  private subscriptionSheet(): ReportSheet {
    const q = this.query.trim().toLowerCase();
    const monthKey = `${this.year}-${String(this.month).padStart(2, '0')}`;
    const rows = this.subscriptions.filter((row) => {
      const renews = row.nextRenewalOn || row.renewsOn || '';
      if (this.subscriptionScope === 'month' && !renews.startsWith(monthKey)) {
        return false;
      }
      if (this.subscriptionScope === 'year' && !renews.startsWith(String(this.year))) {
        return false;
      }
      if (this.subscriptionStatus && row.status !== this.subscriptionStatus) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${row.name} ${row.vendor} ${row.plan} ${row.category} ${row.notes}`.toLowerCase().includes(q);
    });
    const monthly = rows
      .filter((row) => row.status === 'ACTIVE' || row.status === 'TRIAL')
      .reduce((sum, row) => sum + monthlyAmount(row.amount, row.billingCycle), 0);
    const keyOf = (row: ManagementSubscriptionDto) => {
      if (this.subscriptionGroup === 'cycle') {
        return cycleLabel(row.billingCycle);
      }
      if (this.subscriptionGroup === 'category') {
        return row.category?.trim() || row.plan?.trim() || 'Uncategorized';
      }
      return statusLabel(row.status);
    };
    return sheetFrom(
      rows,
      keyOf,
      (key) => key,
      (row) => ({ label: `${row.name} · ${dash(row.vendor)} · renews ${formatDay(row.nextRenewalOn || row.renewsOn)}`, value: money(row.amount) }),
      (list) => money(list.reduce((sum, row) => sum + Number(row.amount || 0), 0)),
      ['Name', 'Vendor', 'Plan', 'Cycle', 'Amount', 'Status', 'Renews', 'Auto renew', 'Notes'],
      (row) => [
        dash(row.name),
        dash(row.vendor),
        dash(row.plan || row.category),
        cycleLabel(row.billingCycle),
        money(row.amount),
        statusLabel(row.status),
        formatDay(row.nextRenewalOn || row.renewsOn),
        row.autoRenew ? 'Yes' : 'No',
        plain(row.notes),
      ],
      [
        { label: 'Plans', value: String(rows.length) },
        { label: 'About / month', value: money(monthly) },
      ],
      rows.length
        ? [`${rows.length} subscription${rows.length === 1 ? '' : 's'} in this report. Active and trial amounts come to about ${money(monthly)} a month.`]
        : ['No subscriptions match this report.'],
    );
  }

  private autopaySheet(): ReportSheet {
    const q = this.query.trim().toLowerCase();
    const monthKey = `${this.year}-${String(this.month).padStart(2, '0')}`;
    const rows = this.autopay.filter((row) => {
      const next = row.nextDebitOn || row.nextPaymentOn || '';
      if (this.autopayScope === 'month' && !next.startsWith(monthKey)) {
        return false;
      }
      if (this.autopayStatus && row.status !== this.autopayStatus) {
        return false;
      }
      if (!q) {
        return true;
      }
      return `${row.name} ${row.payee} ${row.fundingAccount} ${row.category} ${row.notes}`.toLowerCase().includes(q);
    });
    const monthly = rows
      .filter((row) => row.status === 'ACTIVE')
      .reduce((sum, row) => sum + monthlyAmount(row.amount, row.frequency === 'BIWEEKLY' ? 'BIWEEKLY' : row.frequency), 0);
    const keyOf = (row: ManagementAutoPaymentDto) => {
      if (this.autopayGroup === 'frequency') {
        return frequencyLabel(row.frequency);
      }
      if (this.autopayGroup === 'funding') {
        return row.fundingAccount?.trim() || 'No funding account';
      }
      if (this.autopayGroup === 'method') {
        return methodLabel(row.paymentMethod);
      }
      return payStatus(row.status);
    };
    return sheetFrom(
      rows,
      keyOf,
      (key) => key,
      (row) => ({ label: `${row.name} · ${dash(row.payee)} · next ${formatDay(row.nextDebitOn || row.nextPaymentOn)}`, value: money(row.amount) }),
      (list) => money(list.reduce((sum, row) => sum + Number(row.amount || 0), 0)),
      ['Name', 'Payee', 'Method', 'Frequency', 'Amount', 'Status', 'Next debit', 'Funding account', 'Notes'],
      (row) => [
        dash(row.name),
        dash(row.payee),
        methodLabel(row.paymentMethod),
        frequencyLabel(row.frequency),
        money(row.amount),
        payStatus(row.status),
        formatDay(row.nextDebitOn || row.nextPaymentOn),
        dash(row.fundingAccount),
        plain(row.notes),
      ],
      [
        { label: 'Autopays', value: String(rows.length) },
        { label: 'Active / month', value: money(monthly) },
      ],
      rows.length
        ? [`${rows.length} auto payment${rows.length === 1 ? '' : 's'} in this report. Active amounts come to about ${money(monthly)} a month.`]
        : ['No auto payments match this report.'],
    );
  }

  private ensureSales(): void {
    if (this.year < 2026 || this.salesYearLoaded === this.year) {
      return;
    }
    const year = this.year;
    this.financeApi.robinhoodExecutedTrades(year).pipe(catchError(() => of(null))).subscribe((report) => {
      if (this.year !== year) {
        return;
      }
      this.salesReportRows = toSalesRows(report?.trades ?? []);
      this.salesYearLoaded = year;
    });
  }

  private ensureOpenings(): void {
    if (this.year < 2026 || this.openingYearLoaded === this.year) {
      return;
    }
    const year = this.year;
    forkJoin({
      periods: this.financeApi.robinhoodDailyTrackerPeriodBalances(year).pipe(catchError(() => of(null))),
      track: this.financeApi.robinhoodRhAccountsTrack(false).pipe(catchError(() => of(null))),
      plaid: this.financeApi.bankingPlaidOpeningBalances(year).pipe(catchError(() => of(null))),
    }).subscribe(({ periods, track, plaid }) => {
      if (this.year !== year) {
        return;
      }
      const months = withPlaidOpenings(groupsFromBalances(periods, track), plaid);
      this.openingReportMonths = [...months.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([key, groups]) => ({
        key,
        iso: `${key}-01`,
        label: monthTitle(key),
        groups,
      }));
      this.openingYearLoaded = year;
    });
  }
}

function sheetFrom<T>(
  rows: T[],
  keyOf: (row: T) => string,
  titleOf: (key: string) => string,
  lineOf: (row: T) => ReportLine,
  totalOf: (rows: T[]) => string,
  columns: string[],
  detail: (row: T) => string[],
  stats: { label: string; value: string }[],
  paragraphs: string[],
): ReportSheet {
  const buckets = new Map<string, T[]>();
  for (const row of rows) {
    const key = keyOf(row);
    const list = buckets.get(key) ?? [];
    list.push(row);
    buckets.set(key, list);
  }
  const groups = [...buckets.entries()]
    .sort((a, b) => titleOf(a[0]).localeCompare(titleOf(b[0])))
    .map(([key, list]) => ({
      title: titleOf(key),
      total: totalOf(list),
      lines: list.map(lineOf),
    }));
  return { stats, paragraphs, columns, rows: rows.map(detail), groups };
}

function groupsFromBalances(
  report: RobinhoodRhPeriodBalancesDto | null,
  track: RobinhoodRhAccountsTrackDto | null,
): Map<string, DueOpeningGroup[]> {
  const out = new Map<string, DueOpeningGroup[]>();
  const rothRow = (track?.accounts ?? []).find((account) => account.accountSuffix === '2835');
  const rothAmount = rothRow?.totalAccountValue;
  const roth = rothAmount != null && Number.isFinite(Number(rothAmount))
    ? { label: 'Roth IRA (...2835)', amount: Math.round(Number(rothAmount) * 100) / 100 }
    : null;
  const labels = new Map((report?.accounts ?? []).map((account) => [account.accountSuffix, account.label]));
  for (const month of report?.months ?? []) {
    if (!month.key || month.key < '2026-09') {
      continue;
    }
    const accounts = (month.accounts ?? [])
      .filter((account) => account.start != null && Number.isFinite(Number(account.start)))
      .map((account) => ({
        label: labels.get(account.accountSuffix) || `Account (...${account.accountSuffix})`,
        amount: Math.round(Number(account.start) * 100) / 100,
      }));
    if (roth && !accounts.some((account) => account.label.startsWith('Roth IRA'))) {
      accounts.push(roth);
    }
    if (!accounts.length) {
      continue;
    }
    accounts.sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label));
    const total = Math.round(accounts.reduce((sum, account) => sum + account.amount, 0) * 100) / 100;
    out.set(month.key, [{ institution: 'Robinhood', total, accounts }]);
  }
  return out;
}

function withPlaidOpenings(
  base: Map<string, DueOpeningGroup[]>,
  plaid: BankingPlaidOpeningBalancesDto | null,
): Map<string, DueOpeningGroup[]> {
  const out = new Map(base);
  for (const month of plaid?.months ?? []) {
    if (!month.key || month.key < '2026-09') {
      continue;
    }
    const incoming = (month.groups ?? [])
      .map((group) => ({
        institution: group.institution,
        total: Math.round(Number(group.total) * 100) / 100,
        accounts: (group.accounts ?? [])
          .filter((account) => account.amount != null && Number.isFinite(Number(account.amount)))
          .map((account) => ({ label: account.label, amount: Math.round(Number(account.amount) * 100) / 100 })),
      }))
      .filter((group) => group.accounts.length);
    if (!incoming.length) {
      continue;
    }
    const names = new Set(incoming.map((group) => group.institution));
    const kept = (out.get(month.key) ?? []).filter((group) => !names.has(group.institution));
    out.set(month.key, [...kept, ...incoming].sort((a, b) => a.institution.localeCompare(b.institution)));
  }
  return out;
}

function toSalesRows(trades: RobinhoodExecutedTradeDto[]): ManagementDueReportRowDto[] {
  const byDate = new Map<string, { net: number; symbols: string[] }>();
  for (const trade of trades) {
    if (!(trade.side ?? '').trim().toLowerCase().startsWith('sell') || !trade.executedAt) {
      continue;
    }
    const date = centralIsoDate(trade.executedAt);
    if (!date || date < '2026-09-01') {
      continue;
    }
    const row = byDate.get(date) ?? { net: 0, symbols: [] };
    if (trade.realizedPnl != null && Number.isFinite(Number(trade.realizedPnl))) {
      row.net += Number(trade.realizedPnl);
    }
    const symbol = (trade.symbol ?? '').trim().toUpperCase();
    if (symbol && !row.symbols.includes(symbol)) {
      row.symbols.push(symbol);
    }
    byDate.set(date, row);
  }
  const rows: ManagementDueReportRowDto[] = [];
  for (const [date, row] of byDate) {
    const net = Math.round(row.net * 100) / 100;
    const side: ManagementDueSide = net < 0 ? 'PAYABLE' : 'RECEIVABLE';
    rows.push({
      itemId: -Number(date.replaceAll('-', '')),
      occurrenceId: null,
      year: Number(date.slice(0, 4)),
      month: Number(date.slice(5, 7)),
      date,
      side,
      counterparty: 'Robinhood (Sales)',
      recurring: false,
      notes: row.symbols.length ? `Sold ${row.symbols.join(', ')}` : 'Sale',
      settled: true,
      amount: Math.abs(net),
      amountSource: 'market-sale',
      category: null,
    });
  }
  return rows;
}

function centralIsoDate(iso: string): string | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Chicago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
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

function statusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'Active';
    case 'TRIAL': return 'Trial';
    case 'CANCELLED': return 'Cancelled';
    case 'EXPIRED': return 'Expired';
    default: return status;
  }
}

function cycleLabel(cycle: string): string {
  switch (cycle) {
    case 'WEEKLY': return 'Weekly';
    case 'MONTHLY': return 'Monthly';
    case 'ANNUAL': return 'Annual';
    default: return 'Other';
  }
}

function payStatus(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'Active';
    case 'PAUSED': return 'Paused';
    case 'CANCELLED': return 'Cancelled';
    default: return status;
  }
}

function frequencyLabel(frequency: string): string {
  switch (frequency) {
    case 'WEEKLY': return 'Weekly';
    case 'BIWEEKLY': return 'Every two weeks';
    case 'MONTHLY': return 'Monthly';
    case 'ANNUAL': return 'Annual';
    default: return 'Other';
  }
}

function methodLabel(method: string): string {
  switch (method) {
    case 'ACH': return 'Bank transfer';
    case 'CARD': return 'Card';
    case 'BILL_PAY': return 'Bill pay';
    default: return 'Other';
  }
}

function csvLine(cells: string[]): string {
  return cells.map((cell) => {
    const value = cell ?? '';
    return /[",\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
  }).join(',');
}

function tableHtml(columns: string[], rows: string[][]): string {
  return `<table><thead><tr>${columns.map((column) => `<th>${escapeHtml(column)}</th>`).join('')}</tr></thead><tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

function openPrint(title: string, body: string): void {
  const popup = window.open('', '_blank', 'noopener,noreferrer');
  if (!popup) {
    return;
  }
  popup.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${escapeHtml(title)}</title><style>
    body{font:15px/1.45 Georgia,serif;color:#1e293b;margin:2rem;max-width:52rem}
    h1{font-size:1.35rem} h2,h3{font-size:1rem;display:flex;justify-content:space-between;gap:1rem}
    ul{list-style:none;padding:0} li{display:flex;justify-content:space-between;gap:1rem;border-bottom:1px solid #e2e8f0;padding:.3rem 0}
    table{border-collapse:collapse;width:100%} th,td{border:1px solid #cbd5e1;padding:.35rem .45rem;text-align:left;vertical-align:top}
    .note{color:#64748b;font-size:.85rem}
  </style></head><body><h1>${escapeHtml(title)}</h1>${body}</body></html>`);
  popup.document.close();
  popup.focus();
  popup.print();
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
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
