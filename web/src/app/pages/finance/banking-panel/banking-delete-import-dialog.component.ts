import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface BankingDeleteImportDialogData {
  filename: string;
  rowsInserted: number;
  fileKind: string;
}

@Component({
  selector: 'app-banking-delete-import-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Remove this banking import?</h2>
    <mat-dialog-content class="dlg">
      <p>
        You are about to delete the stored file
        <strong>{{ data.filename }}</strong>
        (<span>{{ data.fileKind }}</span>). This upload recorded
        <strong>{{ data.rowsInserted }}</strong>
        ledger row{{ data.rowsInserted === 1 ? '' : 's' }} when it ran.
      </p>
      <ul class="disc">
        <li>
          <strong>All transactions</strong>
          imported from this file will be permanently removed from your banking ledger (database records).
        </li>
        <li>
          The file on disk under the server&apos;s banking import directory will be deleted when possible. Deleting files
          only on disk (outside this app) does <strong>not</strong> clean the ledger.
        </li>
        <li>
          Re-uploading the same file later may succeed; duplicate imports are still skipped by content hash / row
          deduplication rules.
        </li>
      </ul>
      <p class="muted">You cannot undo this action.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close(false)">Cancel</button>
      <button mat-flat-button color="warn" type="button" (click)="ref.close(true)">Remove import and data</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dlg {
        max-width: 520px;
      }
      .disc {
        margin: 0.75rem 0 0;
        padding-left: 1.25rem;
      }
      .disc li {
        margin-bottom: 0.5rem;
      }
      .muted {
        opacity: 0.85;
        font-size: 0.9rem;
      }
    `,
  ],
})
export class BankingDeleteImportDialogComponent {
  readonly data = inject<BankingDeleteImportDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<BankingDeleteImportDialogComponent, boolean>);
}
