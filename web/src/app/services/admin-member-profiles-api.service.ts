import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AdminMemberProfileDetailDto, AdminMemberProfileListItemDto } from '../models/admin-users.models';

@Injectable({ providedIn: 'root' })
export class AdminMemberProfilesApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/member-profiles`;

  /** Members who have saved a member profile (member public id assigned). Admin only. */
  listWithSavedProfile() {
    return this.http.get<AdminMemberProfileListItemDto[]>(this.root);
  }

  /** Read-only profile for another user. Admin only. */
  getMemberProfile(userId: number) {
    return this.http.get<AdminMemberProfileDetailDto>(`${this.root}/${userId}`);
  }
}
