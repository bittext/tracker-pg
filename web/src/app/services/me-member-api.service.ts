import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AuthTokenDto } from './auth.service';
import {
  MeMemberProfileResponseDto,
  MemberGender,
  MeOnboardingStatusDto,
  UsPostalValidationResponseDto,
} from '../models/member.models';

export interface MeMemberProfileSaveRequest {
  firstName: string;
  middleName?: string | null;
  lastName: string;
  nickname?: string | null;
  dateOfBirth: string;
  gender?: MemberGender | null;
  email: string;
  phoneCountryCode: string;
  phoneNationalNumber: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  stateRegion: string;
  postalCode: string;
  addressUseValidatedSuggestion: boolean;
  marketingEmailOptIn: boolean;
  marketingSmsOptIn: boolean;
}

@Injectable({ providedIn: 'root' })
export class MeMemberApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/me`;

  getOnboardingStatus() {
    return this.http.get<MeOnboardingStatusDto>(`${this.root}/onboarding-status`);
  }

  updateCredentials(currentPassword: string, newUsername: string | null, newPassword: string | null) {
    return this.http.post<AuthTokenDto>(`${this.root}/onboarding/credentials`, {
      currentPassword,
      newUsername: newUsername?.trim() || null,
      newPassword: newPassword || null,
    });
  }

  completeOnboarding() {
    return this.http.post<void>(`${this.root}/onboarding/complete`, {});
  }

  getMemberProfile() {
    return this.http.get<MeMemberProfileResponseDto>(`${this.root}/member-profile`);
  }

  /** Records acknowledgment of Privacy policy (financial data & Plaid) before connecting via Plaid Link. */
  acceptPlaidFinancialDataNotice() {
    return this.http.post<MeMemberProfileResponseDto>(`${this.root}/privacy/plaid-financial-data-notice`, {});
  }

  saveMemberProfile(body: MeMemberProfileSaveRequest) {
    return this.http.put<MeMemberProfileResponseDto>(`${this.root}/member-profile`, body);
  }

  changePassword(currentPassword: string, newPassword: string) {
    return this.http.post<AuthTokenDto>(`${this.root}/password`, {
      currentPassword,
      newPassword,
    });
  }

  submitContactFeedback(body: { displayName?: string | null; subject: string; details: string }) {
    return this.http.post<void>(`${this.root}/contact-feedback`, body);
  }

  validateUsPostal(postalCode: string) {
    return this.http.get<UsPostalValidationResponseDto>(`${this.root}/address/validate-us-postal`, {
      params: { postalCode },
    });
  }
}
