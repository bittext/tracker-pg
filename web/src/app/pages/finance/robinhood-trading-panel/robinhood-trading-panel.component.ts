import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  RobinhoodAccountStatusDto,
  RobinhoodAccountTrackerDto,
  RobinhoodAgenticPositionDto,
  RobinhoodAgenticSettingsDto,
  RobinhoodAgenticSyncedOrderDto,
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
  private static readonly LIVE_POSITIONS_LIMIT = 50;
  private static readonly EXCLUDED_ACCOUNT_SUFFIX = '4123';

  private readonly financeApi = inject(FinanceApiService);
  private readonly snackBar = inject(MatSnackBar);

  status: RobinhoodAccountStatusDto | null = null;
  statusLoading = false;

  accountTracker: RobinhoodAccountTrackerDto | null = null;
  accountTrackerLoading = false;

  agenticStatus: RobinhoodAgenticStatusDto | null = null;
  agenticSettings: RobinhoodAgenticSettingsDto | null = null;
  agenticSyncedOrders: RobinhoodAgenticSyncedOrderDto[] = [];
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
  autoTradeEnabled = false;
  autoTradeKillSwitch = false;
  autoTradeRequireApproval = true;
  autoTradeMinPositivityBuy = 15;
  autoTradeMaxPositivitySell = -15;
  autoTradeMinSpikeZ = 1.5;
  autoTradeMinMentions24h = 5;
  autoTradeOrderQuantity = 1;
  autoTradeMaxTradesPerDay = 3;
  autoTradeMaxDailyNotional: number | null = null;
  autoTradeCooldownMinutes = 60;
  autoTradeMarketHoursOnly = true;
  autoTradeEvaluating = false;
  autoTradeLastMessage = '';

  csvApplyToDb = false;
  csvSelectedFile: File | null = null;
  csvSelectedLabel: string | null = null;
  csvUploading = false;
  csvDirectResult: RobinhoodCsvImportResultDto | null = null;
  csvDirectoryResult: RobinhoodCsvSavedImportDto | null = null;
  directoryImportConfigured = false;

  ngOnInit(): void {
    this.refreshStatus();
    this.refreshAccountTracker();
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

  refreshAccountTracker(): void {
    this.accountTrackerLoading = true;
    this.financeApi.robinhoodAccountTracker().subscribe({
      next: (t) => {
        this.accountTracker = t;
        this.accountTrackerLoading = false;
      },
      error: (e) => {
        this.accountTracker = null;
        this.accountTrackerLoading = false;
        this.snackBar.open(`Could not load account tracker — ${formatHttpErrorDetail(e)}`, undefined, {
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
          this.loadAgenticSyncedOrders();
          this.loadAgenticSettings();
        } else {
          this.agenticPositions = [];
          this.agenticSyncedOrders = [];
          this.agenticSettings = null;
        }
      },
      error: () => {
        this.agenticStatus = null;
        this.agenticLoading = false;
        this.agenticPositions = [];
        this.agenticSyncedOrders = [];
        this.agenticSettings = null;
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

  loadAgenticSyncedOrders(): void {
    this.financeApi.robinhoodAgenticSyncedOrders().subscribe({
      next: (o) => {
        this.agenticSyncedOrders = o.orders;
      },
      error: () => {
        this.agenticSyncedOrders = [];
      },
    });
  }

  loadAgenticSettings(): void {
    this.financeApi.robinhoodAgenticSettings().subscribe({
      next: (s) => {
        this.agenticSettings = s;
        this.applySettingsFromDto(s);
      },
      error: () => {
        this.agenticSettings = null;
      },
    });
  }

  private applySettingsFromDto(s: RobinhoodAgenticSettingsDto): void {
    this.settingsRequireApproval = s.requireApproval;
    this.settingsMaxNotional = s.maxOrderNotional;
    this.settingsAllowedSymbols = s.allowedSymbols ?? '';
    this.autoTradeEnabled = s.autoTradeEnabled;
    this.autoTradeKillSwitch = s.autoTradeKillSwitch;
    this.autoTradeRequireApproval = s.autoTradeRequireApproval;
    this.autoTradeMinPositivityBuy = s.autoTradeMinPositivityBuy;
    this.autoTradeMaxPositivitySell = s.autoTradeMaxPositivitySell;
    this.autoTradeMinSpikeZ = s.autoTradeMinSpikeZ;
    this.autoTradeMinMentions24h = s.autoTradeMinMentions24h;
    this.autoTradeOrderQuantity = s.autoTradeOrderQuantity;
    this.autoTradeMaxTradesPerDay = s.autoTradeMaxTradesPerDay;
    this.autoTradeMaxDailyNotional = s.autoTradeMaxDailyNotional;
    this.autoTradeCooldownMinutes = s.autoTradeCooldownMinutes;
    this.autoTradeMarketHoursOnly = s.autoTradeMarketHoursOnly;
    this.autoTradeLastMessage = s.autoTradeLastRunMessage ?? '';
  }

  saveAgenticSettings(): void {
    this.agenticSavingSettings = true;
    this.financeApi
      .robinhoodAgenticSaveSettings({
        requireApproval: this.settingsRequireApproval,
        maxOrderNotional: this.settingsMaxNotional,
        allowedSymbols: this.settingsAllowedSymbols,
        autoTradeEnabled: this.autoTradeEnabled,
        autoTradeKillSwitch: this.autoTradeKillSwitch,
        autoTradeRequireApproval: this.autoTradeRequireApproval,
        autoTradeMinPositivityBuy: this.autoTradeMinPositivityBuy,
        autoTradeMaxPositivitySell: this.autoTradeMaxPositivitySell,
        autoTradeMinSpikeZ: this.autoTradeMinSpikeZ,
        autoTradeMinMentions24h: this.autoTradeMinMentions24h,
        autoTradeOrderQuantity: this.autoTradeOrderQuantity,
        autoTradeMaxTradesPerDay: this.autoTradeMaxTradesPerDay,
        autoTradeMaxDailyNotional: this.autoTradeMaxDailyNotional,
        autoTradeCooldownMinutes: this.autoTradeCooldownMinutes,
        autoTradeMarketHoursOnly: this.autoTradeMarketHoursOnly,
      })
      .subscribe({
        next: (s) => {
          this.agenticSavingSettings = false;
          this.agenticSettings = s;
          this.applySettingsFromDto(s);
          this.snackBar.open('Agentic settings saved', undefined, { duration: 4500 });
        },
        error: (e) => {
          this.agenticSavingSettings = false;
          this.snackBar.open(`Save settings failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
        },
      });
  }

  evaluateAutoTradeNow(): void {
    this.autoTradeEvaluating = true;
    this.financeApi.robinhoodAgenticEvaluateAutoTrade().subscribe({
      next: (r) => {
        this.autoTradeEvaluating = false;
        this.autoTradeLastMessage = r.message;
        this.snackBar.open(r.message || 'Auto-trade evaluation complete', undefined, { duration: 7000 });
        this.refreshAgentic();
      },
      error: (e) => {
        this.autoTradeEvaluating = false;
        this.snackBar.open(`Auto-trade failed — ${formatHttpErrorDetail(e)}`, undefined, { duration: 8000 });
      },
    });
  }

  activateKillSwitch(): void {
    this.autoTradeKillSwitch = true;
    this.autoTradeEnabled = false;
    this.saveAgenticSettings();
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
        this.refreshAccountTracker();
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

  formatPct(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '—';
    }
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(2)}%`;
  }

  formatNbis(value: number | null | undefined): string {
    if (value == null || Number.isNaN(value)) {
      return '—';
    }
    return value.toLocaleString(undefined, { maximumFractionDigits: 4 });
  }

  varianceClass(variance: number | null | undefined): string {
    if (variance == null || variance === 0) {
      return '';
    }
    return variance > 0 ? 'rh-trading__var--pos' : 'rh-trading__var--neg';
  }

  positionTypeLabel(p: RobinhoodAgenticPositionDto): string {
    return p.positionType === 'option' ? 'Option' : 'Equity';
  }

  isOpenPosition(p: RobinhoodAgenticPositionDto): boolean {
    return p.quantity != null && p.quantity !== 0;
  }

  displayedLivePositions(): RobinhoodAgenticPositionDto[] {
    return [...this.agenticPositions]
      .filter((p) => this.isOpenPosition(p) && !this.isExcludedAccount(p))
      .sort((a, b) => Math.abs(b.marketValue ?? 0) - Math.abs(a.marketValue ?? 0))
      .slice(0, RobinhoodTradingPanelComponent.LIVE_POSITIONS_LIMIT);
  }

  private isExcludedAccount(p: RobinhoodAgenticPositionDto): boolean {
    const masked = p.accountNumberMasked ?? '';
    return masked.endsWith(RobinhoodTradingPanelComponent.EXCLUDED_ACCOUNT_SUFFIX);
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
}
