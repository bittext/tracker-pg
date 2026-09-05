import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ManagementWorkPanelComponent } from '../management/management-work-panel/management-work-panel.component';

@Component({
  selector: 'app-life-work',
  standalone: true,
  imports: [CommonModule, RouterLink, ManagementWorkPanelComponent],
  templateUrl: './life-work.component.html',
  styleUrl: './life-work.component.scss',
})
export class LifeWorkComponent {}
