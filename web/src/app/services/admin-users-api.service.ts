import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import {
  AdminCreateUserRequest,
  AdminCreatedUserResponse,
  AdminUpdateUserRequest,
  AdminUserListItemDto,
} from '../models/admin-users.models';

@Injectable({ providedIn: 'root' })
export class AdminUsersApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/users`;

  listUsers() {
    return this.http.get<AdminUserListItemDto[]>(this.root);
  }

  createUser(body: AdminCreateUserRequest) {
    return this.http.post<AdminCreatedUserResponse>(this.root, body);
  }

  updateUser(id: number, body: AdminUpdateUserRequest) {
    return this.http.patch<AdminUserListItemDto>(`${this.root}/${id}`, body);
  }
}
