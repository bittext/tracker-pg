import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MemberProfilePanelComponent } from '../member/member-profile-panel.component';
import { PageHeaderComponent } from '../../components/page-header/page-header.component';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterLink, MatButtonModule, PageHeaderComponent, MemberProfilePanelComponent],
  template: `
    <app-page-header
      title="Settings"
      subtitle="Your member profile and account details."
      [hasActions]="true"
    >
      <a pageHeaderActions mat-stroked-button routerLink="/life/contact">Contact us</a>
    </app-page-header>
    <app-member-profile-panel />
  `,
})
export class SettingsComponent {}
