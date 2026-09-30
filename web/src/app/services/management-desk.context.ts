import { Injectable, signal } from '@angular/core';
import type { ManagementDesk } from '../models/management-desk';

@Injectable({ providedIn: 'root' })
export class ManagementDeskContext {
  readonly desk = signal<ManagementDesk>('LIFE');
}
