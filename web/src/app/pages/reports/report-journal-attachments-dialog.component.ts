import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { JournalAttachmentDto } from '../../models/journal.models';
import { JournalApiService } from '../../services/journal-api.service';

export interface ReportJournalAttachmentsDialogData {
  title: string;
  attachments: JournalAttachmentDto[];
}

@Component({
  selector: 'app-report-journal-attachments-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  templateUrl: './report-journal-attachments-dialog.component.html',
  styleUrl: './report-journal-attachments-dialog.component.scss',
})
export class ReportJournalAttachmentsDialogComponent {
  readonly data = inject<ReportJournalAttachmentsDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ReportJournalAttachmentsDialogComponent>);
  private readonly journalApi = inject(JournalApiService);

  close(): void {
    this.ref.close();
  }

  fileUrl(attachmentId: number): string {
    return this.journalApi.attachmentFilePath(attachmentId);
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
