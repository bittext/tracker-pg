import { CommonModule } from '@angular/common';
import { Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, forkJoin, of } from 'rxjs';
import { JournalEntryDto, JournalSummaryDto, JournalTagDefDto } from '../../models/journal.models';
import { ManagementTaskDto } from '../../models/management.models';
import {
  daysBetweenIsoDates,
  mgmtTaskDueRowClass,
  mgmtTaskDueVisual,
  normalizeMgmtDueIso,
} from '../../util/management-task-due';
import { JournalApiService } from '../../services/journal-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import {
  ReportJournalAttachmentsDialogComponent,
  ReportJournalAttachmentsDialogData,
} from './report-journal-attachments-dialog.component';
import { ReportJournalBodyDialogComponent, ReportJournalBodyDialogData } from './report-journal-body-dialog.component';
import { ReportsExerciseComponent } from './reports-exercise/reports-exercise.component';
import { ReportsFinanceBankingComponent } from './reports-finance-banking/reports-finance-banking.component';
import { ReportsFinanceRobinhoodComponent } from './reports-finance-robinhood/reports-finance-robinhood.component';
import { ReportsManagementNowPanelComponent } from './reports-management-now-panel/reports-management-now-panel.component';
import { INSIGHTS_TAB_LABELS } from '../../config/app-nav.config';

