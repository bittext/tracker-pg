import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { StepUpDialogComponent } from '../components/step-up-dialog/step-up-dialog.component';

export interface StepUpTokenDto {
  stepUpToken: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class StepUpService {
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly apiBase = `${environment.apiBaseUrl}/api/auth`;

  /** Prompt for password and exchange for a short-lived step-up token. */
  async promptAndIssueToken(): Promise<string | null> {
    const password = await firstValueFrom(
      this.dialog
        .open(StepUpDialogComponent, {
          width: 'min(92vw, 24rem)',
          disableClose: true,
          autoFocus: true,
        })
        .afterClosed(),
    );
    if (!password || typeof password !== 'string') {
      return null;
    }
    const res = await firstValueFrom(
      this.http.post<StepUpTokenDto>(`${this.apiBase}/step-up`, { password }),
    );
    return res.stepUpToken;
  }
}
