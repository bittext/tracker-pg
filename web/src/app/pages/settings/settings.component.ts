import { Component } from '@angular/core';
import { MemberProfilePanelComponent } from '../member/member-profile-panel.component';
import { PageHeaderComponent } from '../../components/page-header/page-header.component';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [PageHeaderComponent, MemberProfilePanelComponent],
  template: `
    <app-page-header
      title="Settings"
      subtitle="Your member profile, contact details, and onboarding information."
    />
    <app-member-profile-panel />
  `,
})
export class SettingsComponent {}