export type ReportsSection = 'all' | 'life' | 'markets';
export type ReportsFocus = 'exercise' | 'management' | 'journal' | 'banking';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatCardModule,
    MatTabsModule,
    MatTableModule,
    MatSnackBarModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatMenuModule,
    ReportsExerciseComponent,
    ReportsFinanceBankingComponent,
    ReportsFinanceRobinhoodComponent,
    ReportsManagementNowPanelComponent,
  ],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent implements OnInit {
  private readonly managementApi = inject(ManagementApiService);
  private readonly journalApi = inject(JournalApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly route = inject(ActivatedRoute);

  readonly insightsTabs = INSIGHTS_TAB_LABELS;

  @Input() section: ReportsSection = 'all';
  @Input() focus: ReportsFocus | null = null;

  /** Top-level tab index for the reports mat-tab-group. */
  reportsTabIndex = 0;

  /** Management → Tasks report (full task list). */
  managementTasks: ManagementTaskDto[] = [];
  managementTaskColumns = [
    'mtTitle',
    'mtDue',
    'mtUrgency',
    'mtCategory',
    'mtType',
    'mtDone',
    'mtCreated',
  ];

  journalFrom = '';
  journalTo = '';
  journalQ = '';
  journalTagIds: number[] = [];
  journalTagDefs: JournalTagDefDto[] = [];
  journalReportRows: JournalEntryDto[] = [];
  journalReportColumns = ['jDate', 'jTags', 'jAtt', 'jExcerpt', 'jMenu'];
  journalSummary: JournalSummaryDto | null = null;
  journalSearched = false;

  ngOnInit(): void {
    const routeSection = this.route.snapshot.data['section'] as ReportsSection | undefined;
    if (routeSection) {
      this.section = routeSection;
    }
    const routeFocus = this.route.snapshot.data['focus'] as ReportsFocus | undefined;
    if (routeFocus) {
      this.focus = routeFocus;
    }
    this.reportsTabIndex = this.initialTabIndex();

    const t = this.todayIso();
    this.journalTo = t;
    this.journalFrom = `${t.slice(0, 7)}-01`;
    this.journalApi.listTagDefinitions().subscribe({
      next: (rows) => {
        this.journalTagDefs = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load journal tags', e),
    });
    this.loadManagementTasksReport();
  }

  get showExerciseTab(): boolean {
    return this.section === 'all' || this.section === 'life';
  }

  get showManagementTab(): boolean {
    return this.section === 'all' || this.section === 'life';
  }

  get showFinanceTab(): boolean {
    return this.section === 'all' || this.section === 'life';
  }

  get showJournalTab(): boolean {
    return this.section === 'all' || this.section === 'life';
  }

  get showMarketsOnly(): boolean {
    return this.section === 'markets';
  }

  get pageTitle(): string {
    if (this.section === 'markets') {
      return 'Markets analytics';
    }
    if (this.section === 'life') {
      return 'Insights';
    }
    return 'Reports';
  }

  private initialTabIndex(): number {
    return 0;
  }

  private todayIso(): string {
    const d = new Date();
    const y = d.getFullYear();
    const mo = `${d.getMonth() + 1}`.padStart(2, '0');
    const day = `${d.getDate()}`.padStart(2, '0');
    return `${y}-${mo}-${day}`;
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  private loadManagementTasksReport(): void {
    this.managementApi
      .listTasksReport()
      .pipe(catchError(() => of<ManagementTaskDto[]>([])))
      .subscribe({
        next: (rows) => {
          this.managementTasks = [...rows].sort((a, b) => {
            const da = a.dueDate ?? '';
            const db = b.dueDate ?? '';
            if (da !== db) {
              return db.localeCompare(da);
            }
            return (b.id ?? 0) - (a.id ?? 0);
          });
        },
      });
  }

  formatMgmtDate(iso: string | null | undefined): string {
    if (!iso || iso.length < 10) {
      return '—';
    }
    const y = Number(iso.slice(0, 4));
    const m = Number(iso.slice(5, 7));
    const d = Number(iso.slice(8, 10));
    return new Date(y, m - 1, d).toLocaleDateString();
  }

  formatMgmtInstant(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const t = Date.parse(iso);
    if (Number.isNaN(t)) {
      return iso;
    }
    return new Date(t).toLocaleString();
  }

  urgencyClass(u: string): string {
    if (u === 'HIGH') {
      return 'rep-urgency-high';
    }
    if (u === 'LOW') {
      return 'rep-urgency-low';
    }
    return 'rep-urgency-mid';
  }

  taskDueRowClass(row: ManagementTaskDto): string {
    return mgmtTaskDueRowClass(row, this.todayIso());
  }

  /** Short hint under the due date in the Tasks report (overdue / due today). */
  mgmtDueColumnHint(row: ManagementTaskDto): string | null {
    const today = this.todayIso();
    const v = mgmtTaskDueVisual(row, today);
    const due = normalizeMgmtDueIso(row.dueDate);
    if ((v === 'overdue_1_7' || v === 'overdue_8_30' || v === 'overdue_31_plus') && due) {
      return `${daysBetweenIsoDates(due, today)}d overdue`;
    }
    if (v === 'open_due_today') {
      return 'Due today';
    }
    return null;
  }

  runJournalSearch(): void {
    if (!this.journalFrom || !this.journalTo) {
      return;
    }
    forkJoin({
      rows: this.journalApi.search(
        this.journalFrom,
        this.journalTo,
        this.journalQ.trim() || null,
        this.journalTagIds.length ? this.journalTagIds : null,
        null,
      ),
      sum: this.journalApi.summary(
        this.journalFrom,
        this.journalTo,
        this.journalQ.trim() || null,
        this.journalTagIds.length ? this.journalTagIds : null,
        null,
      ),
    }).subscribe({
      next: ({ rows, sum }) => {
        this.journalSearched = true;
        this.journalReportRows = [...rows].sort((a, b) => {
          const d = b.loggedOn.localeCompare(a.loggedOn);
          return d !== 0 ? d : (b.id ?? 0) - (a.id ?? 0);
        });
        this.journalSummary = sum;
      },
      error: (e) => this.err('Journal search failed', e),
    });
  }

  journalExcerpt(md: string | null | undefined): string {
    const s = (md ?? '').replace(/\s+/g, ' ').trim();
    if (s.length <= 180) {
      return s;
    }
    return `${s.slice(0, 180)}…`;
  }

  formatJournalTagNames(row: JournalEntryDto): string {
    const tags = row.tags ?? [];
    if (!tags.length) {
      return '—';
    }
    return tags
      .map((t) => t.name)
      .sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
      .join(', ');
  }

  journalAttachmentCount(row: JournalEntryDto): number {
    const n = row.attachmentCount;
    if (n != null && Number.isFinite(Number(n))) {
      return Math.max(0, Math.floor(Number(n)));
    }
    return row.attachments?.length ?? 0;
  }

  openJournalBodyDialog(row: JournalEntryDto): void {
    const data: ReportJournalBodyDialogData = {
      title: `Journal — ${row.loggedOn}`,
      bodyMarkdown: row.bodyMarkdown ?? '',
      tagsLine: this.formatJournalTagNames(row),
    };
    this.dialog.open(ReportJournalBodyDialogComponent, {
      width: 'min(92vw, 44rem)',
      maxHeight: '90vh',
      data,
    });
  }

  openJournalAttachmentsDialog(row: JournalEntryDto): void {
    const n = this.journalAttachmentCount(row);
    if (n < 1) {
      return;
    }
    this.journalApi.getEntry(row.id).subscribe({
      next: (e) => {
        const atts = e.attachments ?? [];
        const d: ReportJournalAttachmentsDialogData = {
          title: `Attachments — ${e.loggedOn}`,
          attachments: atts,
        };
        this.dialog.open(ReportJournalAttachmentsDialogComponent, {
          width: 'min(92vw, 30rem)',
          data: d,
        });
      },
      error: (err) => this.err('Could not load journal entry', err),
    });
  }
}
