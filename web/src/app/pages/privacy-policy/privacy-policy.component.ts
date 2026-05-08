import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-privacy-policy',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule],
  templateUrl: './privacy-policy.component.html',
  styleUrl: './privacy-policy.component.scss',
})
export class PrivacyPolicyComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private fragmentSub?: Subscription;

  ngOnInit(): void {
    this.fragmentSub = this.route.fragment
      .pipe(filter((f): f is string => !!f))
      .subscribe((frag) => this.scrollToAnchor(frag));
  }

  ngOnDestroy(): void {
    this.fragmentSub?.unsubscribe();
  }

  private scrollToAnchor(id: string): void {
    queueMicrotask(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  get authenticated(): boolean {
    return this.auth.isAuthenticated();
  }
}
