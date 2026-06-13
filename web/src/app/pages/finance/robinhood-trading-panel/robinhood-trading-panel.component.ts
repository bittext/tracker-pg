import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodAccountStatusDto,
  RobinhoodAgenticOrderDto,
  RobinhoodAgenticPositionDto,
  RobinhoodAgenticSettingsDto,
  RobinhoodAgenticStatusDto,
  RobinhoodCsvImportResultDto,
  RobinhoodCsvSavedImportDto,
} from '../../../models/finance.models';
import { FinanceApiService } from '../../../services/finance-api.service';
import { formatHttpErrorDetail } from '../../../util/http-error';

@Component({
  selector: 'app-robinhood-trading-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './robinhood-trading-panel.component.html',
  styleUrl: './robinhood-trading-panel.component.scss',
})
export class RobinhoodTradingPanelComponent implements OnInit {
  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  status: RobinhoodAccountStatusDto | null = null;
  statusLoading = false;

  agenticStatus: RobinhoodAgenticStatusDto | null = null;
  agenticSettings: RobinhoodAgenticSettingsDto | null = null;
  agenticOrders: RobinhoodAgenticOrderDto[] = [];
  agenticPositions: RobinhoodAgenticPositionDto[] = [];
  agenticLoading = false;
  agenticSyncing = false;
  agenticTokenJson = '';
  agenticSavingTokens = false;
  agenticSavingSettings = false;
  agenticReviewingOrder = false;

  orderSymbol = '';
  orderSide: 'buy' | 'sell' = 'buy';
  orderType: 'market' | 'limit' = 'market';
  orderQuantity: number | null = null;
  orderLimitPrice: number | null = null;
  settingsRequireApproval = true;
  settingsMaxNotional: number | null = null;
  settingsAllowedSymbols = '';

  csvApplyToDb = false;
  csvSelectedFile: File | null = null;
  csvSelectedLabel: string | null = null;
  csvUploading = false;
  csvDirectResult: RobinhoodCsvImportResultDto | null = null;
  csvDirectoryResult: RobinhoodCsvSavedImportDto | null = null;
  directoryImportConfigured = false;

  ngOnInit(): void {
    this.refreshStatus();
    this.refreshAgentic();
    this.financeApi.robinhoodCsvImportUploadStatus().subscribe({
      next: (s) => {
        this.directoryImportConfigured = s.configured;
      },
      error: () => {
        this.directoryImportConfigured = false;
      },
    });
  }

  refreshStatus(): void {
    this.statusLoading = true;
    this.financeApi.robinhoodAccountStatus().subscribe({
      next: (s) => {
        this.status = s;
        this.statusLoading = false;
      },
      error: (e) => {
        this.status = null;
        this.statusLoading = false;
        this.snackBar.open(`Could not load Robinhood status — ${formatHttpErrorDetail(e)}`, undefined, {
          duration: 6000,
        });
      },
    });
  }

  refreshAgentic(): void {
    this.agenticLoading = true;
    this.financeApi.robinhoodAgenticStatus().subscribe({
      next: (s) => {
        this.agenticStatus = s;
        this.agenticLoading = false;
        if (s.connected) {
          this.loadAgenticPositions();
          this.loadAgenticSettings();
          this.loadAgenticOrders();
        } else {
          this.agenticPositions = [];
          this.agenticSettings = null;
          this.agenticOrders = [];
        }
      },
      error: () => {
        this.agenticStatus = null;
        this.agenticLoading = false;
        this.agenticPositions = [];
        this.agenticSettings = null;
        this.agenticOrders = [];
      },
    });
  }

  loadAgenticPositions(): void {
    this.financeApi.robinhoodAgenticPositions().subscribe({
      next: (p) => {
        this.agenticPositions = p.positions;
      },
      error: () => {
        this.agenticPositions = [];
      },
    });
  }

