import { Component } from '@angular/core';
import { ManagementComponent } from '../management/management.component';

@Component({
  selector: 'app-life-work',
  standalone: true,
  imports: [ManagementComponent],
  templateUrl: './life-work.component.html',
  styleUrl: './life-work.component.scss',
})
export class LifeWorkComponent {}
