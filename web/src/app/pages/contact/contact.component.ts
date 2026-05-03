import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MeMemberApiService } from '../../services/me-member-api.service';
import { formatHttpErrorDetail } from '../../util/http-error';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
  ],
  templateUrl: './contact.component.html',
  styleUrl: './contact.component.scss',
})
export class ContactComponent implements OnInit {
  private readonly api = inject(MeMemberApiService);
  private readonly snackBar = inject(MatSnackBar);

  displayName = '';
  subject = '';
  details = '';
  sending = false;

  ngOnInit(): void {
    this.api.getMemberProfile().subscribe({
      next: (p) => {
        this.displayName = (p.nickname ?? '').trim();
      },
      error: () => {},
    });
  }

  submit(): void {
    const sub = this.subject.trim();
    const det = this.details.trim();
    if (!sub) {
      this.snackBar.open('Enter a subject.', undefined, { duration: 3500 });
      return;
    }
    if (!det) {
      this.snackBar.open('Enter the details of your message.', undefined, { duration: 3500 });
      return;
    }
    if (sub.length > 200) {
      this.snackBar.open('Subject must be at most 200 characters.', undefined, { duration: 4000 });
      return;
    }
    if (det.length > 12000) {
      this.snackBar.open('Details must be at most 12,000 characters.', undefined, { duration: 4000 });
      return;
    }
    const dn = this.displayName.trim();
    if (dn.length > 80) {
      this.snackBar.open('Display name must be at most 80 characters.', undefined, { duration: 4000 });
      return;
    }
    this.sending = true;
    this.api
      .submitContactFeedback({
        displayName: dn || null,
        subject: sub,
        details: det,
      })
      .subscribe({
        next: () => {
          this.sending = false;
          this.subject = '';
          this.details = '';
          this.snackBar.open('Feedback sent. Thank you.', undefined, { duration: 5000 });
        },
        error: (e) => {
          this.sending = false;
          this.snackBar.open(`Could not send: ${formatHttpErrorDetail(e)}`, 'Dismiss', { duration: 12000 });
        },
      });
  }
}
