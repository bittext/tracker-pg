import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabChangeEvent, MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatOption, MatSelectModule } from '@angular/material/select';
import { Exercise } from '../../models/fitness.models';
import { FinanceNotificationSettingsDto, FinanceNotificationSettingsRequestDto } from '../../models/finance.models';
import { AuthLoginEventDto } from '../../models/auth-audit.models';
import { JournalTagDefDto } from '../../models/journal.models';
import {
  ManagementCalendarType,
  ManagementCalendarTypeWriteBody,
  ManagementNowCardType,
  ManagementNowCardTypeWriteBody,
  ManagementTaskCategory,
  ManagementTaskType,
} from '../../models/management.models';
import { AdminFinanceRobinhoodCsvComponent } from './admin-finance-robinhood-csv/admin-finance-robinhood-csv.component';
import { AdminRobinhoodAgenticPanelComponent } from './admin-robinhood-agentic-panel/admin-robinhood-agentic-panel.component';
import { LoansPanelComponent } from '../finance/loans-panel/loans-panel.component';
import { AdminPredictsPanelComponent } from './admin-predicts-panel/admin-predicts-panel.component';
import { AdminUsagePanelComponent } from './admin-usage-panel/admin-usage-panel.component';
import { AdminFeaturesPanelComponent } from './admin-features-panel/admin-features-panel.component';
import { BankingPanelComponent } from '../finance/banking-panel/banking-panel.component';
import { AdminAuthAuditApiService } from '../../services/admin-auth-audit-api.service';
import { AdminGithubApiService } from '../../services/admin-github-api.service';
import { MeSignInLogApiService } from '../../services/me-sign-in-log-api.service';
import { FitnessApiService } from '../../services/fitness-api.service';
import { FinanceApiService } from '../../services/finance-api.service';
import { JournalApiService } from '../../services/journal-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { AuthService } from '../../services/auth.service';
import { formatHttpErrorDetail } from '../../util/http-error';
import { GithubRepositoryInsightsDto } from '../../models/github-insights.models';
import { MemberProfilePanelComponent } from '../member/member-profile-panel.component';
import { AdminMemberProfilesApiService } from '../../services/admin-member-profiles-api.service';
import { AdminUsersApiService } from '../../services/admin-users-api.service';
import {
  AdminCreateUserRequest,
  AdminMemberProfileListItemDto,
  AdminProvisionRole,
} from '../../models/admin-users.models';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatSnackBarModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatSelectModule,
    MatOption,
    RouterLink,
    BankingPanelComponent,
    AdminFinanceRobinhoodCsvComponent,
    AdminRobinhoodAgenticPanelComponent,
    LoansPanelComponent,
    AdminPredictsPanelComponent,
    AdminUsagePanelComponent,
    AdminFeaturesPanelComponent,
    MemberProfilePanelComponent,
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly auth = inject(AuthService);

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((qm) => {
      if (qm.get('onboardingProfile') === '1') {
        // Tab "My profile" shifts when the admin-only "Create user" tab is present.
        this.adminTabIndex = this.auth.isAdmin() ? 2 : 1;
      }
    });
  }
  private readonly financeApi = inject(FinanceApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly journalApi = inject(JournalApiService);
  private readonly adminAuthAuditApi = inject(AdminAuthAuditApiService);
  private readonly adminGithubApi = inject(AdminGithubApiService);
  private readonly adminUsersApi = inject(AdminUsersApiService);
  private readonly adminMemberProfilesApi = inject(AdminMemberProfilesApiService);
  private readonly meSignInLogApi = inject(MeSignInLogApiService);
  private readonly snackBar = inject(MatSnackBar);

  exercises: Exercise[] = [];
  columns = ['name', 'category', 'notes', 'actions'];
  newExercise: Partial<Exercise> = { name: '', category: '', notes: '' };

  categories: ManagementTaskCategory[] = [];
  categoryColumns = ['catName', 'catDesc', 'catActions'];
  newCategory: Partial<ManagementTaskCategory> = { name: '', description: '' };

  taskTypes: ManagementTaskType[] = [];
  taskTypeColumns = ['ttName', 'ttNotes', 'ttActions'];
  newTaskType: Partial<ManagementTaskType> = { name: '', notes: '' };

  nowCardTypes: ManagementNowCardType[] = [];
  nowCardTypeColumns = ['ncSlug', 'ncLabel', 'ncBadge', 'ncColor', 'ncSort', 'ncActions'];
  newNowCardType: {
    slug: string;
    label: string;
    badge: string;
    colorHex: string;
    sortIndex: string;
  } = { slug: '', label: '', badge: '', colorHex: '#6366f1', sortIndex: '' };

  calendarTypes: ManagementCalendarType[] = [];
  calendarTypeColumns = ['calCode', 'calLabel', 'calSort', 'calActions'];
  newCalendarType: { code: string; label: string; sortIndex: string } = { code: '', label: '', sortIndex: '' };

  journalTags: JournalTagDefDto[] = [];
  journalTagColumns = ['jName', 'jActions'];
  newJournalTagName = '';
  financeNotificationSettings: FinanceNotificationSettingsDto | null = null;
  financeNotificationSaving = false;
  financeNotificationTesting = false;
  financeNotificationForm: FinanceNotificationSettingsRequestDto = {
    emailAddress: '',
    mobileE164: '',
    emailEnabled: false,
    smsEnabled: false,
  };

  loginEvents: AuthLoginEventDto[] = [];
  loginEventLoading = false;
  loginEventSearch = '';
  loginEventLimit = 100;
  readonly loginEventColumns = ['createdAt', 'eventType', 'username', 'clientIp', 'locationLabel', 'detail', 'userAgent'];

  /** Selected tab in Admin (0 = Sign-in log, …). */
  adminTabIndex = 0;

  /**
   * Tab indices when {@link #isAppAdmin}. Features and Repository (GitHub) are admin-only.
   * 0 Sign-in log · 1 Create user · 2 My profile · 3 Exercise · 4 Journal · 5 Finance · 6 Management · 7 Usage · 8 Features · 9 Repository (GitHub)
   */
  private static readonly USAGE_TAB_INDEX = 7;
  private static readonly FEATURES_TAB_INDEX = 8;
  private static readonly GITHUB_TAB_INDEX = 9;

  createUserSaving = false;
  newProvisionedUser: {
    username: string;
    email: string;
    password: string;
    role: AdminProvisionRole;
    mfaEnabled: boolean;
    active: boolean;
  } = {
    username: '',
    email: '',
    password: '',
    role: 'USER',
    mfaEnabled: false,
    active: true,
  };

  /** Admin → Create user tab: members who saved a profile (browse read-only). */
  adminMemberProfileList: AdminMemberProfileListItemDto[] = [];
  adminMemberProfileListLoading = false;
  adminSelectedMemberUserId: number | null = null;

  githubInsights: GithubRepositoryInsightsDto | null = null;
  githubLoading = false;
  /** Set when the API returns 503 (integration disabled or owner/repo unset). */
  githubNotConfigured = false;
  readonly githubCommitColumns = ['shaShort', 'messageFirstLine', 'authorLogin', 'committedAt', 'link'];
  readonly githubContributorColumns = ['login', 'contributions', 'gh'];

  /** App role ADMIN: full sign-in audit and elevated server APIs under /api/admin/** */
  get isAppAdmin(): boolean {
    return this.auth.isAdmin();
  }

  onAdminTabChange(ev: MatTabChangeEvent): void {
    if (this.isAppAdmin && ev.index === AdminComponent.GITHUB_TAB_INDEX) {
      this.ensureGithubInsightsLoaded();
    }
  }

  ensureGithubInsightsLoaded(): void {
    if (!this.isAppAdmin || this.githubLoading || this.githubInsights) {
      return;
    }
    this.loadGithubInsights();
  }

  refreshGithubInsights(): void {
    if (!this.isAppAdmin) {
      return;
    }
    this.githubInsights = null;
    this.githubNotConfigured = false;
    this.loadGithubInsights();
  }

  loadGithubInsights(): void {
    if (!this.isAppAdmin) {
      return;
    }
    this.githubLoading = true;
    this.adminGithubApi.getRepositoryInsights().subscribe({
      next: (d) => {
        this.githubLoading = false;
        this.githubNotConfigured = false;
        this.githubInsights = d;
      },
      error: (e) => {
        this.githubLoading = false;
        this.githubInsights = null;
        if (this.isGithubIntegrationUnavailable(e)) {
          this.githubNotConfigured = true;
          return;
        }
        this.githubNotConfigured = false;
        this.err('Could not load GitHub insights', e);
      },
    });
  }

  ngOnInit(): void {
    this.reload();
    this.reloadManagement();
    this.reloadJournalTags();
    this.loadFinanceNotificationSettings();
    this.loadLoginEvents();
    if (this.isAppAdmin) {
      this.loadAdminMemberProfileList();
    }
  }

  loadAdminMemberProfileList(): void {
    if (!this.isAppAdmin) {
      return;
    }
    this.adminMemberProfileListLoading = true;
    this.adminMemberProfilesApi.listWithSavedProfile().subscribe({
      next: (rows) => {
        this.adminMemberProfileListLoading = false;
        this.adminMemberProfileList = rows;
      },
      error: (e) => {
        this.adminMemberProfileListLoading = false;
        this.adminMemberProfileList = [];
        this.err('Could not load member profile list', e);
      },
    });
  }

  adminMemberProfileOptionLabel(u: AdminMemberProfileListItemDto): string {
    const name = (u.displayName || '').trim();
    const left = name ? `${name} · ${u.username}` : u.username;
    return `${left} · ID ${u.memberPublicId}${u.onboardingCompleted ? '' : ' · onboarding open'}`;
  }

  compareAdminMemberUserId(a: number | null | undefined, b: number | null | undefined): boolean {
    return (a ?? null) === (b ?? null);
  }

  /** 503 from Spring when tracker.github is off or owner/repo unset; avoid noisy snackbar — show inline help instead. */
  private isGithubIntegrationUnavailable(e: unknown): boolean {
    if (e instanceof HttpErrorResponse && e.status === 503) {
      return true;
    }
    if (typeof e === 'object' && e !== null && 'status' in e) {
      const s = (e as { status?: number }).status;
      if (s === 503) {
        return true;
      }
    }
    const detail = formatHttpErrorDetail(e);
    return detail.includes('GitHub is disabled') || detail.includes('owner/repo is not set');
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }

  reload(): void {
    this.fitnessApi.listExercises().subscribe({
      next: (rows) => {
        this.exercises = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load exercises', e),
    });
  }

  addExercise(): void {
    const name = (this.newExercise.name || '').trim();
    if (!name) {
      return;
    }
    this.fitnessApi
      .createExercise({
        name,
        category: (this.newExercise.category || '').trim() || undefined,
        notes: (this.newExercise.notes || '').trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.newExercise = { name: '', category: '', notes: '' };
          this.reload();
          this.snackBar.open('Exercise added', undefined, { duration: 2500 });
        },
        error: (e) => this.err('Could not add exercise', e),
      });
  }

  deleteExercise(row: Exercise): void {
    if (row.id == null) {
      return;
    }
    this.fitnessApi.deleteExercise(row.id).subscribe({
      next: () => {
        this.reload();
        this.snackBar.open(`Removed “${row.name}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete exercise', e),
    });
  }

  reloadManagement(): void {
    this.managementApi.listCategories().subscribe({
      next: (rows) => {
        this.categories = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load categories', e),
    });
    this.managementApi.listTaskTypes().subscribe({
      next: (rows) => {
        this.taskTypes = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load task types', e),
    });
    this.managementApi.listNowCardTypes().subscribe({
      next: (rows) => {
        this.nowCardTypes = [...rows].sort((a, b) => {
          const si = (a.sortIndex ?? 0) - (b.sortIndex ?? 0);
          if (si !== 0) {
            return si;
          }
          return (a.slug || '').localeCompare(b.slug || '', undefined, { sensitivity: 'base' });
        });
      },
      error: (e) => this.err('Could not load Now card types', e),
    });
    this.managementApi.listCalendarTypes().subscribe({
      next: (rows) => {
        this.calendarTypes = [...rows].sort((a, b) => {
          const si = (a.sortIndex ?? 0) - (b.sortIndex ?? 0);
          if (si !== 0) {
            return si;
          }
          return (a.code || '').localeCompare(b.code || '', undefined, { sensitivity: 'base' });
        });
      },
      error: (e) => this.err('Could not load calendar types', e),
    });
  }

  addCategory(): void {
    const name = (this.newCategory.name || '').trim();
    if (!name) {
      return;
    }
    this.managementApi
      .createCategory({
        name,
        description: (this.newCategory.description || '').trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.newCategory = { name: '', description: '' };
          this.reloadManagement();
          this.snackBar.open('Category added', undefined, { duration: 2500 });
        },
        error: (e) => this.err('Could not add category', e),
      });
  }

  deleteCategory(row: ManagementTaskCategory): void {
    if (row.id == null) {
      return;
    }
    this.managementApi.deleteCategory(row.id).subscribe({
      next: () => {
        this.reloadManagement();
        this.snackBar.open(`Removed category “${row.name}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete category', e),
    });
  }

  addTaskType(): void {
    const name = (this.newTaskType.name || '').trim();
    if (!name) {
      return;
    }
    this.managementApi
      .createTaskType({
        name,
        notes: (this.newTaskType.notes || '').trim() || undefined,
      })
      .subscribe({
        next: () => {
          this.newTaskType = { name: '', notes: '' };
          this.reloadManagement();
          this.snackBar.open('Task type added', undefined, { duration: 2500 });
        },
        error: (e) => this.err('Could not add task type', e),
      });
  }

  deleteTaskType(row: ManagementTaskType): void {
    if (row.id == null) {
      return;
    }
    this.managementApi.deleteTaskType(row.id).subscribe({
      next: () => {
        this.reloadManagement();
        this.snackBar.open(`Removed task type “${row.name}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete task type', e),
    });
  }

  addNowCardType(): void {
    const slug = (this.newNowCardType.slug || '').trim().toLowerCase();
    const label = (this.newNowCardType.label || '').trim();
    const badge = (this.newNowCardType.badge || '').trim();
    const colorHex = (this.newNowCardType.colorHex || '').trim();
    if (!slug || !label || !badge || !colorHex) {
      this.snackBar.open('Slug, label, badge, and color are required.', 'Dismiss', { duration: 5000 });
      return;
    }
    const sortRaw = (this.newNowCardType.sortIndex || '').trim();
    let sortIndex: number | null = null;
    if (sortRaw !== '') {
      const n = Number(sortRaw);
      if (!Number.isFinite(n)) {
        this.snackBar.open('Sort order must be a number.', 'Dismiss', { duration: 5000 });
        return;
      }
      sortIndex = n;
    }
    const body: ManagementNowCardTypeWriteBody = {
      slug,
      label,
      badge,
      colorHex,
      sortIndex,
    };
    this.managementApi.createNowCardType(body).subscribe({
      next: () => {
        this.newNowCardType = { slug: '', label: '', badge: '', colorHex: '#6366f1', sortIndex: '' };
        this.reloadManagement();
        this.snackBar.open('Now card type added', undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not add Now card type', e),
    });
  }

  deleteNowCardType(row: ManagementNowCardType): void {
    if (row.id == null) {
      return;
    }
    this.managementApi.deleteNowCardType(row.id).subscribe({
      next: () => {
        this.reloadManagement();
        this.snackBar.open(`Removed type “${row.slug}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete Now card type', e),
    });
  }

  addCalendarType(): void {
    const code = (this.newCalendarType.code || '').trim().toUpperCase().replace(/\s+/g, '_');
    const label = (this.newCalendarType.label || '').trim();
    if (!code || !label) {
      this.snackBar.open('Code and label are required.', 'Dismiss', { duration: 5000 });
      return;
    }
    if (!/^[A-Z][A-Z0-9_]{0,30}$/.test(code)) {
      this.snackBar.open(
        'Code must start with a letter and use uppercase letters, digits, or underscore.',
        'Dismiss',
        { duration: 6000 },
      );
      return;
    }
    const sortRaw = (this.newCalendarType.sortIndex || '').trim();
    let sortIndex: number | null = null;
    if (sortRaw !== '') {
      const n = Number(sortRaw);
      if (!Number.isFinite(n)) {
        this.snackBar.open('Sort order must be a number.', 'Dismiss', { duration: 5000 });
        return;
      }
      sortIndex = n;
    }
    const body: ManagementCalendarTypeWriteBody = { code, label, sortIndex };
    this.managementApi.createCalendarType(body).subscribe({
      next: () => {
        this.newCalendarType = { code: '', label: '', sortIndex: '' };
        this.reloadManagement();
        this.snackBar.open('Calendar type added', undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not add calendar type', e),
    });
  }

  deleteCalendarType(row: ManagementCalendarType): void {
    if (row.id == null) {
      return;
    }
    this.managementApi.deleteCalendarType(row.id).subscribe({
      next: () => {
        this.reloadManagement();
        this.snackBar.open(`Removed type “${row.code}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete calendar type', e),
    });
  }

  reloadJournalTags(): void {
    this.journalApi.listTagDefinitions().subscribe({
      next: (rows) => {
        this.journalTags = [...rows].sort((a, b) =>
          (a.name || '').localeCompare(b.name || '', undefined, { sensitivity: 'base' }),
        );
      },
      error: (e) => this.err('Could not load journal tags', e),
    });
  }

  addJournalTag(): void {
    const name = (this.newJournalTagName || '').trim();
    if (!name) {
      return;
    }
    this.journalApi.createTag(name).subscribe({
      next: () => {
        this.newJournalTagName = '';
        this.reloadJournalTags();
        this.snackBar.open('Journal tag added', undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not add journal tag', e),
    });
  }

  deleteJournalTag(row: JournalTagDefDto): void {
    this.journalApi.deleteTag(row.id).subscribe({
      next: () => {
        this.reloadJournalTags();
        this.snackBar.open(`Removed tag “${row.name}”`, undefined, { duration: 2500 });
      },
      error: (e) => this.err('Could not delete journal tag', e),
    });
  }

  loadFinanceNotificationSettings(): void {
    this.financeApi.financeNotificationSettings().subscribe({
      next: (settings) => {
        this.financeNotificationSettings = settings;
        this.financeNotificationForm = {
          emailAddress: settings.emailAddress || '',
          mobileE164: settings.mobileE164 || '',
          emailEnabled: settings.emailEnabled,
          smsEnabled: settings.smsEnabled,
        };
      },
      error: (e) => this.err('Could not load finance notification settings', e),
    });
  }

  saveFinanceNotificationSettings(): void {
    this.financeNotificationSaving = true;
    this.financeApi.saveFinanceNotificationSettings(this.financeNotificationForm).subscribe({
      next: (settings) => {
        this.financeNotificationSaving = false;
        this.financeNotificationSettings = settings;
        this.snackBar.open('Finance notification settings saved', undefined, { duration: 2500 });
      },
      error: (e) => {
        this.financeNotificationSaving = false;
        this.err('Could not save finance notification settings', e);
      },
    });
  }

  createProvisionedUser(): void {
    if (!this.isAppAdmin) {
      return;
    }
    const u = (this.newProvisionedUser.username || '').trim();
    const e = (this.newProvisionedUser.email || '').trim();
    const p = this.newProvisionedUser.password;
    if (!u || !e || !p) {
      this.snackBar.open('Username, email, and password are required.', 'Dismiss', { duration: 5000 });
      return;
    }
    if (p.length < 8) {
      this.snackBar.open('Password must be at least 8 characters.', 'Dismiss', { duration: 5000 });
      return;
    }
    const body: AdminCreateUserRequest = {
      username: u,
      email: e,
      password: p,
      role: this.newProvisionedUser.role,
      mfaEnabled: this.newProvisionedUser.mfaEnabled,
      active: this.newProvisionedUser.active,
    };
    this.createUserSaving = true;
    this.adminUsersApi.createUser(body).subscribe({
      next: () => {
        this.createUserSaving = false;
        this.newProvisionedUser = {
          username: '',
          email: '',
          password: '',
          role: 'USER',
          mfaEnabled: false,
          active: true,
        };
        this.snackBar.open(
          'User created. A welcome email was sent when outbound email (SES) is configured on the server.',
          undefined,
          { duration: 6000 },
        );
      },
      error: (err) => {
        this.createUserSaving = false;
        this.err('Could not create user', err);
      },
    });
  }

  loadLoginEvents(): void {
    this.loginEventLoading = true;
    const req = this.isAppAdmin
      ? this.adminAuthAuditApi.listLoginEvents(this.loginEventLimit, this.loginEventSearch)
      : this.meSignInLogApi.list(this.loginEventLimit, this.loginEventSearch);
    req.subscribe({
      next: (rows) => {
        this.loginEventLoading = false;
        this.loginEvents = rows;
      },
      error: (e) => {
        this.loginEventLoading = false;
        this.err('Could not load sign-in log', e);
      },
    });
  }

  testFinanceNotificationSettings(email: boolean, sms: boolean): void {
    this.financeNotificationTesting = true;
    this.financeApi.testFinanceNotificationSettings(email, sms).subscribe({
      next: (r) => {
        this.financeNotificationTesting = false;
        const failures = r.events.filter((e) => e.status === 'FAILED').length;
        this.snackBar.open(
          failures ? `Test completed with ${failures} failure(s)` : 'Finance notification test sent',
          undefined,
          { duration: 3500 },
        );
      },
      error: (e) => {
        this.financeNotificationTesting = false;
        this.err('Could not test finance notifications', e);
      },
    });
  }
}
