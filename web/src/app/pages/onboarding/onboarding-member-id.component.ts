import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MeOnboardingStatusDto } from '../../models/member.models';
import { MeMemberApiService } from '../../services/me-member-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

@Component({
  selector: 'app-onboarding-member-id',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatSnackBarModule],
  templateUrl: './onboarding-member-id.component.html',
  styleUrl: './onboarding-member-id.component.scss',
})
export class OnboardingMemberIdComponent implements OnInit {
  private readonly api = inject(MeMemberApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  status: MeOnboardingStatusDto | null = null;
  loading = true;
  finishing = false;

  ngOnInit(): void {
    this.api.getOnboardingStatus().subscribe({
      next: (s) => {
        this.status = s;
        this.loading = false;
      },
      error: (e) => {
        this.loading = false;
        this.snackBar.open(`Could not load status: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 9000 });
      },
    });
  }

  formatMemberId(id: number): string {
    const s = String(id);
    return s.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  }

  copyId(): void {
    const id = this.status?.memberPublicId;
    if (id == null) {
      return;
    }
    void navigator.clipboard.writeText(String(id)).then(
      () => this.snackBar.open('Member ID copied', undefined, { duration: 2500 }),
      () => this.snackBar.open('Copy failed — select the number and copy manually.', undefined, { duration: 4000 }),
    );
  }

  continue(): void {
    this.finishing = true;
    this.api.completeOnboarding().subscribe({
      next: () => {
        this.finishing = false;
        this.router.navigate(['/welcome']);
      },
      error: (e) => {
        this.finishing = false;
        this.snackBar.open(`Could not finish: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 9000 });
      },
    });
  }
}
