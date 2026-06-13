import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FinanceEntryDocumentDto, FinanceEntryEntityType } from '../../../models/finance.models';
import { FinanceEntryDocumentsApiService } from '../../../services/finance-entry-documents-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-finance-entry-documents',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './finance-entry-documents.component.html',
  styleUrl: './finance-entry-documents.component.scss',
})
export class FinanceEntryDocumentsComponent implements OnChanges {
  private readonly api = inject(FinanceEntryDocumentsApiService);
  private readonly snackBar = inject(MatSnackBar);

  @Input({ required: true }) entityType!: FinanceEntryEntityType;
  @Input({ required: true }) entityId!: number | null;
  @Output() documentsChanged = new EventEmitter<void>();

  documents: FinanceEntryDocumentDto[] = [];
  loading = false;
  uploading = false;

  ngOnChanges(): void {
    this.load();
  }

  load(): void {
    if (this.entityId == null || this.entityId <= 0) {
      this.documents = [];
      return;
    }
    this.loading = true;
    this.api.list(this.entityType, this.entityId).subscribe({
      next: (rows) => {
        this.documents = rows;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load documents — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  onFileSelected(event: Event): void {
    if (this.entityId == null || this.uploading) {
      return;
    }
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.uploading = true;
    this.api.upload(this.entityType, this.entityId, file).subscribe({
      next: () => {
        this.uploading = false;
        this.snackBar.open('Document uploaded', undefined, { duration: 4500 });
        this.load();
        this.documentsChanged.emit();
      },
      error: (e) => {
        this.uploading = false;
        this.snackBar.open(`Upload failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  download(doc: FinanceEntryDocumentDto): void {
    this.api.downloadBlob(doc.id, 'attachment').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.displayName || doc.originalFilename;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => {
        this.snackBar.open(`Download failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  deleteDoc(doc: FinanceEntryDocumentDto): void {
    const label = doc.displayName || doc.originalFilename;
    if (!window.confirm(`Delete document "${label}"?`)) {
      return;
    }
    this.api.delete(doc.id).subscribe({
      next: () => {
        this.snackBar.open('Document deleted', undefined, { duration: 4500 });
        this.load();
        this.documentsChanged.emit();
      },
      error: (e) => {
        this.snackBar.open(`Delete failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  label(doc: FinanceEntryDocumentDto): string {
    return doc.displayName?.trim() || doc.originalFilename;
  }

  sizeLabel(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes < 0) {
      return '—';
    }
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
