/** Admin → Usage tab DTOs (mirror server `com.svp.tracker.admin.dto.usage.*`). */

export interface UsageSummaryDto {
  totalUsers: number;
  activeUsers: number;
  adminUsers: number;
  memberProfilesCount: number;
  activeUsers7d: number;
  activeUsers30d: number;
  signInsSuccess30d: number;
  signInsFailed30d: number;
  itemsCreated7d: number;
  itemsCreated30d: number;
  lastActivityAt: string | null;
}

export interface FeatureUsageDto {
  feature: string;
  totalCount: number;
  activeUsers: number;
  allTimeCount: number;
  lastActivityAt: string | null;
}

export interface DailyActivityPointDto {
  day: string;
  feature: string;
  count: number;
}

export interface SignInDailyPointDto {
  day: string;
  /** `LOGIN_SUCCESS` | `LOGIN_FAILED` | `MFA_REQUIRED` | `MFA_FAILED` | `LOGOUT` */
  eventType: string;
  count: number;
}

export interface MemberUsageDto {
  userId: number;
  username: string;
  displayName: string;
  role: string;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  signInsSuccess30d: number;
  totalItems: number;
  perFeature: Record<string, number>;
}
