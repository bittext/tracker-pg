import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PageHeaderComponent } from '../../../components/page-header/page-header.component';
import { RobinhoodQuarterDto, RobinhoodQuarterlyStatementsDto } from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';
import {
  StatementLine,
  buildStatements,
  isEmptyStatement,
  pickStatement,
  statementsToCsv,
  statementsToPrintHtml,
} from '../../../util/quarterly-statements';

/** Quarterly income, profit and loss, and cash flow statements for the investment accounts. */
@Component({
  selector: 'app-markets-statements',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    PageHeaderComponent,
  ],
  templateUrl: './markets-statements.component.html',
  styleUrl: './markets-statements.component.scss',
})
export class MarketsStatementsComponent implements OnInit {
  private readonly api = inject(FinanceApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snack = inject(MatSnackBar);

  readonly year = signal(new Date().getFullYear());
  readonly account = signal('ALL');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly data = signal<RobinhoodQuarterlyStatementsDto | null>(null);

  readonly yearChoices = (() => {
    const current = new Date().getFullYear();
    const years: number[] = [];
    for (let y = current; y >= 2024; y--) {
      years.push(y);
    }
    return years;
  })();

  /** Newest quarter first. */
  readonly quarters = computed(() => [...(this.data()?.quarters ?? [])].reverse());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .robinhoodQuarterlyStatements(this.year())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (d) => {
          this.data.set(d);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(formatHttpErrorDetail(e));
          this.loading.set(false);
        },
      });
  }

  onYearChange(): void {
    this.data.set(null);
    this.load();
  }

  statement(q: RobinhoodQuarterDto) {
    return pickStatement(q, this.account());
  }

  sections(q: RobinhoodQuarterDto) {
    const s = this.statement(q);
    return s && !isEmptyStatement(s) ? buildStatements(s) : [];
  }

  isGain(l: StatementLine): boolean {
    return l.kind !== 'heading' && l.value != null && l.value > 0.004 && l.label !== 'Opening value' && !l.label.startsWith('Closing') && !l.label.startsWith('Opening');
  }

  isLoss(l: StatementLine): boolean {
    return l.kind !== 'heading' && l.value != null && l.value < -0.004;
  }

  downloadCsv(q: RobinhoodQuarterDto): void {
    const s = this.statement(q);
    if (!s) {
      return;
    }
    const blob = new Blob([statementsToCsv(q, s)], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `statements-${q.key}-${s.suffix === 'ALL' ? 'all-accounts' : s.suffix}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  printPdf(q: RobinhoodQuarterDto): void {
    const s = this.statement(q);
    if (!s) {
      return;
    }
    const w = window.open('', '_blank');
    if (!w) {
      this.snack.open('Allow pop-ups to print or save as PDF.', 'OK', { duration: 5000 });
      return;
    }
    w.document.open();
    w.document.write(statementsToPrintHtml(q, s, this.data()?.notes ?? []));
    w.document.close();
  }
}
