import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RobinhoodCsvSavedImportDto, RobinhoodCsvUploadStatusDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-admin-finance-robinhood-csv',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './admin-finance-robinhood-csv.component.html',
  styleUrl: './admin-finance-robinhood-csv.component.scss',
})
export class AdminFinanceRobinhoodCsvComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  rhCsvUploadStatus: RobinhoodCsvUploadStatusDto | null = null;
  rhCsvUploadStatusLoading = false;
  rhCsvSaveResult: RobinhoodCsvSavedImportDto | null = null;
  rhCsvUploading = false;
  rhCsvApplyToDb = false;
  rhCsvSelectedLabel: string | null = null;
  rhCsvSelectedFile: File | null = null;

  ngOnInit(): void {
    this.loadRhCsvUploadStatus();
  }

  onRhCsvFileSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    this.rhCsvSelectedFile = f ?? null;
    this.rhCsvSelectedLabel = f?.name ?? null;
    this.rhCsvSaveResult = null;
  }

  loadRhCsvUploadStatus(): void {
    this.rhCsvUploadStatusLoading = true;
    this.financeApi.robinhoodCsvImportUploadStatus().subscribe({
      next: (s) => {
        this.rhCsvUploadStatus = s;
        this.rhCsvUploadStatusLoading = false;
      },
      error: (e) => {
        this.rhCsvUploadStatus = null;
        this.rhCsvUploadStatusLoading = false;
        this.snackBar.open(`Could not load CSV upload status — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  uploadRhCsvToImportFolder(): void {
    const f = this.rhCsvSelectedFile;
    if (!f) {
      this.snackBar.open('Choose a CSV file first', undefined, { duration: 4500 });
      return;
    }
    this.rhCsvUploading = true;
    this.rhCsvSaveResult = null;
    this.financeApi.robinhoodCsvSaveToImportDirectory(f, this.rhCsvApplyToDb).subscribe({
      next: (r) => {
        this.rhCsvSaveResult = r;
        this.rhCsvUploading = false;
        const ir = r.importResult;
        let msg: string;
        if (ir.apply && ir.errorCount === 0) {
          msg = `Saved and imported ${ir.insertedRows} row(s)`;
        } else if (ir.apply) {
          msg = `Saved; import finished with ${ir.errorCount} error(s)`;
        } else {
          msg = `Saved; dry-run parsed ${ir.parsedRows} row(s)`;
        }
        this.snackBar.open(msg, undefined, { duration: 6500 });
      },
      error: (e) => {
        this.rhCsvUploading = false;
        this.snackBar.open(`CSV save/import failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }
}
