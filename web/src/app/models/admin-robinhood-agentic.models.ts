import {
  RobinhoodAgenticAutoTradeRunDto,
  RobinhoodAgenticOrderDto,
  RobinhoodAgenticSettingsDto,
} from './finance.models';

export interface AdminRobinhoodAgenticConfigDto {
  featureEnabled: boolean;
  serviceConfigured: boolean;
  executionEnabled: boolean;
  autoTradeServerEnabled: boolean;
  serviceBaseUrl: string;
  syncCronEnabled: boolean;
  syncCron: string;
  serverDefaultMaxOrderNotional: number | null;
  autoTradePollMs: number;
}

export interface AdminRobinhoodAgenticDefaultsDto {
  requireApproval: boolean;
  maxOrderNotional: number | null;
  allowedSymbols: string;
  autoTradeEnabled: boolean;
  autoTradeKillSwitch: boolean;
  autoTradeRequireApproval: boolean;
  autoTradeMinPositivityBuy: number;
  autoTradeMaxPositivitySell: number;
  autoTradeMinSpikeZ: number;
  autoTradeMinMentions24h: number;
  autoTradeOrderQuantity: number;
  autoTradeMaxTradesPerDay: number;
  autoTradeMaxDailyNotional: number | null;
  autoTradeCooldownMinutes: number;
  autoTradeMarketHoursOnly: boolean;
  approvalAlertEmailEnabled: boolean;
  approvalAlertSmsEnabled: boolean;
  updatedAt: string;
}

export interface AdminRobinhoodAgenticStatsDto {
  connectedUsers: number;
  pendingApprovals: number;
  ordersLast24h: number;
  autoTradeRunsLast24h: number;
  autoTradeEnabledUsers: number;
  notificationsLast24h: number;
}

export interface AdminRobinhoodAgenticOrderRowDto {
  order: RobinhoodAgenticOrderDto;
  ownerUserId: number;
  ownerUsername: string;
  ownerEmail: string;
}

export interface AdminRobinhoodAgenticApprovalNotificationDto {
  id: number;
  ownerUserId: number;
  orderId: number;
  channel: string;
  status: string;
  destinationMasked: string;
  detail: string;
  createdAt: string;
}

export interface AdminRobinhoodAgenticTrackerDto {
  pendingOrders: AdminRobinhoodAgenticOrderRowDto[];
  recentOrders: RobinhoodAgenticOrderDto[];
  recentRuns: RobinhoodAgenticAutoTradeRunDto[];
  recentNotifications: AdminRobinhoodAgenticApprovalNotificationDto[];
}

export interface AdminRobinhoodAgenticActionResultDto {
  ok: boolean;
  message: string;
  evaluateResult?: {
    ran: boolean;
    message: string;
    tickersEvaluated: number;
    signalsGenerated: number;
    ordersReviewed: number;
    ordersPlaced: number;
  };
}

export type AdminRobinhoodAgenticDefaultsRequest = Partial<AdminRobinhoodAgenticDefaultsDto>;
