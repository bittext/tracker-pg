import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { JournalAttachmentDto } from '../../models/journal.models';
import { JournalApiService } from '../../services/journal-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

export interface ReportJournalAttachmentsDialogData {
  title: string;
  attachments: JournalAttachmentDto[];
}

@Component({
  selector: 'app-report-journal-attachments-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatSnackBarModule],
  templateUrl: './report-journal-attachments-dialog.component.html',
  styleUrl: './report-journal-attachments-dialog.component.scss',
})
export class ReportJournalAttachmentsDialogComponent {
  readonly data = inject<ReportJournalAttachmentsDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ReportJournalAttachmentsDialogComponent>);
  private readonly journalApi = inject(JournalApiService);
  private readonly snackBar = inject(MatSnackBar);

  /** While a file request is in flight (avoid double-open). */
  openingId: number | null = null;

  close(): void {
    this.ref.close();
  }

  openAttachment(a: JournalAttachmentDto): void {
    this.openingId = a.id;
    this.journalApi.getAttachmentBlob(a.id, 'inline').subscribe({
      next: (blob) => {
        this.openingId = null;
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener,noreferrer');
        setTimeout(() => URL.revokeObjectURL(url), 120_000);
      },
      error: (e) => {
        this.openingId = null;
        this.snackBar.open(`Could not open file: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 10_000 });
      },
    });
  }

  formatSize(bytes: number | null | undefined): string {
    if (bytes == null || !Number.isFinite(bytes) || bytes < 0) {
      return '';
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
