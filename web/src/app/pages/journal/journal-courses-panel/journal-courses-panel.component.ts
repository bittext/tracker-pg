import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  JournalCourseDto,
  JournalCourseStatus,
  JournalCourseWriteBody,
} from '../../../models/journal.models';
import { JournalApiService } from '../../../services/journal-api.service';
import { SafeMarkdownPipe } from '../../../pipes/safe-markdown.pipe';
import { formatHttpErrorDetail } from '../../../util/http-error';

type CourseFilter = 'ALL' | JournalCourseStatus;

@Component({
  selector: 'app-journal-courses-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    SafeMarkdownPipe,
  ],
  templateUrl: './journal-courses-panel.component.html',
  styleUrl: './journal-courses-panel.component.scss',
})
export class JournalCoursesPanelComponent implements OnInit {
  private readonly api = inject(JournalApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly statusOptions: Array<{ value: JournalCourseStatus; label: string }> = [
    { value: 'INTEND', label: 'Plan to learn' },
    { value: 'IN_PROGRESS', label: 'Learning now' },
    { value: 'COMPLETED', label: 'Completed' },
  ];

  readonly filters: Array<{ value: CourseFilter; label: string }> = [
    { value: 'ALL', label: 'All' },
    ...this.statusOptions.map((option) => ({ value: option.value, label: option.label })),
  ];

  loading = false;
  saving = false;
  search = '';
  statusFilter: CourseFilter = 'ALL';
  courses: JournalCourseDto[] = [];
  editingId: number | null = null;
  form: JournalCourseWriteBody = this.emptyForm();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const status = this.statusFilter === 'ALL' ? null : this.statusFilter;
    this.api.listCourses(status, this.search.trim() || null).subscribe({
      next: (rows) => {
        this.courses = rows;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.err('Could not load courses', e);
      },
    });
  }

  filteredCourses(): JournalCourseDto[] {
    return this.courses;
  }

  statusLabel(status: JournalCourseStatus): string {
    return this.statusOptions.find((option) => option.value === status)?.label ?? status;
  }

  selectCourse(course: JournalCourseDto): void {
    this.editingId = course.id;
    this.form = {
      title: course.title,
      provider: course.provider,
      status: course.status,
      url: course.url,
      notesMarkdown: course.notesMarkdown,
      startedOn: course.startedOn,
      completedOn: course.completedOn,
    };
  }

  startNew(): void {
    this.editingId = null;
    this.form = this.emptyForm();
  }

  save(): void {
    const title = (this.form.title ?? '').trim();
    if (!title) {
      this.snackBar.open('Title is required', undefined, { duration: 2500 });
      return;
    }
    const body: JournalCourseWriteBody = {
      title,
      provider: this.nullIfBlank(this.form.provider),
      status: this.form.status,
      url: this.nullIfBlank(this.form.url),
      notesMarkdown: this.form.notesMarkdown ?? '',
      startedOn: this.form.startedOn || null,
      completedOn: this.form.completedOn || null,
    };
    this.saving = true;
    const req =
      this.editingId != null
        ? this.api.updateCourse(this.editingId, body)
        : this.api.createCourse(body);
    req.subscribe({
      next: (saved) => {
        this.saving = false;
        this.snackBar.open(this.editingId != null ? 'Course saved' : 'Course added', undefined, {
          duration: 2000,
        });
        this.editingId = saved.id;
        this.load();
      },
      error: (e) => {
        this.saving = false;
        this.err('Could not save course', e);
      },
    });
  }

  deleteSelected(): void {
    if (this.editingId == null) {
      return;
    }
    this.api.deleteCourse(this.editingId).subscribe({
      next: () => {
        this.snackBar.open('Course deleted', undefined, { duration: 2000 });
        this.startNew();
        this.load();
      },
      error: (e) => this.err('Could not delete course', e),
    });
  }

  openUrl(url: string | null | undefined): void {
    const target = this.normalizeUrl(url);
    if (!target) {
      return;
    }
    window.open(target, '_blank', 'noopener,noreferrer');
  }

  hasUrl(url: string | null | undefined): boolean {
    return !!this.normalizeUrl(url);
  }

  private emptyForm(): JournalCourseWriteBody {
    return {
      title: '',
      provider: '',
      status: 'IN_PROGRESS',
      url: '',
      notesMarkdown: '',
      startedOn: null,
      completedOn: null,
    };
  }

  private nullIfBlank(value: string | null | undefined): string | null {
    const trimmed = (value ?? '').trim();
    return trimmed ? trimmed : null;
  }

  private normalizeUrl(url: string | null | undefined): string | null {
    const trimmed = (url ?? '').trim();
    if (!trimmed) {
      return null;
    }
    if (/^https?:\/\//i.test(trimmed)) {
      return trimmed;
    }
    return `https://${trimmed}`;
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 8000 });
  }
}
