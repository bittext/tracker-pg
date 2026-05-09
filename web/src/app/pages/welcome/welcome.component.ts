import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../services/auth.service';
import { MeMemberApiService } from '../../services/me-member-api.service';

@Component({
  selector: 'app-welcome',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  templateUrl: './welcome.component.html',
  styleUrl: './welcome.component.scss',
})
export class WelcomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly meMemberApi = inject(MeMemberApiService);

  /** Prefer saved profile first name; otherwise username; finally a neutral fallback. */
  displayName = 'there';

  ngOnInit(): void {
    const u = (this.auth.username ?? '').trim();
    this.displayName = u || 'there';
    this.meMemberApi.getMemberProfile().subscribe({
      next: (p) => {
        const first = (p.firstName ?? '').trim();
        if (first) {
          this.displayName = first;
        }
      },
    });
  }
}
