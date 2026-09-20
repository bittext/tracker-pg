import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FinanceNewsScanDto } from '../../models/finance.models';
import { FinanceApiService } from '../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

export interface MarketsNewsScanTickersDialogData {
  tickers: string[];
}

@Component({
  selector: 'app-markets-news-scan-tickers-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSnackBarModule],
  template: `
    <h2 mat-dialog-title>News scan tickers</h2>
    <mat-dialog-content class="dlg">
      <p class="muted">
        Up to 12 symbols. Separate with commas, spaces, or new lines. The Markets header scans today’s worldwide
        headlines for this list.
      </p>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Tickers</mat-label>
        <textarea matInput rows="5" [(ngModel)]="draft" placeholder="HOOD, MRNA, SKHY"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="ref.close()" [disabled]="saving">Cancel</button>
      <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="saving">Save</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dlg {
        min-width: min(28rem, 86vw);
      }
      .full {
        width: 100%;
      }
      .muted {
        margin: 0 0 0.75rem;
        font-size: 0.85rem;
        color: var(--app-text-muted, #64748b);
      }
    `,
  ],
})
export class MarketsNewsScanTickersDialogComponent {
  readonly data = inject<MarketsNewsScanTickersDialogData>(MAT_DIALOG_DATA);
  readonly ref = inject(MatDialogRef<MarketsNewsScanTickersDialogComponent, FinanceNewsScanDto | undefined>);
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  draft = (this.data.tickers ?? []).join(', ');
  saving = false;

  save(): void {
    this.saving = true;
    this.financeApi.replaceFinanceNewsScanTickers(this.draft).subscribe({
      next: (scan) => {
        this.saving = false;
        this.ref.close(scan);
      },
      error: (err: unknown) => {
        this.saving = false;
        this.snackBar.open(formatHttpErrorDetail(err), 'Dismiss', { duration: 6000 });
      },
    });
  }
}
