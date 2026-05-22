import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, finalize, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { buildLoginLocationContext } from '../util/login-location';

export interface AuthTokenDto {
  token: string;
  expiresAt: string;
  username: string;
  role: string;
}

interface LoginResponseDto {
  mfaRequired: boolean;
  challengeId: string | null;
  message: string;
  token: AuthTokenDto | null;
}

const TOKEN_KEY = 'tracker.auth.token';
const USER_KEY = 'tracker.auth.user';
const ROLE_KEY = 'tracker.auth.role';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiBase = `${environment.apiBaseUrl}/api/auth`;
  private readonly authState$ = new BehaviorSubject<boolean>(this.hasStoredToken());

  readonly isAuthenticated$ = this.authState$.asObservable();

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get username(): string | null {
    return localStorage.getItem(USER_KEY);
  }

  get role(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  isAuthenticated(): boolean {
    return this.authState$.value;
  }

  /** True when login stored role is ADMIN (same value as server JWT / AuthTokenDto). */
  isAdmin(): boolean {
    const r = this.role;
    return r != null && r.trim().toUpperCase() === 'ADMIN';
  }

  login(username: string, password: string) {
    const location = buildLoginLocationContext();
    return this.http
      .post<LoginResponseDto>(`${this.apiBase}/login`, {
        username,
        password,
        locationFingerprintSource: location.locationFingerprintSource,
        locationLabel: location.locationLabel,
      })
      .pipe(
        map((res) => {
          if (res.mfaRequired) {
            throw new Error(res.message || 'MFA is required for this user.');
          }
          if (!res.token?.token) {
            throw new Error('Login failed: token was not returned.');
          }
          return res.token;
        }),
        tap((token) => this.applyToken(token)),
      );
  }

  applyToken(token: AuthTokenDto): void {
    localStorage.setItem(TOKEN_KEY, token.token);
    localStorage.setItem(USER_KEY, token.username);
    localStorage.setItem(ROLE_KEY, token.role);
    this.authState$.next(true);
  }

  logout(redirectToLogin = true): void {
    const token = this.token;
    const location = buildLoginLocationContext();
    const finish = () => {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(ROLE_KEY);
      this.authState$.next(false);
      if (redirectToLogin) {
        this.router.navigate(['/login']);
      }
    };

    if (!token) {
      finish();
      return;
    }

    this.http
      .post(`${this.apiBase}/logout`, {
        locationFingerprintSource: location.locationFingerprintSource,
        locationLabel: location.locationLabel,
      })
      .pipe(finalize(finish))
      .subscribe({ error: () => {} });
  }

  private hasStoredToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }
}
