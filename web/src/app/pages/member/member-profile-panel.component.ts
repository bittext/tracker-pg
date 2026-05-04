import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatOption, MatSelectModule } from '@angular/material/select';
import { MatRadioModule } from '@angular/material/radio';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MeMemberProfileResponseDto, MemberGender, UsPostalValidationResponseDto } from '../../models/member.models';
import { AuthService } from '../../services/auth.service';
import { MeMemberApiService } from '../../services/me-member-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

@Component({
  selector: 'app-member-profile-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOption,
    MatCheckboxModule,
    MatRadioModule,
    MatIconModule,
    MatSnackBarModule,
  ],
  templateUrl: './member-profile-panel.component.html',
  styleUrl: './member-profile-panel.component.scss',
})
export class MemberProfilePanelComponent implements OnInit {
  private readonly api = inject(MeMemberApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  /** When true, show change-password (account setup finished). */
  onboardingComplete = false;

  /** Set from API when email / phone are stored on auth_users and must not be edited here. */
  contactEmailLockedFromAuth = false;
  contactPhoneLockedFromAuth = false;
  /** When phone is locked, full E.164 from the server for read-only display. */
  accountPhoneE164: string | null = null;

  saving = false;
  validatingZip = false;
  zipLookup: UsPostalValidationResponseDto | null = null;
  addressChoice: 'mine' | 'validated' = 'mine';

  form = {
    firstName: '',
    middleName: '',
    lastName: '',
    nickname: '',
    dateOfBirth: '',
    gender: '' as MemberGender | '',
    email: '',
    phoneCountryCode: '+1',
    phoneNationalNumber: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    stateRegion: '',
    postalCode: '',
    marketingEmailOptIn: false,
    marketingSmsOptIn: false,
  };

  pwdCurrent = '';
  pwdNew = '';
  pwdConfirm = '';
  pwdMaskCurrent = true;
  pwdMaskNew = true;
  pwdMaskConfirm = true;
  pwdSaving = false;

  ngOnInit(): void {
    forkJoin({
      profile: this.api.getMemberProfile(),
      status: this.api.getOnboardingStatus(),
    }).subscribe({
      next: ({ profile, status }) => {
        this.patchFromDto(profile);
        this.onboardingComplete = status.onboardingCompleted;
      },
      error: (e) => this.err('Could not load profile', e),
    });
  }

  changePassword(): void {
    if (!this.pwdCurrent || !this.pwdNew) {
      this.snackBar.open('Enter your current password and a new password.', undefined, { duration: 4000 });
      return;
    }
    if (this.pwdNew.length < 8) {
      this.snackBar.open('New password must be at least 8 characters.', undefined, { duration: 4000 });
      return;
    }
    if (this.pwdNew !== this.pwdConfirm) {
      this.snackBar.open('New password and confirmation do not match.', undefined, { duration: 4500 });
      return;
    }
    this.pwdSaving = true;
    this.api.changePassword(this.pwdCurrent, this.pwdNew).subscribe({
      next: (tok) => {
        this.auth.applyToken(tok);
        this.pwdSaving = false;
        this.pwdCurrent = '';
        this.pwdNew = '';
        this.pwdConfirm = '';
        this.snackBar.open('Password updated. You remain signed in with a new session token.', undefined, { duration: 5000 });
      },
      error: (e) => {
        this.pwdSaving = false;
        this.err('Could not change password', e);
      },
    });
  }

  validateZip(): void {
    const z = this.form.postalCode.trim();
    if (!z) {
      this.snackBar.open('Enter a ZIP code first.', undefined, { duration: 3500 });
      return;
    }
    this.validatingZip = true;
    this.zipLookup = null;
    this.api.validateUsPostal(z).subscribe({
      next: (r) => {
        this.validatingZip = false;
        this.zipLookup = r;
        if (r.places?.length) {
          this.addressChoice = 'validated';
        } else {
          this.addressChoice = 'mine';
        }
      },
      error: (e) => {
        this.validatingZip = false;
        this.err('ZIP lookup failed', e);
      },
    });
  }

  save(): void {
    if (!this.form.firstName.trim() || !this.form.lastName.trim() || !this.form.dateOfBirth) {
      this.snackBar.open('First name, last name, and date of birth are required.', undefined, { duration: 4000 });
      return;
    }
    if (!this.contactEmailLockedFromAuth && !this.form.email.trim()) {
      this.snackBar.open('Email is required.', undefined, { duration: 3500 });
      return;
    }
    if (!this.contactPhoneLockedFromAuth && !this.form.phoneNationalNumber.replace(/\D/g, '')) {
      this.snackBar.open('Phone number is required.', undefined, { duration: 3500 });
      return;
    }
    if (!this.form.addressLine1.trim() || !this.form.city.trim() || !this.form.stateRegion.trim() || !this.form.postalCode.trim()) {
      this.snackBar.open('Complete address fields (line 1, city, state, ZIP).', undefined, { duration: 4500 });
      return;
    }
    const nick = this.form.nickname.trim();
    if (nick.length > 80) {
      this.snackBar.open('Nickname must be at most 80 characters.', undefined, { duration: 4000 });
      return;
    }
    this.saving = true;
    const useValidated = this.addressChoice === 'validated' && !!this.zipLookup?.places?.length;
    this.api
      .saveMemberProfile({
        firstName: this.form.firstName.trim(),
        middleName: this.form.middleName.trim() || null,
        lastName: this.form.lastName.trim(),
        nickname: nick || null,
        dateOfBirth: this.form.dateOfBirth,
        gender: this.form.gender === '' ? null : (this.form.gender as MemberGender),
        email: this.form.email.trim(),
        phoneCountryCode: this.form.phoneCountryCode.trim(),
        phoneNationalNumber: this.form.phoneNationalNumber.trim(),
        addressLine1: this.form.addressLine1.trim(),
        addressLine2: this.form.addressLine2.trim() || null,
        city: this.form.city.trim(),
        stateRegion: this.form.stateRegion.trim(),
        postalCode: this.form.postalCode.trim(),
        addressUseValidatedSuggestion: useValidated,
        marketingEmailOptIn: this.form.marketingEmailOptIn,
        marketingSmsOptIn: this.form.marketingSmsOptIn,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.snackBar.open('Profile saved', undefined, { duration: 2500 });
          this.api.getOnboardingStatus().subscribe({
            next: (s) => {
              if (!s.onboardingCompleted && s.profileSubmitted) {
                this.router.navigate(['/onboarding/member-id']);
              }
            },
            error: () => {},
          });
        },
        error: (e) => {
          this.saving = false;
          this.err('Could not save profile', e);
        },
      });
  }

