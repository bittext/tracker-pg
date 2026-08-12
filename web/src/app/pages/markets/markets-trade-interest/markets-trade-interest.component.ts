import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  RobinhoodTradeInterestDto,
  RobinhoodTradeInterestRequestDto,
} from '../../../models/finance.models';
import { RobinhoodTradeInterestApiService } from '../../../services/robinhood-trade-interest-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-markets-trade-interest',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './markets-trade-interest.component.html',
  styleUrl: './markets-trade-interest.component.scss',
})
export class MarketsTradeInterestComponent implements OnInit {
  private readonly api = inject(RobinhoodTradeInterestApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly displayedColumns = [
    'plannedAt',
    'kind',
    'symbol',
    'underlying',
    'contract',
    'expiry',
    'status',
    'note',
    'actions',
  ] as const;

  rows: RobinhoodTradeInterestDto[] = [];
  statusFilter = 'OPEN';
  loading = false;
  saving = false;
  editingId: number | null = null;

  /** Bound to datetime-local (local wall clock). */
  plannedAtLocal = '';
  form: {
    instrumentKind: 'STOCK' | 'OPTION';
    symbol: string;
    underlyingPrice: number | null;
    contractTargetPrice: number | null;
    expiryDate: string;
    note: string;
    status: 'OPEN' | 'TAKEN' | 'PASSED' | 'EXPIRED';
  } = this.emptyForm();

  ngOnInit(): void {
    this.resetForm();
    this.refresh();
  }

  get isOption(): boolean {
    return this.form.instrumentKind === 'OPTION';
  }

  refresh(): void {
    this.loading = true;
    const status = this.statusFilter === 'ALL' ? null : this.statusFilter;
    this.api.list(status).subscribe({
      next: (rows) => {
        this.rows = rows;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.toastError(err);
      },
    });
  }

  onKindChange(): void {
    if (!this.isOption) {
      this.form.contractTargetPrice = null;
      this.form.expiryDate = '';
    }
  }

  startEdit(row: RobinhoodTradeInterestDto): void {
    this.editingId = row.id;
    this.plannedAtLocal = this.toDatetimeLocal(row.plannedAt);
    this.form = {
      instrumentKind: row.instrumentKind === 'OPTION' ? 'OPTION' : 'STOCK',
      symbol: row.symbol,
      underlyingPrice: row.underlyingPrice,
      contractTargetPrice: row.contractTargetPrice,
      expiryDate: row.expiryDate ?? '',
      note: row.note ?? '',
      status:
        row.status === 'TAKEN' || row.status === 'PASSED' || row.status === 'EXPIRED'
          ? row.status
          : 'OPEN',
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.resetForm();
  }

  save(): void {
    const symbol = this.form.symbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Symbol is required.', 'Dismiss', { duration: 3500 });
      return;
    }
    if (!this.plannedAtLocal) {
      this.snackBar.open('Plan date and time are required.', 'Dismiss', { duration: 3500 });
      return;
    }
    const underlying = Number(this.form.underlyingPrice);
    if (!(underlying > 0)) {
      this.snackBar.open('Underlying stock cost must be greater than zero.', 'Dismiss', { duration: 3500 });
      return;
    }
    let contract: number | null = null;
    let expiry: string | null = null;
    if (this.form.instrumentKind === 'OPTION') {
      contract = Number(this.form.contractTargetPrice);
      if (!(contract > 0)) {
        this.snackBar.open('Contract target cost is required for options.', 'Dismiss', { duration: 3500 });
        return;
      }
      if (!this.form.expiryDate) {
        this.snackBar.open('Expiry date is required for options.', 'Dismiss', { duration: 3500 });
        return;
      }
      expiry = this.form.expiryDate;
    }
    const plannedAt = this.fromDatetimeLocal(this.plannedAtLocal);
    if (!plannedAt) {
      this.snackBar.open('Could not parse plan date/time.', 'Dismiss', { duration: 3500 });
      return;
    }
    const body: RobinhoodTradeInterestRequestDto = {
      instrumentKind: this.form.instrumentKind,
      symbol,
      plannedAt,
      underlyingPrice: underlying,
      contractTargetPrice: contract,
      expiryDate: expiry,
      note: this.form.note.trim() || null,
      status: this.form.status,
    };
    this.saving = true;
    const wasEdit = this.editingId != null;
    const req$ = wasEdit ? this.api.update(this.editingId!, body) : this.api.create(body);
    req$.subscribe({
      next: () => {
        this.saving = false;
        this.cancelEdit();
        this.refresh();
        this.snackBar.open(wasEdit ? 'Updated.' : 'Saved.', 'Dismiss', { duration: 2500 });
      },
      error: (err) => {
        this.saving = false;
        this.toastError(err);
      },
    });
  }

  remove(row: RobinhoodTradeInterestDto): void {
    if (!confirm(`Delete interest in ${row.symbol}?`)) {
      return;
    }
    this.api.delete(row.id).subscribe({
      next: () => {
        if (this.editingId === row.id) {
          this.cancelEdit();
        }
        this.refresh();
      },
      error: (err) => this.toastError(err),
    });
  }

  markStatus(row: RobinhoodTradeInterestDto, status: 'TAKEN' | 'PASSED' | 'OPEN'): void {
    const body: RobinhoodTradeInterestRequestDto = {
      instrumentKind: row.instrumentKind === 'OPTION' ? 'OPTION' : 'STOCK',
      symbol: row.symbol,
      plannedAt: row.plannedAt,
      underlyingPrice: row.underlyingPrice,
      contractTargetPrice: row.contractTargetPrice,
      expiryDate: row.expiryDate,
      note: row.note,
      status,
    };
    this.api.update(row.id, body).subscribe({
      next: () => this.refresh(),
      error: (err) => this.toastError(err),
    });
  }

  private resetForm(): void {
    this.form = this.emptyForm();
    this.plannedAtLocal = this.toDatetimeLocal(new Date().toISOString());
  }

  private emptyForm() {
    return {
      instrumentKind: 'STOCK' as const,
      symbol: '',
      underlyingPrice: null as number | null,
      contractTargetPrice: null as number | null,
      expiryDate: '',
      note: '',
      status: 'OPEN' as const,
    };
  }

  /** ISO instant → `YYYY-MM-DDTHH:mm` for datetime-local. */
  private toDatetimeLocal(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return '';
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  /** Local datetime-local value → ISO instant. */
  private fromDatetimeLocal(local: string): string | null {
    const d = new Date(local);
    if (Number.isNaN(d.getTime())) {
      return null;
    }
    return d.toISOString();
  }

  private toastError(err: unknown): void {
    this.snackBar.open(formatHttpErrorDetail(err) || 'Request failed', 'Dismiss', { duration: 5000 });
  }
}
