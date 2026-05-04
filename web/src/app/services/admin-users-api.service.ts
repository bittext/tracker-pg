import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../environments/environment';
import { AdminCreateUserRequest, AdminCreatedUserResponse } from '../models/admin-users.models';

@Injectable({ providedIn: 'root' })
export class AdminUsersApiService {
  private readonly http = inject(HttpClient);
  private readonly root = `${environment.apiBaseUrl}/api/admin/users`;

  createUser(body: AdminCreateUserRequest) {
    return this.http.post<AdminCreatedUserResponse>(this.root, body);
  }
}