  private patchFromDto(p: MeMemberProfileResponseDto): void {
    this.form.firstName = p.firstName ?? '';
    this.form.middleName = p.middleName ?? '';
    this.form.lastName = p.lastName ?? '';
    this.form.nickname = p.nickname ?? '';
    this.form.dateOfBirth = p.dateOfBirth ?? '';
    this.form.gender = (p.gender as MemberGender | null | undefined) ?? '';
    this.form.email = p.email ?? '';
    this.contactEmailLockedFromAuth = p.contactEmailLockedFromAuth ?? false;
    this.contactPhoneLockedFromAuth = p.contactPhoneLockedFromAuth ?? false;
    this.accountPhoneE164 = p.accountPhoneE164 ?? null;
    if (this.contactPhoneLockedFromAuth) {
      this.form.phoneCountryCode = '+1';
      this.form.phoneNationalNumber = '';
    } else {
      this.form.phoneCountryCode = p.phoneCountryCode ?? '+1';
      this.form.phoneNationalNumber = p.phoneNationalNumber ?? '';
    }
    this.form.addressLine1 = p.addressLine1 ?? '';
    this.form.addressLine2 = p.addressLine2 ?? '';
    this.form.city = p.city ?? '';
    this.form.stateRegion = p.stateRegion ?? '';
    this.form.postalCode = p.postalCode ?? '';
    this.form.marketingEmailOptIn = p.marketingEmailOptIn;
    this.form.marketingSmsOptIn = p.marketingSmsOptIn;
    this.addressChoice = p.addressUseValidatedSuggestion ? 'validated' : 'mine';
    if (p.validatedPostalCode || p.validatedCity) {
      this.zipLookup = {
        postalCode: p.validatedPostalCode ?? '',
        places:
          p.validatedCity && p.validatedStateRegion
            ? [{ placeName: p.validatedCity, stateAbbreviation: p.validatedStateRegion, stateName: '' }]
            : [],
        source: 'saved',
        message: null,
      };
    }
  }

  private err(msg: string, e: unknown): void {
    this.snackBar.open(`${msg}: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 9000 });
  }
}
