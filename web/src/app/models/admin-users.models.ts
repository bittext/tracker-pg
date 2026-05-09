import type { MeMemberProfileResponseDto } from './member.models';

export type AdminProvisionRole = 'USER' | 'ADMIN';

/** GET /api/admin/member-profiles */
export interface AdminMemberProfileListItemDto {
  userId: number;
  username: string;
  memberPublicId: number;
  displayName: string;
  onboardingCompleted: boolean;
}

/** GET /api/admin/member-profiles/{userId} */
export interface AdminMemberProfileDetailDto {
  userId: number;
  username: string;
  role: string;
  onboardingCompleted: boolean;
  profile: MeMemberProfileResponseDto;
}

export interface AdminCreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: AdminProvisionRole;
  mfaEnabled: boolean;
  active: boolean;
}

export interface AdminCreatedUserResponse {
  id: number;
  username: string;
  email: string;
  role: string;
}
