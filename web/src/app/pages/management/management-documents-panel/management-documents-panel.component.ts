import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { ManagementDocumentDto, ManagementDocumentWriteBody } from '../../../models/management.models';
import { ManagementApiService } from '../../../services/management-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

type PreviewKind = 'none' | 'loading' | 'image' | 'pdf' | 'text' | 'unsupported';

@Component({
  selector: 'app-management-documents-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './management-documents-panel.component.html',
  styleUrl: './management-documents-panel.component.scss',
})
export class ManagementDocumentsPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly sanitizer = inject(DomSanitizer);

  documents: ManagementDocumentDto[] = [];
  loadingList = false;
  uploading = false;
  savingMeta = false;
  deleting = false;

  fileInput: File | null = null;
  uploadDisplayName = '';
  uploadDocType = 'Personal';

  search = '';

  selected: ManagementDocumentDto | null = null;
  editDisplayName = '';
  editDocType = '';

  previewKind: PreviewKind = 'none';
  previewText = '';
  private previewObjectUrl: string | null = null;
  safeImageUrl: SafeUrl | null = null;
  safePdfUrl: SafeResourceUrl | null = null;

  private pendingSelectId: number | null = null;

  ngOnInit(): void {
    this.refreshAll();
  }

  ngOnDestroy(): void {
    this.revokePreviewUrl();
  }

  refreshAll(): void {
    this.reloadList();
  }

  get filteredDocuments(): ManagementDocumentDto[] {
    const q = this.search.trim().toLowerCase();
    if (!q) {
      return this.documents;
    }
    return this.documents.filter(
      (d) =>
        d.displayName.toLowerCase().includes(q) ||
        d.docType.toLowerCase().includes(q) ||
        (d.originalFilename || '').toLowerCase().includes(q),
    );
  }

  onFileSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = input.files;
    this.fileInput = files && files.length ? files[0] : null;
    if (this.fileInput && !this.uploadDisplayName.trim()) {
      this.uploadDisplayName = this.fileInput.name;
    }
    input.value = '';
  }

  upload(): void {
    if (!this.fileInput) {
      this.err('Choose a file first');
      return;
    }
    const dn = this.uploadDisplayName.trim();
    const dt = this.uploadDocType.trim();
    if (!dn || !dt) {
      this.err('Name and type are required');
      return;
    }
    this.uploading = true;
    this.api.uploadDocument(this.fileInput, dn, dt).subscribe({
      next: (row) => {
        this.uploading = false;
        this.fileInput = null;
        this.uploadDisplayName = '';
        this.snackBar.open('Document saved', 'Dismiss', { duration: 3500 });
        this.pendingSelectId = row.id;
        this.reloadList();
      },
      error: (e) => {
        this.uploading = false;
        this.err('Upload failed', e);
      },
    });
  }

  select(doc: ManagementDocumentDto): void {
    this.selected = doc;
    this.editDisplayName = doc.displayName;
    this.editDocType = doc.docType;
    this.loadPreview(doc);
  }

  clearSelection(): void {
    this.pendingSelectId = null;
    this.selected = null;
    this.editDisplayName = '';
    this.editDocType = '';
    this.revokePreviewUrl();
    this.previewKind = 'none';
    this.previewText = '';
  }

  saveMetadata(): void {
    if (!this.selected) {
      return;
    }
    const body: ManagementDocumentWriteBody = {
      displayName: this.editDisplayName.trim(),
      docType: this.editDocType.trim(),
    };
    if (!body.displayName || !body.docType) {
      this.err('Name and type cannot be empty');
      return;
    }
    this.savingMeta = true;
    this.api.updateDocument(this.selected.id, body).subscribe({
      next: (row) => {
        this.savingMeta = false;
        const idx = this.documents.findIndex((d) => d.id === row.id);
        if (idx >= 0) {
          this.documents[idx] = row;
          this.documents = [...this.documents];
        }
        this.selected = row;
        this.editDisplayName = row.displayName;
        this.editDocType = row.docType;
        this.snackBar.open('Details updated', 'Dismiss', { duration: 2500 });
      },
      error: (e) => {
        this.savingMeta = false;
        this.err('Could not update', e);
      },
    });
  }

  deleteSelected(): void {
    if (!this.selected) {
      return;
    }
    if (!confirm(`Remove “${this.selected.displayName}” from your vault?`)) {
      return;
    }
    const id = this.selected.id;
    this.deleting = true;
    this.api.deleteDocument(id).subscribe({
      next: () => {
        this.deleting = false;
        this.documents = this.documents.filter((d) => d.id !== id);
        this.clearSelection();
        this.snackBar.open('Document removed', 'Dismiss', { duration: 2500 });
      },
      error: (e) => {
        this.deleting = false;
        this.err('Could not delete', e);
      },
    });
  }

  downloadSelected(): void {
    if (!this.selected) {
      return;
    }
    this.api.getDocumentBlob(this.selected.id, 'attachment').subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = this.selected!.originalFilename || this.selected!.displayName || 'download';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.err('Download failed', e),
    });
  }

  formatBytes(n: number): string {
    if (n < 1024) {
      return `${n} B`;
    }
    if (n < 1024 * 1024) {
      return `${(n / 1024).toFixed(1)} KB`;
    }
    return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  }

  private reloadList(): void {
    this.loadingList = true;
    this.api.listDocuments().subscribe({
      next: (rows) => {
        this.loadingList = false;
        this.documents = rows;
        const pending = this.pendingSelectId;
        if (pending != null) {
          this.pendingSelectId = null;
          const u = rows.find((r) => r.id === pending);
          if (u) {
            this.select(u);
            return;
          }
        }
        if (this.selected) {
          const u = rows.find((r) => r.id === this.selected!.id);
          if (u) {
            this.selected = u;
            this.editDisplayName = u.displayName;
            this.editDocType = u.docType;
          } else {
            this.clearSelection();
          }
        }
      },
      error: (e) => {
        this.loadingList = false;
        this.err('Could not load documents', e);
      },
    });
  }

  private loadPreview(doc: ManagementDocumentDto): void {
    this.revokePreviewUrl();
    this.previewKind = 'loading';
    this.previewText = '';
    this.safeImageUrl = null;
    this.safePdfUrl = null;
    this.api.getDocumentBlob(doc.id, 'inline').subscribe({
      next: (blob) => {
        const ct = (doc.contentType && doc.contentType.trim()) || blob.type || 'application/octet-stream';
        if (ct.startsWith('text/') || ct === 'application/json' || ct === 'application/xml') {
          blob
            .text()
            .then((t) => {
              this.previewKind = 'text';
              this.previewText = t;
            })
            .catch(() => {
              this.previewKind = 'unsupported';
            });
          return;
        }
        const url = URL.createObjectURL(blob);
        this.previewObjectUrl = url;
        if (ct.startsWith('image/')) {
          this.previewKind = 'image';
          this.safeImageUrl = this.sanitizer.bypassSecurityTrustUrl(url);
        } else if (ct === 'application/pdf' || ct.includes('pdf')) {
          this.previewKind = 'pdf';
          this.safePdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
        } else {
          this.previewKind = 'unsupported';
          URL.revokeObjectURL(url);
          this.previewObjectUrl = null;
        }
      },
      error: (e) => {
        this.previewKind = 'unsupported';
        this.err('Preview failed', e);
      },
    });
  }

  private revokePreviewUrl(): void {
    if (this.previewObjectUrl) {
      URL.revokeObjectURL(this.previewObjectUrl);
      this.previewObjectUrl = null;
    }
    this.safeImageUrl = null;
    this.safePdfUrl = null;
  }

  private err(msg: string, e?: unknown): void {
    const detail = e != null ? formatHttpErrorDetail(e) : '';
    this.snackBar.open(detail ? `${msg}: ${detail}` : msg, 'Dismiss', { duration: 6000 });
  }
}
