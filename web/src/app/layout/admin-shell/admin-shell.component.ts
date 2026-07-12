import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Thin wrapper for admin routes — child pages own their chrome. */
@Component({
  selector: 'app-admin-shell',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './admin-shell.component.html',
  styleUrl: './admin-shell.component.scss',
})
export class AdminShellComponent {}
