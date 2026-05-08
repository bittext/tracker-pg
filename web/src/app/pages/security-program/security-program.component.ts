import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-security-program',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule],
  templateUrl: './security-program.component.html',
  styleUrl: './security-program.component.scss',
})
export class SecurityProgramComponent {
  private readonly auth = inject(AuthService);

  get authenticated(): boolean {
    return this.auth.isAuthenticated();
  }
}
