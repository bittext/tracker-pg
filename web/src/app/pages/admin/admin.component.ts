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
import { ManagementTaskCategory, ManagementTaskType } from '../../models/management.models';
import { FitnessApiService } from '../../services/fitness-api.service';
import { ManagementApiService } from '../../services/management-api.service';
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
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private readonly fitnessApi = inject(FitnessApiService);
  private readonly managementApi = inject(ManagementApiService);
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

  ngOnInit(): void {
    this.reload();
    this.reloadManagement();
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
}
