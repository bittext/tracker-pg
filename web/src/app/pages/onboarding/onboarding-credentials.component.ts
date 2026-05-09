import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../services/auth.service';
import { MeMemberApiService } from '../../services/me-member-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

@Component({
  selector: 'app-onboarding-credentials',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatButtonModule,
    MatSnackBarModule,
  ],
  templateUrl: './onboarding-credentials.component.html',
  styleUrl: './onboarding-credentials.component.scss',
})
export class OnboardingCredentialsComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly api = inject(MeMemberApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly form = this.fb.nonNullable.group({
    currentPassword: ['', Validators.required],
    newUsername: [''],
    newPassword: [''],
    confirmPassword: [''],
  });

  submitting = false;
  skipBusy = false;
  maskCurrentPassword = true;
  maskNewPassword = true;
  maskConfirmPassword = true;

  skip(): void {
    if (this.skipBusy || this.submitting) {
      return;
    }
    this.skipBusy = true;
    this.api.skipCredentialsStep().subscribe({
      next: () => {
        this.skipBusy = false;
        this.router.navigate(['/admin'], { queryParams: { onboardingProfile: '1' } });
      },
      error: (e) => {
        this.skipBusy = false;
        this.snackBar.open(`Could not continue: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 9000 });
      },
    });
  }

  submit(): void {
    if (this.form.invalid || this.submitting || this.skipBusy) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newUsername, newPassword, confirmPassword } = this.form.getRawValue();
    const nu = newUsername.trim();
    const np = newPassword;
    if (!nu && !np) {
      this.snackBar.open('Choose a new username and/or a new password to continue.', undefined, { duration: 5000 });
      return;
    }
    if (np && np !== confirmPassword) {
      this.snackBar.open('New password and confirmation do not match.', undefined, { duration: 5000 });
      return;
    }
    this.submitting = true;
    this.api.updateCredentials(currentPassword, nu || null, np || null).subscribe({
      next: (tok) => {
        this.auth.applyToken(tok);
        this.submitting = false;
        this.form.patchValue({
          currentPassword: '',
          newPassword: '',
          confirmPassword: '',
        });
        this.snackBar.open('Sign-in updated. Next, add your member profile in Admin.', undefined, { duration: 4500 });
        this.router.navigate(['/admin'], { queryParams: { onboardingProfile: '1' } });
      },
      error: (e) => {
        this.submitting = false;
        this.snackBar.open(`Could not update: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 9000 });
      },
    });
  }
}
