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
  JournalBookDto,
  JournalBookStatus,
  JournalBookWriteBody,
} from '../../../models/journal.models';
import { JournalApiService } from '../../../services/journal-api.service';
import { SafeMarkdownPipe } from '../../../pipes/safe-markdown.pipe';
import { formatHttpErrorDetail } from '../../../util/http-error';

type BookFilter = 'ALL' | JournalBookStatus;

@Component({
  selector: 'app-journal-books-panel',
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
  templateUrl: './journal-books-panel.component.html',
  styleUrl: './journal-books-panel.component.scss',
})
export class JournalBooksPanelComponent implements OnInit {
  private readonly api = inject(JournalApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly statusOptions: Array<{ value: JournalBookStatus; label: string }> = [
    { value: 'TO_READ', label: 'Want to read' },
    { value: 'READING', label: 'Reading now' },
    { value: 'FINISHED', label: 'Finished' },
  ];

  readonly filters: Array<{ value: BookFilter; label: string }> = [
    { value: 'ALL', label: 'All' },
    ...this.statusOptions.map((option) => ({ value: option.value, label: option.label })),
  ];

  readonly ratingOptions = [5, 4, 3, 2, 1];

  loading = false;
  saving = false;
  search = '';
  statusFilter: BookFilter = 'ALL';
  books: JournalBookDto[] = [];
  editingId: number | null = null;
  form: JournalBookWriteBody = this.emptyForm();

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const status = this.statusFilter === 'ALL' ? null : this.statusFilter;
    this.api.listBooks(status, this.search.trim() || null).subscribe({
      next: (rows) => {
        this.books = rows;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.err('Could not load books', e);
      },
    });
  }

  statusLabel(status: JournalBookStatus): string {
    return this.statusOptions.find((option) => option.value === status)?.label ?? status;
  }

  selectBook(book: JournalBookDto): void {
    this.editingId = book.id;
    this.form = {
      title: book.title,
      author: book.author,
      status: book.status,
      url: book.url,
      notesMarkdown: book.notesMarkdown,
      startedOn: book.startedOn,
      finishedOn: book.finishedOn,
      rating: book.rating,
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
    const body: JournalBookWriteBody = {
      title,
      author: this.nullIfBlank(this.form.author),
      status: this.form.status,
      url: this.nullIfBlank(this.form.url),
      notesMarkdown: this.form.notesMarkdown ?? '',
      startedOn: this.form.startedOn || null,
      finishedOn: this.form.finishedOn || null,
      rating: this.form.rating ?? null,
    };
    this.saving = true;
    const req =
      this.editingId != null ? this.api.updateBook(this.editingId, body) : this.api.createBook(body);
    req.subscribe({
      next: (saved) => {
        this.saving = false;
        this.snackBar.open(this.editingId != null ? 'Book saved' : 'Book added', undefined, {
          duration: 2000,
        });
        this.editingId = saved.id;
        this.load();
      },
      error: (e) => {
        this.saving = false;
        this.err('Could not save book', e);
      },
    });
  }

  deleteSelected(): void {
    if (this.editingId == null) {
      return;
    }
    this.api.deleteBook(this.editingId).subscribe({
      next: () => {
        this.snackBar.open('Book deleted', undefined, { duration: 2000 });
        this.startNew();
        this.load();
      },
      error: (e) => this.err('Could not delete book', e),
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

  private emptyForm(): JournalBookWriteBody {
    return {
      title: '',
      author: '',
      status: 'READING',
      url: '',
      notesMarkdown: '',
      startedOn: null,
      finishedOn: null,
      rating: null,
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