  loadAgenticSettings(): void {
    this.financeApi.robinhoodAgenticSettings().subscribe({
      next: (s) => {
        this.agenticSettings = s;
        this.settingsRequireApproval = s.requireApproval;
        this.settingsMaxNotional = s.maxOrderNotional;
        this.settingsAllowedSymbols = s.allowedSymbols ?? '';
      },
      error: () => {
        this.agenticSettings = null;
      },
    });
  }

  loadAgenticOrders(): void {
    this.financeApi.robinhoodAgenticOrders().subscribe({
      next: (o) => {
        this.agenticOrders = o.orders;
      },
      error: () => {
        this.agenticOrders = [];
      },
    });
  }

  saveAgenticSettings(): void {
    this.agenticSavingSettings = true;
    this.financeApi
      .robinhoodAgenticSaveSettings({
        requireApproval: this.settingsRequireApproval,
        maxOrderNotional: this.settingsMaxNotional,
        allowedSymbols: this.settingsAllowedSymbols,
      })
      .subscribe({
        next: (s) => {
          this.agenticSavingSettings = false;
          this.agenticSettings = s;
          this.snackBar.open('Agentic guardrails saved', undefined, { duration: 4500 });
        },
        error: (e) => {
          this.agenticSavingSettings = false;
          this.snackBar.open(`Save settings failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }

  reviewAgenticOrder(): void {
    const symbol = this.orderSymbol.trim().toUpperCase();
    if (!symbol) {
      this.snackBar.open('Enter a symbol', undefined, { duration: 4500 });
      return;
    }
    if (this.orderQuantity == null || this.orderQuantity <= 0) {
      this.snackBar.open('Enter a positive quantity', undefined, { duration: 4500 });
      return;
    }
    if (this.orderType === 'limit' && (this.orderLimitPrice == null || this.orderLimitPrice <= 0)) {
      this.snackBar.open('Enter a limit price', undefined, { duration: 4500 });
      return;
    }
    this.agenticReviewingOrder = true;
    this.financeApi
      .robinhoodAgenticReviewOrder({
        symbol,
        side: this.orderSide,
        type: this.orderType,
        quantity: this.orderQuantity,
        limitPrice: this.orderType === 'limit' ? this.orderLimitPrice : null,
      })
      .subscribe({
        next: (o) => {
          this.agenticReviewingOrder = false;
          const msg =
            o.status === 'placed'
              ? `Order placed (${o.symbol} ${o.side})`
              : `Order reviewed — status: ${o.status}`;
          this.snackBar.open(msg, undefined, { duration: 6000 });
          this.loadAgenticOrders();
          if (o.status === 'placed') {
            this.syncAgentic();
          }
        },
        error: (e) => {
          this.agenticReviewingOrder = false;
          this.snackBar.open(`Order review failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }

  approveAgenticOrder(order: RobinhoodAgenticOrderDto): void {
    this.financeApi.robinhoodAgenticApproveOrder(order.id).subscribe({
      next: () => {
        this.snackBar.open(`Order approved and placed (${order.symbol})`, undefined, { duration: 5000 });
        this.loadAgenticOrders();
        this.syncAgentic();
      },
      error: (e) => {
        this.snackBar.open(`Approve failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        this.loadAgenticOrders();
      },
    });
  }

  rejectAgenticOrder(order: RobinhoodAgenticOrderDto): void {
    this.financeApi.robinhoodAgenticRejectOrder(order.id).subscribe({
      next: () => {
        this.snackBar.open('Order rejected', undefined, { duration: 4500 });
        this.loadAgenticOrders();
      },
      error: (e) => {
        this.snackBar.open(`Reject failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  saveAgenticTokens(): void {
    const raw = this.agenticTokenJson.trim();
    if (!raw) {
      this.snackBar.open('Paste .tokens.json contents or access_token', undefined, { duration: 4500 });
      return;
    }
    let accessToken = raw;
    let refreshToken = '';
    try {
      const parsed = JSON.parse(raw) as { access_token?: string; refresh_token?: string };
      if (parsed.access_token) {
        accessToken = parsed.access_token;
        refreshToken = parsed.refresh_token ?? '';
      }
    } catch {
      // treat as bare access token
    }
    this.agenticSavingTokens = true;
    this.financeApi.robinhoodAgenticSaveTokens(accessToken, refreshToken).subscribe({
      next: () => {
        this.agenticSavingTokens = false;
        this.agenticTokenJson = '';
        this.snackBar.open('Robinhood Agentic tokens saved', undefined, { duration: 4500 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.agenticSavingTokens = false;
        this.snackBar.open(`Save tokens failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  syncAgentic(): void {
    this.agenticSyncing = true;
    this.financeApi.robinhoodAgenticSync().subscribe({
      next: (r) => {
        this.agenticSyncing = false;
        this.snackBar.open(r.message || 'Sync complete', undefined, { duration: 5000 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.agenticSyncing = false;
        this.snackBar.open(`Sync failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  disconnectAgentic(): void {
    this.financeApi.robinhoodAgenticDisconnect().subscribe({
      next: () => {
        this.snackBar.open('Robinhood Agentic disconnected', undefined, { duration: 4500 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.snackBar.open(`Disconnect failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  onCsvSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files?.[0];
    this.csvSelectedFile = f ?? null;
    this.csvSelectedLabel = f?.name ?? null;
    this.csvDirectResult = null;
    this.csvDirectoryResult = null;
  }

  uploadCsvDirect(): void {
    const f = this.csvSelectedFile;
    if (!f) {
      this.snackBar.open('Choose a CSV file first', undefined, { duration: 4500 });
      return;
    }
    this.csvUploading = true;
    this.csvDirectResult = null;
    this.financeApi.robinhoodImportCsv(f, this.csvApplyToDb).subscribe({
      next: (r) => {
        this.csvDirectResult = r;
        this.csvUploading = false;
        this.snackBar.open(this.importResultMessage(r), undefined, { duration: 6500 });
        if (r.apply && r.errorCount === 0) {
          this.refreshStatus();
        }
      },
      error: (e) => {
        this.csvUploading = false;
        this.snackBar.open(`CSV import failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  uploadCsvToDirectory(): void {
    const f = this.csvSelectedFile;
    if (!f) {
      this.snackBar.open('Choose a CSV file first', undefined, { duration: 4500 });
      return;
    }
    this.csvUploading = true;
    this.csvDirectoryResult = null;
    this.financeApi.robinhoodCsvSaveToImportDirectory(f, this.csvApplyToDb).subscribe({
      next: (r) => {
        this.csvDirectoryResult = r;
        this.csvUploading = false;
        this.snackBar.open(this.importResultMessage(r.importResult), undefined, { duration: 6500 });
        if (r.importResult.apply && r.importResult.errorCount === 0) {
          this.refreshStatus();
        }
      },
      error: (e) => {
        this.csvUploading = false;
        this.snackBar.open(`CSV save/import failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  private importResultMessage(r: RobinhoodCsvImportResultDto): string {
    if (r.apply && r.errorCount === 0) {
      return `Imported ${r.insertedRows} row(s)`;
    }
    if (r.apply) {
      return `Import finished with ${r.errorCount} error(s)`;
    }
    return `Dry-run: parsed ${r.parsedRows} row(s)`;
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
  }

  positionTypeLabel(p: RobinhoodAgenticPositionDto): string {
    return p.positionType === 'option' ? 'Option' : 'Equity';
  }

  optionContractLabel(p: RobinhoodAgenticPositionDto): string {
    if (p.positionType !== 'option') {
      return '—';
    }
    const type = p.optionType ? p.optionType.toUpperCase() : '?';
    const strike = p.strikePrice ?? '—';
    const exp = p.expirationDate ? p.expirationDate.slice(0, 10) : '—';
    return `${type} ${strike} · ${exp}`;
  }

  orderStatusLabel(status: string): string {
    return status.replace(/_/g, ' ');
  }

  isPendingOrder(o: RobinhoodAgenticOrderDto): boolean {
    return o.status === 'pending_approval';
  }
}
