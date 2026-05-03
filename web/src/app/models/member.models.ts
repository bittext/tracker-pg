/** Matches server {@code MemberGender}; omit or null = prefer not to say. */
export type MemberGender = 'FEMALE' | 'MALE' | 'NON_BINARY' | 'OTHER';

export interface MeOnboardingStatusDto {
  onboardingCompleted: boolean;
  credentialsStepCompleted: boolean;
  profileSubmitted: boolean;
  memberPublicId: number | null;
  username: string;
}

export interface MeMemberProfileResponseDto {
  firstName: string | null;
  middleName: string | null;
  lastName: string | null;
  nickname: string | null;
  dateOfBirth: string | null;
  gender: MemberGender | null;
  email: string | null;
  phoneCountryCode: string | null;
  phoneNationalNumber: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  validatedPostalCode: string | null;
  validatedCity: string | null;
  validatedStateRegion: string | null;
  addressUseValidatedSuggestion: boolean;
  marketingEmailOptIn: boolean;
  marketingSmsOptIn: boolean;
  memberPublicId: number | null;
}

export interface UsPostalPlaceDto {
  placeName: string;
  stateAbbreviation: string;
  stateName: string;
}

export interface UsPostalValidationResponseDto {
  postalCode: string;
  places: UsPostalPlaceDto[];
  source: string;
  message: string | null;
}
