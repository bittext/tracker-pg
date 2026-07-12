import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-step-up-dialog',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Confirm your password</h2>
    <mat-dialog-content>
      <p class="muted">Re-enter your password to confirm this Markets action.</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Password</mat-label>
        <input
          matInput
          type="password"
          [(ngModel)]="password"
          name="stepUpPassword"
          autocomplete="current-password"
          (keyup.enter)="confirm()"
        />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="cancel()">Cancel</button>
      <button mat-flat-button color="primary" type="button" [disabled]="!password.trim()" (click)="confirm()">
        Confirm
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full-width {
      width: 100%;
    }
    .muted {
      color: var(--app-text-muted);
      font-size: 0.875rem;
      margin: 0 0 0.75rem;
    }
  `,
})
export class StepUpDialogComponent {
  private readonly ref = inject(MatDialogRef<StepUpDialogComponent, string | undefined>);

  password = '';

  cancel(): void {
    this.ref.close(undefined);
  }

  confirm(): void {
    const p = this.password.trim();
    if (!p) {
      return;
    }
    this.ref.close(p);
  }
}
