import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { SafeMarkdownPipe } from '../../pipes/safe-markdown.pipe';

export interface ReportJournalBodyDialogData {
  title: string;
  bodyMarkdown: string;
  tagsLine: string;
}

@Component({
  selector: 'app-report-journal-body-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, SafeMarkdownPipe],
  templateUrl: './report-journal-body-dialog.component.html',
  styleUrl: './report-journal-body-dialog.component.scss',
})
export class ReportJournalBodyDialogComponent {
  readonly data = inject<ReportJournalBodyDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<ReportJournalBodyDialogComponent>);

  close(): void {
    this.ref.close();
  }
}
