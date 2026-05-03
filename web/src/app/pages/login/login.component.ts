import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submitting = false;
  error: string | null = null;

  submit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.error = null;
    const { username, password } = this.form.getRawValue();
    this.auth.login(username.trim(), password).subscribe({
      next: () => {
        const redirect = this.route.snapshot.queryParamMap.get('redirect') || '/welcome';
        this.router.navigateByUrl(redirect);
      },
      error: (e: unknown) => {
        this.submitting = false;
        this.error = this.toMessage(e);
      },
      complete: () => {
        this.submitting = false;
      },
    });
  }

  private toMessage(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      if (e.status === 401) {
        return 'Invalid username or password.';
      }
      if (e.error && typeof e.error === 'object' && 'message' in e.error) {
        const m = (e.error as { message?: unknown }).message;
        if (typeof m === 'string' && m.length > 0) {
          return m;
        }
      }
      return e.message || 'Login failed.';
    }
    if (e instanceof Error) {
      return e.message;
    }
    return 'Login failed.';
  }
}
