import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { Exercise } from '../../models/fitness.models';
import { FinanceNotificationSettingsDto, FinanceNotificationSettingsRequestDto } from '../../models/finance.models';
import { AuthLoginEventDto } from '../../models/auth-audit.models';
import { JournalTagDefDto } from '../../models/journal.models';
import { ManagementTaskCategory, ManagementTaskType } from '../../models/management.models';
import { AdminFinanceRobinhoodCsvComponent } from './admin-finance-robinhood-csv/admin-finance-robinhood-csv.component';
import { BankingPanelComponent } from '../finance/banking-panel/banking-panel.component';
import { AdminAuthAuditApiService } from '../../services/admin-auth-audit-api.service';
import { FitnessApiService } from '../../services/fitness-api.service';
import { FinanceApiService } from '../../services/finance-api.service';
import { JournalApiService } from '../../services/journal-api.service';
import { ManagementApiService } from '../../services/management-api.service';
import { AuthService } from '../../services/auth.service';
import { formatHttpErrorDetail } from '../../util/http-error';

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
    RouterLink,
    BankingPanelComponent,
    AdminFinanceRobinhoodCsvComponent,
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly financeApi = inject(FinanceApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly journalApi = inject(JournalApiService);
  private readonly adminAuthAuditApi = inject(AdminAuthAuditApiService);
  private readonly auth = inject(AuthService);
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
  readonly loginEventColumns = ['createdAt', 'eventType', 'username', 'clientIp', 'detail', 'userAgent'];

  /** App role ADMIN: required for the Sign-in log API and that tab. */
  get isAppAdmin(): boolean {
    return this.auth.isAdmin();
  }

  ngOnInit(): void {
    this.reload();
    this.reloadManagement();
    this.reloadJournalTags();
    this.loadFinanceNotificationSettings();
    if (this.isAppAdmin) {
      this.loadLoginEvents();
    }
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

  loadLoginEvents(): void {
    if (!this.isAppAdmin) {
      return;
    }
    this.loginEventLoading = true;
    this.adminAuthAuditApi.listLoginEvents(this.loginEventLimit, this.loginEventSearch).subscribe({
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
